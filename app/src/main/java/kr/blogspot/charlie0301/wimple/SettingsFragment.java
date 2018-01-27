package kr.blogspot.charlie0301.wimple;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import kr.blogspot.charlie0301.wimple.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.model.Section;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.Toast;

public class SettingsFragment extends PreferenceFragmentCompat implements IWimpleFragment {

	private final static String LOG_TAG = "SettingsFragment";

	private final static int PICK_CONTACT_REQUEST = 1;

	private final WimpleImpl wimple = WimpleImpl.getInstance();

	private WeakReference<WimpleActivity> wimpleActivity;

	public static final String KEY_SMS_POST_ENABLE = "pref_smsPostEnable";
	//public static final String KEY_SMS_TARGET_PHONE_NUMBERS = "pref_smsTargetPhoneNumbers";
	public static final String KEY_SMS_PICK_CONTACT = "pref_smsPickContact";
	public static final String KEY_SMS_CONTACT_LIST = "pref_smsContactList";
	public static final String KEY_SMS_SEND_NOW = "pref_smsSendNow";
	public static final String KEY_SMS_MAX_STORE_NUMBER = "pref_smsMaxStoreNumber";	
	public static final String KEY_MONTHLY_ITEM_COUNT = "pref_monthlyItemCount";
	public static final String KEY_MONTHLY_ITEM_DISPLAY = "pref_monthlyItemDisplay";
	public static final String KEY_FINANCIAL_STATE_AUTO_REFRESH = "pref_financialStateAutoRefresh";
	public static final String KEY_FINANCIAL_STATE_SHOW_GROUP = "pref_financialStateShowGroup";
	public static final String KEY_INCOME_EXPENSE_STATE_AUTO_REFRESH = "pref_incomeExpenseStateAutoRefresh";
	public static final String KEY_DISABLE_MEMO = "pref_disableMemo";
	public static final String KEY_INCOME_EXPENSE_ENABLE_BUDGET = "pref_incomeExpenseStateEnableBudget";
	public static final String KEY_INCOME_EXPENSE_SHOW_GROUP = "pref_incomeExpenseStateShowGroup";

	ListPreference listSections;


	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

		wimple.getAllSections(true);

		addPreferencesFromResource(R.xml.settings);

		listSections = (ListPreference) findPreference("preference_sections");
		{
			CharSequence entries[] = new String[1];
			CharSequence entryValues[] = new String[1];
			entries[0] = "Please wait for seconds";
			entryValues[0] = "Please wait for seconds";
			listSections.setEntries(entries);
			listSections.setEntryValues(entryValues);
			listSections.setValueIndex(0);
		}

		final Preference logout = findPreference("preference_logout");
		logout.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@SuppressWarnings("deprecation")
			@Override
			public boolean onPreferenceClick(Preference preference) {
				wimple.cleanAuth();
				wimple.clearAllDBRecords();

				if(WimpleActivity.context.get() != null){
					CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(WimpleActivity.context.get());
					CookieManager cookieManager = CookieManager.getInstance();
					cookieManager.setAcceptCookie(true);
					cookieManager.removeSessionCookie();
					cookieSyncManager.sync();

					WimpleActivity.context.get().deleteDatabase("webview.db");
					WimpleActivity.context.get().deleteDatabase("webviewCache.db");
				}

				//System.runFinalizersOnExit(true);
				//System.exit(0);

				Intent intent = new Intent(WimpleActivity.context.get(), SplashScreenActivity.class);
				intent.putExtra("auth_again", "");
				startActivity(intent);
				wimpleActivity.get().finish();

				return false;
			}
		});

		updateContactList();

		final Preference smsPostEnable = findPreference(KEY_SMS_POST_ENABLE);
		smsPostEnable.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if(newValue instanceof Boolean){
					Boolean boolVal = (Boolean)newValue;
					if(boolVal){
						WimpleActivity.sm(CommandID.PERMISSIONS_REQUEST_RECEIVE_SMS, Manifest.permission.RECEIVE_SMS);
					}
				}
				return true;
			}
		});

		final Preference pickContact = findPreference(KEY_SMS_PICK_CONTACT);
		pickContact.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {

				Intent pickContactIntent = new Intent(Intent.ACTION_PICK, Uri.parse("content://contacts"));
				pickContactIntent.setType(Phone.CONTENT_TYPE);
				startActivityForResult(pickContactIntent, PICK_CONTACT_REQUEST);
				return false;
			}
		});

		final Preference sendNow = findPreference(KEY_SMS_SEND_NOW);
		sendNow.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {

				final SharedPreferences smsKeySettings = WimpleActivity.context.get().getSharedPreferences(SMSReceiver.smsKey, 0);
				final String storedSMS = smsKeySettings.getString(SMSReceiver.smsBodyTag, "");
				int numberOfStoredSMSs = smsKeySettings.getInt(SMSReceiver.smsNumberTag, 0);

				//Log.d(LOG_TAG, "sotredSMS = " + storedSMS);
				Log.d(LOG_TAG, "numberofSMSs = " + numberOfStoredSMSs);

				if(storedSMS.isEmpty() ||
						numberOfStoredSMSs <= 0)
				{
					Toast.makeText(WimpleActivity.context.get(), getResources().getString(R.string.settings_sms_send_cant), Toast.LENGTH_LONG).show();
					return false;
				}
				
				wimple.postPayments(storedSMS, "0000");
				SharedPreferences.Editor editor = smsKeySettings.edit();
				editor.putString(SMSReceiver.smsBodyTag, "").putInt(SMSReceiver.smsNumberTag, 0).apply();

				return false;
			}
		});
	}

	private void updateContactList(){

		final ListPreference contactList = (ListPreference)findPreference(KEY_SMS_CONTACT_LIST);
		final SharedPreferences smsSettings = WimpleActivity.context.get().getSharedPreferences(SMSReceiver.smsKey, 0);
		String storedTels = smsSettings.getString(SMSReceiver.smsTelTag, "");
		storedTels = storedTels.trim();
		contactList.setSummary(storedTels);

		if(storedTels.isEmpty()){
			CharSequence entries[] = new String[1];
			CharSequence entryValues[] = new String[1];
			entries[0] = "------";
			entryValues[0] = "------";
			contactList.setEntries(entries);
			contactList.setEntryValues(entryValues);

			return;
		}

		List<String> list = Arrays.asList(storedTels.split("\\s*,\\s*"));
		if(list.isEmpty()){
			Log.d(LOG_TAG, "contact list is empty!!!");
			return;
		}

		CharSequence entries[] = new String[list.size()];
		CharSequence entryValues[] = new String[list.size()];
		int i = 0;

		for (String contact : list) {
			entries[i] = contact;
			entryValues[i] = contact;
			i++;
		}
		contactList.setEntries(entries);
		contactList.setEntryValues(entryValues);
		contactList.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				Log.d(LOG_TAG, "new section id = " + newValue.toString());
				SharedPreferences smsKeySettings = WimpleActivity.context.get().getSharedPreferences(SMSReceiver.smsKey, 0);
				String storedTels = smsKeySettings.getString(SMSReceiver.smsTelTag, "");

				if(storedTels.isEmpty()){
					return false;
				}
				// TODO : make this efficiently,,, no time.
				storedTels = storedTels.replace(newValue.toString(), "").trim();
				storedTels = storedTels.replaceAll(",+", ",");
				if(storedTels.startsWith(",")){
					storedTels = storedTels.substring(1);
				}
				if(storedTels.endsWith(",")){
					storedTels = storedTels.substring(0, storedTels.length() - 1);
				}
				smsKeySettings.edit().putString(SMSReceiver.smsTelTag, storedTels).apply();

				updateContactList();
				return false;
			}
		});
	}

	@SuppressWarnings("unchecked")
	@Override
	public void handleMessage(Message msg) {
		int command = msg.what;
		boolean booleanStatus = msg.arg1 == 1;
		Object obj = msg.obj;

		// if fragment is added or not to the activity
		if(!isAdded()){
			return;
		}

		if(null == WimpleActivity.context.get()){
			return;
		}

		switch(command){

		case CommandID.GET_ALL_SECTION_RECEIVED :{

			Collection<Section> list = (Collection<Section>)obj;
			if(null == list ||
					list.isEmpty()){
				return;
			}

			if (listSections == null) {
				return;
			}

			CharSequence entries[] = new String[list.size()];
			CharSequence entryValues[] = new String[list.size()];
			int i = 0;
			int idx = 0;
			for (Section section : list) {
				entries[i] = section.getTitle();
				if(null != wimple.getDefaultSectionID() &&
						0 == section.getId().compareTo(wimple.getDefaultSectionID())){
					idx = i;
				}
				entryValues[i] = section.getId();
				i++;
			}
			listSections.setEntries(entries);
			listSections.setEntryValues(entryValues);
			listSections.setValueIndex(idx);
			listSections.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {					
					if(0 == newValue.toString().compareTo(listSections.getValue())){
						return false;
					}

					int idx = listSections.findIndexOfValue(newValue.toString());
					CharSequence entries[] = listSections.getEntries();
					if(-1 == idx)
						return false;

					wimple.setDefaultSectionID(newValue.toString());
					wimple.setDefaultSectionName(entries[idx].toString());
					wimple.clearAllDBRecords();

					if(WimpleActivity.context != null){
						SharedPreferences settings = WimpleActivity.context.get().getSharedPreferences(WimpleImpl.settingsKey, Context.MODE_PRIVATE);
						settings.edit().putString("section_id", wimple.getDefaultSectionID()).apply();
						settings.edit().putString("section_name", wimple.getDefaultSectionName()).apply();
					}

					Intent intent = new Intent(WimpleActivity.context.get(), SplashScreenActivity.class);
					startActivity(intent);
					wimpleActivity.get().finish();

					return false;
				}
			});
			break;
		}

		case CommandID.POST_PAYMENT_RESPONSE_RECEIVED :
		{
			if(booleanStatus)
			{
				Toast.makeText(WimpleActivity.context.get(), getResources().getString(R.string.settings_sms_send_success), Toast.LENGTH_LONG).show();
			}else{
				Toast.makeText(WimpleActivity.context.get(), getResources().getString(R.string.settings_sms_send_failed), Toast.LENGTH_LONG).show();
			} 
			break;
		}
		}
	}

	@Override
	public void setActivityInstance(WimpleActivity instance) {
		wimpleActivity = new WeakReference<>(instance);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (requestCode == PICK_CONTACT_REQUEST) {
			if (resultCode == Activity.RESULT_OK) {

				Uri contactUri = data.getData();
				String[] projection = {Phone.NUMBER};

				Cursor cursor = WimpleActivity.context.get().getContentResolver()
						.query(contactUri, projection, null, null, null);
				if(cursor ==null)
					return;

				cursor.moveToFirst();

				int column = cursor.getColumnIndex(Phone.NUMBER);
				String number = cursor.getString(column);

				if(null != number)
				{
					number = number.replace("-", "").trim();

					SharedPreferences smsKeySettings = WimpleActivity.context.get().getSharedPreferences(SMSReceiver.smsKey, 0);
					String storedTels = smsKeySettings.getString(SMSReceiver.smsTelTag, "");

					if(storedTels.contains(number)){
						return;
					}

					if(storedTels.endsWith(",") ||
							storedTels.isEmpty()){
						storedTels = storedTels + number;
					}else{
						storedTels = storedTels + "," + number;	
					}					
					smsKeySettings.edit().putString(SMSReceiver.smsTelTag, storedTels).apply();

					updateContactList();
				}
			}
		}
	}
}
