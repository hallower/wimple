package me.blog.imhallower.wimple.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import me.blog.imhallower.wimple.model.Account;
import me.blog.imhallower.wimple.model.Entry;
import me.blog.imhallower.wimple.model.Section;
import me.blog.imhallower.wimple.model.UserInfo;

import org.json.simple.JSONArray;
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

	private final RestAPIInvoker rai;

	private UserInfo userInfo = null;

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
		public void onGetAllSectionReceived(boolean status, Collection<Section> list) {}

		@Override
		public void onGetAuthTempToken(boolean status, String tempToken) {}

		@Override
		public void onGetAuthAccessToken(boolean status, Map<String, String> result) {}

		@Override
		public void onGetUserInfoReceived(boolean status, UserInfo info) { }

		@Override
		public void onGetAllAccountReceived(boolean status, Collection<Account> list) {}

		@Override
		public void onGetEntriesReceived(boolean status, Collection<Entry> list) {}

		@Override
		public void onGetLatestEntriesReceived(boolean status, Collection<Entry> list) { }
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

		/*
		if(null == sdbh){
			sdbh = new SessionDBHandler(PromiseImpl.context);    
		}*/
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
	private static final String vo42iw5me4vxz = "923e20fb19eba88a47878bb51016b590ea33b24c";

	private String token;
	private String tokenSecret;
	private String userID;

	private final Semaphore authInProgress = new Semaphore(0);
	private static Integer sequence = 10000;
	private boolean isAuthed = false;

	private static final class Path {

		private static final String AUTH_REQUEST_TOKEN 	= "app_auth/request_token";
		private static final String AUTH_AUTHORIZE 		= "app_auth/authorize";
		private static final String AUTH_ACCESS_TOKEN 	= "app_auth/access_token";

		private static final String USER_INFO				= "api/user.json";

		private static final String SECTIONS_ALL			= "api/sections.json";
		private static final String SECTIONS_DEFAULT		= "api/sections/default.json";

		private static final String ACCOUNT_ALL			= "api/accounts.json";

		private static final String ENTRIES_ALL			= "api/entries.json_array";
		private static final String ENTRIES_LATEST		= "api/entries/latest.json_array";

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
	 * Handler
	 */

	private static final class CommandID {

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
				responseListener.onGetAuthTempToken(booleanStatus, obj.toString());
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
				responseListener.onGetUserInfoReceived(booleanStatus, (UserInfo) obj);
				break;

			case CommandID.CMD_GET_SECTIONS :
				responseListener.onGetAllSectionReceived(booleanStatus, (Collection<Section>) obj);
				break;

			case CommandID.CMD_GET_ACCOUNT_ALL :
				responseListener.onGetAllAccountReceived(booleanStatus, (Collection<Account>) obj);
				break;

			case CommandID.CMD_GET_ENTRIES :
				responseListener.onGetEntriesReceived(booleanStatus, (Collection<Entry>) obj);
				break;

			case CommandID.CMD_GET_LATEST_ENTRIES :
				responseListener.onGetLatestEntriesReceived(booleanStatus, (Collection<Entry>) obj);
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

	public boolean getAllAccounts(String sectionID){

		if(false == isAuthed){
			return false;
		}

		new GetAllAccountsTaskThread(sectionID, "").start();		
		return true;
	}

	public boolean getAllAccounts(String sectionID, String dateFilter){

		if(false == isAuthed){
			return false;
		}

		new GetAllAccountsTaskThread(sectionID, dateFilter).start();		
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

					list.add(new Account(name.toString(), account));
				}
			}

			sm(CommandID.CMD_GET_ACCOUNT_ALL, 1, 0, list);
		}			

	}

	public boolean getAllEntries(String sectionID, String latestDate, String oldestDate){

		if(false == isAuthed){
			return false;
		}

		new GetAllEntriesTaskThread(sectionID, latestDate, oldestDate, 0).start();		
		return true;
	}

	public boolean getAllEntries(String sectionID, String latestDate, String oldestDate, int count){

		if(false == isAuthed){
			return false;
		}

		new GetAllEntriesTaskThread(sectionID, latestDate, oldestDate, count).start();		
		return true;
	}

	private class GetAllEntriesTaskThread extends Thread{

		final String sectionID;
		final String latestDate;
		final String oldestDate;
		final int count;

		GetAllEntriesTaskThread(String sectionID, String latestDate, String oldestDate, int count){
			this.sectionID = sectionID;
			this.latestDate = latestDate;
			this.oldestDate = oldestDate;
			this.count = count;
		}

		@Override
		public void run() {

			Collection<Entry> list = new ArrayList<Entry>();
			String path = "?section_id=" + sectionID + "&start_date=" + oldestDate + "&end_date=" + latestDate;

			if(0 > count){
				path += "&limit=" + count;
			}

			JSONObject json = rai.invokeGET(Path.ENTRIES_ALL + path);
			if(null == json){
				sm(CommandID.CMD_GET_ENTRIES, 0, 0, list);
				return;
			}

			JSONObject results = (JSONObject) json.get("results");
			for(Object type : results.keySet()){

				if(0 != type.toString().compareTo("rows")){
					continue;
				}

				JSONArray rows  = (JSONArray) results.get(type);
				for(int i = 0; i < rows.size(); i++){
					JSONObject row = (JSONObject) rows.get(i);

					if(0 == row.get("l_account_id").toString().compareToIgnoreCase("x0") ||
							0 == row.get("r_account_id").toString().compareToIgnoreCase("x0") ){
						// TODO : handle this as removed item.
						continue;
					}

					list.add(new Entry(row));
				}
			}
			sm(CommandID.CMD_GET_ENTRIES, 1, 0, list);
		}			

	}

	public boolean getLatestEntries(String sectionID, int count){

		if(false == isAuthed){
			return false;
		}

		new GetLatestEntriesTaskThread(sectionID, count).start();		
		return true;
	}

	private class GetLatestEntriesTaskThread extends Thread{

		final String sectionID;
		final int count;

		GetLatestEntriesTaskThread(String sectionID, int count){
			this.sectionID = sectionID;
			this.count = count;
		}

		@Override
		public void run() {

			Collection<Entry> list = new ArrayList<Entry>();
			String path = "?section_id=" + sectionID;

			if(0 > count){
				path += "&limit=" + count;
			}

			JSONObject json = rai.invokeGET(Path.ENTRIES_LATEST + path);

			if(null == json){
				sm(CommandID.CMD_GET_LATEST_ENTRIES, 0, 0, list);
				return;
			}

			JSONArray results = (JSONArray) json.get("results");
			for(int i = 0; i < results.size(); i++){
				JSONObject row = (JSONObject) results.get(i);

				if(0 == row.get("l_account_id").toString().compareToIgnoreCase("x0") ||
						0 == row.get("r_account_id").toString().compareToIgnoreCase("x0") ){
					// TODO : handle this as removed item.
					continue;
				}

				Entry item = new Entry(row);
				String balance = row.get("total").toString();
				if(null != balance && 
						false == balance.isEmpty()){
					item.setBalance(balance);
				}

				list.add(new Entry(row));
			}
			sm(CommandID.CMD_GET_LATEST_ENTRIES, 1, 0, list);
		}			

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
