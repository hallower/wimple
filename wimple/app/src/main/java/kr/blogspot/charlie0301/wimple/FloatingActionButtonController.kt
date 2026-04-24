package kr.blogspot.charlie0301.wimple

import android.util.Log
import android.view.MotionEvent
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
        fab.post { restorePosition() }
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

    private fun savePosition(x: Float, y: Float) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
            .putFloat(KEY_FAB_POS_X, x)
            .putFloat(KEY_FAB_POS_Y, y)
            .apply()
    }

    private fun restorePosition() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
        val savedX = sharedPref.getFloat(KEY_FAB_POS_X, Float.MIN_VALUE)
        if (savedX == Float.MIN_VALUE) return

        val parent = fab.parent as ViewGroup
        val savedY = sharedPref.getFloat(KEY_FAB_POS_Y, 0f)
        fab.x = savedX.coerceIn(0f, (parent.width - fab.width).toFloat())
        fab.y = savedY.coerceIn(0f, (parent.height - fab.height).toFloat())
    }

    companion object {
        private const val LOG_TAG = "FabController"
        private const val KEY_FAB_POS_X = "fab_pos_x"
        private const val KEY_FAB_POS_Y = "fab_pos_y"
    }
}
