package kr.blogspot.charlie0301;

import java.util.Collection;

import kr.blogspot.charlie0301.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.model.Section;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

public class SettingsFragment extends PreferenceFragment  implements IWimpleFragment {

	private final static String LOG_TAG = "SettingsFragment";

	private static Context context;

	private static final WimpleImpl wimple = WimpleImpl.getInstance();

	private static WimpleActivity wimpleActivity;

	private SharedPreferences settings;

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
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		context = WimpleActivity.context;
		settings = context.getSharedPreferences(WimpleImpl.settingsKey, Context.MODE_PRIVATE);
		wimple.getAllSections(true);
		
		addPreferencesFromResource(R.xml.settings);

		listSections = (ListPreference) findPreference("preference_sections");

		Preference logout = findPreference("preference_logout");
		logout.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				wimple.cleanAuth();
				wimple.clearAllDBRecords();

				CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(context);
				CookieManager cookieManager = CookieManager.getInstance();
				cookieManager.setAcceptCookie(true);
				cookieManager.removeSessionCookie();
				cookieSyncManager.sync();

				context.deleteDatabase("webview.db");
				context.deleteDatabase("webviewCache.db");

				//System.runFinalizersOnExit(true);
				//System.exit(0);

				Intent intent = new Intent(context, SplashScreenActivity.class);
				intent.putExtra("auth_again", "");
				startActivity(intent);
				wimpleActivity.finish();

				return false;
			}
		});
	}

	@SuppressWarnings("unchecked")
	@Override
	public void handleMessage(Message msg) {
		int command = msg.what;
		//boolean booleanStatus = msg.arg1 == 1;
		Object obj = msg.obj;

		// if fragment is added or not to the activity
		if(false == isAdded()){
			return;
		}

		if(null == context){
			context = WimpleActivity.context;
			if(null == context){
				return;
			}
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
					Log.d(LOG_TAG, "new section id = " + newValue.toString() + ", prev = " + listSections.getValue());
					
					wimple.setDefaultSectionID(newValue.toString());
					wimple.clearAllDBRecords();
					
					settings.edit().putString("section_id", wimple.getDefaultSectionID()).commit();
					Intent intent = new Intent(context, SplashScreenActivity.class);
					startActivity(intent);
					wimpleActivity.finish();

					return false;
				}
			});
		}
		}
	}

	@Override
	public void refreshView() {
	}

	@Override
	public void setActivityInstance(WimpleActivity instance) {
		SettingsFragment.wimpleActivity = instance;
	}
}
