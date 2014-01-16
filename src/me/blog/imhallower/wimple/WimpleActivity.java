package me.blog.imhallower.wimple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import me.blog.imhallower.wimple.impl.IWimpleResponseListener;
import me.blog.imhallower.wimple.impl.IWimpleStatusListener;
import me.blog.imhallower.wimple.impl.WimpleImpl;
import me.blog.imhallower.wimple.model.Account;
import me.blog.imhallower.wimple.model.Entry;
import me.blog.imhallower.wimple.model.Item;
import me.blog.imhallower.wimple.model.Section;
import me.blog.imhallower.wimple.model.UserInfo;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.ActionBar.TabListener;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class WimpleActivity extends FragmentActivity implements
ActionBar.TabListener {

	private static final String LOG_TAG = "WimpleActivity";
	private static final int PIN_NUMBER_REQUEST = 1379;

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


	public static final class CommandID {

		private CommandID() {}

		public static final int CMD_BASE = 10000;

		public static final int EXIT = CMD_BASE + 1;
		public static final int TOAST_LONG = CMD_BASE + 3;
		public static final int TOAST_SHORT = CMD_BASE + 5;
		public static final int GET_PIN = CMD_BASE + 7;
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

	}

	public static void sm(int cmd, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, 1, 0, msg));    
	}    

	public static void sm(int cmd, int a1, int a2, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, a1, a2, msg));    
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		context = getApplicationContext();
		actionBar = getActionBar();
		setupWimpleImpl();
		setupHandler();

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
				invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
			}

			public void onDrawerOpened(View drawerView) {
				invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
			}
		};
		mDrawerLayout.setDrawerListener(mDrawerToggle);


		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(
				getSupportFragmentManager());

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
				}

			}
		});


		if (savedInstanceState == null) {
			setPagerAdapter(0);
		}

		wimple.getTempToken();
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
		ImageView icon = (ImageView)findViewById(R.id.my_profile_icon);
		//WidgetItem.replaceBitmapOfImageView(icon, info.getUserImgURL(), false);

		// Set  Name
		TextView name = (TextView)findViewById(R.id.my_profile_name);
		name.setText(info.getName());

		/*
			// temporary
			int nLevel = 8;
			if(nLevel > 10)
					nLevel = 10;

			TextView textLevel = (TextView)findViewById(R.id.my_profile_level);
			ImageView progressLevel = (ImageView)findViewById(R.id.my_profile_progress);

			textLevel.setText(getResources().getString(R.string.profile_level_prefix) + nLevel);
		    Resources resources = context.getResources();
		    DisplayMetrics metrics = resources.getDisplayMetrics();
		    float px = nLevel * 18 * (metrics.densityDpi / 160f);	    
			RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams ) progressLevel.getLayoutParams();
			params.width = (int)px;
			progressLevel.setLayoutParams(params);
		 */
		// Set OnClick listener => Detail Profile information
		RelativeLayout rlProfileWindow = (RelativeLayout)findViewById(R.id.my_profile_information_window);
		rlProfileWindow.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				// TODO : later
				//Intent intent = new Intent(context, DetailProfileActivity.class);
				//startActivity(intent);
			}

		});

	}

	private void setPagerAdapter(int n)
	{
		List<String> titles = listSubmenuTitles.get(n);
		List<Fragment> fragments = listSubmenuClasses.get(n);
		int nFragment = titles.size();

		if(currentMenuId == n){
			mDrawerLayout.closeDrawer(mSideMenu);
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
		Log.d(LOG_TAG, "SetpageAdapter Added Total " + nFragment + " submenus");

		mSectionsPagerAdapter.notifyDataSetChanged();

		if(nFragment> 1)
		{
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
			for(int i = 0; i < nFragment; i++){   		
				ActionBar.Tab tab = actionBar.newTab();
				actionBar.addTab(tab.setText(titles.get(i)).setTabListener((TabListener) this));
			}    	
		}
		else
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

		currentTabPosition = 0;
		setTitle(menuTitles[n]);
		mDrawerLayout.closeDrawer(mSideMenu);

		// For invitation display    	
		if(currentMenuId == 0 && currentTabPosition == 0){			

		}
	}

	private void moveTabOfPager(int pageID){

		if(currentTabPosition == pageID){
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

	private void setupWimpleImpl() {
		wimple.setApplicationContext(context);
		wimple.setStatusListener(new IWimpleStatusListener(){

			@Override
			public void onLoggedIn(boolean status) {
				if(status){
					sm(CommandID.WIMPLE_LOGGIN_SUCCESS, "");
					wimple.getAllAccounts(true);
					wimple.getLatestItems(true);
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
				// TODO Auto-generated method stub

			}

			@Override
			public void onNetworkConnectionLost() {
				// TODO Auto-generated method stub

			}

		});
		wimple.setResponseListener(new IWimpleResponseListener(){

			@Override
			public void onGetAuthTempToken(boolean status, String tempToken) {

				if(false == status){
					// TODO : login!!! 
					return;
				}

				if(null == tempToken || tempToken.isEmpty()){
					// TODO : do something
					return;
				}

				sm(CommandID.GET_PIN, tempToken);				
			}

			@Override
			public void onGetAuthAccessToken(boolean status,
					Map<String, String> result) {

				if(result.isEmpty()){
					Log.e(LOG_TAG, "Auth is failed.");
					// TODO : do something
					return;
				}

				String token = result.get("token");
				String tokenSecret = result.get("token_secret");
				String userID = result.get("user_id");

				if(null == tokenSecret ||
						true == tokenSecret.isEmpty()){
					Log.e(LOG_TAG, "Auth is failed.");
					// TODO : do something
					return;
				}

				wimple.getUserInfo();
				wimple.getAllSections();				
			}

			@Override
			public void onGetUserInfoResponseReceived(boolean status, UserInfo info) { 
				// TODO : we have to save to DB and use it at initial time
				if(status){
					Log.e(LOG_TAG, info.toString());
					sm(CommandID.UPDATE_USER_INFO, info);	
				}else{
					Toast.makeText(context, "Login FaileD!!!!", Toast.LENGTH_LONG).show();
				}
			}

			@Override
			public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list) {
				sm(CommandID.GET_ALL_SECTION_RECEIVED, list);
			}

			@Override
			public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list) {
				sm(CommandID.GET_ALL_ACCOUNT_RECEIVED, list);
			}

			@Override
			public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list) {
				sm(CommandID.GET_ENTRIES_RECEIVED, list);
			}

			@Override
			public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list) {
				sm(CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED, status);
			}

			@Override
			public void onMakeEntryResponseReceived(boolean status) {
				sm(CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED, status);				
			}

			@Override
			public void onGetFrequentItemsResponseReceived(boolean status,
					Collection<Item> list) {
				sm(CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED, list);
			}

			@Override
			public void onGetLatestItemsResponseReceived(boolean status,
					Collection<Item> list) {
				sm(CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED, list);
			}				

		});
	}


	@SuppressLint("HandlerLeak")
	private void setupHandler() {
		mainHandler = new Handler() {

			@Override
			public void handleMessage(Message msg) {

				int command = msg.what;
				Object obj = msg.obj;


				switch(command){

				case CommandID.TOAST_LONG :
					Toast.makeText(context, obj.toString(), Toast.LENGTH_LONG).show();
					break;

				case CommandID.TOAST_SHORT :
					Toast.makeText(context, obj.toString(), Toast.LENGTH_SHORT).show();
					break;

				case CommandID.GET_PIN :
				{
					Intent intent = new Intent(context, WebViewActivity.class);
					intent.putExtra("temp_token", obj.toString());
					startActivityForResult(intent, PIN_NUMBER_REQUEST);
					break;	
				}

				case CommandID.UPDATE_USER_INFO :
				{
					setMyInfoOnMenu((UserInfo)obj);
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

				// to all
				case CommandID.WIMPLE_LOGGIN_SUCCESS :
				case CommandID.WIMPLE_LOGGIN_FAILED :
				case CommandID.WIMPLE_LOGGOUT :
				case CommandID.GET_ALL_ACCOUNT_RECEIVED :
				case CommandID.GET_ALL_SECTION_RECEIVED :
				case CommandID.GET_ENTRIES_RECEIVED :
				case CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED :
				case CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED :
				case CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED :					
				{

					for(int i=0; i < mSectionsPagerAdapter.getCount() ; i++){

						Fragment fg = mSectionsPagerAdapter.getItem(i);

						if(fg instanceof IWimpleFragment){
							IWimpleFragment wfg = (IWimpleFragment) fg;
							wfg.handleMessage(msg);
						}	
					}
					break;
				}	

				default : {	
					Log.d(LOG_TAG, "Invalid Command ID=" + command);
					break;
				}

				}
				super.handleMessage(msg);
			}
		};
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {


		if(requestCode == PIN_NUMBER_REQUEST){
			if(resultCode != RESULT_OK){
				return;
			}else{
				String tempToken = data.getExtras().getString("temp_token");
				String pin = data.getExtras().getString("pin");

				if(null != tempToken &&
						null != pin){
					wimple.getAccessToken(tempToken, pin);					
				}else{
					//throw new Exception("Intent Arguemtn is invlaid, at WimpleActivity");
					Log.e(LOG_TAG, "Intent Arguemtn is invlaid, at WimpleActivity");
				}

			}
		}else{
			super.onActivityResult(requestCode, resultCode, data);	
		}		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
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

		private Collection<Fragment> frags = new ArrayList<Fragment>();

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}  

		@Override
		public Fragment getItem(int position) {
			return (Fragment)frags.toArray()[position];
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
		boolean drawerOpen = mDrawerLayout.isDrawerOpen(mSideMenu);
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

		/*
        case R.id.action_add_promise: {
        	Intent intent = new Intent(getApplicationContext(), SessionCreateActivity.class);
        	startActivity(intent);
        	return true;
        }
		 */

		default:
			return super.onOptionsItemSelected(item);
		}
	}

}
