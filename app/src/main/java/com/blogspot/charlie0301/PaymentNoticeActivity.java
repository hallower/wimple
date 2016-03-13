package com.blogspot.charlie0301;

import java.util.Calendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

public class PaymentNoticeActivity extends Activity {

	private static final String paymentURL = "https://whooing.com/#account/payment";
	public static boolean noMore = false;

	private static final String SETTING_KEY = "wimple.settings";
	private static final String NOMORE_TIME = "nomoretime";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_payment_notice);

		SharedPreferences settings = getApplicationContext().getSharedPreferences(SETTING_KEY, Context.MODE_PRIVATE);
		Long value = settings.getLong(NOMORE_TIME, 0);
		if(value != 0){
			value -= Calendar.getInstance().getTimeInMillis();
			if(value < 1000 * 60 * 60 * 12){
				Log.d("PaymentNoticeActivity", "within 12 hours, user clicked no more payment notice activity.");
				PaymentNoticeActivity.noMore = true;
				finish();
				return;
			}	
		}
		
		findViewById(R.id.fullscreen_go_to_payment).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(paymentURL));
				startActivity(i);
				finish();
			}
		});
		
		findViewById(R.id.fullscreen_close).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				PaymentNoticeActivity.noMore = true;
				SharedPreferences settings = getApplicationContext().getSharedPreferences(SETTING_KEY, Context.MODE_PRIVATE);
				settings.edit().putLong(NOMORE_TIME, Long.valueOf(Calendar.getInstance().getTimeInMillis())).commit(); 
				
				finish();
			}
		});
	}

}
