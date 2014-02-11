package kr.blogspot.charlie0301;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class PaymentNoticeActivity extends Activity {

	private static final String paymentURL = "https://whooing.com/#account/payment";
	public static boolean noMore = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_payment_notice);

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
				finish();
			}
		});
	}

}
