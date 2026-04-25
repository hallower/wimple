package kr.blogspot.charlie0301.wimple

import android.content.Context
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.Toast
import kr.blogspot.charlie0301.wimple.impl.IWimpleResponseListener
import kr.blogspot.charlie0301.wimple.impl.IWimpleStatusListener
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.model.Account
import kr.blogspot.charlie0301.wimple.model.AccountState
import kr.blogspot.charlie0301.wimple.model.Budget
import kr.blogspot.charlie0301.wimple.model.Entry
import kr.blogspot.charlie0301.wimple.model.Item
import kr.blogspot.charlie0301.wimple.model.Section
import kr.blogspot.charlie0301.wimple.model.UserInfo
import java.util.ArrayList

/**
 * Bundles the boilerplate of subscribing to [WimpleImpl] status/response events.
 *
 * Default behaviour for every callback: forward to [handler] using the matching
 * [CommandID] — mirrors WimpleActivity's "everything goes to mainHandler" pattern.
 *
 * Replace specific callbacks via the builder methods (e.g. [onAuthTempToken]); anything
 * not overridden falls back to the default forwarder. Defaults that previously did
 * nothing (auth callbacks, postNews, network connection) stay no-op.
 */
class WimpleListenerBinder(
    private val appContext: Context,
    private val handler: Handler
) {

    // ----- status callback slots (initialised to default behaviour) -----

    private var loggedIn: (Boolean) -> Unit = { status ->
        send(if (status) CommandID.WIMPLE_LOGGIN_SUCCESS else CommandID.WIMPLE_LOGGIN_FAILED, "")
    }
    private var loggedOut: () -> Unit = { send(CommandID.WIMPLE_LOGGOUT, "") }
    private var profilePictureUpdated: () -> Unit = {
        send(CommandID.WIMPLE_PROFILE_PICTURE_UPDATED, "")
    }
    private var networkEstablished: () -> Unit = {}
    private var networkLost: () -> Unit = {}

    // ----- response callback slots -----

    // Auth callbacks default to no-op: only the splash flow cares about them.
    private var authTempToken: (Boolean, String) -> Unit = { _, _ -> }
    private var authAccessToken: (Boolean, Map<String, String>) -> Unit = { _, _ -> }

    private var userInfo: (Boolean, UserInfo) -> Unit = { status, info ->
        if (status) {
            Log.i(LOG_TAG, info.toString())
            send(CommandID.UPDATE_USER_INFO, 1, 0, info)
        } else {
            Toast.makeText(appContext, "Login Failed!!!!", Toast.LENGTH_LONG).show()
        }
    }
    private var allSection: (Boolean, Collection<Section>) -> Unit = { status, list ->
        send(CommandID.GET_ALL_SECTION_RECEIVED, status.toFlag(), 0, list)
    }
    private var allAccount: (Boolean, Collection<Account>) -> Unit = { status, list ->
        send(CommandID.GET_ALL_ACCOUNT_RECEIVED, status.toFlag(), 0, list)
    }
    private var entries: (Boolean, Collection<Entry>) -> Unit = { status, list ->
        send(CommandID.GET_ENTRIES_RECEIVED, status.toFlag(), 0, list)
    }
    private var latestEntries: (Boolean, Collection<Entry>) -> Unit = { status, list ->
        send(CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED, status.toFlag(), 0, list)
    }
    private var makeEntry: (Boolean, String) -> Unit = { status, entryDate ->
        send(CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED, status.toFlag(), 0, entryDate)
    }
    private var frequentItems: (Boolean, Collection<Item>) -> Unit = { status, list ->
        send(CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED, status.toFlag(), 0, list)
    }
    private var latestItems: (Boolean, Collection<Item>) -> Unit = { status, list ->
        send(CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED, status.toFlag(), 0, list)
    }
    private var modifyEntry: (Boolean, Entry) -> Unit = { status, entry ->
        send(CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED, status.toFlag(), 0, entry)
    }
    private var monthlyItems: (Boolean, ArrayList<Item>) -> Unit = { status, list ->
        send(CommandID.GET_MONTHLY_ITEMS_RESPONSE_RECEIVED, status.toFlag(), 0, list)
    }
    private var removeEntry: (Boolean, String) -> Unit = { status, id ->
        send(CommandID.REMOVE_ENTRY_RESPONSE_RECEIVED, status.toFlag(), 0, id)
    }
    private var removeMonthlyItem: (Boolean, String) -> Unit = { status, id ->
        send(CommandID.REMOVE_MONTHLY_ITEMS_RESPONSE_RECEIVED, status.toFlag(), 0, id)
    }
    private var financialState: (Boolean, Collection<AccountState>) -> Unit = { status, list ->
        send(CommandID.GET_FINANCIAL_STATE_RESPONSE_RECEIVED, status.toFlag(), 0, list)
    }
    private var incomeAndExpense: (Boolean, Collection<AccountState>) -> Unit = { status, list ->
        send(CommandID.GET_INCOME_AND_EXPENSE_RESPONSE_RECEIVED, status.toFlag(), 0, list)
    }
    private var budget: (Boolean, Boolean, Map<String, Budget>) -> Unit = { status, isIncome, list ->
        send(CommandID.GET_BUDGET_RESPONSE_RECEIVED, status.toFlag(), isIncome.toFlag(), list)
    }
    private var postNews: (Boolean, String) -> Unit = { _, _ -> }
    private var postPayments: (Boolean) -> Unit = { status ->
        send(CommandID.POST_PAYMENT_RESPONSE_RECEIVED, status.toFlag(), 0, "")
    }

    // ----- builder methods -----

    fun onLoggedIn(block: (Boolean) -> Unit) = apply { loggedIn = block }
    fun onLoggedOut(block: () -> Unit) = apply { loggedOut = block }
    fun onProfilePictureUpdated(block: () -> Unit) = apply { profilePictureUpdated = block }
    fun onNetworkEstablished(block: () -> Unit) = apply { networkEstablished = block }
    fun onNetworkLost(block: () -> Unit) = apply { networkLost = block }

    fun onAuthTempToken(block: (Boolean, String) -> Unit) = apply { authTempToken = block }
    fun onAuthAccessToken(block: (Boolean, Map<String, String>) -> Unit) = apply { authAccessToken = block }
    fun onUserInfo(block: (Boolean, UserInfo) -> Unit) = apply { userInfo = block }
    fun onAllSection(block: (Boolean, Collection<Section>) -> Unit) = apply { allSection = block }
    fun onAllAccount(block: (Boolean, Collection<Account>) -> Unit) = apply { allAccount = block }
    fun onEntries(block: (Boolean, Collection<Entry>) -> Unit) = apply { entries = block }
    fun onLatestEntries(block: (Boolean, Collection<Entry>) -> Unit) = apply { latestEntries = block }
    fun onMakeEntry(block: (Boolean, String) -> Unit) = apply { makeEntry = block }
    fun onFrequentItems(block: (Boolean, Collection<Item>) -> Unit) = apply { frequentItems = block }
    fun onLatestItems(block: (Boolean, Collection<Item>) -> Unit) = apply { latestItems = block }
    fun onModifyEntry(block: (Boolean, Entry) -> Unit) = apply { modifyEntry = block }
    fun onMonthlyItems(block: (Boolean, ArrayList<Item>) -> Unit) = apply { monthlyItems = block }
    fun onRemoveEntry(block: (Boolean, String) -> Unit) = apply { removeEntry = block }
    fun onRemoveMonthlyItem(block: (Boolean, String) -> Unit) = apply { removeMonthlyItem = block }
    fun onFinancialState(block: (Boolean, Collection<AccountState>) -> Unit) = apply { financialState = block }
    fun onIncomeAndExpense(block: (Boolean, Collection<AccountState>) -> Unit) = apply { incomeAndExpense = block }
    fun onBudget(block: (Boolean, Boolean, Map<String, Budget>) -> Unit) = apply { budget = block }
    fun onPostNews(block: (Boolean, String) -> Unit) = apply { postNews = block }
    fun onPostPayments(block: (Boolean) -> Unit) = apply { postPayments = block }

    /**
     * Push the configured listeners onto [WimpleImpl] and inject the application context.
     */
    fun attach(): WimpleListenerBinder {
        val wimple = WimpleImpl.getInstance()
        wimple.setApplicationContext(appContext)
        wimple.setStatusListener(buildStatusListener())
        wimple.setResponseListener(buildResponseListener())
        return this
    }

    // ----- listener adapters -----

    private fun buildStatusListener(): IWimpleStatusListener = object : IWimpleStatusListener {
        override fun onLoggedIn(status: Boolean) = loggedIn(status)
        override fun onLoggedOut() = loggedOut()
        override fun onProfilePictureUpdated() = profilePictureUpdated()
        override fun onNetworkConnectionEstablished() = networkEstablished()
        override fun onNetworkConnectionLost() = networkLost()
    }

    private fun buildResponseListener(): IWimpleResponseListener = object : IWimpleResponseListener {
        override fun onGetAuthTempToken(status: Boolean, tempToken: String) =
            authTempToken(status, tempToken)
        override fun onGetAuthAccessToken(status: Boolean, result: Map<String, String>) =
            authAccessToken(status, result)
        override fun onGetUserInfoResponseReceived(status: Boolean, info: UserInfo) =
            userInfo(status, info)
        override fun onGetAllSectionResponseReceived(status: Boolean, list: Collection<Section>) =
            allSection(status, list)
        override fun onGetAllAccountResponseReceived(status: Boolean, list: Collection<Account>) =
            allAccount(status, list)
        override fun onGetEntriesResponseReceived(status: Boolean, list: Collection<Entry>) =
            entries(status, list)
        override fun onGetLatestEntriesResponseReceived(status: Boolean, list: Collection<Entry>) =
            latestEntries(status, list)
        override fun onMakeEntryResponseReceived(status: Boolean, entryDate: String) =
            makeEntry(status, entryDate)
        override fun onModifyEntryResponseReceived(status: Boolean, entry: Entry) =
            modifyEntry(status, entry)
        override fun onRemoveEntryResponseReceived(status: Boolean, id: String) =
            removeEntry(status, id)
        override fun onPostPaymentsResponseReceived(status: Boolean) = postPayments(status)
        override fun onGetFrequentItemsResponseReceived(status: Boolean, list: Collection<Item>) =
            frequentItems(status, list)
        override fun onGetLatestItemsResponseReceived(status: Boolean, list: Collection<Item>) =
            latestItems(status, list)
        override fun onGetMonthlyItemsResponseReceived(status: Boolean, list: ArrayList<Item>) =
            monthlyItems(status, list)
        override fun onRemoveMonthlyItemResponseReceived(status: Boolean, id: String) =
            removeMonthlyItem(status, id)
        override fun onGetFinancialStateResponseReceived(status: Boolean, list: Collection<AccountState>) =
            financialState(status, list)
        override fun onGetIncomeAndExpenseResponseReceived(status: Boolean, list: Collection<AccountState>) =
            incomeAndExpense(status, list)
        override fun onGetBudgetResponseReceived(status: Boolean, isIncome: Boolean, list: Map<String, Budget>) =
            budget(status, isIncome, list)
        override fun onPostNewsResponseReceived(status: Boolean, id: String) = postNews(status, id)
    }

    // ----- handler send helpers (matches the historical sm() in WimpleActivity.Companion) -----

    private fun send(cmd: Int, obj: Any) {
        handler.sendMessage(Message.obtain(handler, cmd, 1, 0, obj))
    }

    private fun send(cmd: Int, a1: Int, a2: Int, obj: Any) {
        handler.sendMessage(Message.obtain(handler, cmd, a1, a2, obj))
    }

    private fun Boolean.toFlag(): Int = if (this) 1 else 0

    companion object {
        private const val LOG_TAG = "WimpleListenerBinder"
    }
}
