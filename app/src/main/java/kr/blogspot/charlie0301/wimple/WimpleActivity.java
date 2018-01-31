package kr.blogspot.charlie0301.wimple;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Map;

import kr.blogspot.charlie0301.wimple.impl.IWimpleResponseListener;
import kr.blogspot.charlie0301.wimple.impl.IWimpleStatusListener;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.impl.util.WidgetItem;
import kr.blogspot.charlie0301.wimple.model.Account;
import kr.blogspot.charlie0301.wimple.model.AccountState;
import kr.blogspot.charlie0301.wimple.model.Budget;
import kr.blogspot.charlie0301.wimple.model.Entry;
import kr.blogspot.charlie0301.wimple.model.Item;
import kr.blogspot.charlie0301.wimple.model.Section;
import kr.blogspot.charlie0301.wimple.model.UserInfo;

public class WimpleActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String LOG_TAG = "WimpleActivity";
    private static final String whooingURL = "https://new.whooing.com";

    private static final WeakReference<WimpleImpl> wimple = new WeakReference<>(WimpleImpl.getInstance());
    private static Handler mainHandler;
    public static WeakReference<Context> context;

	private int currentMenuID;
	private DrawerLayout drawer;
	private Fragment currentFragment;
	private FloatingActionButton fab;

	private TextView textLevel;
	private ImageView profileIcon;

    public static final class CommandID {

        private CommandID() {}

        static final int CMD_BASE = 10000;

        static final int EXIT = CMD_BASE + 1;
        static final int TOAST_LONG = CMD_BASE + 3;
        static final int TOAST_SHORT = CMD_BASE + 5;
        static final int FATAL_ERROR = CMD_BASE + 6;
        static final int GET_PIN = CMD_BASE + 7;
        static final int SHOW_STATUS = CMD_BASE + 8;
        static final int UPDATE_USER_INFO = CMD_BASE + 9;
        static final int GET_ALL_ACCOUNT_RECEIVED = CMD_BASE + 11;
        static final int WIMPLE_LOGGIN_SUCCESS = CMD_BASE + 13;
        static final int WIMPLE_LOGGIN_FAILED = CMD_BASE + 15;
        static final int WIMPLE_LOGGOUT = CMD_BASE + 17;
        static final int GET_ALL_SECTION_RECEIVED = CMD_BASE + 19;
        static final int GET_MAKE_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 21;
        static final int GET_FREQUENT_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 23;
        static final int GET_LATEST_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 25;
        static final int GET_LATEST_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 27;
        static final int GET_ENTRIES_RECEIVED = CMD_BASE + 29;
        static final int MODIFY_ENTRY_OR_ADD_MONTHLY_ITEM = CMD_BASE + 31;
        static final int GET_MODIFY_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 33;
        static final int GET_MONTHLY_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 35;
        static final int WIMPLE_PROFILE_PICTURE_UPDATED = CMD_BASE + 37;
        static final int REMOVE_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 39;
        static final int REMOVE_MONTHLY_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 41;
        static final int GET_FINANCIAL_STATE_RESPONSE_RECEIVED = CMD_BASE + 43;
        static final int GET_INCOME_AND_EXPENSE_RESPONSE_RECEIVED = CMD_BASE + 45;
        static final int GET_BUDGET_RESPONSE_RECEIVED = CMD_BASE + 47;
        static final int POST_PAYMENT_RESPONSE_RECEIVED = CMD_BASE + 49;
		static final int PERMISSIONS_REQUEST_RECEIVE_SMS = CMD_BASE + 51;
    }

	public static void sm(int cmd, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, 1, 0, msg));
	}

	public static void sm(int cmd, int a1, int a2, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, a1, a2, msg));
	}

	public static void smd(int cmd, Object msg, long ms){
		mainHandler.sendMessageDelayed(Message.obtain(mainHandler, cmd, 1, 0, msg), ms);
	}

	@Override
	protected void onResume() {
		context = new WeakReference<>(getApplicationContext());
		Log.i(LOG_TAG, "WimpleActivity - onResume!!!");
		setupWimpleImpl();
		super.onResume();
	}

	@Override
    protected void onCreate(Bundle savedInstanceState) {
		context = new WeakReference<>(getApplicationContext());
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wimple);

		// GUI
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
				if(currentMenuID != R.id.menu_transaction_insert){
					replaceWimpleFragment(R.id.menu_transaction_insert);
				}else{
					replaceWimpleFragment(R.id.menu_transaction_list);
				}
            }
        });

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setItemIconTintList(null);
        navigationView.setNavigationItemSelectedListener(this);

		// Setup Wimple
		setupHandler();
		setupWimpleImpl();

		if(null != findViewById(R.id.fragment_container))
		{
			if (savedInstanceState != null)
				return;
			setDefaultFragment();
		}
	}

	private void requestPermissions(String permission) {
		if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, permission)) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
				Toast.makeText(this, R.string.permission_sms_recv, Toast.LENGTH_LONG).show();
				// TODO : Should I request again?
			} else {
				ActivityCompat.requestPermissions(this,
						new String[]{permission},
						CommandID.PERMISSIONS_REQUEST_RECEIVE_SMS);
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {
		switch (requestCode) {
			case CommandID.PERMISSIONS_REQUEST_RECEIVE_SMS: {
				if (grantResults.length > 0
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					sm(CommandID.TOAST_LONG, getResources().getString(R.string.permission_SMS_recv_accept));
				} else {
					sm(CommandID.TOAST_LONG, getResources().getString(R.string.permission_SMS_recv_deny));
				}
			}
		}
	}

	private void hideVirtualKeyboard(){
		EditText editText = (EditText) findViewById(R.id.insert_entry_title);
		if(null != editText){
			editText.setFocusable(false);
			editText.setFocusableInTouchMode(true);
		}
		View view = this.getCurrentFocus();
		if (view != null) {
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
		}
	}
	private void setDefaultFragment() {
		fab.setImageResource(R.drawable.ic_fab_list);
		currentMenuID = R.id.menu_transaction_insert;
		currentFragment = new TransactionInsertFragment();
		((IWimpleFragment)currentFragment).setActivityInstance(this);
		currentFragment.setArguments(getIntent().getExtras());
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.add(R.id.fragment_container, currentFragment);
		transaction.commit();
	}

	@Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.wimple, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_go_to_whooing) {
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(whooingURL));
			startActivity(i);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

		// TODO : what will be happend?
		if(!replaceWimpleFragment(id))
			return false;

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

	private boolean replaceWimpleFragment(int id) {
		return replaceWimpleFragment(id, null);
	}

	private boolean replaceWimpleFragment(int id, Bundle bundle) {
		boolean isNeedAddFab = true;

		hideVirtualKeyboard();

		if(currentMenuID == id)
			return true;

		if (id == R.id.menu_transaction_insert) {
			isNeedAddFab = false;
			currentFragment = new TransactionInsertFragment();
			//mDrawerList.setItemChecked(position, true);
			//setTitle(mPlanetTitles[position]);
		} else if (id == R.id.menu_transaction_list) {
			currentFragment = new TransactionListFragment();
		} else if (id == R.id.menu_financial_overview) {
			currentFragment = new FinancialStateSummaryFragment();
		} else if (id == R.id.menu_saving) {
			currentFragment = new SavingStateSummaryFragment();
		} else if (id == R.id.menu_debt) {
			currentFragment = new DebtStateSummaryFragment();
		} else if (id == R.id.menu_income_expense_overview) {
			currentFragment = new IncomeExpenseSummaryFragment();
		} else if (id == R.id.menu_income) {
			currentFragment = new IncomeSummaryFragment();
		} else if (id == R.id.menu_expense) {
			currentFragment = new ExpenseSummaryFragment();
		} else if (id == R.id.menu_preference) {
			currentFragment = new SettingsFragment();
		} else {
			return false;
		}

		currentMenuID = id;

		try{
			((IWimpleFragment)currentFragment).setActivityInstance(this);
			if(null != bundle)
				currentFragment.setArguments(bundle);
			FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
			transaction.replace(R.id.fragment_container, currentFragment);
			transaction.commit();
		}catch(Exception e){
			Log.e(LOG_TAG, "replaceWimpleFragment: " + e.getMessage());
			return false;
		}

		if(isNeedAddFab) {
			fab.setImageResource(R.drawable.ic_fab_add);
		}else {
			fab.setImageResource(R.drawable.ic_fab_list);
		}

		return true;
	}


	private void setMyInfoOnMenu(UserInfo info)
	{
		profileIcon = (ImageView)findViewById(R.id.my_profile_icon);
		if(null == profileIcon)
		{
			smd(CommandID.UPDATE_USER_INFO, info, 1000);
			return;
		}

		TextView sectionTitle = (TextView)findViewById(R.id.section_title);
		sectionTitle.setText(wimple.get().getDefaultSectionName());

		WidgetItem.replaceBitmapOfImageView(profileIcon, wimple.get().getProfilePicture(), false);

		TextView name = (TextView)findViewById(R.id.my_profile_name);
		name.setText(info.getName());

		textLevel = (TextView)findViewById(R.id.my_profile_level);

		updateAPIRemaining();

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

	private void updateAPIRemaining() {

		if(null == textLevel)
			return;

		int nLevel = wimple.get().getRemainedAPICall();
		if(nLevel < 0)
			nLevel = 0;

		textLevel.setText(getResources().getString(R.string.number_api_count) + " " + nLevel);
	}

	private void setupWimpleImpl() {
		wimple.get().setApplicationContext(context.get());
		wimple.get().setStatusListener(new IWimpleStatusListener(){

			@Override
			public void onLoggedIn(boolean status) {
				if(status){
					sm(CommandID.WIMPLE_LOGGIN_SUCCESS, "");
				}else{
					sm(CommandID.WIMPLE_LOGGIN_FAILED, "");
				}
			}

			@Override
			public void onLoggedOut() {
				sm(CommandID.WIMPLE_LOGGOUT, "");
			}

			@Override
			public void onNetworkConnectionEstablished() {
			}

			@Override
			public void onNetworkConnectionLost() {
			}

			@Override
			public void onProfilePictureUpdated() {
				sm(CommandID.WIMPLE_PROFILE_PICTURE_UPDATED, "");
			}

		});
		wimple.get().setResponseListener(new IWimpleResponseListener(){

			@Override
			public void onGetAuthTempToken(boolean status, String tempToken) {
			}

			@Override
			public void onGetAuthAccessToken(boolean status,
											 Map<String, String> result) {
			}

			@Override
			public void onGetUserInfoResponseReceived(boolean status, UserInfo info) {
				if(status){
					Log.i(LOG_TAG, info.toString());
					sm(CommandID.UPDATE_USER_INFO, 1, 0, info);
				}else{
					Toast.makeText(context.get(), "Login FaileD!!!!", Toast.LENGTH_LONG).show();
				}
			}

			@Override
			public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list) {
				sm(CommandID.GET_ALL_SECTION_RECEIVED, status?1:0, 0, list);
			}

			@Override
			public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list) {
				sm(CommandID.GET_ALL_ACCOUNT_RECEIVED, status?1:0, 0, list);
			}

			@Override
			public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list) {
				sm(CommandID.GET_ENTRIES_RECEIVED, status?1:0, 0, list);
			}

			@Override
			public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list) {
				sm(CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED, status?1:0, 0, list);
			}

			@Override
			public void onMakeEntryResponseReceived(boolean status, String entryDate) {
				sm(CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED, status?1:0, 0, entryDate);
			}

			@Override
			public void onGetFrequentItemsResponseReceived(boolean status,
														   Collection<Item> list) {
				sm(CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED, status?1:0, 0, list);
			}

			@Override
			public void onGetLatestItemsResponseReceived(boolean status,
														 Collection<Item> list) {
				sm(CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED, status?1:0, 0, list);
			}

			@Override
			public void onModifyEntryResponseReceived(boolean status, Entry entry) {
				sm(CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED, status?1:0, 0, entry);
			}

			@Override
			public void onGetMonthlyItemsResponseReceived(boolean status,
														  ArrayList<Item> list) {
				sm(CommandID.GET_MONTHLY_ITEMS_RESPONSE_RECEIVED, status?1:0, 0, list);
			}

			@Override
			public void onRemoveEntryResponseReceived(boolean status, String id) {
				sm(CommandID.REMOVE_ENTRY_RESPONSE_RECEIVED, status?1:0, 0, id);
			}

			@Override
			public void onRemoveMonthlyItemResponseReceived(boolean status, String id) {
				sm(CommandID.REMOVE_MONTHLY_ITEMS_RESPONSE_RECEIVED, status?1:0, 0, id);
			}

			@Override
			public void onGetFinancialStateResponseReceived(boolean status,
															Collection<AccountState> list) {
				sm(CommandID.GET_FINANCIAL_STATE_RESPONSE_RECEIVED, status?1:0, 0, list);
			}

			@Override
			public void onGetIncomeAndExpenseResponseReceived(boolean status,
															  Collection<AccountState> list) {
				sm(CommandID.GET_INCOME_AND_EXPENSE_RESPONSE_RECEIVED, status?1:0, 0, list);
			}

			@Override
			public void onGetBudgetResponseReceived(boolean status, boolean isIncome,
													Map<String, Budget> list) {
				sm(CommandID.GET_BUDGET_RESPONSE_RECEIVED, status?1:0, isIncome?1:0, list);
			}

			@Override
			public void onPostNewsResponseReceived(boolean status, String id) { }

			@Override
			public void onPostPaymentsResponseReceived(boolean status) {
				sm(CommandID.POST_PAYMENT_RESPONSE_RECEIVED, status?1:0, 0, "");
			}

		});

		if(wimple.get().isAuthed() &&
				wimple.get().isInitializedFinished()){
			// Already Logged-in
			Log.d(LOG_TAG, "wimpleactivity, logged in, default section existing");
			wimple.get().getUserInfo(true);
		}else{
			// not initialized or not Logged-in
			Log.d(LOG_TAG, "wimpleactivity, not initialzed or not logged in");
			Intent intent = new Intent(context.get(), SplashScreenActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			context.get().startActivity(intent);
		}
	}


	@SuppressLint("HandlerLeak")
	private void setupHandler() {
		mainHandler = new Handler() {

			@Override
			public void handleMessage(Message msg) {

				int command = msg.what;
				Object obj = msg.obj;

				updateAPIRemaining();

				switch(command){

					case CommandID.TOAST_LONG :
						Snackbar.make(drawer, obj.toString(), Snackbar.LENGTH_LONG)
								.setAction("Action", null).show();
						break;

					case CommandID.TOAST_SHORT :
						Snackbar.make(drawer, obj.toString(), Snackbar.LENGTH_SHORT)
								.setAction("Action", null).show();
						break;

					case CommandID.PERMISSIONS_REQUEST_RECEIVE_SMS :
						requestPermissions(obj.toString());
						break;

					case CommandID.UPDATE_USER_INFO :
					{
						setMyInfoOnMenu((UserInfo)obj);
						break;
					}

					case CommandID.WIMPLE_PROFILE_PICTURE_UPDATED :
					{
						WidgetItem.replaceBitmapOfImageView(profileIcon, wimple.get().getProfilePicture(), false);
						break;
					}

					// TransactionInsertFragment
					case CommandID.MODIFY_ENTRY_OR_ADD_MONTHLY_ITEM : {

						if(!(currentFragment instanceof TransactionInsertFragment)){
							replaceWimpleFragment(R.id.menu_transaction_insert);
							smd(msg.what, msg.obj, 300);
						}

						if(null != currentFragment &&
								currentFragment instanceof IWimpleFragment)
						{
							IWimpleFragment wfg = (IWimpleFragment) currentFragment;
							wfg.handleMessage(msg);
						}
						break;
					}

					// to all
					case CommandID.WIMPLE_LOGGIN_SUCCESS :
						wimple.get().getMonthlyItems();
						// No break;

					case CommandID.WIMPLE_LOGGIN_FAILED :
					case CommandID.WIMPLE_LOGGOUT :
					case CommandID.GET_ALL_ACCOUNT_RECEIVED :
					case CommandID.GET_ALL_SECTION_RECEIVED :
					case CommandID.GET_ENTRIES_RECEIVED :
					case CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED :
					case CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED :
					case CommandID.GET_MONTHLY_ITEMS_RESPONSE_RECEIVED :
					case CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED :
					case CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED :
					case CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED :

					default :
					{
						if(null != currentFragment &&
								currentFragment instanceof IWimpleFragment)
						{
							IWimpleFragment wfg = (IWimpleFragment) currentFragment;
							wfg.handleMessage(msg);
						}
						break;
					}

				}
				super.handleMessage(msg);
			}
		};
	}
}
