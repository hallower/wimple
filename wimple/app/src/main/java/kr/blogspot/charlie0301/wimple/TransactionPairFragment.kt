package kr.blogspot.charlie0301.wimple

import androidx.fragment.app.Fragment

/**
 * Large-screen pair: transaction list on the left, transaction insert on the right.
 * Activated by the drawer entries `menu_transaction_list` or `menu_transaction_insert`
 * when [R.bool.isLargeScreen] is true.
 */
class TransactionPairFragment : TwoPaneFragment() {
    override fun createLeftFragment(): Fragment = TransactionListFragment()
    override fun createRightFragment(): Fragment = TransactionInsertFragment()
}
