package kr.blogspot.charlie0301;

import kr.blogspot.charlie0301.impl.WimpleImpl;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

public class SettingsFragment extends PreferenceFragment  implements IWimpleFragment {

	//private final static String LOG_TAG = "SettingsFragment";

	private static WimpleActivity wimpleActivity;

	public static final String KEY_MONTHLY_ITEM_COUNT = "pref_monthlyItemCount";
	public static final String KEY_MONTHLY_ITEM_DISPLAY = "pref_monthlyItemDisplay";
	public static final String KEY_FINANCIAL_STATE_AUTO_REFRESH = "pref_financialStateAutoRefresh";
	public static final String KEY_INCOME_EXPENSE_STATE_AUTO_REFRESH = "pref_incomeExpenseStateAutoRefresh";
	public static final String KEY_DISABLE_MEMO = "pref_disableMemo";
	public static final String KEY_INCOME_EXPENSE_ENABLE_BUDGET = "pref_incomeExpenseStateEnableBudget";
		

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.settings);

		Preference logout = findPreference("preference_logout");
		logout.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				WimpleImpl.getInstance().cleanAuth();
				WimpleImpl.getInstance().clearAllDBRecords();

				CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(WimpleActivity.context);
				CookieManager cookieManager = CookieManager.getInstance();
				cookieManager.setAcceptCookie(true);
				cookieManager.removeSessionCookie();
				cookieSyncManager.sync();

				WimpleActivity.context.deleteDatabase("webview.db");
				WimpleActivity.context.deleteDatabase("webviewCache.db");

				//System.runFinalizersOnExit(true);
				//System.exit(0);

				Intent intent = new Intent(WimpleActivity.context, SplashScreenActivity.class);
				intent.putExtra("auth_again", "");
				startActivity(intent);
				wimpleActivity.finish();
				
				return false;
			}
		});
	}

	@Override
	public void handleMessage(Message msg) {
	}

	@Override
	public void refreshView() {
	}

	@Override
	public void setActivityInstance(WimpleActivity instance) {
		SettingsFragment.wimpleActivity = instance;
	}
}
