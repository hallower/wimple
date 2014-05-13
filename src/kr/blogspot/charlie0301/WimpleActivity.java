package kr.blogspot.charlie0301;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import kr.blogspot.charlie0301.impl.IWimpleResponseListener;
import kr.blogspot.charlie0301.impl.IWimpleStatusListener;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.impl.util.Utils;
import kr.blogspot.charlie0301.impl.util.WidgetItem;
import kr.blogspot.charlie0301.model.Account;
import kr.blogspot.charlie0301.model.AccountState;
import kr.blogspot.charlie0301.model.Entry;
import kr.blogspot.charlie0301.model.Item;
import kr.blogspot.charlie0301.model.Section;
import kr.blogspot.charlie0301.model.UserInfo;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.ActionBar.TabListener;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.Toast;

public class WimpleActivity extends FragmentActivity implements
ActionBar.TabListener, OnMenuItemClickListener {

	private static final String LOG_TAG = "WimpleActivity";
	private static final String whooingURL = "https://whooing.com";

	private static final WimpleImpl wimple = WimpleImpl.getInstance();
	private static Handler mainHandler;
	public static Context context;

	private DrawerLayout mDrawerLayout;
	private LinearLayout mSideMenu;
	private ActionBarDrawerToggle mDrawerToggle;

	private int currentMenuId = -1;
	private String[] menuTitles;
	private List<Integer> mListSideMemuID;
	private List<List<String>> listSubmenuTitles;
	private List<List<Fragment>> listSubmenuClasses;
	private SideMenuClickListener mSideMenuClickListener;
	private ActionBar actionBar;

	private int currentTabPosition = -1;
	private SectionsPagerAdapter mSectionsPagerAdapter;
	private ViewPager mViewPager;

	// GUI
	TextView textLevel;
	ImageView progressLevel;
	ImageView profileIcon;

	// TODO : change this as enum
	public static final class CommandID {

		private CommandID() {}

		public static final int CMD_BASE = 10000;

		public static final int EXIT = CMD_BASE + 1;
		public static final int TOAST_LONG = CMD_BASE + 3;
		public static final int TOAST_SHORT = CMD_BASE + 5;
		public static final int FATAL_ERROR = CMD_BASE + 6;
		public static final int GET_PIN = CMD_BASE + 7;
		public static final int SHOW_STATUS = CMD_BASE + 8;		
		public static final int UPDATE_USER_INFO = CMD_BASE + 9;
		public static final int GET_ALL_ACCOUNT_RECEIVED = CMD_BASE + 11;
		public static final int WIMPLE_LOGGIN_SUCCESS = CMD_BASE + 13;
		public static final int WIMPLE_LOGGIN_FAILED = CMD_BASE + 15;
		public static final int WIMPLE_LOGGOUT = CMD_BASE + 17;
		public static final int GET_ALL_SECTION_RECEIVED = CMD_BASE + 19;
		public static final int GET_MAKE_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 21;
		public static final int GET_FREQUENT_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 23;
		public static final int GET_LATEST_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 25;
		public static final int GET_LATEST_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 27;
		public static final int GET_ENTRIES_RECEIVED = CMD_BASE + 29;
		public static final int MODIFY_ENTRY_OR_ADD_MONTHLY_ITEM = CMD_BASE + 31;
		public static final int GET_MODIFY_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 33;
		public static final int GET_MONTHLY_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 35;
		public static final int WIMPLE_PROFILE_PICTURE_UPDATED = CMD_BASE + 37;
		public static final int REMOVE_ENTRY_RESPONSE_RECEIVED = CMD_BASE + 39;
		public static final int REMOVE_MONTHLY_ITEMS_RESPONSE_RECEIVED = CMD_BASE + 41;
		public static final int GET_FINANCIAL_STATE_RESPONSE_RECEIVED = CMD_BASE + 43;
		public static final int GET_INCOME_AND_EXPENSE_RESPONSE_RECEIVED = CMD_BASE + 45;
	}

	public static void sm(int cmd, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, 1, 0, msg));    
	}    

	public static void sm(int cmd, int a1, int a2, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, a1, a2, msg));    
	}

	@Override
	protected void onResume() {

		setupWimpleImpl(false);

		super.onResume();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wimple);

		context = getApplicationContext();
		actionBar = getActionBar();

		setupHandler();
		setupWimpleImpl(true);
		setupMenus();


		// set a custom shadow that overlays the main content when the drawer opens
		mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
		// set up the drawer's list view with items and click listener


		mSideMenuClickListener = new SideMenuClickListener();
		for(int i=0; i< mListSideMemuID.size(); i++)
			findViewById(mListSideMemuID.get(i)).setOnClickListener(mSideMenuClickListener);


		// Set up the action bar.
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// enable ActionBar app icon to behave as action to toggle nav drawer
		actionBar.setDisplayHomeAsUpEnabled(true);        
		//actionBar.setHomeButtonEnabled(true);

		/*
		{
			actionBar.setCustomView(R.layout.actionbar);

			TextView txtTitle = (TextView)actionBar.getCustomView().findViewById(R.id.actionbar_title);
			txtTitle.setText(context.getResources().getString(R.string.drawer_menu_transaction));
			txtTitle.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					finish();
				}
			});

			ImageView back = (ImageView)actionBar.getCustomView().findViewById(R.id.actionbar_back);
			back.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					finish();
				}
			});
			actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
		}
		 */

		// ActionBarDrawerToggle ties together the the proper interactions
		// between the sliding drawer and the action bar app icon
		mDrawerToggle = new ActionBarDrawerToggle(
				this,                  /* host Activity */
				mDrawerLayout,         /* DrawerLayout object */
				R.drawable.ic_drawer,  /* nav drawer image to replace 'Up' caret */
				R.string.drawer_open,  /* "open drawer" description for accessibility */
				R.string.drawer_close  /* "close drawer" description for accessibility */
				) {
			public void onDrawerClosed(View view) {
				actionBar.setDisplayHomeAsUpEnabled(true);
				invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
			}

			public void onDrawerOpened(View drawerView) {
				updateAPIRemainning();
				actionBar.setDisplayHomeAsUpEnabled(false);
				invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
			}
		};
		mDrawerLayout.setDrawerListener(mDrawerToggle);


		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(
				getFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);
		mViewPager.setOffscreenPageLimit(3);

		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mViewPager
		.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {

				if(actionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_TABS &&
						actionBar.getNavigationItemCount() > 0){
					actionBar.setSelectedNavigationItem(position);
					currentTabPosition = position;
				}

			}
		});

		if (savedInstanceState == null) {
			setPagerAdapter(0);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);

		savedInstanceState.putInt("currentMenuId", currentMenuId);
		savedInstanceState.putInt("currentTabPosition", currentTabPosition);
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		int restoredMenuId = savedInstanceState.getInt("currentMenuId");
		int restoredTabPosition = savedInstanceState.getInt("currentTabPosition");
		setPagerAdapter(restoredMenuId, true);
		moveTabOfPager(restoredTabPosition, true);

		wimple.getUserInfo(false);
		wimple.getDefaultSections(false);	
	}

	private void setupMenus() {
		menuTitles = getResources().getStringArray(R.array.menu_titles);
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mSideMenu = (LinearLayout) findViewById(R.id.sidemenu_layout);

		{
			listSubmenuTitles = new ArrayList<List<String>>();
			listSubmenuClasses = new ArrayList<List<Fragment>>();
			mListSideMemuID = new ArrayList<Integer>();

			String[] allTitles = getResources().getStringArray(R.array.drawer_menus_title);        	

			for(String title : allTitles)
			{
				List<String> titles = new ArrayList<String>();
				//Log.d(LOG_TAG, "title =");
				for(String t : title.split(",")){
					//Log.d(LOG_TAG, t + ", ");
					titles.add(t);
				}
				listSubmenuTitles.add(titles);
			}            

			String[] allClasses = getResources().getStringArray(R.array.drawer_menus_class);
			for(String clas : allClasses)
			{
				List<Fragment> clases = new ArrayList<Fragment>();
				//Log.d(LOG_TAG, "class =");
				for(String cla : clas.split(",")){
					//Log.d(LOG_TAG, cla + ", ");
					try{
						Class<?> c = Class.forName(cla);
						Fragment frag = (Fragment)c.newInstance();

						if(frag instanceof IWimpleFragment){
							((IWimpleFragment) frag).setActivityInstance(this);
						}

						clases.add(frag);
					}catch(Exception e){
						e.printStackTrace();
						continue;
					}

				}
				listSubmenuClasses.add(clases); 
			}

			TypedArray ar = getResources().obtainTypedArray(R.array.side_menu_layout_id);
			int len = ar.length();
			for (int i = 0; i < len; i++)
				mListSideMemuID.add( ar.getResourceId(i, 0));
			ar.recycle();

		}
	}


	private class SideMenuClickListener implements View.OnClickListener  {


		public void onClick(View v) {	

			int nClickedID = v.getId();
			int n, len = mListSideMemuID.size();
			for(n=0; n< len; n++)
			{				
				if(mListSideMemuID.get(n) == nClickedID)				
					break;
			}

			if(n >= len)
				return;

			setPagerAdapter(n);	        
		}
	}

	private void setMyInfoOnMenu(UserInfo info)
	{

		// Set Icon
		profileIcon = (ImageView)findViewById(R.id.my_profile_icon);
		WidgetItem.replaceBitmapOfImageView(profileIcon, wimple.getProfilePicture(), false);

		// Set  Name
		TextView name = (TextView)findViewById(R.id.my_profile_name);
		name.setText(info.getName());

		// temporary
		textLevel = (TextView)findViewById(R.id.my_profile_level);
		progressLevel = (ImageView)findViewById(R.id.my_profile_progress);

		updateAPIRemainning();

		// Set OnClick listener => Detail Profile information
		LinearLayout rlProfileWindow = (LinearLayout)findViewById(R.id.my_profile_information_window);
		rlProfileWindow.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				// TODO : later
				//Intent intent = new Intent(context, DetailProfileActivity.class);
				//startActivity(intent);
			}

		});

	}

	private void updateAPIRemainning() {

		if(null == textLevel){
			return;
		}

		double totalLevel = wimple.getTotalAPICall();
		int nLevel = wimple.getRemainedAPICall();

		//Log.d(LOG_TAG, "updateAPIRemainning = " + nLevel + ", TotalLevel = " + totalLevel);

		if(nLevel < 0){
			nLevel = 0;
		}

		textLevel.setText(getResources().getString(R.string.number_api_count) + nLevel);
		float px = Utils.getDPSize((int)(130.0 * ((double)nLevel / totalLevel)));		
		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams ) progressLevel.getLayoutParams();
		params.width = (int)px;
		progressLevel.setLayoutParams(params);
	}

	private void setPagerAdapter(int n)
	{
		setPagerAdapter(n, false);	
	}

	private void setPagerAdapter(int n, boolean forceUpdate)
	{
		List<String> titles = listSubmenuTitles.get(n);
		List<Fragment> fragments = listSubmenuClasses.get(n);
		int nFragment = titles.size();

		if(currentMenuId == n &&
				forceUpdate == false){
			mDrawerLayout.closeDrawer(mSideMenu);
			return;
		}

		if(titles.size() == 0 ||
				fragments.size() == 0){
			return;
		}

		currentMenuId = n;
		actionBar.removeAllTabs();
		mSectionsPagerAdapter.clear();

		for(int i = 0; i < nFragment; i++){
			Fragment fg = fragments.get(i);
			Log.d(LOG_TAG, "SetpageAdapter Adding >> " + fg.getClass().getName());
			mSectionsPagerAdapter.addItem(fg);
		}
		mSectionsPagerAdapter.notifyDataSetChanged();

		Log.d(LOG_TAG, "SetpageAdapter Added Total " + nFragment + " submenus");

		if(nFragment> 1)
		{
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
			for(int i = 0; i < nFragment; i++){   		
				ActionBar.Tab tab = actionBar.newTab();
				actionBar.addTab(tab.setText(titles.get(i)).setTabListener((TabListener) this));
			}    	
		}
		else{
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		}

		currentTabPosition = 0;
		setTitle(menuTitles[n]);
		
		mDrawerLayout.closeDrawer(mSideMenu);
	}

	public void moveTabOfPager(int pageID){
		moveTabOfPager(pageID, false);
	}

	public void moveTabOfPager(int pageID, boolean forceUpdate){

		if(currentTabPosition == pageID &&
				false == forceUpdate ){
			return;
		}

		int tabs = actionBar.getNavigationItemCount();
		if(tabs > 1){
			if(tabs < pageID){
				actionBar.setSelectedNavigationItem(0);
				currentTabPosition = 0;
			}else{
				actionBar.setSelectedNavigationItem(pageID);
				currentTabPosition = pageID;
			}
		}
		else{
			actionBar.setSelectedNavigationItem(0);
			currentTabPosition = 0;
		}
		invalidateOptionsMenu();
	}

	private void setupWimpleImpl(boolean initialTime) {
		wimple.setApplicationContext(context);
		wimple.setStatusListener(new IWimpleStatusListener(){

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
		wimple.setResponseListener(new IWimpleResponseListener(){

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
					Toast.makeText(context, "Login FaileD!!!!", Toast.LENGTH_LONG).show();
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

		});

		if(initialTime){
			wimple.getUserInfo(true);
			wimple.getDefaultSections(false);	
		}		
	}


	@SuppressLint("HandlerLeak")
	private void setupHandler() {
		mainHandler = new Handler() {

			@Override
			public void handleMessage(Message msg) {

				int command = msg.what;
				Object obj = msg.obj;

				updateAPIRemainning();

				switch(command){

				case CommandID.TOAST_LONG :
					Toast.makeText(context, obj.toString(), Toast.LENGTH_LONG).show();
					break;

				case CommandID.TOAST_SHORT :
					Toast.makeText(context, obj.toString(), Toast.LENGTH_SHORT).show();
					break;

				case CommandID.UPDATE_USER_INFO :
				{
					setMyInfoOnMenu((UserInfo)obj);
					break;
				}

				case CommandID.WIMPLE_PROFILE_PICTURE_UPDATED :
				{
					WidgetItem.replaceBitmapOfImageView(profileIcon, wimple.getProfilePicture(), false);
					break;	
				}

				case CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED :								
				{

					Fragment fg = mSectionsPagerAdapter.getItem(currentTabPosition);

					if(fg instanceof IWimpleFragment){
						IWimpleFragment wfg = (IWimpleFragment) fg;
						wfg.handleMessage(msg);
					}
					break;
				}

				// TransactionInsertFragment
				case CommandID.MODIFY_ENTRY_OR_ADD_MONTHLY_ITEM : {

					moveTabOfPager(0);
					Fragment fg = mSectionsPagerAdapter.getItem(0);

					if(fg instanceof IWimpleFragment){
						IWimpleFragment wfg = (IWimpleFragment) fg;
						wfg.handleMessage(msg);
					}
					break;
				}

				// to all
				case CommandID.WIMPLE_LOGGIN_SUCCESS :
					wimple.getMonthlyItems();
					// No break;

				case CommandID.WIMPLE_LOGGIN_FAILED :
				case CommandID.WIMPLE_LOGGOUT :
				case CommandID.GET_ALL_ACCOUNT_RECEIVED :
				case CommandID.GET_ALL_SECTION_RECEIVED :
				case CommandID.GET_ENTRIES_RECEIVED :
				case CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED :
				case CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED :
				case CommandID.GET_MONTHLY_ITEMS_RESPONSE_RECEIVED :

				case CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED :
				case CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED : 

				default :
				{

					for(int i=0; i < mSectionsPagerAdapter.getCount() ; i++){

						Object fg = mSectionsPagerAdapter.getItem(i);

						if(fg instanceof IWimpleFragment){
							IWimpleFragment wfg = (IWimpleFragment) fg;
							wfg.handleMessage(msg);
						}	
					}
					break;
				}	

				}
				super.handleMessage(msg);
			}
		};
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.main, menu);

		/*
		ImageButton locButton = (ImageButton) menu.findItem(R.id.action_more).getActionView();
		locButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				showMenu(v);
			}
		});
		 */
		return true;
	}

	@Override
	public void onTabSelected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends FragmentStatePagerAdapter {

		private ArrayList<Fragment> frags = new ArrayList<Fragment>();

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}  

		@Override
		public Fragment getItem(int position) {
			return frags.get(position);
		}


		@Override
		public int getCount() {
			return frags.size();
		}

		public void addItem(Fragment fragment){
			frags.add(fragment);
		}

		public void clear(){
			frags.clear();
		}

		@Override
		public int getItemPosition(Object object) {

			Fragment frag = (Fragment)object;
			for(int i = 0 ; i < frags.size() ; i++){
				Fragment fragInList = (Fragment) frags.toArray()[i];

				if(fragInList.equals(frag)){
					return i;
				}
			}

			/*
			Log.e(LOG_TAG, "getItemPosition => Not found");
			Log.e(LOG_TAG, "getItemPosition => list count => " + frags.size());
			Log.e(LOG_TAG, "getItemPosition => object => " + object.getClass().getName());
			 */
			return POSITION_NONE;
		}
	}


	/* Called whenever we call invalidateOptionsMenu() */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if(null == mDrawerLayout){
			Log.e(LOG_TAG, "Oops, please check why mDrawerLayout is null in onPrepareOptionMenu");
			return super.onPrepareOptionsMenu(menu);
		}
		// If the nav drawer is open, hide action items related to the content view
		//boolean drawerOpen = mDrawerLayout.isDrawerOpen(mSideMenu);
		/*
        menu.findItem(R.id.action_add_promise).setVisible(true);
        menu.findItem(R.id.action_temp).setVisible(drawerOpen);
		 */
		switch(currentMenuId){
		default:
			/*
        case 0: // home
        	menu.findItem(R.id.action_show_profile_statics).setVisible(false);
            menu.findItem(R.id.action_main_option).setVisible(false);
        	break;
			 */        	
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// The action bar home/up action should open or close the drawer.
		// ActionBarDrawerToggle will take care of this.
		if (mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		// Handle action buttons
		switch(item.getItemId()) {


		case R.id.action_go_to_whooing: {
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(whooingURL));
			startActivity(i);
			return true;
		}

		default:
			return super.onOptionsItemSelected(item);
		}
	}


	public void showMenu(View v) {
		PopupMenu popup = new PopupMenu(this, v);

		// This activity implements OnMenuItemClickListener
		popup.setOnMenuItemClickListener(this);
		popup.inflate(R.menu.more_options);
		popup.show();
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_find_entry:
			// TODO : find entry
			return true;
		default:
			return false;
		}
	}
}
