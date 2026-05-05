package kr.blogspot.charlie0301.wimple.impl;

import android.content.Context;
import android.content.SharedPreferences;

final class WimpleAuthStore {

    private static final String KEY_TOKEN = "token";
    private static final String KEY_TOKEN_SECRET = "token_secret";
    private static final String KEY_USER_ID = "userid";
    private static final String KEY_SECTION_ID = "section_id";
    private static final String KEY_SECTION_NAME = "section_name";

    private final SharedPreferences prefs;

    WimpleAuthStore(Context context) {
        prefs = context.getSharedPreferences(WimpleImpl.settingsKey, Context.MODE_PRIVATE);
    }

    StoredSession load() {
        return new StoredSession(
                prefs.getString(KEY_TOKEN, null),
                prefs.getString(KEY_TOKEN_SECRET, null),
                prefs.getString(KEY_USER_ID, null),
                prefs.getString(KEY_SECTION_ID, null),
                prefs.getString(KEY_SECTION_NAME, "Default")
        );
    }

    void saveAuth(String token, String tokenSecret, String userID) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_TOKEN_SECRET, tokenSecret)
                .putString(KEY_USER_ID, userID)
                .apply();
    }

    void saveSection(String sectionID, String sectionName) {
        prefs.edit()
                .putString(KEY_SECTION_ID, sectionID)
                .putString(KEY_SECTION_NAME, sectionName)
                .apply();
    }

    void clear() {
        prefs.edit()
                .putString(KEY_TOKEN, "")
                .putString(KEY_TOKEN_SECRET, "")
                .putString(KEY_USER_ID, "")
                .putString(KEY_SECTION_ID, "")
                .putString(KEY_SECTION_NAME, "")
                .apply();
    }

    static final class StoredSession {
        final String token;
        final String tokenSecret;
        final String userID;
        final String sectionID;
        final String sectionName;

        StoredSession(String token, String tokenSecret, String userID, String sectionID, String sectionName) {
            this.token = token;
            this.tokenSecret = tokenSecret;
            this.userID = userID;
            this.sectionID = sectionID;
            this.sectionName = sectionName;
        }
    }
}
