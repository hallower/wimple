package kr.blogspot.charlie0301.wimple

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment

/**
 * Maps navigation drawer menu item IDs to the Fragment that should occupy the content
 * pane. Adding a new screen = one row here, no changes to routing code.
 *
 * The factory returns a fresh Fragment instance on every call, matching the previous
 * if/else chain that did `XxxFragment()` for each navigation tap.
 */
enum class MenuFragment(
    @param:IdRes val menuId: Int,
    val factory: () -> Fragment
) {
    TRANSACTION_INSERT(R.id.menu_transaction_insert,         ::TransactionInsertFragment),
    TRANSACTION_LIST  (R.id.menu_transaction_list,           ::TransactionListFragment),
    FINANCIAL_OVERVIEW(R.id.menu_financial_overview,         ::FinancialStateSummaryFragment),
    SAVING            (R.id.menu_saving,                     ::SavingStateSummaryFragment),
    DEBT              (R.id.menu_debt,                       ::DebtStateSummaryFragment),
    INCOME_EXPENSE    (R.id.menu_income_expense_overview,    ::IncomeExpenseSummaryFragment),
    INCOME            (R.id.menu_income,                     ::IncomeSummaryFragment),
    EXPENSE           (R.id.menu_expense,                    ::ExpenseSummaryFragment),
    PREFERENCE        (R.id.menu_preference,                 ::SettingsFragment);

    companion object {
        private val byMenuId: Map<Int, MenuFragment> = entries.associateBy { it.menuId }
        fun fromMenuId(@IdRes id: Int): MenuFragment? = byMenuId[id]
    }
}
