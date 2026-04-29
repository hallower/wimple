package kr.blogspot.charlie0301.wimple

import androidx.fragment.app.Fragment

/**
 * Large-screen pair: income summary on the left, expense summary on the right.
 * Activated by the drawer entries `menu_income` or `menu_expense` when
 * [R.bool.isLargeScreen] is true.
 */
class IncomeExpensePairFragment : TwoPaneFragment() {
    override fun createLeftFragment(): Fragment = IncomeSummaryFragment()
    override fun createRightFragment(): Fragment = ExpenseSummaryFragment()
}
