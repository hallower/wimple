package kr.blogspot.charlie0301;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.json.simple.JSONObject;

import kr.blogspot.charlie0301.impl.IWimpleResponseListener;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.model.Account;
import kr.blogspot.charlie0301.model.AccountState;
import kr.blogspot.charlie0301.model.Budget;
import kr.blogspot.charlie0301.model.Entry;
import kr.blogspot.charlie0301.model.Item;
import kr.blogspot.charlie0301.model.Section;
import kr.blogspot.charlie0301.model.UserInfo;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

public class PostNewsActivity extends Activity {

	private static final String LOG_TAG = "PostNewsActivity";

	private static final WimpleImpl wimple = WimpleImpl.getInstance();
	public static Context context;

	private TextView tvSubject;
	private TextView tvContent;
	private TextView tvURL;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_post_news);


		context = getApplicationContext();

		// intent check

		String url = "";

		// double check if app is restarted forcedly
		Intent intent= getIntent();
		String action = intent.getAction();
		String type = intent.getType();	

		if (Intent.ACTION_SEND.equals(action) && type != null) {
			if (type.startsWith("text/") ||
					type.startsWith("plain/")) {
				url = intent.getStringExtra(Intent.EXTRA_TEXT);	
			}
		}

		if(null == url ||
				url.isEmpty()){
			Toast.makeText(context, getResources().getString(R.string.invalid_news_share_method), Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		setupWimpleImpl();
		
		// Wimple login check
		if(false == wimple.isAuthed()){
			Toast.makeText(context, getResources().getString(R.string.program_exit), Toast.LENGTH_SHORT).show();
			finish();
			return;
		}		

		// Widget
		tvSubject = (TextView)findViewById(R.id.post_news_subject);
		tvContent = (TextView)findViewById(R.id.post_news_content);		
		tvURL = (TextView)findViewById(R.id.post_news_url);
		tvURL.setText(url);

		ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		if(true == clipboard.hasPrimaryClip()){
			if(clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)){
				ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
				tvSubject.setText(item.getText());	
			}
		}

		((TextView)findViewById(R.id.post_news_do_post)).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				String escapedURL = "";
				String escapedComment = "";
				
				try {
					escapedURL = URLEncoder.encode(tvURL.getText().toString(), "UTF-8");
					escapedComment = URLEncoder.encode(tvContent.getText().toString(), "UTF-8");
				} catch (UnsupportedEncodingException e) {					
					Toast.makeText(context, getResources().getString(R.string.invalid_news_url), Toast.LENGTH_SHORT).show();
					finish();
					return;
				}
				
				String newsContents = escapedURL;
				newsContents += " %0A%0A";
				newsContents += escapedComment;
				newsContents += " %0A%0A";
				newsContents += " posted by Wimple";
				
				wimple.postNews(tvSubject.getText().toString(), newsContents);
			}
		});

	}

	private void setupWimpleImpl() {
		wimple.setApplicationContext(context);

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
			}

			@Override
			public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list) {
			}

			@Override
			public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list) {
			}

			@Override
			public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list) {
			}

			@Override
			public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list) {
			}

			@Override
			public void onMakeEntryResponseReceived(boolean status, String entryDate) {
			}

			@Override
			public void onGetFrequentItemsResponseReceived(boolean status,
					Collection<Item> list) {
			}

			@Override
			public void onGetLatestItemsResponseReceived(boolean status,
					Collection<Item> list) {
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
			}

			@Override
			public void onGetIncomeAndExpenseResponseReceived(boolean status,
					Collection<AccountState> list) {
			}

			@Override
			public void onGetBudgetResponseReceived(boolean status, boolean isIncome,
					Map<String, Budget> list) {
			}

			@Override
			public void onPostNewsResponseReceived(boolean status, String id) {
				if(status){
					Toast.makeText(context, getResources().getString(R.string.post_news_succeed), Toast.LENGTH_SHORT).show();
				}else{
					Toast.makeText(context, getResources().getString(R.string.post_news_failed), Toast.LENGTH_SHORT).show();					
				}
				finish();
			}				

		});
	}

}
