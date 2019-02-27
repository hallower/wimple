package kr.blogspot.charlie0301.wimple

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_wimple.*
import kotlinx.android.synthetic.main.app_bar_wimple.*
import kotlinx.android.synthetic.main.fragment_transaction_insert_tab.*
import kotlinx.android.synthetic.main.nav_header_wimple.*
import kr.blogspot.charlie0301.wimple.impl.IWimpleResponseListener
import kr.blogspot.charlie0301.wimple.impl.IWimpleStatusListener
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.WidgetItem
import kr.blogspot.charlie0301.wimple.model.*
import java.util.*

class WimpleActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private var currentMenuID: Int = 0
    private var currentFragment: androidx.fragment.app.Fragment?= null

    override fun onResume() {
        Log.i(LOG_TAG, "WimpleActivity - onResume!!!")
        setupWimpleImpl()
        super.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(LOG_TAG, "WimpleActivity - onCreate!!!")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wimple)

        // GUI
        setSupportActionBar(toolbar)

        fab.setOnClickListener {
            if (this.currentMenuID != R.id.menu_transaction_insert) {
                replaceWimpleFragment(R.id.menu_transaction_insert)
            } else {
                replaceWimpleFragment(R.id.menu_transaction_list)
            }
        }

        val toggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.itemIconTintList = null
        nav_view.setNavigationItemSelectedListener(this)

        // Setup Wimple
        setupHandler()
        setupWimpleImpl()

        if (null != findViewById(R.id.fragment_container)) {
            if (savedInstanceState != null)
                return
            setDefaultFragment()
        }
    }

    private fun hideVirtualKeyboard() {
        insert_entry_title.isFocusable = false
        insert_entry_title.isFocusableInTouchMode = true
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun setDefaultFragment() {
        fab.setImageResource(R.drawable.ic_fab_list)
        this.currentMenuID = R.id.menu_transaction_insert

        var insertFragment: TransactionInsertFragment = TransactionInsertFragment()
        (insertFragment as IWimpleFragment).setActivityInstance(this)
        insertFragment.arguments = intent.extras
        this.currentFragment = insertFragment;

        val transaction = supportFragmentManager.beginTransaction()
        transaction.add(R.id.fragment_container, insertFragment)
        transaction.commit()

    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
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

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun replaceWimpleFragment(id: Int, bundle: Bundle? = null): Boolean {
        var isNeedAddFab = true

        hideVirtualKeyboard()

        if (this.currentMenuID == id) {
            return true
        }

        if (id == R.id.menu_transaction_insert) {
            isNeedAddFab = false
            this.currentFragment = TransactionInsertFragment()
            //mDrawerList.setItemChecked(position, true);
            //setTitle(mPlanetTitles[position]);
        } else if (id == R.id.menu_transaction_list) {
            this.currentFragment = TransactionListFragment()
        } else if (id == R.id.menu_financial_overview) {
            this.currentFragment = FinancialStateSummaryFragment()
        } else if (id == R.id.menu_saving) {
            this.currentFragment = SavingStateSummaryFragment()
        } else if (id == R.id.menu_debt) {
            this.currentFragment = DebtStateSummaryFragment()
        } else if (id == R.id.menu_income_expense_overview) {
            this.currentFragment = IncomeExpenseSummaryFragment()
        } else if (id == R.id.menu_income) {
            this.currentFragment = IncomeSummaryFragment()
        } else if (id == R.id.menu_expense) {
            this.currentFragment = ExpenseSummaryFragment()
        } else if (id == R.id.menu_preference) {
            this.currentFragment = SettingsFragment()
        } else {
            return false
        }

        this.currentMenuID = id

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

        if (isNeedAddFab) {
            fab!!.setImageResource(R.drawable.ic_fab_add)
        } else {
            fab!!.setImageResource(R.drawable.ic_fab_list)
        }

        return true
    }


    private fun setMyInfoOnMenu(info: UserInfo) {
        if (null == my_profile_icon) {
            smd(CommandID.UPDATE_USER_INFO, info, 1000)
            return
        }
        section_title.text = WimpleImpl.getInstance().defaultSectionName
        WidgetItem.replaceBitmapOfImageView(my_profile_icon, WimpleImpl.getInstance().profilePicture, false)
        my_profile_name.text = info.name

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

        if (null == my_profile_level)
            return

        var nLevel = WimpleImpl.getInstance().remainedAPICall!!
        if (nLevel < 0)
            nLevel = 0

        my_profile_level.text = resources.getString(R.string.number_api_count) + " " + nLevel
    }

    private fun setupWimpleImpl() {
        WimpleImpl.getInstance().setApplicationContext(applicationContext)
        WimpleImpl.getInstance().setStatusListener(object : IWimpleStatusListener {

            override fun onLoggedIn(status: Boolean) {
                if (status) {
                    sm(CommandID.WIMPLE_LOGGIN_SUCCESS, "")
                } else {
                    sm(CommandID.WIMPLE_LOGGIN_FAILED, "")
                }
            }

            override fun onLoggedOut() {
                sm(CommandID.WIMPLE_LOGGOUT, "")
            }

            override fun onNetworkConnectionEstablished() {}

            override fun onNetworkConnectionLost() {}

            override fun onProfilePictureUpdated() {
                sm(CommandID.WIMPLE_PROFILE_PICTURE_UPDATED, "")
            }

        })
        WimpleImpl.getInstance().setResponseListener(object : IWimpleResponseListener {

            override fun onGetAuthTempToken(status: Boolean, tempToken: String) {}

            override fun onGetAuthAccessToken(status: Boolean,
                                              result: Map<String, String>) {
            }

            override fun onGetUserInfoResponseReceived(status: Boolean, info: UserInfo) {
                if (status) {
                    Log.i(LOG_TAG, info.toString())
                    sm(CommandID.UPDATE_USER_INFO, 1, 0, info)
                } else {
                    Toast.makeText(applicationContext, "Login Failed!!!!", Toast.LENGTH_LONG).show()
                }
            }

            override fun onGetAllSectionResponseReceived(status: Boolean, list: Collection<Section>) {
                sm(CommandID.GET_ALL_SECTION_RECEIVED, if (status) 1 else 0, 0, list)
            }

            override fun onGetAllAccountResponseReceived(status: Boolean, list: Collection<Account>) {
                sm(CommandID.GET_ALL_ACCOUNT_RECEIVED, if (status) 1 else 0, 0, list)
            }

            override fun onGetEntriesResponseReceived(status: Boolean, list: Collection<Entry>) {
                sm(CommandID.GET_ENTRIES_RECEIVED, if (status) 1 else 0, 0, list)
            }

            override fun onGetLatestEntriesResponseReceived(status: Boolean, list: Collection<Entry>) {
                sm(CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED, if (status) 1 else 0, 0, list)
            }

            override fun onMakeEntryResponseReceived(status: Boolean, entryDate: String) {
                sm(CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED, if (status) 1 else 0, 0, entryDate)
            }

            override fun onGetFrequentItemsResponseReceived(status: Boolean,
                                                            list: Collection<Item>) {
                sm(CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED, if (status) 1 else 0, 0, list)
            }

            override fun onGetLatestItemsResponseReceived(status: Boolean,
                                                          list: Collection<Item>) {
                sm(CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED, if (status) 1 else 0, 0, list)
            }

            override fun onModifyEntryResponseReceived(status: Boolean, entry: Entry) {
                sm(CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED, if (status) 1 else 0, 0, entry)
            }

            override fun onGetMonthlyItemsResponseReceived(status: Boolean,
                                                           list: ArrayList<Item>) {
                sm(CommandID.GET_MONTHLY_ITEMS_RESPONSE_RECEIVED, if (status) 1 else 0, 0, list)
            }

            override fun onRemoveEntryResponseReceived(status: Boolean, id: String) {
                sm(CommandID.REMOVE_ENTRY_RESPONSE_RECEIVED, if (status) 1 else 0, 0, id)
            }

            override fun onRemoveMonthlyItemResponseReceived(status: Boolean, id: String) {
                sm(CommandID.REMOVE_MONTHLY_ITEMS_RESPONSE_RECEIVED, if (status) 1 else 0, 0, id)
            }

            override fun onGetFinancialStateResponseReceived(status: Boolean,
                                                             list: Collection<AccountState>) {
                sm(CommandID.GET_FINANCIAL_STATE_RESPONSE_RECEIVED, if (status) 1 else 0, 0, list)
            }

            override fun onGetIncomeAndExpenseResponseReceived(status: Boolean,
                                                               list: Collection<AccountState>) {
                sm(CommandID.GET_INCOME_AND_EXPENSE_RESPONSE_RECEIVED, if (status) 1 else 0, 0, list)
            }

            override fun onGetBudgetResponseReceived(status: Boolean, isIncome: Boolean,
                                                     list: Map<String, Budget>) {
                sm(CommandID.GET_BUDGET_RESPONSE_RECEIVED, if (status) 1 else 0, if (isIncome) 1 else 0, list)
            }

            override fun onPostNewsResponseReceived(status: Boolean, id: String) {}

            override fun onPostPaymentsResponseReceived(status: Boolean) {
                sm(CommandID.POST_PAYMENT_RESPONSE_RECEIVED, if (status) 1 else 0, 0, "")
            }

        })

        if (WimpleImpl.getInstance().isAuthed && WimpleImpl.getInstance().isInitializedFinished) {
            // Already Logged-in
            Log.d(LOG_TAG, "WimpleActivity, logged in, default section existing")
            WimpleImpl.getInstance().getUserInfo(true)
        } else {
            // not initialized or not Logged-in
            Log.d(LOG_TAG, "WimpleActivity, not initialized or not logged in")
            val intent = Intent(applicationContext, SplashScreenActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            applicationContext.startActivity(intent)
        }
    }


    @SuppressLint("HandlerLeak")
    private fun setupHandler() {
        mainHandler = object : Handler() {

            override fun handleMessage(msg: Message) {

                val command = msg.what
                val obj = msg.obj

                if(this@WimpleActivity.currentFragment == null)
                    return;

                updateAPIRemaining()

                when (command) {

                    CommandID.TOAST_LONG -> Snackbar.make(drawer_layout, obj.toString(), Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show()

                    CommandID.TOAST_SHORT -> Snackbar.make(drawer_layout, obj.toString(), Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show()

                    CommandID.UPDATE_USER_INFO -> {
                        setMyInfoOnMenu(obj as UserInfo)
                    }

                    CommandID.WIMPLE_PROFILE_PICTURE_UPDATED -> {
                        WidgetItem.replaceBitmapOfImageView(my_profile_icon, WimpleImpl.getInstance().profilePicture, false)
                    }

                    // TransactionInsertFragment
                    CommandID.MODIFY_ENTRY_OR_ADD_MONTHLY_ITEM -> {

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
        private const val whooingURL = "https://new.whooing.com"

        private var mainHandler: Handler? = null

        object CommandID {
            private const val CMD_BASE = 10000

            const val EXIT = CMD_BASE + 1
            const val TOAST_LONG = CMD_BASE + 3
            const val TOAST_SHORT = CMD_BASE + 5
            const val FATAL_ERROR = CMD_BASE + 6
            const val GET_PIN = CMD_BASE + 7
            const val SHOW_STATUS = CMD_BASE + 8
            const val UPDATE_USER_INFO = CMD_BASE + 9
            const val GET_ALL_ACCOUNT_RECEIVED = CMD_BASE + 11
            const val WIMPLE_LOGGIN_SUCCESS = CMD_BASE + 13
            const val WIMPLE_LOGGIN_FAILED = CMD_BASE + 15
            const val WIMPLE_LOGGOUT = CMD_BASE + 17
            const val GET_ALL_SECTION_RECEIVED = CMD_BASE + 19
            const val GET_MAKE_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 21
            const val GET_FREQUENT_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 23
            const val GET_LATEST_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 25
            const val GET_LATEST_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 27
            const val GET_ENTRIES_RECEIVED = CMD_BASE + 29
            const val MODIFY_ENTRY_OR_ADD_MONTHLY_ITEM = CMD_BASE + 31
            const val GET_MODIFY_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 33
            const val GET_MONTHLY_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 35
            const val WIMPLE_PROFILE_PICTURE_UPDATED = CMD_BASE + 37
            const val REMOVE_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 39
            const val REMOVE_MONTHLY_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 41
            const val GET_FINANCIAL_STATE_RESPONSE_RECEIVED = CMD_BASE + 43
            const val GET_INCOME_AND_EXPENSE_RESPONSE_RECEIVED = CMD_BASE + 45
            const val GET_BUDGET_RESPONSE_RECEIVED = CMD_BASE + 47
            const val POST_PAYMENT_RESPONSE_RECEIVED = CMD_BASE + 49
        }

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
