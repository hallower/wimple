package kr.blogspot.charlie0301.wimple

import android.os.Bundle
import android.os.Message
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceHeaderFragmentCompat

/**
 * Two-pane settings host. Built on [PreferenceHeaderFragmentCompat], which uses a
 * SlidingPaneLayout under the hood:
 *
 *  - On wide screens (Galaxy Fold unfolded, sw600dp+ tablets): both panes visible at once.
 *    Tapping a header in the left pane swaps the right pane to that category's fragment
 *    without leaving the screen.
 *  - On narrow screens (phone, Galaxy Fold folded): the panes overlap. Tapping a header
 *    slides the detail in over the headers; system back gesture pops it.
 *
 * Bridges [IWimpleFragment] for [WimpleActivity]'s message routing — fans
 * [handleMessage] out to every attached child sub-fragment that opts in by implementing
 * the interface (currently only [GeneralSettingsFragment], for section list updates).
 *
 * Sub-fragments are registered via `app:fragment` attributes in
 * [R.xml.settings_headers]; adding a new category = add an XML entry + a fragment class.
 */
class SettingsHostFragment : PreferenceHeaderFragmentCompat(), IWimpleFragment {

    override fun onCreatePreferenceHeader(): PreferenceFragmentCompat = SettingsHeadersFragment()

    override fun handleMessage(msg: Message) {
        if (!isAdded) return
        // Both header and detail fragments live under childFragmentManager; the headers
        // fragment doesn't implement IWimpleFragment so it's naturally skipped.
        for (child in childFragmentManager.fragments) {
            if (child is IWimpleFragment) child.handleMessage(msg)
        }
    }

    override fun setActivityInstance(instance: WimpleActivity) {
        // Sub-fragments don't need the activity reference (the previous SettingsFragment
        // stored it but never read it); leave this as a no-op so we don't have to plumb
        // it through the child fragment manager's lifecycle callbacks.
    }

    /**
     * Header pane — just the list of category entries. Stays a separate class because
     * [PreferenceHeaderFragmentCompat] requires its `onCreatePreferenceHeader()` to
     * return a [PreferenceFragmentCompat] instance and not the host itself.
     */
    class SettingsHeadersFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_headers, rootKey)
        }
    }
}
