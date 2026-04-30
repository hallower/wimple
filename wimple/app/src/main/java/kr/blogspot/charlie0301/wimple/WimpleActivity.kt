package kr.blogspot.charlie0301.wimple

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kr.blogspot.charlie0301.wimple.databinding.ActivityWimpleBinding
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.WidgetItem
import kr.blogspot.charlie0301.wimple.model.*
import java.util.*

class WimpleActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private var currentMenuID: Int = R.id.menu_transaction_insert
    private var currentFragment: androidx.fragment.app.Fragment? = null

    private lateinit var binding: ActivityWimpleBinding
    private lateinit var fabController: FloatingActionButtonController

    override fun onResume() {
        Log.i(LOG_TAG, "WimpleActivity - onResume!!!")
        setupWimpleImpl()
        super.onResume()
        kr.blogspot.charlie0301.wimple.impl.BankNotifications.retryIfPending(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(LOG_TAG, "WimpleActivity - onCreate!!!")
        super.onCreate(savedInstanceState)

        binding = ActivityWimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // GUI
        val toolBar = this.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)

        setSupportActionBar(toolBar)

        fabController = FloatingActionButtonController(
            activity = this,
            fab = findViewById(R.id.fab),
            currentMenuIdProvider = { currentMenuID },
            onNavigateTo = { menuId -> replaceWimpleFragment(menuId) }
        )
        fabController.attach()

        val toggle = ActionBarDrawerToggle(
                this, binding.drawerLayout, toolBar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.itemIconTintList = null
        binding.navView.setNavigationItemSelectedListener(this)

        // Logic
        setupHandler()

        if (savedInstanceState != null) {
            Log.i(LOG_TAG, "WimpleActivity - onCreate!!!, savedInstanceState is NOT null")
            val savedMenuId = savedInstanceState.getInt("currentMenuID", R.id.menu_transaction_insert)
            val restored = supportFragmentManager.findFragmentById(R.id.fragment_container)

            // After a config change that flips the screen-size class — most commonly
            // unfolding a Z Fold from cover (≈411dp) to main (≈750dp) — the previously
            // saved fragment may no longer match what newFragmentForMenu would produce
            // for this menu id (single-pane vs. two-pane). When the class differs, drop
            // the restored fragment and route fresh so the user actually gets the
            // form-factor-appropriate view instead of the previous one.
            val expectedClass = newFragmentForMenu(savedMenuId)?.javaClass
            if (restored is IWimpleFragment && restored.javaClass == expectedClass) {
                currentFragment = restored
                currentMenuID = savedMenuId
                Log.i(LOG_TAG, "WimpleActivity - onCreate!!!, restored fragment=$currentFragment, menuID=$currentMenuID")
                BiometricOnboarding.showIfNeeded(this)
                return
            }
            Log.i(LOG_TAG, "WimpleActivity - onCreate, screen class changed; re-routing menuID=$savedMenuId")
            installInitialFragment(savedMenuId)
            BiometricOnboarding.showIfNeeded(this)
            return
        }

        setDefaultFragment()
        BiometricOnboarding.showIfNeeded(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The CoordinatorLayout that anchors the FAB resizes on orientation/screenSize
        // changes (which are listed in configChanges, so the activity stays alive),
        // but the FAB's absolute x/y stays put — leaving it stranded mid-screen on
        // the new bounds. Re-apply the persisted corner-anchored position so the FAB
        // snaps back to its border buffer.
        if (::fabController.isInitialized) {
            fabController.reapplyPosition()
        }

        // configChanges in the manifest swallows orientation/screenSize changes without
        // a recreation, but the screen-size *class* (single vs. two-pane) can still
        // flip if the device gets resized in multi-window or via fold-state changes
        // that don't recreate. Re-evaluate the expected fragment class here and swap
        // if necessary so the pair view appears (or disappears) reactively.
        val current = currentFragment ?: return
        val expectedClass = newFragmentForMenu(currentMenuID)?.javaClass ?: return
        if (current.javaClass != expectedClass) {
            Log.i(LOG_TAG, "onConfigurationChanged: form factor changed; re-routing menuID=$currentMenuID")
            installInitialFragment(currentMenuID)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Override the 1-arg form: that's what the framework calls during normal
        // recreation (e.g., theme/locale change, low-memory kill, or any config
        // change not covered by the manifest's configChanges). The 2-arg
        // (Bundle, PersistableBundle) variant only fires when the activity is
        // declared with persistableMode="persistAcrossReboots", which we don't
        // use — so overriding only that one silently dropped currentMenuID and
        // forced every recreation back to the default screen.
        outState.putInt("currentMenuID", currentMenuID)
    }

    private fun hideVirtualKeyboard() {
        //insertEntry insert_entry_title?.isFocusable = false
        //insert_entry_title?.isFocusableInTouchMode = true
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun setDefaultFragment() {
        if (this.currentFragment != null) return
        // TODO : make this configurable
        installInitialFragment(R.id.menu_transaction_insert)
    }

    /**
     * Build the fragment for [menuId] via the screen-size-aware [newFragmentForMenu]
     * and install it into the content container, replacing whatever's there. Used by
     * both the first-launch default and the post-recreation re-route in [onCreate]
     * when the saved fragment no longer matches the current form factor.
     *
     * Going through `newFragmentForMenu` here was the missing piece: the previous
     * default-fragment path hardcoded TransactionInsertFragment, which meant a fresh
     * launch on a tablet/foldable always opened single-pane regardless of
     * R.bool.isLargeScreen. Routing through the helper makes the default behave like
     * any other navigation tap and pick the pair fragment when applicable.
     */
    private fun installInitialFragment(menuId: Int) {
        val fragment = newFragmentForMenu(menuId) ?: return
        (fragment as IWimpleFragment).setActivityInstance(this)
        fragment.arguments = intent.extras
        this.currentFragment = fragment
        this.currentMenuID = menuId

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.wimple, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_go_to_whooing) {
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(whooingURL)
            startActivity(i)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (!replaceWimpleFragment(id))
            return false

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun replaceWimpleFragment(id: Int, bundle: Bundle? = null): Boolean {
        hideVirtualKeyboard()

        if (this.currentMenuID == id) {
            return true
        }

        val nextFragment = newFragmentForMenu(id) ?: return false

        // On large screens two drawer entries (e.g. 거래입력 / 거래목록) resolve to the same
        // pair fragment. When the user toggles between them we don't want to recreate the
        // already-displayed pair — that would tear down both child fragments and lose any
        // in-progress input. Instead, just update the menu ID so the drawer's selected-item
        // highlight follows the tap and refresh the FAB icon.
        val current = this.currentFragment
        if (current != null && current::class == nextFragment::class) {
            this.currentMenuID = id
            fabController.refreshIcon()
            return true
        }

        this.currentFragment = nextFragment
        this.currentMenuID = id

        fabController.refreshIcon()

        if (this.currentFragment!!.isAdded)
            return true

        try {
            (this.currentFragment as IWimpleFragment).setActivityInstance(this)
            if (null != bundle) {
                this.currentFragment!!.arguments = bundle
            }
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, this.currentFragment as androidx.fragment.app.Fragment)
            transaction.commit()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "replaceWimpleFragment: " + e.message)
            return false
        }
        return true
    }

    /**
     * Resolve a drawer menu id to the fragment that should occupy the content pane.
     *
     * On phones (R.bool.isLargeScreen=false) this is a 1:1 mapping driven by the
     * [MenuFragment] enum. On tablets and unfolded foldables (sw600dp+) the three
     * paired drawer entries instead resolve to a [TwoPaneFragment] subclass that
     * shows both siblings side by side, so the user doesn't have to ping-pong
     * through the drawer.
     */
    private fun newFragmentForMenu(id: Int): androidx.fragment.app.Fragment? {
        if (resources.getBoolean(R.bool.isLargeScreen)) {
            when (id) {
                R.id.menu_transaction_insert,
                R.id.menu_transaction_list -> return TransactionPairFragment()

                R.id.menu_saving,
                R.id.menu_debt -> return SavingDebtPairFragment()

                R.id.menu_income,
                R.id.menu_expense -> return IncomeExpensePairFragment()
            }
        }
        return MenuFragment.fromMenuId(id)?.factory?.invoke()
    }


    private fun setMyInfoOnMenu(info: UserInfo) {
        val headerView = binding.navView.getHeaderView(0)

        val sectionTitle = headerView.findViewById<TextView>(R.id.section_title)
        val myProfileIcon = headerView.findViewById<ImageView>(R.id.my_profile_icon)
        val myProfileName = headerView.findViewById<TextView>(R.id.my_profile_name)

        if (null == myProfileIcon) {
            smd(CommandID.UPDATE_USER_INFO, info, 1000)
            return
        }
        sectionTitle.text = WimpleImpl.getInstance().defaultSectionName
        WidgetItem.replaceBitmapOfImageView(myProfileIcon, WimpleImpl.getInstance().profilePicture, false)
        myProfileName.text = info.name

        updateAPIRemaining()

        /*
        // Set OnClick listener => Detail Profile information
		LinearLayout rlProfileWindow = (LinearLayout)findViewById(R.id.my_profile_information_window);
		rlProfileWindow.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO : later
				//Intent intent = new Intent(context, DetailProfileActivity.class);
				//startActivity(intent);
			}

		});
		*/
    }

    private fun updateAPIRemaining() {
        val headerView = binding.navView.getHeaderView(0)
        val myProfileLevel = headerView.findViewById<TextView>(R.id.my_profile_level)

        if (null == myProfileLevel)
            return

        var nLevel = WimpleImpl.getInstance().remainedAPICall!!
        if (nLevel < 0)
            nLevel = 0

        myProfileLevel.text = resources.getString(R.string.number_api_count) + " " + nLevel
    }

    private fun setupWimpleImpl() {
        // Default forwarding behaviour for every callback lives in WimpleListenerBinder; we
        // accept all defaults here. The auth check below is the only WimpleActivity-specific
        // logic that needs to stay in this method.
        WimpleListenerBinder(applicationContext, mainHandler!!).attach()

        // We only check `isAuthed` here, NOT `isInitializedFinished`. The latter is a runtime
        // flag (set after the first successful section query) that is never persisted, so it
        // is always false on a cold start — gating splash redirection on it caused the task
        // to be wiped via FLAG_ACTIVITY_CLEAR_TASK after every process death, throwing the
        // user back to the default fragment. `getUserInfo(true)` only requires `isAuthed`
        // and triggers the rest of the bootstrap on its own.
        if (WimpleImpl.getInstance().isAuthed) {
            // Token loaded from SharedPreferences (or already authed in this process).
            Log.d(LOG_TAG, "WimpleActivity, authed — bootstrapping user info")
            WimpleImpl.getInstance().getUserInfo(true)

            // Replicate the splash-flow section warm-up that we lose by skipping
            // SplashScreenActivity on cold-start-with-token. getDefaultSections(false)
            // hits the section DB cache instantly and ends up firing onLoggedIn →
            // WIMPLE_LOGGIN_SUCCESS. The fragment's handler reruns initWimple(),
            // giving GET_ALL_ACCOUNT_RECEIVED a guaranteed second arrival — needed
            // because TransactionInsertFragment can race the user tapping a latest
            // item before its own getAllAccounts call returns, leaving the bottom
            // category empty until something re-triggers selectLeftCategory().
            if (!WimpleImpl.getInstance().isInitializedFinished) {
                WimpleImpl.getInstance().getDefaultSections(false)
            }
        } else {
            // No stored token / token cleared. Show splash on top of this activity (no
            // CLEAR_TASK) — combined with WimpleActivity's singleTask launch mode this means
            // splash.moveToMain() reuses the existing instance, so restored fragments survive.
            Log.d(LOG_TAG, "WimpleActivity, no auth — launching splash on top")
            startActivity(Intent(this, SplashScreenActivity::class.java))
        }
    }


    @SuppressLint("HandlerLeak")
    private fun setupHandler() {
        mainHandler = object : Handler() {

            override fun handleMessage(msg: Message) {

                val command = msg.what
                val obj = msg.obj

                if (this@WimpleActivity.currentFragment == null)
                    return

                updateAPIRemaining()

                when (command) {

                    CommandID.TOAST_LONG -> Snackbar.make(binding.drawerLayout, obj.toString(), Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show()

                    CommandID.TOAST_SHORT -> Snackbar.make(binding.drawerLayout, obj.toString(), Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show()

                    CommandID.UPDATE_USER_INFO -> {
                        setMyInfoOnMenu(obj as UserInfo)
                    }

                    CommandID.WIMPLE_PROFILE_PICTURE_UPDATED -> {
                        val headerView = binding.navView.getHeaderView(0)
                        val myProfileIcon = headerView.findViewById<ImageView>(R.id.my_profile_icon)

                        WidgetItem.replaceBitmapOfImageView(myProfileIcon, WimpleImpl.getInstance().profilePicture, false)
                    }

                    // TransactionInsertFragment
                    CommandID.MODIFY_ENTRY, CommandID.ADD_MONTHLY_ITEM -> {

                        // The insert fragment may be the current single-pane fragment OR the
                        // right pane of a TransactionPairFragment on large screens. In the
                        // latter case TwoPaneFragment.handleMessage forwards the command into
                        // the embedded TransactionInsertFragment, so don't kick the user off
                        // the pair view back to the single insert screen.
                        val current = this@WimpleActivity.currentFragment
                        val alreadyHostsInsert = current is TransactionInsertFragment
                            || current is TransactionPairFragment
                        if (!alreadyHostsInsert) {
                            replaceWimpleFragment(R.id.menu_transaction_insert)
                            smd(msg.what, msg.obj, 300)
                        }

                        if (this@WimpleActivity.currentFragment is IWimpleFragment) {
                            val wfg = this@WimpleActivity.currentFragment as IWimpleFragment?
                            wfg!!.handleMessage(msg)
                        }
                    }

                    // to all
                    CommandID.WIMPLE_LOGGIN_SUCCESS -> {
                        WimpleImpl.getInstance().monthlyItems
                        if (this@WimpleActivity.currentFragment is IWimpleFragment) {
                            val wfg = this@WimpleActivity.currentFragment as IWimpleFragment?
                            wfg!!.handleMessage(msg)
                        }
                    }
/*
                     CommandID.WIMPLE_LOGGIN_FAILED,
                     CommandID.WIMPLE_LOGGOUT,
                     CommandID.GET_ALL_ACCOUNT_RECEIVED,
                     CommandID.GET_ALL_SECTION_RECEIVED,
                     CommandID.GET_ENTRIES_RECEIVED,
                     CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED,
                     CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED,
                     CommandID.GET_MONTHLY_ITEMS_RESPONSE_RECEIVED,
                     CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED,
                     CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED,
                     CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED -> {
                        if (null != this@WimpleActivity.currentFragment && this@WimpleActivity.currentFragment is IWimpleFragment) {
                            val wfg = this@WimpleActivity.currentFragment as IWimpleFragment?
                            wfg!!.handleMessage(msg)
                        }
                    }
*/
                    else -> {
                        if (this@WimpleActivity.currentFragment is IWimpleFragment) {
                            val wfg = this@WimpleActivity.currentFragment as IWimpleFragment?
                            wfg!!.handleMessage(msg)
                        }
                    }
                }
                super.handleMessage(msg)
            }
        }
    }

    companion object {

        private const val LOG_TAG = "WimpleActivity"
        private const val whooingURL = "https://whooing.com"

        private var mainHandler: Handler? = null

        fun sm(cmd: Int, msg: Any) {
            this.mainHandler!!.sendMessage(Message.obtain(this.mainHandler, cmd, 1, 0, msg))
        }

        fun sm(cmd: Int, a1: Int, a2: Int, msg: Any) {
            this.mainHandler!!.sendMessage(Message.obtain(this.mainHandler, cmd, a1, a2, msg))
        }

        fun smd(cmd: Int, msg: Any, ms: Long) {
            this.mainHandler!!.sendMessageDelayed(Message.obtain(this.mainHandler, cmd, 1, 0, msg), ms)
        }
    }
}
