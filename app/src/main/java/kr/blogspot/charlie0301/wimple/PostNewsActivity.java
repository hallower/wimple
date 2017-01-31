package kr.blogspot.charlie0301.wimple;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import kr.blogspot.charlie0301.wimple.impl.IWimpleResponseListener;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.impl.util.RemoteContent;
import kr.blogspot.charlie0301.wimple.model.Account;
import kr.blogspot.charlie0301.wimple.model.AccountState;
import kr.blogspot.charlie0301.wimple.model.Budget;
import kr.blogspot.charlie0301.wimple.model.Entry;
import kr.blogspot.charlie0301.wimple.model.Item;
import kr.blogspot.charlie0301.wimple.model.Section;
import kr.blogspot.charlie0301.wimple.model.UserInfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

public class PostNewsActivity extends Activity {

	private static final String LOG_TAG = "PostNewsActivity";

	private static final WimpleImpl wimple = WimpleImpl.getInstance();
	public static Context context;
	private static ProgressDialog dialog;
	
	private TextView tvSubject;
	private TextView tvContent;
	private TextView tvURL;

	private String charset;

	private class DownloadWebPageTask extends AsyncTask<String, Void, String> {
		@Override
		protected String doInBackground(String... urls) {
			String response = "";

			for (String url : urls) {			
				
				Log.d(LOG_TAG, "submitted url is " + url);
				
				String lcURL = url.toLowerCase(Locale.US);
				String targetURL = url;
				if(!lcURL.startsWith("http")){
					targetURL = url.substring(url.indexOf("http"));
				}
				response = RemoteContent.getInstance().getTitlePartOfPage(targetURL);
			}
			return response;
		}

		@Override
		protected void onPostExecute(String result) {
			//Log.d(LOG_TAG, "charset = " + charset + ", " + result);

			int startPos = result.indexOf("<title");
			if(startPos < 0){
				startPos = result.indexOf("<TITLE");
			}
			startPos = result.indexOf( ">", startPos + 1);

			int endPos = result.indexOf("</title>");
			if(endPos < 0){
				endPos = result.indexOf("</TITLE>");
			}

			if(startPos < 0 ||
					endPos < 0 ||
					endPos > result.length()){
				Log.d(LOG_TAG, "Invalid web page!!!, Cant get title");
				return;
			}

			final String exportedTitle = result.substring(startPos + 1, endPos);
			showTitleSelectionWindow(Html.fromHtml(exportedTitle).toString());	
		}
	}

	void showTitleSelectionWindow(final String exportedTitle){
		AlertDialog.Builder alt_bld = new AlertDialog.Builder(this);
		alt_bld.setMessage(getResources().getString(R.string.post_news_set_title) + 
				"\n\n\"" + exportedTitle + "\"").setCancelable(
				false).setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						tvSubject.setText(exportedTitle);
					}
				}).setNegativeButton("No",
						new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {							
						dialog.cancel();
					}
				});

		AlertDialog alert = alt_bld.create();
		alert.setTitle(getResources().getString(R.string.post_news_title_imported));
		//alert.setIcon(R.drawable.icon);
		alert.show();			
	}

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
			Toast.makeText(context, getResources().getString(R.string.post_invalid_news_share_method), Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		setupWimpleImpl();

		// Wimple login check
		if(!wimple.isAuthed()){
			Toast.makeText(context, getResources().getString(R.string.program_exit), Toast.LENGTH_SHORT).show();
			finish();
			return;
		}		

		// Widget
		tvSubject = (TextView)findViewById(R.id.post_news_subject);
		tvContent = (TextView)findViewById(R.id.post_news_content);		
		tvURL = (TextView)findViewById(R.id.post_news_url);
		tvURL.setText(url);

		DownloadWebPageTask task = new DownloadWebPageTask();
		task.execute(new String[] { url });

		ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		if(clipboard.hasPrimaryClip()){
			//if(clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)){
			ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
			tvSubject.setText(item.getText());	
			//}
		}

		((TextView)findViewById(R.id.post_news_do_post)).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				String escapedSubject = "";
				String escapedURL = "";
				String escapedComment = "";

				try {
					escapedSubject = URLEncoder.encode(tvSubject.getText().toString(), "UTF-8");
					escapedURL = URLEncoder.encode(tvURL.getText().toString(), "UTF-8");
					escapedComment = URLEncoder.encode(tvContent.getText().toString(), "UTF-8");
				} catch (UnsupportedEncodingException e) {					
					Toast.makeText(context, getResources().getString(R.string.post_invalid_news_url), Toast.LENGTH_SHORT).show();
					finish();
					return;
				}

				String newsContents = escapedURL;
				newsContents += " %0A%0A";
				newsContents += escapedComment;
				newsContents += " %0A%0A";
				newsContents += " posted by Wimple (https://whooing.com/zS2h)";

				dialog = ProgressDialog.show(PostNewsActivity.this,"",
						context.getResources().getText(R.string.post_news_wait_for_while),true);
				
				wimple.postNews(escapedSubject, newsContents);
			}
		});	

	}

	private void setupWimpleImpl() {
		wimple.setApplicationContext(context);

		wimple.setResponseListener(new IWimpleResponseListener(){

			@Override
			public void onGetAuthTempToken(boolean status, String tempToken) {}

			@Override
			public void onGetAuthAccessToken(boolean status,
					Map<String, String> result) {	}

			@Override
			public void onGetUserInfoResponseReceived(boolean status, UserInfo info) {	}

			@Override
			public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list) {	}

			@Override
			public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list) {	}

			@Override
			public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list) {	}

			@Override
			public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list) {	}

			@Override
			public void onMakeEntryResponseReceived(boolean status, String entryDate) {	}

			@Override
			public void onGetFrequentItemsResponseReceived(boolean status,	Collection<Item> list) {	}

			@Override
			public void onGetLatestItemsResponseReceived(boolean status,	Collection<Item> list) {	}

			@Override
			public void onModifyEntryResponseReceived(boolean status, Entry entry) {	}

			@Override
			public void onGetMonthlyItemsResponseReceived(boolean status,	ArrayList<Item> list) {	}

			@Override
			public void onRemoveEntryResponseReceived(boolean status, String id) {	}

			@Override
			public void onRemoveMonthlyItemResponseReceived(boolean status, String id) {	}

			@Override
			public void onGetFinancialStateResponseReceived(boolean status,	Collection<AccountState> list) {	}

			@Override
			public void onGetIncomeAndExpenseResponseReceived(boolean status, Collection<AccountState> list) {		}

			@Override
			public void onGetBudgetResponseReceived(boolean status, boolean isIncome,	Map<String, Budget> list) {		}

			@Override
			public void onPostNewsResponseReceived(boolean status, String id) {
				dialog.dismiss();
				dialog = null;
				
				if(status){
					Toast.makeText(context, getResources().getString(R.string.post_news_succeed), Toast.LENGTH_SHORT).show();
				}else{
					Toast.makeText(context, getResources().getString(R.string.post_news_failed), Toast.LENGTH_SHORT).show();					
				}
				finish();
			}

			@Override
			public void onPostPaymentsResponseReceived(boolean status) { }

		});
	}

}
