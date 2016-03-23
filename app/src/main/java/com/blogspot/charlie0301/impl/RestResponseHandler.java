package com.blogspot.charlie0301.impl;

import com.blogspot.charlie0301.PaymentNoticeActivity;
import com.blogspot.charlie0301.SplashScreenActivity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class RestResponseHandler {

	public static final String LOG_TAG = "RestResponseHandler";
	public Context context;

	public RestResponseHandler() {
		super();
	}

	public void setApplicationContext(Context context){
		this.context = context;
	}

	/*
	 * 코드	내용
	 * 200	모든 데이터가 정상적으로 반환됨(레코드가 없을 수 있음)
	 * 204	레코드가 없음.
	 * 400	요청한 파라미터 중 누락되었거나 잘못되었을 수 있음. 개선하여야할 파라미터는 error_parameters에 배열로 반환됨.
	 * 401	권한을 벗어난 요청
	 * 402	하루 요청횟수를 초과하였을 때. 업그레이드 안내 페이지로 이동시켜야함.
	 * 405	인증토큰이 만료되거나 잘못 되었을 때.	 =>  언제든지 사용자가 후잉에서 인증토큰의 권한을 취소할 수 있으므로 항상 이 값을 확인하여 재인증을 요구하는 구조여야 함.
	 * 500	서버측에서 발생한 에러. 잠시 후 다시 시도하면 됨.
	 */
	public void handleRestResponse(int code){

		if(null == context){
			Log.e(LOG_TAG, "RestResponseHandler is not initialized!!!");
			return;
		}

		switch(code){
		case 402 :
		{
			Log.e(LOG_TAG, "All free report are used!!!(402), Open the payment dialog!!!");

			// TODO : remove backcall
			if(false == PaymentNoticeActivity.noMore){
				Intent intent = new Intent(context, PaymentNoticeActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
				context.startActivity(intent);
			}else{
				Log.e(LOG_TAG, "User wanted to close PaymentNoticeActivity anymore today");
				// TODO : show toast!!!
				//Toast.makeText(context, context.getResources().getString(R.string.notice_need_upgrade), Toast.LENGTH_LONG).show();
			}
		}
		break;
		case 405 :
		{
			Log.e(LOG_TAG, "Server requests reauthentication(405), restarting this app!!!");
			WimpleImpl.getInstance().cleanAuth();

			Intent intent = new Intent(context, SplashScreenActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			intent.putExtra("auth_again", "");
			context.startActivity(intent);
		}
		break;
		default :
			// do nothing
		}		
	}
}
