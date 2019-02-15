package kr.blogspot.charlie0301.wimple;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import kr.blogspot.charlie0301.wimple.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.model.Section;

public class SettingsFragment extends PreferenceFragmentCompat implements IWimpleFragment {

    private final static String LOG_TAG = "SettingsFragment";
    private final WimpleImpl wimple = WimpleImpl.getInstance();

    private WeakReference<WimpleActivity> wimpleActivity;

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

                if (WimpleActivity.context.get() != null) {
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
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message msg) {
        int command = msg.what;
        boolean booleanStatus = msg.arg1 == 1;
        Object obj = msg.obj;

        // if fragment is added or not to the activity
        if (!isAdded()) {
            return;
        }

        if (null == WimpleActivity.context.get()) {
            return;
        }

        switch (command) {

            case CommandID.GET_ALL_SECTION_RECEIVED: {

                Collection<Section> list = (Collection<Section>) obj;
                if (null == list ||
                        list.isEmpty()) {
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
                    if (null != wimple.getDefaultSectionID() &&
                            0 == section.getId().compareTo(wimple.getDefaultSectionID())) {
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
                        if (0 == newValue.toString().compareTo(listSections.getValue())) {
                            return false;
                        }

                        int idx = listSections.findIndexOfValue(newValue.toString());
                        CharSequence entries[] = listSections.getEntries();
                        if (-1 == idx)
                            return false;

                        wimple.setDefaultSectionID(newValue.toString());
                        wimple.setDefaultSectionName(entries[idx].toString());
                        wimple.clearAllDBRecords();

                        if (WimpleActivity.context != null) {
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

            case CommandID.POST_PAYMENT_RESPONSE_RECEIVED: {
                if (booleanStatus) {
                    Toast.makeText(WimpleActivity.context.get(), getResources().getString(R.string.settings_sms_send_success), Toast.LENGTH_LONG).show();
                } else {
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
}
