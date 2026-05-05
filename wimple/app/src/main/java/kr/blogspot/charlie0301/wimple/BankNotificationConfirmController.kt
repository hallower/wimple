package kr.blogspot.charlie0301.wimple

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kr.blogspot.charlie0301.wimple.impl.BankNotificationClassifier
import kr.blogspot.charlie0301.wimple.impl.IWimpleResponseListener
import kr.blogspot.charlie0301.wimple.impl.LocalReviewQueue
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.db.MerchantMappingDBHandler
import kr.blogspot.charlie0301.wimple.model.Account
import java.util.Calendar

/**
 * Drives one-tap [확정] and bulk-confirm from the review queue. The form-driven path
 * (TransactionInsertFragment) handles its own makeEntry response via the message handler;
 * we don't have that route here because we don't show the form, so this proxies
 * [WimpleImpl.setResponseListener] for the duration of a confirm round (or batch) and
 * restores the original when done. A single proxy is installed once per batch — the
 * controller tracks "currently confirming" externally so we don't have to swap listeners
 * between rows.
 *
 * Caller responsibilities:
 *  - Run on the main thread.
 *  - Call [release] in the activity's onPause / onDestroy so a stale proxy doesn't
 *    outlive the surface that owns it.
 */
class BankNotificationConfirmController(
    private val activity: BankNotificationReviewActivity,
    private val onComplete: () -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<Pair<LocalReviewQueue.ReviewItem, BankNotificationClassifier.Result>>()
    private var current: PendingConfirm? = null
    private var savedListener: IWimpleResponseListener? = null
    /** Outstanding makeEntry didn't respond fast enough — bail out so the proxy gets restored. */
    private val timeoutRunnable = Runnable { onResponse(false, "<timeout>") }

    private data class PendingConfirm(
        val item: LocalReviewQueue.ReviewItem,
        val result: BankNotificationClassifier.Result,
        val lAcc: Account,
        val rAcc: Account
    )

    /** Confirm a single READY row; subsequent rows added via [confirmBatch] will be ignored if a confirm is already running. */
    fun confirmOne(item: LocalReviewQueue.ReviewItem, result: BankNotificationClassifier.Result) {
        if (current != null || pending.isNotEmpty()) {
            toast(activity.getString(R.string.bank_noti_review_confirm_busy))
            return
        }
        pending.addLast(item to result)
        ensureProxyInstalled()
        startNext()
    }

    /** Process a list of (item, result) sequentially; prior queue is replaced. */
    fun confirmBatch(items: List<Pair<LocalReviewQueue.ReviewItem, BankNotificationClassifier.Result>>) {
        if (current != null || pending.isNotEmpty()) {
            toast(activity.getString(R.string.bank_noti_review_confirm_busy))
            return
        }
        if (items.isEmpty()) return
        pending.addAll(items)
        ensureProxyInstalled()
        startNext()
    }

    fun release() {
        mainHandler.removeCallbacks(timeoutRunnable)
        restoreListener()
        pending.clear()
        current = null
    }

    // -------------------- internals --------------------

    private fun ensureProxyInstalled() {
        if (savedListener != null) return
        val original = WimpleImpl.getResponseListener() ?: run {
            Log.w(LOG_TAG, "no upstream listener registered; aborting")
            toast(activity.getString(R.string.bank_noti_review_confirm_failed))
            return
        }
        savedListener = original
        WimpleImpl.getInstance()?.setResponseListener(MakeEntryProxy(original) { status, entryDate ->
            // Hop to main and let the controller dispatch — proxy callback runs on whatever
            // thread WimpleImpl's handler thread happens to be on.
            mainHandler.post { onResponse(status, entryDate) }
        })
    }

    private fun restoreListener() {
        val saved = savedListener ?: return
        WimpleImpl.getInstance()?.setResponseListener(saved)
        savedListener = null
    }

    private fun startNext() {
        if (pending.isEmpty()) {
            restoreListener()
            onComplete()
            return
        }
        val (item, result) = pending.removeFirst()

        // Validate the row has everything we need to fire-and-forget. Anything missing → skip
        // and continue (don't fail the whole batch on one bad row).
        val merchant = result.merchant?.takeIf { it.isNotBlank() }
        val kind = result.kind?.takeIf { it.isNotBlank() }
        val amount = result.amount?.takeIf { it > 0.0 }
        val lId = result.leftAccountId?.takeIf { it.isNotBlank() }
        val rId = result.rightAccountId?.takeIf { it.isNotBlank() }
        if (merchant == null || kind == null || amount == null || lId == null || rId == null) {
            toast(activity.getString(R.string.bank_noti_review_confirm_skip_invalid))
            startNext()
            return
        }

        val wimple = WimpleImpl.getInstance() ?: run {
            toast(activity.getString(R.string.bank_noti_review_confirm_failed))
            release()
            onComplete()
            return
        }
        val accounts = wimple.cachedAccounts
        val lAcc = accounts.firstOrNull { it.id == lId }
        val rAcc = accounts.firstOrNull { it.id == rId }
        if (lAcc == null || rAcc == null) {
            // Suggested account no longer exists — could happen after a rename in Whooing.
            // Skip and let the user fix it via [수동 입력].
            toast(activity.getString(R.string.bank_noti_review_confirm_skip_invalid))
            startNext()
            return
        }

        current = PendingConfirm(item, result, lAcc, rAcc)
        // Use today as the entry date — the bank notification timestamp is "when the message
        // arrived", which can differ from the actual transaction date for delayed pushes.
        // Keeping it simple matches the existing manual-entry default.
        val today = Calendar.getInstance().timeInMillis
        val ok = wimple.makeEntry(today, lAcc, rAcc, merchant, amount, "")
        if (!ok) {
            current = null
            toast(activity.getString(R.string.bank_noti_review_confirm_failed))
            startNext()
            return
        }
        // Belt-and-suspenders: if the response never lands (lost listener, server hang) we'd
        // be stuck in-flight forever. Posted timeout fires the same path as a real failure.
        mainHandler.postDelayed(timeoutRunnable, RESPONSE_TIMEOUT_MS)
    }

    private fun onResponse(status: Boolean, entryDate: String) {
        mainHandler.removeCallbacks(timeoutRunnable)
        val ctx = current ?: return
        current = null

        if (status) {
            val merchant = ctx.result.merchant.orEmpty()
            val kind = ctx.result.kind.orEmpty()
            if (merchant.isNotBlank() && kind.isNotBlank()) {
                MerchantMappingDBHandler(activity).upsert(
                    merchant, kind,
                    ctx.lAcc.what, ctx.lAcc.id,
                    ctx.rAcc.what, ctx.rAcc.id
                )
            }
            LocalReviewQueue.removeById(activity, ctx.item.id)
            toast(activity.getString(R.string.bank_noti_review_confirm_success_format, merchant))
        } else {
            toast(activity.getString(R.string.bank_noti_review_confirm_failed))
        }

        // Refresh after every row so the user sees the queue shrink in real time during a
        // batch. The activity's adapter pulls from the queue store, so this is enough.
        onComplete()

        if (pending.isNotEmpty()) {
            startNext()
        } else {
            restoreListener()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }

    /** Wraps the upstream response listener and intercepts only the makeEntry callback. */
    private class MakeEntryProxy(
        delegate: IWimpleResponseListener,
        private val onMakeEntry: (Boolean, String) -> Unit
    ) : IWimpleResponseListener by delegate {
        override fun onMakeEntryResponseReceived(status: Boolean, entryDate: String) {
            // Don't forward — the upstream listener was set up for whichever fragment hosts
            // the manual-entry form, and none of the other listeners care about a makeEntry
            // initiated from the review activity. Forwarding would surface the success toast
            // twice and re-clear forms that aren't the active surface.
            onMakeEntry(status, entryDate)
        }
    }

    companion object {
        private const val LOG_TAG = "ConfirmController"
        private const val RESPONSE_TIMEOUT_MS = 30_000L
    }
}
