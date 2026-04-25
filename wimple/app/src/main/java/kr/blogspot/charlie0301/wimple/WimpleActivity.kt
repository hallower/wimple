package kr.blogspot.charlie0301.wimple

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.PersistableBundle
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

        if(savedInstanceState != null){
            Log.i(LOG_TAG, "WimpleActivity - onCreate!!!, savedInstanceState is NOT null")
            val f = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (f is IWimpleFragment) {
                currentFragment = f
                currentMenuID = savedInstanceState.getInt("currentMenuID")
            }
            Log.i(LOG_TAG, "WimpleActivity - onCreate!!!, currentFragment=$currentFragment, currentMenuID=$currentMenuID")
            return
        }

        setDefaultFragment()
        BiometricOnboarding.showIfNeeded(this)
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
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
        this.currentMenuID = R.id.menu_transaction_insert

        if(this.currentFragment != null)
            return

        // TODO : make this configurable
        val insertFragment = TransactionInsertFragment()
        (insertFragment as IWimpleFragment).setActivityInstance(this)
        insertFragment.arguments = intent.extras
        this.currentFragment = insertFragment

        if (this.currentFragment!!.isAdded)
            return

        val transaction = supportFragmentManager.beginTransaction()
        transaction.add(R.id.fragment_container, insertFragment)
        transaction.commit()

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

        val target = MenuFragment.fromMenuId(id) ?: return false
        this.currentFragment = target.factory()
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

                        if (this@WimpleActivity.currentFragment !is TransactionInsertFragment) {
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
