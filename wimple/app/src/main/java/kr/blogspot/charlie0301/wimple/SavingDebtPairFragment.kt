package kr.blogspot.charlie0301.wimple

import androidx.fragment.app.Fragment

/**
 * Large-screen pair: saving/asset summary on the left, debt summary on the right.
 * Activated by the drawer entries `menu_saving` or `menu_debt` when
 * [R.bool.isLargeScreen] is true.
 */
class SavingDebtPairFragment : TwoPaneFragment() {
    override fun createLeftFragment(): Fragment = SavingStateSummaryFragment()
    override fun createRightFragment(): Fragment = DebtStateSummaryFragment()
}
