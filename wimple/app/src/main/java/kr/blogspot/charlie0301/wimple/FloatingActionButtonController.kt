package kr.blogspot.charlie0301.wimple

import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs

/**
 * Encapsulates all floating-action-button behavior previously living in [WimpleActivity]:
 *  - Per-fragment icon selection
 *  - Tap-to-rotate-fragment navigation
 *  - Drag-to-reposition with bounds clamping
 *  - Position persistence across launches
 *  - Computing the "next" and "one-after-next" pages based on user's floating-button preference
 *
 * The controller is deliberately decoupled from WimpleActivity's fragment-swap logic via two
 * callbacks: `onNavigateTo` fires when the user taps the FAB, and `currentMenuIdProvider` lets
 * this class read the hosting activity's current fragment id without coupling to it.
 *
 * Usage:
 *   val fabController = FloatingActionButtonController(
 *       activity, binding.fab,
 *       currentMenuIdProvider = { currentMenuID },
 *       onNavigateTo = { menuId -> replaceWimpleFragment(menuId) }
 *   )
 *   fabController.attach()
 *   // after a fragment swap from other sources (e.g. nav drawer):
 *   fabController.refreshIcon()
 */
class FloatingActionButtonController(
    private val activity: AppCompatActivity,
    private val fab: FloatingActionButton,
    private val currentMenuIdProvider: () -> Int,
    private val onNavigateTo: (menuId: Int) -> Unit
) {

    fun attach() {
        setIconForMenu(getNextPage().first)
        fab.setOnClickListener {
            val (next, afterNext) = getNextPage()
            onNavigateTo(next)
            setIconForMenu(afterNext)
        }
        makeDraggable()
        // First-layout case: registering the listener before the activity's initial
        // layout pass guarantees parent dimensions are populated when restorePosition
        // reads them.
        runAfterNextLayout { restorePosition() }
    }

    /** Called by the hosting activity when the fragment changed through a non-FAB path
     *  (e.g. the navigation drawer) so the FAB icon reflects the NEXT page, not the current one. */
    fun refreshIcon() {
        setIconForMenu(getNextPage().first)
    }

    private fun setIconForMenu(menuId: Int) {
        val iconRes = when (menuId) {
            R.id.menu_transaction_insert -> R.drawable.ic_fab_add
            R.id.menu_transaction_list -> R.drawable.ic_fab_list
            R.id.menu_financial_overview -> R.drawable.ic_fab_finalcial
            R.id.menu_saving -> R.drawable.ic_fab_saving
            R.id.menu_debt -> R.drawable.ic_fab_debt
            R.id.menu_income_expense_overview -> R.drawable.ic_fab_incexp
            R.id.menu_income -> R.drawable.ic_fab_inc
            R.id.menu_expense -> R.drawable.ic_fab_exp
            else -> R.drawable.ic_fab_add
        }
        fab.setImageResource(iconRes)
    }

    /**
     * Returns `(navigateTargetId, afterNextIconId)` — the fragment to swap to on the NEXT tap,
     * and the fragment that the FAB icon should then represent (i.e. the one after that).
     *
     * Rotation order follows [SettingsFragment.KEY_FLOATING_BUTTON] (a user-editable StringSet of
     * menu ids, prefixed with ordinal digits like `"3menu_financial_overview"` so sorting gives
     * the user-chosen order). Falls back to a two-step transaction-insert ↔ transaction-list
     * rotation if the preference is empty.
     */
    fun getNextPage(): Pair<Int, Int> {
        val currentMenuId = currentMenuIdProvider()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity.applicationContext)
        val selected = sharedPref.getStringSet(SettingsFragment.KEY_FLOATING_BUTTON, null)

        if (selected.isNullOrEmpty() || currentMenuId == 0) {
            Log.e(LOG_TAG, "Floating Button pages are not set!!!")
            return when (currentMenuId) {
                R.id.menu_transaction_insert ->
                    Pair(R.id.menu_transaction_list, R.id.menu_transaction_insert)
                R.id.menu_transaction_list ->
                    Pair(R.id.menu_transaction_insert, R.id.menu_transaction_list)
                else ->
                    Pair(R.id.menu_transaction_insert, R.id.menu_transaction_list)
            }
        }

        val sortedValues = selected.sortedBy { it }
        val currentEntryName = activity.resources.getResourceEntryName(currentMenuId)

        for (idx in sortedValues.indices) {
            // The preference values carry an order-prefix digit (e.g. "3menu_financial_overview"),
            // so compare from character 1 onwards.
            if (currentEntryName == sortedValues[idx].substring(1)) {
                val nextName = sortedValues[(idx + 1) % sortedValues.size].substring(1)
                val afterName = sortedValues[(idx + 2) % sortedValues.size].substring(1)
                Log.d(LOG_TAG, "Move = $nextName, Next = $afterName")
                return Pair(
                    activity.resources.getIdentifier(nextName, "id", activity.packageName),
                    activity.resources.getIdentifier(afterName, "id", activity.packageName)
                )
            }
        }

        if (sortedValues.size == 1) {
            val only = sortedValues[0].substring(1)
            Log.d(LOG_TAG, "Move = $only, Next = $only")
            val id = activity.resources.getIdentifier(only, "id", activity.packageName)
            return Pair(id, id)
        }

        val firstName = sortedValues[0].substring(1)
        val secondName = sortedValues[1].substring(1)
        Log.d(LOG_TAG, "Move = $firstName, Next = $secondName")
        return Pair(
            activity.resources.getIdentifier(firstName, "id", activity.packageName),
            activity.resources.getIdentifier(secondName, "id", activity.packageName)
        )
    }

    private fun makeDraggable() {
        var dX = 0f
        var dY = 0f
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false
        val dragThreshold = 8f

        fab.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Delta captures (touch point → view top-left) at press start, then the same
                    // delta is re-applied during MOVE. This makes the view follow the finger
                    // wherever it went, regardless of which point on the FAB the user grabbed.
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) {
                        val dx = abs(event.rawX - touchStartX)
                        val dy = abs(event.rawY - touchStartY)
                        if (dx > dragThreshold || dy > dragThreshold) isDragging = true
                    }
                    if (isDragging) {
                        val parent = view.parent as ViewGroup
                        view.x = (event.rawX + dX)
                            .coerceIn(0f, (parent.width - view.width).toFloat())
                        view.y = (event.rawY + dY)
                            .coerceIn(0f, (parent.height - view.height).toFloat())
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        savePosition(view.x, view.y)
                        // Consume the event so OnClickListener doesn't fire after a drag.
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    /**
     * Re-apply the persisted position against the current parent bounds. Called from
     * [WimpleActivity.onConfigurationChanged] so a fold/unfold (now handled live without
     * activity recreation, after configChanges was widened to cover screenLayout +
     * smallestScreenSize) still picks up the right per-state coordinates.
     *
     * Timing matters here. onConfigurationChanged fires BEFORE the framework has run a
     * new layout pass — observed via the system's `handleResized` log line landing a
     * few ms later, and the actual relayout tens of ms after that. So at this point:
     *
     *  - `parent.width` still reflects the OLD fold state (e.g. 1968px from unfolded
     *    when we're actually transitioning to the 1080px folded state).
     *  - The FAB's mLeft is still at the old gravity-end position.
     *  - androidx.core's doOnLayout short-circuits to "run immediately" because the
     *    view is already laid out and no layout has been requested yet — which means
     *    we'd compute translationX off the stale mLeft. The post-layout pass then
     *    updates mLeft to the new bounds without touching translationX, and the
     *    visible position lands far off-screen (left for fold, right for unfold).
     *
     * The fix: register an OnLayoutChangeListener and explicitly request a layout.
     * The listener fires *after* the next layout pass completes, by which time
     * `parent.width` and `mLeft` reflect the new config. Resetting translationX/Y
     * to 0 first prevents the FAB from briefly rendering at a wrong location during
     * the layout pass (gravity-end in the new bounds becomes the transient position
     * while we wait for restorePosition to run).
     */
    fun reapplyPosition() {
        fab.translationX = 0f
        fab.translationY = 0f
        runAfterNextLayout { restorePosition() }
        fab.requestLayout()
    }

    /**
     * Defer [action] until after the next layout pass completes. Unlike
     * `View.doOnLayout`, this never runs synchronously — even if the view is already
     * laid out, we wait for a fresh layout. That's the property we need around
     * config changes where parent bounds are about to update but haven't yet.
     */
    private inline fun runAfterNextLayout(crossinline action: () -> Unit) {
        fab.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                fab.removeOnLayoutChangeListener(this)
                action()
            }
        })
    }

    /**
     * Persist the FAB position under a key pair scoped to the current fold state.
     * R.bool.isLargeScreen splits "compact" (phone / Z Fold cover) from "expanded"
     * (tablet / Z Fold main). Each state remembers where the user parked the FAB
     * on it independently — dragging on the cover doesn't move the unfolded
     * placement, and vice versa.
     *
     * The drag handler already clamps during MOVE, but we re-clamp at save time
     * as a defensive guard so a future change to the drag path can't accidentally
     * persist out-of-bounds coordinates that would later resolve off-screen.
     */
    private fun savePosition(x: Float, y: Float) {
        val parent = fab.parent as? ViewGroup
        val pw = parent?.width?.toFloat() ?: 0f
        val ph = parent?.height?.toFloat() ?: 0f
        val clampedX = if (pw > 0f) x.coerceIn(0f, pw - fab.width) else x
        val clampedY = if (ph > 0f) y.coerceIn(0f, ph - fab.height) else y

        val (keyX, keyY) = positionKeysForCurrentConfig()
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
            .putFloat(keyX, clampedX)
            .putFloat(keyY, clampedY)
            .apply()
    }

    private fun restorePosition() {
        val parent = fab.parent as? ViewGroup ?: return
        val pw = parent.width.toFloat()
        val ph = parent.height.toFloat()
        if (pw <= 0f || ph <= 0f) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val (keyX, keyY) = positionKeysForCurrentConfig()

        // One-time migration from the pre-fold-aware single key pair. Whichever
        // fold state the user is in now inherits the legacy coordinates; the
        // other state stays unset until the user drags it (and meanwhile gets
        // the default bottom-end placement below).
        if (!prefs.contains(keyX) && prefs.contains(LEGACY_KEY_X)) {
            val legacyX = prefs.getFloat(LEGACY_KEY_X, 0f)
            val legacyY = prefs.getFloat(LEGACY_KEY_Y, 0f)
            prefs.edit()
                .putFloat(keyX, legacyX)
                .putFloat(keyY, legacyY)
                .remove(LEGACY_KEY_X)
                .remove(LEGACY_KEY_Y)
                .apply()
        }

        if (prefs.contains(keyX)) {
            val savedX = prefs.getFloat(keyX, 0f)
            val savedY = prefs.getFloat(keyY, 0f)
            fab.x = savedX.coerceIn(0f, pw - fab.width)
            fab.y = savedY.coerceIn(0f, ph - fab.height)
        } else {
            // No saved coords for this fold state. Reset to the layout's intent —
            // bottom-end with fab_margin — since the FAB has a manual x/y carried
            // over from the OTHER state and would otherwise sit at coordinates
            // that were correct for those bounds but are now off-screen or in
            // the middle of the smaller layout.
            val margin = activity.resources.getDimensionPixelSize(R.dimen.fab_margin).toFloat()
            fab.x = pw - fab.width - margin
            fab.y = ph - fab.height - margin
        }
    }

    private fun positionKeysForCurrentConfig(): Pair<String, String> {
        return if (activity.resources.getBoolean(R.bool.isLargeScreen)) {
            KEY_FAB_POS_X_EXPANDED to KEY_FAB_POS_Y_EXPANDED
        } else {
            KEY_FAB_POS_X_COMPACT to KEY_FAB_POS_Y_COMPACT
        }
    }

    companion object {
        private const val LOG_TAG = "FabController"

        // Pre-fold-aware single key pair. Migrated once into whichever per-state
        // pair matches the active fold mode at first restore.
        private const val LEGACY_KEY_X = "fab_pos_x"
        private const val LEGACY_KEY_Y = "fab_pos_y"

        // Per-fold-state absolute pixel coordinates. The compact pair is used when
        // R.bool.isLargeScreen is false (Z Fold cover, phones); the expanded pair
        // when true (Z Fold main display, tablets).
        private const val KEY_FAB_POS_X_COMPACT = "fab_pos_x_compact"
        private const val KEY_FAB_POS_Y_COMPACT = "fab_pos_y_compact"
        private const val KEY_FAB_POS_X_EXPANDED = "fab_pos_x_expanded"
        private const val KEY_FAB_POS_Y_EXPANDED = "fab_pos_y_expanded"
    }
}
