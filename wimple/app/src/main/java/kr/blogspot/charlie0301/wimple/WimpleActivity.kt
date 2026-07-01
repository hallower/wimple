package kr.blogspot.charlie0301.wimple

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kr.blogspot.charlie0301.wimple.databinding.ActivityWimpleBinding
import kr.blogspot.charlie0301.wimple.impl.LocalReviewQueue
import kr.blogspot.charlie0301.wimple.impl.WhooingNotifications
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.WidgetItem
import kr.blogspot.charlie0301.wimple.model.*
import java.util.*

class WimpleActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private var currentMenuID: Int = R.id.menu_transaction_insert
    private var currentFragment: androidx.fragment.app.Fragment? = null

    private lateinit var binding: ActivityWimpleBinding
    private lateinit var fabController: FloatingActionButtonController

    // Cached unread-notification badge count fetched once per Activity lifetime.
    // Stays at 0 (= bell hidden) until the one-shot onResume fetch returns; the
    // user clears it server-side by visiting whooing.com, never from Wimple.
    private var notificationCount: Int = 0
    private var notificationMenuItem: MenuItem? = null
    private var hasFetchedNotifications: Boolean = false

    // Local review queue badge — visible only when at least one notification is awaiting
    // review. Refreshed on every onResume since the queue can grow while we're foregrounded
    // (notification listener writes asynchronously).
    private var reviewQueueMenuItem: MenuItem? = null

    private var toolBar: Toolbar? = null

    // Per-activity-instance "shake the toolbar icon once" guards. Armed at activity creation
    // and disarmed the first time the matching badge appears, so a user opening the app sees a
    // single attention-grab; later badge visibility flips during the same session don't replay.
    private var reviewShakeArmed: Boolean = true
    private var notificationShakeArmed: Boolean = true

    // Most-recent toolbar bubble popup so we can dismiss it on pause/destroy. Only one
    // exists at a time — the second bubble (if both icons appear in the same onResume)
    // would dismiss the first, which is fine because they're short-lived attention nudges,
    // not concurrent UI. Kept null when no bubble is showing.
    private var activeToolbarBubble: PopupWindow? = null

    override fun onPause() {
        // Dismiss any in-flight toolbar bubble before the window detaches; leaving a
        // PopupWindow attached to a finishing activity would throw "WindowManager$BadTokenException".
        activeToolbarBubble?.dismiss()
        activeToolbarBubble = null
        super.onPause()
    }

    override fun onResume() {
        Log.i(LOG_TAG, "WimpleActivity - onResume!!!")
        setupWimpleImpl()
        super.onResume()
        kr.blogspot.charlie0301.wimple.impl.BankNotifications.retryIfPending(this)
        if (!hasFetchedNotifications) {
            hasFetchedNotifications = true
            fetchNotificationBadge()
        }
        refreshReviewQueueBadge()
        // Finish a "user accepted AI suggestion but had to go grant notification access"
        // round-trip. No-op when no pending acceptance is recorded; otherwise checks the
        // current grant state and flips the local-review toggle if granted.
        LocalReviewSuggestion.resumeIfPending(this)
        // After the initial bank-app picker from the AI-suggestion enable flow, open the
        // review screen so the first-time tutorial fires immediately.
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(BankNotificationListener.KEY_LOCAL_REVIEW_POST_PICKER_PENDING, false)) {
            prefs.edit().putBoolean(BankNotificationListener.KEY_LOCAL_REVIEW_POST_PICKER_PENDING, false).apply()
            startActivity(
                Intent(this, BankNotificationReviewActivity::class.java)
                    .putExtra(BankNotificationReviewActivity.EXTRA_SHOW_TUTORIAL, true)
            )
        }
    }

    private fun refreshReviewQueueBadge() {
        val count = LocalReviewQueue.count(this)
        val visible = count > 0
        reviewQueueMenuItem?.isVisible = visible
        if (visible && reviewShakeArmed) {
            reviewShakeArmed = false
            shakeToolbarItem(R.id.action_review_queue)
            showToolbarBubble(
                R.id.action_review_queue,
                getString(R.string.toolbar_bubble_review_count, count)
            )
        }
    }

    private fun fetchNotificationBadge() {
        val wimple = WimpleImpl.getInstance() ?: return
        Thread {
            val count = WhooingNotifications.fetchBadgeCount(wimple)
            runOnUiThread {
                notificationCount = count
                val visible = count > 0
                notificationMenuItem?.isVisible = visible
                if (visible && notificationShakeArmed) {
                    notificationShakeArmed = false
                    shakeToolbarItem(R.id.action_whooing_notifications)
                    showToolbarBubble(
                        R.id.action_whooing_notifications,
                        getString(R.string.toolbar_bubble_whooing_notifications)
                    )
                }
            }
        }.start()
    }

    /**
     * One-shot attention nudge for an "always" toolbar action that just became visible. The
     * MenuItem-backed action view inherits the menu item id, so we can find it on the toolbar
     * once layout has settled and rotate it briefly. Posted twice — once to wait out the
     * current layout pass, then again because Toolbar's action view inflation can lag the
     * isVisible flip by a frame, especially when the badge appears during onCreateOptionsMenu.
     */
    private fun shakeToolbarItem(itemId: Int) {
        val tb = toolBar ?: return
        tb.post {
            val v = tb.findViewById<View>(itemId)
            if (v != null) {
                playShake(v)
            } else {
                tb.post {
                    tb.findViewById<View>(itemId)?.let(::playShake)
                }
            }
        }
    }

    private fun playShake(view: View) {
        ObjectAnimator.ofFloat(
            view, View.ROTATION,
            0f, -15f, 15f, -12f, 12f, -8f, 8f, -4f, 4f, 0f
        ).apply {
            duration = 700L
            start()
        }
    }

    /**
     * One-shot chip-style bubble anchored under a toolbar action's view. Pairs with the
     * shake animation as an attention nudge — the shake says "look here", the bubble
     * explains "why". Auto-dismisses after [BUBBLE_VISIBLE_MS]; replaces any prior bubble
     * so the second of two simultaneous appearances doesn't stack on top of the first.
     *
     * Posts the show twice through the toolbar message queue for the same reason
     * [shakeToolbarItem] does: the action view can lag the isVisible flip by a frame.
     */
    private fun showToolbarBubble(itemId: Int, text: String) {
        val tb = toolBar ?: return
        tb.post {
            val anchor = tb.findViewById<View>(itemId)
            if (anchor != null) {
                presentBubble(anchor, text)
            } else {
                tb.post {
                    tb.findViewById<View>(itemId)?.let { presentBubble(it, text) }
                }
            }
        }
    }

    private fun presentBubble(anchor: View, text: String) {
        // Drop any earlier bubble so a fast-arriving second one (both badges appearing in
        // the same onResume) doesn't leave a stale popup behind. The user can still see the
        // later one because we show it immediately after.
        activeToolbarBubble?.dismiss()
        val bubbleView = LayoutInflater.from(this)
            .inflate(R.layout.view_toolbar_bubble, binding.root, false) as TextView
        bubbleView.text = text
        // Measure the bubble at WRAP_CONTENT so we know its natural width and can center
        // it horizontally under the anchor. Earlier versions used Gravity.END on
        // showAsDropDown, which aligned the bubble's RIGHT edge to the anchor's right
        // edge — since the bubble text ("은행 알림 N건") is wider than the icon, the
        // result was the bubble extending entirely to the LEFT of the icon. PopupWindow
        // auto-shifts on overflow when we center, so the rightmost icon
        // (action_whooing_notifications) still renders within bounds even if the
        // centered position would clip the right screen edge.
        bubbleView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val xOffset = (anchor.width - bubbleView.measuredWidth) / 2
        val popup = PopupWindow(
            bubbleView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            /* focusable */ false
        ).apply {
            // Outside-touchable false so the bubble doesn't intercept taps on the toolbar
            // (the icon should still be tappable while the bubble is up).
            isOutsideTouchable = false
            isTouchable = false
            // No animation style needed — auto-dismiss is enough; adding fade would also
            // delay the actual disappearance past BUBBLE_VISIBLE_MS in a janky way on
            // older devices.
        }
        // Default gravity (TOP|START) anchors the popup's top-left to the anchor's
        // bottom-left; with the negative-or-positive xOffset above, the popup's center
        // now sits below the anchor's center.
        popup.showAsDropDown(anchor, xOffset, 0)
        activeToolbarBubble = popup
        // Lifecycle-safe dismiss: post to the toolbar so cancellation comes for free on
        // activity teardown (Toolbar is part of the view hierarchy).
        toolBar?.postDelayed({
            if (activeToolbarBubble === popup) {
                popup.dismiss()
                activeToolbarBubble = null
            }
        }, BUBBLE_VISIBLE_MS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(LOG_TAG, "WimpleActivity - onCreate!!!")
        super.onCreate(savedInstanceState)

        binding = ActivityWimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // GUI
        val toolBar = this.findViewById<Toolbar>(R.id.toolbar)
        this.toolBar = toolBar

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
                LocalReviewSuggestion.showIfNeeded(this)
                return
            }
            Log.i(LOG_TAG, "WimpleActivity - onCreate, screen class changed; re-routing menuID=$savedMenuId")
            installInitialFragment(savedMenuId)
            LocalReviewSuggestion.showIfNeeded(this)
            return
        }

        // Honor an explicit menu request on cold launch (e.g., review activity → manual entry).
        // Falls back to the configured default screen when no extra is present.
        val requestedMenu = consumeOpenMenuExtra(intent)
        if (requestedMenu != null) {
            installInitialFragment(requestedMenu)
        } else {
            setDefaultFragment()
        }
        // Suggest AI-classified entry to users on supported devices the first time after a
        // fresh install, logout, or app update. Self-gates internally so calling here on every
        // onCreate path is cheap and idempotent.
        LocalReviewSuggestion.showIfNeeded(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask launchMode means re-entry through Intent.FLAG_ACTIVITY_CLEAR_TOP routes
        // here instead of onCreate. Update the stored intent so subsequent getIntent() calls
        // return the new one, then process the menu request if present.
        setIntent(intent)
        val requestedMenu = consumeOpenMenuExtra(intent) ?: return
        // Forward intent extras as fragment arguments so prefill keys (title/amount) flow
        // through to TransactionInsertFragment when it's freshly created. The fragment
        // ignores keys it doesn't recognize.
        replaceWimpleFragment(requestedMenu, intent.extras)

        // If the same fragment was already on screen, replaceWimpleFragment early-returns
        // without re-applying arguments, so the prefill never reaches the form. Push the
        // values directly into the live instance to cover that case.
        applyPrefillToCurrentTransactionFragment(intent)
    }

    private fun applyPrefillToCurrentTransactionFragment(intent: Intent) {
        // On large screens (isLargeScreen=true) the drawer's menu_transaction_insert
        // entry routes to TransactionPairFragment, not TransactionInsertFragment
        // directly — the insert form is the right pane of the pair. Drill through
        // the pair's childFragmentManager to find the embedded insert fragment so
        // same-instance prefill (review activity → form already on screen) lands
        // on the actual form rather than getting silently dropped at the cast.
        // Fresh-creation prefill is handled separately by TransactionPairFragment
        // copying the host's args into the inner fragment at construction time.
        val current = currentFragment
        val fragment = when (current) {
            is TransactionInsertFragment -> current
            is TransactionPairFragment -> current.findPaneOfType<TransactionInsertFragment>()
            else -> null
        } ?: return
        val title = intent.getStringExtra(EXTRA_PREFILL_TITLE)
        val amount = if (intent.hasExtra(EXTRA_PREFILL_AMOUNT))
            intent.getDoubleExtra(EXTRA_PREFILL_AMOUNT, 0.0)
        else null
        val leftAccountId = intent.getStringExtra(EXTRA_PREFILL_LEFT_ACCOUNT_ID)
        val rightAccountId = intent.getStringExtra(EXTRA_PREFILL_RIGHT_ACCOUNT_ID)
        val reviewItemId = intent.getStringExtra(EXTRA_REVIEW_ITEM_ID)
        val reviewMerchant = intent.getStringExtra(EXTRA_REVIEW_MERCHANT)
        val reviewKind = intent.getStringExtra(EXTRA_REVIEW_KIND)
        val notificationText = intent.getStringExtra(EXTRA_REVIEW_NOTIFICATION_TEXT)
        val notificationSource = intent.getStringExtra(EXTRA_REVIEW_NOTIFICATION_SOURCE)
        val notificationTitle = intent.getStringExtra(EXTRA_REVIEW_NOTIFICATION_TITLE)
        if (title == null && amount == null && leftAccountId == null && rightAccountId == null
            && reviewItemId == null && notificationText == null) return
        fragment.applyPrefill(
            title, amount, leftAccountId, rightAccountId,
            reviewItemId, reviewMerchant, reviewKind,
            notificationText, notificationSource, notificationTitle
        )
        // Single-shot — remove from the active intent so the next config change doesn't
        // re-prefill on top of user edits.
        intent.removeExtra(EXTRA_PREFILL_TITLE)
        intent.removeExtra(EXTRA_PREFILL_AMOUNT)
        intent.removeExtra(EXTRA_PREFILL_LEFT_ACCOUNT_ID)
        intent.removeExtra(EXTRA_PREFILL_RIGHT_ACCOUNT_ID)
        intent.removeExtra(EXTRA_REVIEW_ITEM_ID)
        intent.removeExtra(EXTRA_REVIEW_MERCHANT)
        intent.removeExtra(EXTRA_REVIEW_KIND)
        intent.removeExtra(EXTRA_REVIEW_NOTIFICATION_TEXT)
        intent.removeExtra(EXTRA_REVIEW_NOTIFICATION_SOURCE)
        intent.removeExtra(EXTRA_REVIEW_NOTIFICATION_TITLE)
    }

    private fun consumeOpenMenuExtra(intent: Intent?): Int? {
        if (intent == null) return null
        val menuId = intent.getIntExtra(EXTRA_OPEN_MENU, 0)
        if (menuId == 0) return null
        // Single-shot — strip the extra so a later config change / restart doesn't re-route.
        intent.removeExtra(EXTRA_OPEN_MENU)
        return menuId
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
        notificationMenuItem = menu.findItem(R.id.action_whooing_notifications)?.also {
            it.isVisible = notificationCount > 0
        }
        reviewQueueMenuItem = menu.findItem(R.id.action_review_queue)
        refreshReviewQueueBadge()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_go_to_whooing || id == R.id.action_whooing_notifications) {
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(whooingURL)
            startActivity(i)
            return true
        }
        if (id == R.id.action_review_queue) {
            startActivity(Intent(this, BankNotificationReviewActivity::class.java))
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

        /**
         * How long the toolbar bubble stays on screen before auto-dismissing. Sized to
         * overlap the 700ms shake animation and give the user a beat to read the short
         * Korean phrase ("은행 알림 N건" or "후잉 게시판 새 알림") without lingering long
         * enough to block subsequent UI interactions.
         */
        private const val BUBBLE_VISIBLE_MS: Long = 2500L

        /**
         * Intent extra that asks WimpleActivity to install (or swap to) a specific drawer
         * menu id on entry. Used by [BankNotificationReviewActivity] to hand off to the
         * transaction-insert form when the user taps "Enter manually". Single-shot —
         * cleared after consumption so a config change doesn't re-route.
         */
        const val EXTRA_OPEN_MENU = "wimple.extra.open_menu"

        /**
         * Optional companion extras used together with [EXTRA_OPEN_MENU] when routing into
         * the transaction-insert form from a classified review row. The fragment reads its
         * own arguments in onViewCreated to seed the title and amount fields. Forwarded
         * verbatim from the launching intent — WimpleActivity itself doesn't interpret them.
         */
        const val EXTRA_PREFILL_TITLE = "wimple.extra.prefill_title"
        const val EXTRA_PREFILL_AMOUNT = "wimple.extra.prefill_amount"
        const val EXTRA_PREFILL_LEFT_ACCOUNT_ID = "wimple.extra.prefill_left_account_id"
        const val EXTRA_PREFILL_RIGHT_ACCOUNT_ID = "wimple.extra.prefill_right_account_id"

        /**
         * "Review session" extras tagging the launch as originating from a queue row. When
         * present in the manual-entry intent the form, on a successful submit, will (1)
         * remove the row from [LocalReviewQueue] and (2) upsert the user-confirmed account
         * pair into [MerchantMappingDBHandler] so subsequent matching merchants auto-resolve.
         */
        const val EXTRA_REVIEW_ITEM_ID = "wimple.extra.review_item_id"
        const val EXTRA_REVIEW_MERCHANT = "wimple.extra.review_merchant"
        const val EXTRA_REVIEW_KIND = "wimple.extra.review_kind"

        /**
         * Original bank-notification body shown above the form so the user can verify what
         * the AI extracted before confirming. Optional — UNPARSED rows still benefit from
         * seeing the source even though no fields are prefilled.
         */
        const val EXTRA_REVIEW_NOTIFICATION_TEXT = "wimple.extra.review_notification_text"
        const val EXTRA_REVIEW_NOTIFICATION_SOURCE = "wimple.extra.review_notification_source"
        // Original notification title (e.g., "[KB카드] 출금"). Propagated alongside the body
        // so the form's success branch can persist it as part of the few-shot training row.
        const val EXTRA_REVIEW_NOTIFICATION_TITLE = "wimple.extra.review_notification_title"

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
