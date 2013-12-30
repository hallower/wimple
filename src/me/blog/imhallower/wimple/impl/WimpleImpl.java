package me.blog.imhallower.wimple.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import me.blog.imhallower.wimple.impl.RestAPIInvoker.HTTP_METHOD;
import me.blog.imhallower.wimple.impl.db.UserInfoDBHandler;
import me.blog.imhallower.wimple.model.Account;
import me.blog.imhallower.wimple.model.Entry;
import me.blog.imhallower.wimple.model.Item;
import me.blog.imhallower.wimple.model.Section;
import me.blog.imhallower.wimple.model.UserInfo;

import org.json.simple.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;


public class WimpleImpl implements IWimpleImpl {

	private static final WimpleImpl INSTANCE = new WimpleImpl();

	private static Context context = null;
	private final HandlerThread dispatchHandlerThread;
	private final MainHandler mainHandler;   

	private final EntryManager em = new EntryManager(this);
	private final ItemManager im = new ItemManager(this);
	
	private final RestAPIInvoker rai;
	
	private UserInfoDBHandler uidbh = null;


	// Temporary!!!
	public String firstSectionID;
	public UserInfo userInfo = null;
	public Collection<Section> sectionList = null;
	public String accountSectionID;
	public Collection<Account> accountList = null; 

	private static IWimpleStatusListener statusListener = new IWimpleStatusListener(){

		@Override
		public void onLoggedIn(boolean status) { }

		@Override
		public void onLoggedOut() { }

		@Override
		public void onNetworkConnectionEstablished() {}

		@Override
		public void onNetworkConnectionLost() {}

	};

	private static IWimpleResponseListener responseListener = new IWimpleResponseListener(){

		@Override
		public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list) {}
		@Override
		public void onGetAuthTempToken(boolean status, String tempToken) {}
		@Override
		public void onGetAuthAccessToken(boolean status, Map<String, String> result) {}
		@Override
		public void onGetUserInfoResponseReceived(boolean status, UserInfo info) { }
		@Override
		public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list) {}
		@Override
		public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list) {}
		@Override
		public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list) { }
		@Override
		public void onMakeEntryResponseReceived(boolean status) { }
		@Override
		public void onGetFrequentItemsResponseReceived(boolean status, Collection<Item> list) { }
		@Override
		public void onGetLatestItemsResponseReceived(boolean status, Collection<Item> list) { }
	};

	protected WimpleImpl(){ 

		dispatchHandlerThread = new HandlerThread("Dispatching");
		dispatchHandlerThread.start();
		mainHandler = new MainHandler(dispatchHandlerThread.getLooper());
		rai = new RestAPIInvoker(this);

	}

	/**
	 * Get instance of Promise class
	 * <p>
	 * The response of this method will be returned the instance of the Promise class.
	 */
	public static WimpleImpl getInstance(){
		return INSTANCE;
	}

	public void setApplicationContext(Context context){
		WimpleImpl.context = context;


		if(null == uidbh){
			uidbh = new UserInfoDBHandler(WimpleImpl.context);    
		}
	}

	public void setStatusListener(IWimpleStatusListener listener){
		WimpleImpl.statusListener = listener;
	}

	public void setResponseListener(IWimpleResponseListener listener){
		WimpleImpl.responseListener = listener;
	}

	public static IWimpleStatusListener getStatusListener() {
		return statusListener;
	}

	public static IWimpleResponseListener getResponseListener() {
		return responseListener;
	}


	private static final String LOG_TAG = "Wimple";

	private static final String serviceHost = "https://whooing.com/";
	//private static final String appID = "139";
	//private static final String vo42iw5me4vxz = "a33134b9c4107b9c00d2f8f128a1f69b8a3d0360";
	private static final String appID = "140";
	private static final String vo42iw5me4vxz = "***REDACTED-WIMPLE-APP-SECRET***";

	private String token;
	private String tokenSecret;
	private String userID;

	private final Semaphore authInProgress = new Semaphore(0);
	private static Integer sequence = 10000;
	private boolean isAuthed = false;

	public static final class Path {

		public static final String AUTH_REQUEST_TOKEN 	= "app_auth/request_token";
		public static final String AUTH_AUTHORIZE 		= "app_auth/authorize";
		public static final String AUTH_ACCESS_TOKEN 		= "app_auth/access_token";

		public static final String USER_INFO				= "api/user.json";

		public static final String SECTIONS_ALL			= "api/sections.json";
		public static final String SECTIONS_DEFAULT		= "api/sections/default.json";

		public static final String ACCOUNT_ALL			= "api/accounts.json";

		public static final String ENTRIES_ALL			= "api/entries.json_array";
		public static final String ENTRIES_LATEST			= "api/entries/latest.json_array";	
		
		public static final String ITEM_FREQUENT			= "api/frequent_items.json_array";
		public static final String ITEM_LATEST			= "api/entries/latest_items.json_array";

	};


	/*
	 * These APIs are regarding Promise Library management
	 */

	/**
	 * Start using the Promise library
	 * 
	 */
	public boolean start(){
		return true;
	}

	/**
	 * Stop using the Promise library
	 * 
	 */
	public void stop(){

	}


	public String getServicehost() {
		return serviceHost;
	}

	public String getAppid() {
		return appID;
	}

	public String getVo42iw5me4vxz() {
		return vo42iw5me4vxz;
	}

	public String getToken() {
		return token;
	}

	public String getTokenSecret() {
		return tokenSecret;
	}

	public String getUserID() {
		return userID;
	}

	public Integer getSequence() {
		return sequence++;
	}


	/*
	 * Utils
	 */
	public JSONObject invokeRESTAPI(HTTP_METHOD method, String path, String params){
		return rai.invokeRESTAPI(method, path, params);
	}


	/*
	 * Handler
	 */

	public static final class CommandID {

		private CommandID() {}

		public static final int CMD_BASE = 1000;

		public static final int CMD_AUTHENTICATION_SUCCEED = CMD_BASE + 1;
		public static final int CMD_AUTHENTICATION_FAILED = CMD_BASE + 3;
		public static final int CMD_GET_TEMP_TOKEN = CMD_BASE + 5;
		public static final int CMD_GET_ACCESS_TOKEN = CMD_BASE + 7;

		public static final int CMD_GET_USER_INFO = CMD_BASE + 9;

		public static final int CMD_GET_SECTIONS = CMD_BASE + 11;
		public static final int CMD_GET_SECTIONS_DEFAULT = CMD_BASE + 13;
		public static final int CMD_GET_ACCOUNT_ALL = CMD_BASE + 15;
		public static final int CMD_GET_ENTRIES = CMD_BASE + 17;
		public static final int CMD_GET_LATEST_ENTRIES = CMD_BASE + 19;
		public static final int CMD_POST_ENTRY = CMD_BASE + 21;		
		public static final int CMD_GET_FRQUENT_ITEMS = CMD_BASE + 23;		
		public static final int CMD_GET_LATEST_ITEMS = CMD_BASE + 25;

	}


	public void sm(int cmd, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, 1, 0, msg));    
	}

	public void sm(int cmd, int a1, int a2, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, a1, a2, msg));    
	}

	private class MainHandler extends Handler {

		@SuppressLint("HandlerLeak")
		public MainHandler(Looper looper){
			super(looper);
		}

		@SuppressWarnings("unchecked")
		public void handleMessage(Message msg) {

			int command = msg.what;
			boolean booleanStatus = (msg.arg1 == 1);
			Object obj = msg.obj;

			Log.d(LOG_TAG, "CMDCode = " + command + " Status = " + booleanStatus + " OBJ= " + ((null!=obj)?obj.getClass().getName():""));

			switch(command){

			case CommandID.CMD_AUTHENTICATION_SUCCEED :

				Log.d(LOG_TAG, "Wimple authentication is succeed!!!");
				break;

			case CommandID.CMD_AUTHENTICATION_FAILED :

				Log.d(LOG_TAG, "Wimple authentication is failed!!!");
				break;

			case CommandID.CMD_GET_TEMP_TOKEN :
				responseListener.onGetAuthTempToken(booleanStatus, (String) obj);
				break;

			case CommandID.CMD_GET_ACCESS_TOKEN :
			{
				Map<String, String> list = (Map<String, String>) obj;

				token = list.get("token");
				tokenSecret = list.get("token_secret");
				userID = list.get("user_id");

				// TODO : Have to store userID
				if(null == context){
					Log.e(LOG_TAG, "Application Context is not set!!!");
					//throw new Exception("Application Context is not set!!!");
				}

				SharedPreferences settings = context.getSharedPreferences("wimple.settings", 0);
				settings.edit().putString("userid", userID).commit(); 

				if(booleanStatus){
					Log.d(LOG_TAG, "CMD_GET_ACCESS_TOKEN is succeed!!!");
				}else{
					Log.d(LOG_TAG, "CMD_GET_ACCESS_TOKEN is failed!!!");
				}
				responseListener.onGetAuthAccessToken(booleanStatus, list);
				statusListener.onLoggedIn(booleanStatus);
			}
			break;

			case CommandID.CMD_GET_USER_INFO :
				responseListener.onGetUserInfoResponseReceived(booleanStatus, (UserInfo) obj);
				break;

			case CommandID.CMD_GET_SECTIONS :
				responseListener.onGetAllSectionResponseReceived(booleanStatus, (Collection<Section>) obj);
				break;

			case CommandID.CMD_GET_ACCOUNT_ALL :
				responseListener.onGetAllAccountResponseReceived(booleanStatus, (Collection<Account>) obj);
				break;

			case CommandID.CMD_GET_ENTRIES :
				responseListener.onGetEntriesResponseReceived(booleanStatus, (Collection<Entry>) obj);
				break;

			case CommandID.CMD_GET_LATEST_ENTRIES :
				responseListener.onGetLatestEntriesResponseReceived(booleanStatus, (Collection<Entry>) obj);
				break;

			case CommandID.CMD_POST_ENTRY :
				responseListener.onMakeEntryResponseReceived(booleanStatus);
				break;
				
			case CommandID.CMD_GET_FRQUENT_ITEMS :
				responseListener.onGetFrequentItemsResponseReceived(booleanStatus, (Collection<Item>)obj);
				break;
				
			case CommandID.CMD_GET_LATEST_ITEMS :
				responseListener.onGetLatestItemsResponseReceived(booleanStatus, (Collection<Item>)obj);
				break;
				
			default : 
				break;

			}

		}
	}




	/*
	 * Server APIs
	 */


	/*
	 * Auth APIs
	 */

	public Boolean isAuthed(){
		return isAuthed;
	}

	public Boolean getTempToken(){

		if(isAuthed){
			// TODO : how to handle this?
			return false;
		}

		new Thread(){
			@Override
			public void run() {

				String params = "app_id=" + appID + "&app_secret=" + vo42iw5me4vxz ;
				Map<String, String> list = rai.invokeRESTAPIForMap(Path.AUTH_REQUEST_TOKEN, params);				

				if(null == list){
					sm(CommandID.CMD_GET_TEMP_TOKEN, 0, 0, new HashMap<String, String>());
					return;
				}
				sm(CommandID.CMD_GET_TEMP_TOKEN, 1, 0, list.get("token"));
			}
		}.start();		

		return true;
	}


	/*
	private String getPin(String token){

		String params = "token=" + token;
		Map<String, String> list = invokeRESTAPIForMap(Path.AUTH_AUTHORIZE, params);

		return list.get("pin");
	}
	 */

	public Boolean getAccessToken(String token, String pin){

		if(isAuthed){
			// TODO : how to handle this?
			return false;
		}

		new GetAccessTokenTaskThread(token, pin).start();

		return true;
	}

	public class GetAccessTokenTaskThread extends Thread {

		private final String token;
		private final String pin;

		public GetAccessTokenTaskThread(String token, String pin) {
			super();
			this.token = token;
			this.pin = pin;
		}

		@Override
		public void run() {

			String params = "app_id=" + appID + "&app_secret=" + vo42iw5me4vxz + "&token=" + token + "&pin=" + pin;
			Log.d(LOG_TAG, "[GetAccessTokenTaskThread] PARAMS : " + params);
			Map<String, String> list = rai.invokeRESTAPIForMap(Path.AUTH_ACCESS_TOKEN, params);

			if(null == list){
				sm(CommandID.CMD_GET_ACCESS_TOKEN, 0, 0, new HashMap<String, String>());
				return;
			}

			String tokenSecret = list.get("token_secret");
			if(null != tokenSecret &&
					false == tokenSecret.isEmpty()){
				isAuthed = true;	
			}

			sm(CommandID.CMD_GET_ACCESS_TOKEN, 1, 0, list);
		}
	}

	public boolean getAllSections(){

		if(false == isAuthed){
			return false;
		}

		new Thread(){
			@Override
			public void run() {

				if(null != sectionList){
					sm(CommandID.CMD_GET_SECTIONS, 1, 0, sectionList);
					return;
				}

				Collection<Section> list = new ArrayList<Section>();

				JSONObject json = rai.invokeGET(Path.SECTIONS_ALL);

				if(null == json){
					sm(CommandID.CMD_GET_SECTIONS, 0, 0, list);
					return;
				}


				JSONObject results = (JSONObject) json.get("results");				
				String sid = "";

				for(Object key : results.keySet()){
					JSONObject section = (JSONObject) results.get(key);

					Object isolation = section.get("isolation");
					if(null != isolation &&
							0 == isolation.toString().compareToIgnoreCase("y")){
						continue;
					}

					list.add(new Section(section));
				}

				// TODO : insert into DB
				sectionList = list;
				firstSectionID = ((Section)list.toArray()[0]).getId();
				sm(CommandID.CMD_GET_SECTIONS, 1, 0, list);
			}			

		}.start();		
		return true;
	}


	public boolean getUserInfo(){

		if(false == isAuthed){
			return false;
		}

		new Thread(){

			@Override
			public void run() {

				UserInfo info = new UserInfo();

				JSONObject json = rai.invokeGET(Path.USER_INFO);

				if(null == json){
					sm(CommandID.CMD_GET_USER_INFO, 0, 0, null);
					return;
				}

				JSONObject result = (JSONObject) json.get("results");				

				info = new UserInfo(result);
				userInfo = info;
				// TODO : insert UserInfo to DB
				sm(CommandID.CMD_GET_USER_INFO, 1, 0, info);


			}			

		}.start();		
		return true;
	}

	public boolean getDefaultSections(){

		if(false == isAuthed){
			return false;
		}

		new Thread(){

			@Override
			public void run() {

				Collection<Section> list = new ArrayList<Section>();

				JSONObject json = rai.invokeGET(Path.SECTIONS_DEFAULT);

				if(null == json){
					sm(CommandID.CMD_GET_SECTIONS_DEFAULT, 0, 0, list);
					return;
				}

				JSONObject results = (JSONObject) json.get("results");				
				String sid = "";

				for(Object key : results.keySet()){
					JSONObject section = (JSONObject) results.get(key);

					Object isolation = section.get("isolation");
					if(null != isolation &&
							0 == isolation.toString().compareToIgnoreCase("y")){
						continue;
					}

					list.add(new Section(section));
				}

				sm(CommandID.CMD_GET_SECTIONS_DEFAULT, 1, 0, list);
			}			

		}.start();		
		return true;
	}

	public boolean getAllAccounts(){

		if(false == isAuthed ||
				null == firstSectionID ||
				firstSectionID.isEmpty()){
			return false;
		}

		new GetAllAccountsTaskThread(firstSectionID, "").start();		
		return true;
	}

	public boolean getAllAccounts(String dateFilter){

		if(false == isAuthed ||
				null == firstSectionID ||
						firstSectionID.isEmpty()){
			return false;
		}

		new GetAllAccountsTaskThread(firstSectionID, dateFilter).start();		
		return true;
	}

	private class GetAllAccountsTaskThread extends Thread{

		final String sectionID;
		final String dateFilter;

		GetAllAccountsTaskThread(String sectionID, String dateFilter){
			this.sectionID = sectionID;
			this.dateFilter = dateFilter;
		}

		@Override
		public void run() {

			// TODO : accountList update!!!
			if(null != accountList){
				sm(CommandID.CMD_GET_ACCOUNT_ALL, 0, 0, accountList);
				return;
			}

			String path = "?section_id=" + sectionID;

			if(false == dateFilter.isEmpty()){				
				try{		
					SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
					sdf.setLenient(false);
					sdf.parse(dateFilter);

					path += "&start_date=" + dateFilter;
				}
				catch(Exception e){
					//ignore
				}
			}
			Collection<Account> list = new ArrayList<Account>();

			try{
			JSONObject json = rai.invokeGET(Path.ACCOUNT_ALL + path);
			if(null == json){
				sm(CommandID.CMD_GET_ACCOUNT_ALL, 0, 0, list);
				return;
			}

			JSONObject results = (JSONObject) json.get("results");
			for(Object type : results.keySet()){

				JSONObject accountType  = (JSONObject) results.get(type);
				for(Object name : accountType.keySet()){
					JSONObject account = (JSONObject) accountType.get(name);

						list.add(new Account(type.toString(), account));
					}
				}
			} catch(Exception e){
				e.printStackTrace();
				sm(CommandID.CMD_GET_ACCOUNT_ALL, 0, 0, list);
			}

			// TODO : insert list to DB!!!
			accountList = list;	
			accountSectionID = sectionID;

			sm(CommandID.CMD_GET_ACCOUNT_ALL, 1, 0, list);
		}			

	}


	public boolean getAllEntries(String latestDate, String oldestDate){

		if(false == isAuthed){
			return false;
		}

		return em.getAllEntries(firstSectionID, latestDate, oldestDate);
	}

	public boolean getAllEntries(String latestDate, String oldestDate, int count){

		if(false == isAuthed){
			return false;
		}

		return em.getAllEntries(firstSectionID, latestDate, oldestDate, count);
	}


	public boolean getLatestEntries(int count, boolean noDuplicate){

		if(false == isAuthed){
			return false;
		}

		return em.getLatestEntries(firstSectionID, count, noDuplicate);
	}

	public boolean makeEntry(Long date, Account left, Account right, 
			String title, Double amount, String memo){
		if(false == isAuthed){
			return false;
		}

		return em.makeEntry(firstSectionID, date, left, right, title, amount, memo);
	}
	
	public boolean getFrequentItems(){
		if(false == isAuthed){
			return false;
		}
		
		return im.getFrequentItems(firstSectionID);
	}
	
	public boolean getLatestItems(){
		if(false == isAuthed){
			return false;
		}
		
		return im.getLatestItems(firstSectionID);
	}
	/*
	private class InvokeRESTAPITaskThread extends Thread {

		private final String path;
		private final String params;

		public InvokeRESTAPITaskThread(String path, String params){

			if(path.startsWith("/")){
				this.path = path.substring(1);
			}else{	
				this.path = path;
			}

			this.params = params;
		}

		public void run() {

		}
	}
	 */
}
