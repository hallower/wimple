package kr.blogspot.charlie0301;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
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

public class WebViewActivity extends Activity {

	private static final String LOG_TAG = "WebViewActivity";
	
	/* URL saved to be loaded after fb login */
	private static final String target_url="https://whooing.com/app_auth/authorize";
	private static final String target_url_prefix="whooing.com";
	private Context mContext;
	private WebView mWebview;
	private WebView mWebviewPop;
	private FrameLayout mContainer;
	
	private String tempToken = "";

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

	    setContentView(R.layout.activity_web_view);
	    
	    {   
	    	Intent intent = getIntent();
	    	
	    	if(null == intent){
	    		throw new NullPointerException("Intent doesn't have argument!!!");	    	
	    	}

	    	tempToken = intent.getStringExtra("temp_token");	    	
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
	    mWebview.loadUrl(target_url + "?token=" + tempToken);

	    mContext=this.getApplicationContext();

	}


	private class UriWebViewClient extends WebViewClient {
	    @Override
	    public boolean shouldOverrideUrlLoading(WebView view, String url) {
	        String host = Uri.parse(url).getHost();
	        Log.d(LOG_TAG, "URL=" + url);
	        if (host.equals(target_url_prefix)) 
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
	        				
	        		Intent intent = new Intent();
					intent.putExtra("temp_token", tempToken);
					intent.putExtra("pin", pin);
					setResult(RESULT_OK, intent);
					finish();
					
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
	        mWebviewPop = new WebView(mContext);
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
	        Log.d(LOG_TAG, "called");
	    }

	}
}
