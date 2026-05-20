package kr.blogspot.charlie0301.wimple

import android.os.Bundle
import androidx.fragment.app.Fragment

/**
 * Large-screen pair: transaction list on the left, transaction insert on the right.
 * Activated by the drawer entries `menu_transaction_list` or `menu_transaction_insert`
 * when [R.bool.isLargeScreen] is true.
 */
class TransactionPairFragment : TwoPaneFragment() {
    override fun createLeftFragment(): Fragment = TransactionListFragment()
    override fun createRightFragment(): Fragment = TransactionInsertFragment().also {
        // Forward this host pair fragment's arguments to the insert pane so the AI
        // review-flow prefill keys (title / amount / account ids / review-session
        // identifiers) reach [TransactionInsertFragment.consumePrefillArguments] on
        // first creation. WimpleActivity.replaceWimpleFragment stamps the bundle
        // onto the pair (the currentFragment) but the inner fragment is constructed
        // here without args — without this copy the prefill never lands. Defensive
        // copy so the inner's arg-clear (single-shot read) doesn't disturb us.
        arguments?.let { hostArgs -> it.arguments = Bundle(hostArgs) }
    }
}
