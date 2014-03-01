package kr.blogspot.charlie0301;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import kr.blogspot.charlie0301.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.impl.IWimpleResponseListener;
import kr.blogspot.charlie0301.impl.IWimpleStatusListener;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.model.Account;
import kr.blogspot.charlie0301.model.AccountState;
import kr.blogspot.charlie0301.model.Entry;
import kr.blogspot.charlie0301.model.Item;
import kr.blogspot.charlie0301.model.Section;
import kr.blogspot.charlie0301.model.UserInfo;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SplashScreenActivity extends Activity {

	private static final String LOG_TAG = "SplashScreenActivity";

	private static final WimpleImpl wimple = WimpleImpl.getInstance();
	private static Handler mainHandler;
	public static Context context;

	// GUI
	private TextView txtStatus;

	// WebView for Loggin
	private static final String target_url="https://whooing.com/app_auth/authorize";
	private static final String target_url_prefix="whooing.com";

	private WebView mWebview;
	private WebView mWebviewPop;
	private FrameLayout mContainer;

	// Data
	private static final int PIN_NUMBER_REQUEST = 1379;
	private String storedTempToken = "";

	public static void sm(int cmd, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, 1, 0, msg));    
	}    

	public static void sm(int cmd, int a1, int a2, Object msg){
		mainHandler.sendMessage(Message.obtain(mainHandler, cmd, a1, a2, msg));    
	}

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash_screen);
		context = getApplicationContext();

		// double check if app is restarted forcedly
		Intent intent= getIntent();
	    if(intent.hasExtra("auth_again")){
	    	Log.e(LOG_TAG, "Need to do Auth again, clean auth!!!");
	    	Toast.makeText(context, context.getResources().getString(R.string.notice_need_auth), Toast.LENGTH_LONG).show();
	    	wimple.cleanAuth();
	    }
		
		txtStatus = (TextView)findViewById(R.id.splash_status);
		setupHandler();
		setupWimpleImpl();

		sm(CommandID.SHOW_STATUS, context.getResources().getString(R.string.loggin_auth));

		if(true == wimple.getTempToken()){
			// Already Logged-in
			wimple.getDefaultSections(true);
			refreshCache();
		}

		// final View controlsView =
		// findViewById(R.id.fullscreen_content_controls);
		CookieManager cookieManager = CookieManager.getInstance(); 
		cookieManager.setAcceptCookie(true); 
		mWebview = (WebView) findViewById(R.id.webview);
		//mWebviewPop = (WebView) findViewById(R.id.webviewPop);
		mContainer = (FrameLayout) findViewById(R.id.webview_frame);
		WebSettings webSettings = mWebview.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setAppCacheEnabled(true);
		webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
		webSettings.setSupportMultipleWindows(true);
		mWebview.setWebViewClient(new UriWebViewClient());
		mWebview.setWebChromeClient(new UriChromeClient());		

	}

	private void refreshCache(){
		wimple.getAllAccounts(true);		
		wimple.getLatestItems(true);
		wimple.getMonthlyItems(true);
		// Because of Free API count reduction
		wimple.getFinancialState(DateFormatUtils.getServerDateString(""), true);
	}

	private void setupWimpleImpl() {
		wimple.setApplicationContext(context);
		wimple.setStatusListener(new IWimpleStatusListener(){

			@Override
			public void onLoggedIn(boolean status) {
				if(status){
					sm(CommandID.SHOW_STATUS, context.getResources().getString(R.string.loggin_success));
					sm(CommandID.WIMPLE_LOGGIN_SUCCESS, "");
					refreshCache();
				}else{
					sm(CommandID.SHOW_STATUS, context.getResources().getString(R.string.loggin_failed));
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
			}

		});

		wimple.setResponseListener(new IWimpleResponseListener(){

			@Override
			public void onGetAuthTempToken(boolean status, String tempToken) {

				if(false == status){
					exitApplication(context.getResources().getString(R.string.fatal_error)); 
					return;
				}

				if(null == tempToken || tempToken.isEmpty()){
					exitApplication(context.getResources().getString(R.string.fatal_error));
					return;
				}

				storedTempToken = tempToken;				
				sm(CommandID.GET_PIN, tempToken);				
			}

			@Override
			public void onGetAuthAccessToken(boolean status,
					Map<String, String> result) {

				if(result.isEmpty()){
					Log.e(LOG_TAG, "Auth is failed.");
					exitApplication(context.getResources().getString(R.string.fatal_error));
					return;
				}

				//String token = result.get("token");
				String tokenSecret = result.get("token_secret");
				//String userID = result.get("user_id");

				if(null == tokenSecret ||
						true == tokenSecret.isEmpty()){
					Log.e(LOG_TAG, "Auth is failed.");
					exitApplication(context.getResources().getString(R.string.fatal_error));
					return;
				}

				//sm(CommandID.SHOW_STATUS, context.getResources().getString(R.string.loggin_user_info));
				wimple.getUserInfo(true);
				wimple.getDefaultSections(false);				
			}

			@Override
			public void onGetUserInfoResponseReceived(boolean status, UserInfo info) { 
				if(status){
					Log.e(LOG_TAG, info.toString());	
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
			public void onMakeEntryResponseReceived(boolean status, String entryDate) {
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

			@Override
			public void onModifyEntryResponseReceived(boolean status, Entry entry) {
			}

			@Override
			public void onGetMonthlyItemsResponseReceived(boolean status,
					ArrayList<Item> list) {
			}

			@Override
			public void onRemoveEntryResponseReceived(boolean status, String id) {				
			}

			@Override
			public void onRemoveMonthlyItemResponseReceived(boolean status, String id) {
			}

			@Override
			public void onGetFinancialStateResponseReceived(boolean status,
					Collection<AccountState> list) {
				// TODO Auto-generated method stub
				
			}				

		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if(requestCode == PIN_NUMBER_REQUEST){
			if(resultCode == RESULT_OK){
				String tempToken = data.getExtras().getString("temp_token");
				String pin = data.getExtras().getString("pin");

				if(null != tempToken &&
						null != pin){
					wimple.getAccessToken(tempToken, pin);
					return;
				}
			}

			// Exit			
			exitApplication(context.getResources().getString(R.string.program_exit));
		}else{
			super.onActivityResult(requestCode, resultCode, data);	
		}		
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

				case CommandID.FATAL_ERROR : 
					Toast.makeText(context, context.getResources().getString(R.string.fatal_error), Toast.LENGTH_LONG).show();
					break;

				case CommandID.SHOW_STATUS:
					txtStatus.setText((String)obj);
					break;

				case CommandID.GET_PIN :
				{
					/*
					Intent intent = new Intent(context, WebViewActivity.class);
					intent.putExtra("temp_token", obj.toString());
					startActivityForResult(intent, PIN_NUMBER_REQUEST);
					 */
					mWebview.loadUrl(target_url + "?token=" + storedTempToken);
					mWebview.setVisibility(View.VISIBLE);
					break;	
				}

				case CommandID.GET_ALL_ACCOUNT_RECEIVED :
					Log.d(LOG_TAG, "All Account Information received!");
					break;

				case CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED :
					Log.d(LOG_TAG, "Latest Items received");
					break;

				case CommandID.GET_ALL_SECTION_RECEIVED :
					finishedAuthentication();
					break;

				default : {	
					Log.d(LOG_TAG, "Invalid Command ID=" + command);
					break;
				}

				}
				super.handleMessage(msg);
			}


		};
	}

	private void finishedAuthentication() {
		sm(CommandID.SHOW_STATUS, context.getResources().getString(R.string.loggin_end));
		Intent intent = new Intent(context, WimpleActivity.class);
		startActivity(intent);
		finish();
	}

	private void exitApplication(String toastMessage) {
		sm(CommandID.TOAST_LONG, toastMessage);
		finish();
	}

	/*
	 * for WebView
	 */

	private class UriWebViewClient extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			String host = Uri.parse(url).getHost();
			//Log.d(LOG_TAG, "URL=" + url);
			if (host.startsWith(target_url_prefix)) 
			{
				if(url.contains("pin=")){

					int startPos = url.indexOf("pin=") + 4;
					int endPos = url.indexOf("&", startPos);
					String pin;

					if(-1 == endPos){
						pin = url.substring(startPos, url.length());	
					}else{
						pin = url.substring(startPos, endPos);
					}		

					mWebview.setVisibility(View.INVISIBLE);

					if(null != pin){
						wimple.getAccessToken(storedTempToken, pin);
					}else{
						// Exit					
						exitApplication(context.getResources().getString(R.string.program_exit));
					}
					return true;
				}

				if(url.contains("logout")){
					// Exit					
					exitApplication(context.getResources().getString(R.string.program_exit));
				}

				// This is my web site, so do not override; let my WebView load
				// the page
				if(mWebviewPop!=null)
				{
					mWebviewPop.setVisibility(View.GONE);
					mContainer.removeView(mWebviewPop);
					mWebviewPop=null;
				}
				return false;
			}

			// Otherwise, the link is not for a page on my site, so launch
			// another Activity that handles URLs
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			startActivity(intent);
			return true;
		}

		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler,
				SslError error) {
			Log.d(LOG_TAG, "onReceivedSslError");
			//super.onReceivedSslError(view, handler, error);
		}
	}

	class UriChromeClient extends WebChromeClient {

		@SuppressLint("SetJavaScriptEnabled")
		@SuppressWarnings("deprecation")
		@Override
		public boolean onCreateWindow(WebView view, boolean isDialog,
				boolean isUserGesture, Message resultMsg) {
			mWebviewPop = new WebView(context);
			mWebviewPop.setVerticalScrollBarEnabled(false);
			mWebviewPop.setHorizontalScrollBarEnabled(false);
			mWebviewPop.setWebViewClient(new UriWebViewClient());
			mWebviewPop.getSettings().setJavaScriptEnabled(true);
			mWebviewPop.getSettings().setSavePassword(false);
			mWebviewPop.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT));
			mContainer.addView(mWebviewPop);
			WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
			transport.setWebView(mWebviewPop);
			resultMsg.sendToTarget();

			return true;
		}

		@Override
		public void onCloseWindow(WebView window) {
			//Log.d(LOG_TAG, "called");
		}

	}
}
