package kr.blogspot.charlie0301.wimple

import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * Container fragment that hosts two top-level fragments side by side. Used on large
 * screens (sw600dp+) so the navigation drawer's paired entries — transaction
 * insert/list, saving/debt, income/expense — open both panes at once instead of
 * forcing the user to drawer-toggle between them.
 *
 * Bridges [IWimpleFragment] for the existing routing layer in [WimpleActivity]:
 * the activity treats the pair as a normal fragment, and this base class fans
 * out [setActivityInstance] / [handleMessage] to whichever child fragments are
 * currently attached.
 */
abstract class TwoPaneFragment : Fragment(), IWimpleFragment {

    /** Concrete subclasses pick the fragment that occupies the LEFT pane. */
    protected abstract fun createLeftFragment(): Fragment

    /** Concrete subclasses pick the fragment that occupies the RIGHT pane. */
    protected abstract fun createRightFragment(): Fragment

    /**
     * Activity reference forwarded by [WimpleActivity.replaceWimpleFragment] before
     * the fragment transaction runs. Stored so we can propagate it into the child
     * fragments when they're created in [onViewCreated], since
     * [setActivityInstance] arrives before the child FragmentManager has any
     * fragments to forward to.
     */
    private var pendingActivity: WimpleActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_two_pane, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // On config change the child fragments are restored automatically by the
        // child FragmentManager via the FrameLayout ids, so only seed them on a
        // fresh instantiation. Adding twice would stack two copies in each pane.
        if (savedInstanceState == null) {
            val left = createLeftFragment()
            val right = createRightFragment()
            // Wire activity ref *before* commit() so children see it during their
            // onCreate/onCreateView, matching the order WimpleActivity uses for
            // single-pane routing.
            pendingActivity?.let {
                if (left is IWimpleFragment) left.setActivityInstance(it)
                if (right is IWimpleFragment) right.setActivityInstance(it)
            }
            childFragmentManager.beginTransaction()
                .replace(R.id.pane_left, left, TAG_LEFT)
                .replace(R.id.pane_right, right, TAG_RIGHT)
                .commit()
        } else {
            // After config change: re-wire activity ref into the restored children.
            pendingActivity?.let { propagateActivity(it) }
        }
    }

    override fun setActivityInstance(instance: WimpleActivity) {
        pendingActivity = instance
        propagateActivity(instance)
    }

    override fun handleMessage(msg: Message) {
        // Fan out to both panes; each child decides whether the message is relevant.
        // Note: panes share the same Message reference. Existing handlers only read
        // its fields rather than mutating them, so a single instance is safe.
        for (tag in arrayOf(TAG_LEFT, TAG_RIGHT)) {
            (childFragmentManager.findFragmentByTag(tag) as? IWimpleFragment)
                ?.handleMessage(msg)
        }
    }

    private fun propagateActivity(instance: WimpleActivity) {
        for (tag in arrayOf(TAG_LEFT, TAG_RIGHT)) {
            (childFragmentManager.findFragmentByTag(tag) as? IWimpleFragment)
                ?.setActivityInstance(instance)
        }
    }

    companion object {
        private const val TAG_LEFT = "two_pane_left"
        private const val TAG_RIGHT = "two_pane_right"
    }
}
