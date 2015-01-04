package kr.blogspot.charlie0301;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.blogspot.charlie0301.impl.IWimpleResponseListener;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.model.Account;
import kr.blogspot.charlie0301.model.AccountState;
import kr.blogspot.charlie0301.model.Budget;
import kr.blogspot.charlie0301.model.Entry;
import kr.blogspot.charlie0301.model.Item;
import kr.blogspot.charlie0301.model.Section;
import kr.blogspot.charlie0301.model.UserInfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;


public class SMSReceiver extends BroadcastReceiver {

	private static final String LOG_TAG = "SMSReceiver";
	
	private static final WimpleImpl wimple = WimpleImpl.getInstance();
	
	private Context context;
	
	// TODO : change this as DBMS
	private static final String[] targetNumbers = {"18001111", "15882323", "15889955", "01044805316" };

	@Override
	public void onReceive(Context context, Intent intent) {

		if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {

			final Bundle bundle = intent.getExtras();
			if (bundle == null) {
				return;
			}
			
			this.context = context;			
			setupWimpleImpl();
			
			if(false == wimple.isAuthed()){
				Log.e(LOG_TAG, "Not loggined");
				return;
			}
			
			wimple.getDefaultSections(false);

			String totalPayments = "";
			String senderNumber = "";

			try {

				final Object[] pdusObj = (Object[]) bundle.get("pdus");

				for (Object pduObj : pdusObj) {

					SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) pduObj);
					String sender = currentMessage.getDisplayOriginatingAddress();

					for(String number : targetNumbers){

						if(0 == number.compareTo(sender)){
							Date date = new Date(currentMessage.getTimestampMillis());
							String message = currentMessage.getDisplayMessageBody();
							senderNumber = sender;

							//Log.d(LOG_TAG, "sender = "+ sender + ", message = " + message + ", date = " + date );

							Pattern pattern = Pattern.compile("\\d\\d/\\d\\d");
							Matcher matcher = pattern.matcher(message);
							if(matcher.find()){
								totalPayments = message + "\n" + totalPayments;
							}else{
								totalPayments = message + "\n" + DateFormatUtils.getSMSDateFormat().format(date) 
										+ "\n" + totalPayments;
							}
							break;
						}
					}
				}

				if(false == totalPayments.isEmpty()){
					Log.d(LOG_TAG, "posting body = " + totalPayments);
					
					if(true == wimple.isAuthed() &&
							true == wimple.isInitializedFinished()){
						wimple.postPayments(totalPayments, senderNumber);
					}
				}

			} catch (Exception e) {
				Log.e(LOG_TAG, "Someingthing wrong during receiving SMS" + e);
			}
		}
	}

	private void setupWimpleImpl() {
		wimple.setApplicationContext(context);

		wimple.setResponseListener(new IWimpleResponseListener(){

			@Override
			public void onGetAuthTempToken(boolean status, String tempToken) { }

			@Override
			public void onGetAuthAccessToken(boolean status, Map<String, String> result) { }

			@Override
			public void onGetUserInfoResponseReceived(boolean status, UserInfo info) { }

			@Override
			public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list) {	}

			@Override
			public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list) {	}

			@Override
			public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list) { }

			@Override
			public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list) { }

			@Override
			public void onMakeEntryResponseReceived(boolean status, String entryDate) {	}

			@Override
			public void onGetFrequentItemsResponseReceived(boolean status, Collection<Item> list) {	}

			@Override
			public void onGetLatestItemsResponseReceived(boolean status, Collection<Item> list) { }

			@Override
			public void onModifyEntryResponseReceived(boolean status, Entry entry) { }

			@Override
			public void onGetMonthlyItemsResponseReceived(boolean status, ArrayList<Item> list) { }

			@Override
			public void onRemoveEntryResponseReceived(boolean status, String id) { }

			@Override
			public void onRemoveMonthlyItemResponseReceived(boolean status, String id) { }

			@Override
			public void onGetFinancialStateResponseReceived(boolean status,	Collection<AccountState> list) { }

			@Override
			public void onGetIncomeAndExpenseResponseReceived(boolean status, Collection<AccountState> list) { }

			@Override
			public void onGetBudgetResponseReceived(boolean status, boolean isIncome, Map<String, Budget> list) { }

			@Override
			public void onPostNewsResponseReceived(boolean status, String id) { }

			@Override
			public void onPostPaymentsResponseReceived(boolean status) { 
				Log.d(LOG_TAG, "payment reported!!!, result = " + status);
			}

		});
	}
}
