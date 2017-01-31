package kr.blogspot.charlie0301.wimple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.blogspot.charlie0301.wimple.impl.IWimpleResponseListener;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.model.Account;
import kr.blogspot.charlie0301.wimple.model.AccountState;
import kr.blogspot.charlie0301.wimple.model.Budget;
import kr.blogspot.charlie0301.wimple.model.Entry;
import kr.blogspot.charlie0301.wimple.model.Item;
import kr.blogspot.charlie0301.wimple.model.Section;
import kr.blogspot.charlie0301.wimple.model.UserInfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class SMSReceiver extends BroadcastReceiver {

	private static final String LOG_TAG = "SMSReceiver";

	public static final String smsKey = "wimple.sms";
	public static final String smsTelTag = "sms.tel";
	public static final String smsBodyTag = "sms.body";
	public static final String smsNumberTag = "sms.numberOf";

	private static final WimpleImpl wimple = WimpleImpl.getInstance();

	private Context context;

	@Override
	public void onReceive(Context context, Intent intent) {

		this.context = context;
		final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		final SharedPreferences sms = context.getSharedPreferences(smsKey, 0);
		
		Boolean isNeedToRead = sharedPref.getBoolean(SettingsFragment.KEY_SMS_POST_ENABLE, false);
		if(!isNeedToRead){
			return;
		}
		
		//Log.d(LOG_TAG, "SMSReceiver - onReceive");

		if ("android.provider.Telephony.SMS_RECEIVED"
				.equals(intent.getAction())) {

			final Bundle bundle = intent.getExtras();
			if (bundle == null) {
				return;
			}

			setupWimpleImpl();
			if (!wimple.isAuthed()) {
				Log.e(LOG_TAG, "Not loggined");
				return;
			}

			String storedTels = sms.getString(smsTelTag, "");

			List<String> targetNumbers = Arrays.asList(storedTels.split("\\s*,\\s*"));
			if(targetNumbers.isEmpty()){
				Log.d(LOG_TAG, "contact list is empty!!!");
				return;
			}
			
			String savedSectionID = context.getSharedPreferences(
					WimpleImpl.settingsKey, Context.MODE_PRIVATE).getString(
							"section_id", null);
			if (null == savedSectionID || savedSectionID.isEmpty()) {
				wimple.getDefaultSections(false);
			} else {
				wimple.getAllSections(false);
			}

			String totalPayments = "";
			String senderNumber = "";

			try {

				final Object[] pdusObj = (Object[]) bundle.get("pdus");

				for (Object pduObj : pdusObj) {

					SmsMessage currentMessage = SmsMessage
							.createFromPdu((byte[]) pduObj);
					String sender = currentMessage
							.getDisplayOriginatingAddress();

					for (String number : targetNumbers) {

						if (0 == number.replaceAll("\\s+","").compareTo(sender)) {
							Date date = new Date(
									currentMessage.getTimestampMillis());
							String message = currentMessage
									.getDisplayMessageBody();
							senderNumber = sender;

							// Log.d(LOG_TAG, "sender = "+ sender +
							// ", message = " + message + ", date = " + date );

							Pattern pattern = Pattern.compile("\\d\\d/\\d\\d");
							Matcher matcher = pattern.matcher(message);
							if (matcher.find()) {
								totalPayments = message + "\n" + totalPayments;
							} else {
								totalPayments = message
										+ "\n"
										+ DateFormatUtils.getSMSDateFormat()
										.format(date) + "\n"
										+ totalPayments;
							}
							break;
						}
					}
				}

				if (!totalPayments.isEmpty()) {

					// Restore preferences
					String storedSMS = sms.getString(smsBodyTag, "");

					storedSMS += totalPayments;

					int numberOfStoredSMSs = sms.getInt(smsNumberTag, 0);
					SharedPreferences.Editor editor = sms.edit();
					
					int max_limit = Integer.parseInt(sharedPref.getString(SettingsFragment.KEY_SMS_MAX_STORE_NUMBER, "5"));
					
					if(++numberOfStoredSMSs < max_limit)
					{
						editor.putString(smsBodyTag, storedSMS).putInt(smsNumberTag, numberOfStoredSMSs).commit();
						Log.d(LOG_TAG, "pending sms, cause of a number of current stored sms is " + numberOfStoredSMSs);
						return;
					}

					//Log.d(LOG_TAG, "posting body = " + storedSMS);

					if (wimple.isAuthed()
							&& wimple.isInitializedFinished()) {
						wimple.postPayments(storedSMS, senderNumber);
						// TODO : error check, actually postPayment is asynchrnous function.
						// so it can be check in the callback in the setupWimpleImpl().
						// In this broadcast receiver, Im not sure whether the callback can be called in the main thread or not.
						editor.putString(smsBodyTag, "").putInt(smsNumberTag, 0).commit();
					}

				}

			} catch (Exception e) {
				Log.e(LOG_TAG, "Someingthing wrong during receiving SMS" + e);
			}
		}
	}

	private void setupWimpleImpl() {
		wimple.setApplicationContext(context);

		wimple.setResponseListener(new IWimpleResponseListener() {

			@Override
			public void onGetAuthTempToken(boolean status, String tempToken) {
			}

			@Override
			public void onGetAuthAccessToken(boolean status,
					Map<String, String> result) {
			}

			@Override
			public void onGetUserInfoResponseReceived(boolean status,
					UserInfo info) {
			}

			@Override
			public void onGetAllSectionResponseReceived(boolean status,
					Collection<Section> list) {
			}

			@Override
			public void onGetAllAccountResponseReceived(boolean status,
					Collection<Account> list) {
			}

			@Override
			public void onGetEntriesResponseReceived(boolean status,
					Collection<Entry> list) {
			}

			@Override
			public void onGetLatestEntriesResponseReceived(boolean status,
					Collection<Entry> list) {
			}

			@Override
			public void onMakeEntryResponseReceived(boolean status,
					String entryDate) {
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
			public void onModifyEntryResponseReceived(boolean status,
					Entry entry) {
			}

			@Override
			public void onGetMonthlyItemsResponseReceived(boolean status,
					ArrayList<Item> list) {
			}

			@Override
			public void onRemoveEntryResponseReceived(boolean status, String id) {
			}

			@Override
			public void onRemoveMonthlyItemResponseReceived(boolean status,
					String id) {
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
			public void onGetBudgetResponseReceived(boolean status,
					boolean isIncome, Map<String, Budget> list) {
			}

			@Override
			public void onPostNewsResponseReceived(boolean status, String id) {
			}

			@Override
			public void onPostPaymentsResponseReceived(boolean status) {
				Log.d(LOG_TAG, "payment reported!!!, result = " + status);
			}

		});
	}
}
