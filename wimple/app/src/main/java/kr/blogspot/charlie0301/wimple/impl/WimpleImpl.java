package kr.blogspot.charlie0301.wimple.impl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;

import kr.blogspot.charlie0301.wimple.R;
import kr.blogspot.charlie0301.wimple.impl.RestAPIInvoker.HTTPMethod;
import kr.blogspot.charlie0301.wimple.impl.db.AccountDBHandler;
import kr.blogspot.charlie0301.wimple.impl.db.AccountStateDBHandler;
import kr.blogspot.charlie0301.wimple.impl.db.BudgetDBHandler;
import kr.blogspot.charlie0301.wimple.impl.db.EntryDBHandler;
import kr.blogspot.charlie0301.wimple.impl.db.IncomeExpenseDBHandler;
import kr.blogspot.charlie0301.wimple.impl.db.ItemDBHandler;
import kr.blogspot.charlie0301.wimple.impl.db.SectionDBHandler;
import kr.blogspot.charlie0301.wimple.impl.db.UserInfoDBHandler;
import kr.blogspot.charlie0301.wimple.impl.util.DrawableBitmapCache;
import kr.blogspot.charlie0301.wimple.impl.util.ImageUtils;
import kr.blogspot.charlie0301.wimple.impl.util.RemoteContent;
import kr.blogspot.charlie0301.wimple.model.Account;
import kr.blogspot.charlie0301.wimple.model.AccountState;
import kr.blogspot.charlie0301.wimple.model.Budget;
import kr.blogspot.charlie0301.wimple.model.Entry;
import kr.blogspot.charlie0301.wimple.model.Item;
import kr.blogspot.charlie0301.wimple.model.Section;
import kr.blogspot.charlie0301.wimple.model.UserInfo;


public class WimpleImpl implements IWimpleImpl {

    private static final WimpleImpl INSTANCE = new WimpleImpl();

    private static WeakReference<Context> context;
    private final MainHandler mainHandler;

    // subsystems
    private final EntryManager em = new EntryManager(this);
    private final ItemManager im = new ItemManager(this);
    private final RestAPIInvoker rai;
    private final RestResponseHandler rrh;

    private UserInfoDBHandler uidbh = null;
    private AccountDBHandler adbh = null;
    private ItemDBHandler lidbh = null;
    private SectionDBHandler sdbh = null;
    private EntryDBHandler edbh = null;
    private ItemDBHandler midbh = null;
    private AccountStateDBHandler asdbh = null;        // financial status
    private IncomeExpenseDBHandler iedbh = null;    // income and expense
    private BudgetDBHandler bddbh = null;

    // static references
    private static final Locale locale = Resources.getSystem().getConfiguration().locale;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", locale);
    public static final String settingsKey = "wimple.auth";

    // Data
    private int countOfRemainedAPICall = -1;
    private int countOfTotalAPICall = 1;
    private String defaultSectionID;
    private String defaultSectionName = "Default";
    private Hashtable<String, Semaphore> apiAvailableSemaphore = new Hashtable<>();

    private static IWimpleStatusListener statusListener = new IWimpleStatusListener() {

        @Override
        public void onLoggedIn(boolean status) {
        }

        @Override
        public void onLoggedOut() {
        }

        @Override
        public void onProfilePictureUpdated() {
        }

        @Override
        public void onNetworkConnectionEstablished() {
        }

        @Override
        public void onNetworkConnectionLost() {
        }
    };

    private static IWimpleResponseListener responseListener = new IWimpleResponseListener() {

        @Override
        public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list) {
        }

        @Override
        public void onGetAuthTempToken(boolean status, String tempToken) {
        }

        @Override
        public void onGetAuthAccessToken(boolean status, Map<String, String> result) {
        }

        @Override
        public void onGetUserInfoResponseReceived(boolean status, UserInfo info) {
        }

        @Override
        public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list) {
        }

        @Override
        public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list) {
        }

        @Override
        public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list) {
        }

        @Override
        public void onMakeEntryResponseReceived(boolean status, String entryDate) {
        }

        @Override
        public void onGetFrequentItemsResponseReceived(boolean status, Collection<Item> list) {
        }

        @Override
        public void onGetLatestItemsResponseReceived(boolean status, Collection<Item> list) {
        }

        @Override
        public void onModifyEntryResponseReceived(boolean status, Entry entry) {
        }

        @Override
        public void onGetMonthlyItemsResponseReceived(boolean status, ArrayList<Item> list) {
        }

        @Override
        public void onRemoveEntryResponseReceived(boolean status, String id) {
        }

        @Override
        public void onRemoveMonthlyItemResponseReceived(boolean status, String id) {
        }

        @Override
        public void onGetFinancialStateResponseReceived(boolean status, Collection<AccountState> list) {
        }

        @Override
        public void onGetIncomeAndExpenseResponseReceived(boolean status, Collection<AccountState> list) {
        }

        @Override
        public void onGetBudgetResponseReceived(boolean status, boolean isIncome, Map<String, Budget> list) {
        }

        @Override
        public void onPostNewsResponseReceived(boolean status, String id) {
        }

        @Override
        public void onPostPaymentsResponseReceived(boolean status) {
        }
    };

    protected WimpleImpl() {

        HandlerThread dispatchHandlerThread = new HandlerThread("Dispatching");
        dispatchHandlerThread.start();
        mainHandler = new MainHandler(dispatchHandlerThread.getLooper());
        rai = new RestAPIInvoker(this);
        rrh = new RestResponseHandler();

        apiAvailableSemaphore.put("getAllEntries", new Semaphore(1));
        // Because of delayed account list display, ignore multiple Account get request and response received situation.
        //apiAvailableSemaphore.put("getAllAccounts", new Semaphore(1));
        apiAvailableSemaphore.put("getFinancialState", new Semaphore(1));
        apiAvailableSemaphore.put("getIncomeAndExpense", new Semaphore(1));
        apiAvailableSemaphore.put("getIncomeBudget", new Semaphore(1));
        apiAvailableSemaphore.put("getExpenseBudget", new Semaphore(1));
        apiAvailableSemaphore.put("postNews", new Semaphore(1));
        apiAvailableSemaphore.put("postPayments", new Semaphore(1));
    }

    public Semaphore getApiAvailableSemaphore(String key) {
        return apiAvailableSemaphore.get(key);
    }

    public static WimpleImpl getInstance() {
        return INSTANCE;
    }

    public void setApplicationContext(Context context) {
        if(WimpleImpl.context != null)
            return;

        WimpleImpl.context = new WeakReference<>(context);
        rrh.setApplicationContext(context);

        SharedPreferences settings = context.getSharedPreferences(settingsKey, Context.MODE_PRIVATE);

        String saved_value = settings.getString("token", null);
        if (token.isEmpty() &&
                null != saved_value) {
            token = saved_value;
        }

        saved_value = settings.getString("token_secret", null);
        if (tokenSecret.isEmpty() &&
                null != saved_value) {
            tokenSecret = saved_value;
        }

        if (!token.isEmpty() &&
                !tokenSecret.isEmpty()) {
            this.isAuthed = true;
        }

        saved_value = settings.getString("userid", null);
        if (userID.isEmpty() &&
                null != saved_value) {
            userID = saved_value;
        }

        saved_value = settings.getString("section_id", null);
        if (null != saved_value) {
            defaultSectionID = saved_value;

            saved_value = settings.getString("section_name", "Default");
            defaultSectionName = saved_value;
        }

        if (null == uidbh) {
            uidbh = new UserInfoDBHandler(WimpleImpl.context.get());
        }

        if (null == adbh) {
            adbh = new AccountDBHandler(WimpleImpl.context.get());
        }

        if (null == lidbh) {
            lidbh = new ItemDBHandler(WimpleImpl.context.get(), "frequentitems");
        }

        if (null == sdbh) {
            sdbh = new SectionDBHandler(WimpleImpl.context.get());
        }

        if (null == edbh) {
            edbh = new EntryDBHandler(WimpleImpl.context.get());
        }

        if (null == midbh) {
            midbh = new ItemDBHandler(WimpleImpl.context.get(), "monthlyitems");
        }

        if (null == asdbh) {
            asdbh = new AccountStateDBHandler(WimpleImpl.context.get());
        }

        if (null == iedbh) {
            iedbh = new IncomeExpenseDBHandler(WimpleImpl.context.get());
        }

        if (null == bddbh) {
            bddbh = new BudgetDBHandler(WimpleImpl.context.get());
        }
    }

    public void setStatusListener(IWimpleStatusListener listener) {
        WimpleImpl.statusListener = listener;
    }

    public void setResponseListener(IWimpleResponseListener listener) {
        WimpleImpl.responseListener = listener;
    }

    public static IWimpleStatusListener getStatusListener() {
        return statusListener;
    }

    public static IWimpleResponseListener getResponseListener() {
        return responseListener;
    }

    public String getDefaultSectionID() {
        return defaultSectionID;
    }

    public void setDefaultSectionID(String defaultSectionID) {
        this.defaultSectionID = defaultSectionID;
    }

    public String getDefaultSectionName() {
        return defaultSectionName + " " + context.get().getResources().getString(R.string.section);
    }

    public void setDefaultSectionName(String defaultSectionName) {
        if (null == defaultSectionName ||
                0 == defaultSectionName.length())
            return;
        this.defaultSectionName = defaultSectionName;
    }

    private static final String LOG_TAG = "Wimple";

    private static final String serviceHost = "https://whooing.com/";
    private static final String appID = "140";
    private static final String vo42iw5me4vxz = "***REDACTED-WIMPLE-APP-SECRET***";

    //private final Semaphore authInProgress = new Semaphore(0);
    private static Integer sequence = 10000;

    private String token = "";
    private String tokenSecret = "";
    private String userID = "";

    /*
     *  Login success = isAuthed(true) >> success getting section, account = isInitializedFinished(true)
     *  In case of isAuthed(true), token & tokenSecret & userID are all filled(!isEmpty())
     */
    private boolean isAuthed = false;
    private boolean isInitializedFinished = false;

    static final class Path {

        static final String AUTH_REQUEST_TOKEN = "app_auth/request_token";
        static final String AUTH_AUTHORIZE = "app_auth/authorize";
        static final String AUTH_ACCESS_TOKEN = "app_auth/access_token";

        static final String USER_INFO = "api/user.json";

        static final String SECTIONS_ALL = "api/sections.json";
        static final String SECTIONS_DEFAULT = "api/sections/default.json";

        static final String ACCOUNT_ALL = "api/accounts.json_array";

        static final String ENTRIES_ALL = "api/entries.json_array";
        static final String ENTRIES_LATEST = "api/entries/latest.json_array";
        static final String ENTRIES_MODIFY = "api/entries/";
        static final String ENTRIES_REMOVE = ENTRIES_MODIFY;
        static final String POST_PAYMENT = "api/entries/outside.json";
        static final String REPORT_FAILED_PAYMENT = "api/entries/outside_report.json";

        static final String ITEM_FREQUENT = "api/frequent_items.json_array";
        static final String ITEM_LATEST = "api/entries/latest_items.json_array";
        static final String ITEM_MONTHLY = "api/monthly_items.json_array";
        static final String ITEM_MONTHLY_REMOVE = "api/monthly_items/slot1/";

        static final String FINANCIAL_STATE = "api/bs.json_array";

        static final String INCOME_AND_EXPENSE = "api/pl.json_array";
        static final String BUDGET_INCOME = "api/budget/income.json_array";
        static final String BUDGET_EXPENSES = "api/budget/expenses.json_array";

        static final String MONEYNEWS = "api/bbs/moneynews.json";

    }

    ;


    /*
     * These APIs are regarding Promise Library management
     */

    public String getServicehost() {
        return serviceHost;
    }

    public String getAppid() {
        return appID;
    }

    public String getVo42iw5me4vxz() {
        return vo42iw5me4vxz;
    }

    public String getToken() {
        return token;
    }

    public String getTokenSecret() {
        return tokenSecret;
    }

    public String getUserID() {
        return userID;
    }

    public Integer getSequence() {
        return sequence++;
    }


    /*
     * Utils
     */
    public JSONObject invokeRESTAPI(HTTPMethod method, String path, String params) {
        return rai.invokeRESTAPI(method, path, params);
    }

    public void handleRESTErrorResponse(int code) {
        rrh.handleRestResponse(code);
    }

    /*
     * Handler
     */

    public static final class CommandID {

        private CommandID() {
        }

        static final int CMD_BASE = 1000;

        static final int CMD_AUTHENTICATION_SUCCEED = CMD_BASE + 1;
        static final int CMD_AUTHENTICATION_FAILED = CMD_BASE + 3;
        static final int CMD_GET_TEMP_TOKEN = CMD_BASE + 5;
        static final int CMD_GET_ACCESS_TOKEN = CMD_BASE + 7;

        static final int CMD_GET_USER_INFO = CMD_BASE + 9;

        static final int CMD_GET_SECTIONS = CMD_BASE + 11;
        static final int CMD_GET_SECTIONS_DEFAULT = CMD_BASE + 13;
        static final int CMD_GET_ACCOUNT_ALL = CMD_BASE + 15;
        static final int CMD_GET_ENTRIES = CMD_BASE + 17;
        static final int CMD_GET_LATEST_ENTRIES = CMD_BASE + 19;
        static final int CMD_POST_ENTRY = CMD_BASE + 21;
        static final int CMD_GET_FRQUENT_ITEMS = CMD_BASE + 23;
        static final int CMD_GET_LATEST_ITEMS = CMD_BASE + 25;
        static final int CMD_PUT_ENTRY = CMD_BASE + 27;
        static final int CMD_GET_MONTHLY_ITEMS = CMD_BASE + 29;
        static final int CMD_PROFILE_PICTURE_UPDATED = CMD_BASE + 31;
        static final int CMD_DELETE_ENTRY = CMD_BASE + 33;
        static final int CMD_DELETE_MONTHLY_ITEMS = CMD_BASE + 35;
        static final int CMD_GET_FINANCIAL_STATE = CMD_BASE + 37;
        static final int CMD_GET_INCOME_AND_EXPENSE = CMD_BASE + 39;
        static final int CMD_GET_BUDGET = CMD_BASE + 41;
        static final int CMD_POST_NEWS = CMD_BASE + 43;
        static final int CMD_POST_PAYMENTS = CMD_BASE + 45;
    }


    public void sm(int cmd, Object msg) {
        mainHandler.sendMessage(Message.obtain(mainHandler, cmd, 1, 0, msg));
    }

    public void sm(int cmd, int a1, int a2, Object msg) {
        mainHandler.sendMessage(Message.obtain(mainHandler, cmd, a1, a2, msg));
    }

    private class MainHandler extends Handler {

        @SuppressLint("HandlerLeak")
        MainHandler(Looper looper) {
            super(looper);
        }

        @SuppressWarnings("unchecked")
        public void handleMessage(Message msg) {

            int command = msg.what;
            boolean booleanStatus = (msg.arg1 == 1);
            Object obj = msg.obj;

            Log.d(LOG_TAG, "CMDCode = " + command + " Status = " + booleanStatus + " OBJ= " + ((null != obj) ? obj.getClass().getName() : ""));

            switch (command) {

                case CommandID.CMD_AUTHENTICATION_SUCCEED:
                    Log.d(LOG_TAG, "Wimple authentication is succeed!!!");
                    break;

                case CommandID.CMD_AUTHENTICATION_FAILED:
                    Log.d(LOG_TAG, "Wimple authentication is failed!!!");
                    break;

                case CommandID.CMD_GET_TEMP_TOKEN:
                    responseListener.onGetAuthTempToken(booleanStatus, (String) obj);
                    break;

                case CommandID.CMD_GET_ACCESS_TOKEN: {
                    Map<String, String> list = (Map<String, String>) obj;

                    if (list == null) {
                        return;
                    }
                    token = list.get("token");
                    tokenSecret = list.get("token_secret");
                    userID = list.get("user_id");

                    // TODO : Have to store userID
                    if (null == context.get()) {
                        Log.e(LOG_TAG, "Application Context is not set!!!");
                        //throw new Exception("Application Context is not set!!!");
                    }

                    SharedPreferences settings = context.get().getSharedPreferences(settingsKey, context.get().MODE_PRIVATE);
                    settings.edit().putString("token", token).apply();
                    settings.edit().putString("token_secret", tokenSecret).apply();
                    settings.edit().putString("userid", userID).apply();

                    if (booleanStatus) {
                        Log.d(LOG_TAG, "CMD_GET_ACCESS_TOKEN is succeed!!!");
                    } else {
                        Log.d(LOG_TAG, "CMD_GET_ACCESS_TOKEN is failed!!!");
                    }
                    responseListener.onGetAuthAccessToken(booleanStatus, list);
                }
                break;

                case CommandID.CMD_GET_USER_INFO: {
                    if (null == obj)
                        return;

                    UserInfo ui = (UserInfo) obj;
                    if (booleanStatus) {
                        switch (ui.getAPICountLevel()) {
                            case 2:
                                countOfTotalAPICall = 200;
                                break;
                            case 3:
                                countOfTotalAPICall = 1000;
                                break;
                            default:
                                countOfTotalAPICall = 30;
                                break;
                        }
                    }

                    new ProfileDownloadTaskThread(ui.getUserImgURL()).start();
                    responseListener.onGetUserInfoResponseReceived(booleanStatus, ui);
                    break;
                }

                case CommandID.CMD_PROFILE_PICTURE_UPDATED: {
                    statusListener.onProfilePictureUpdated();
                    break;
                }

                case CommandID.CMD_GET_SECTIONS:
                case CommandID.CMD_GET_SECTIONS_DEFAULT:
                    isInitializedFinished = true;
                    statusListener.onLoggedIn(booleanStatus);
                    responseListener.onGetAllSectionResponseReceived(booleanStatus, (Collection<Section>) obj);
                    break;

                case CommandID.CMD_GET_ACCOUNT_ALL:
                    responseListener.onGetAllAccountResponseReceived(booleanStatus, (Collection<Account>) obj);
                    break;

                case CommandID.CMD_GET_ENTRIES:
                    responseListener.onGetEntriesResponseReceived(booleanStatus, (Collection<Entry>) obj);
                    break;

                case CommandID.CMD_GET_LATEST_ENTRIES:
                    responseListener.onGetLatestEntriesResponseReceived(booleanStatus, (Collection<Entry>) obj);
                    break;

                case CommandID.CMD_POST_ENTRY:
                    responseListener.onMakeEntryResponseReceived(booleanStatus, (String) obj);
                    break;

                case CommandID.CMD_GET_FRQUENT_ITEMS:
                    responseListener.onGetFrequentItemsResponseReceived(booleanStatus, (Collection<Item>) obj);
                    break;

                case CommandID.CMD_GET_LATEST_ITEMS:
                    responseListener.onGetLatestItemsResponseReceived(booleanStatus, (Collection<Item>) obj);
                    break;

                case CommandID.CMD_PUT_ENTRY:
                    responseListener.onModifyEntryResponseReceived(booleanStatus, (Entry) obj);
                    break;

                case CommandID.CMD_GET_MONTHLY_ITEMS:
                    responseListener.onGetMonthlyItemsResponseReceived(booleanStatus, (ArrayList<Item>) obj);
                    break;

                case CommandID.CMD_DELETE_ENTRY:
                    responseListener.onRemoveEntryResponseReceived(booleanStatus, (String) obj);
                    break;

                case CommandID.CMD_DELETE_MONTHLY_ITEMS:
                    responseListener.onRemoveMonthlyItemResponseReceived(booleanStatus, (String) obj);
                    break;

                case CommandID.CMD_GET_FINANCIAL_STATE:
                    responseListener.onGetFinancialStateResponseReceived(booleanStatus, (Collection<AccountState>) obj);
                    break;

                case CommandID.CMD_GET_INCOME_AND_EXPENSE:
                    responseListener.onGetIncomeAndExpenseResponseReceived(booleanStatus, (Collection<AccountState>) obj);
                    break;

                case CommandID.CMD_GET_BUDGET:
                    responseListener.onGetBudgetResponseReceived(booleanStatus, (msg.arg2 == 1), (Map<String, Budget>) obj);
                    break;

                case CommandID.CMD_POST_NEWS:
                    responseListener.onPostNewsResponseReceived(booleanStatus, (String) obj);
                    break;

                default:
                    break;

            }

        }
    }




    /*
     * Server APIs
     */


    /*
     * Auth APIs
     */

    public Boolean isAuthed() {
        return this.isAuthed;
    }

    public Boolean isInitializedFinished() {
        return this.isInitializedFinished;
    }

    public void cleanAuth() {

        token = "";
        tokenSecret = "";
        userID = "";
        defaultSectionID = "";
        defaultSectionName = "";

        SharedPreferences settings = context.get().getSharedPreferences(settingsKey, context.get().MODE_PRIVATE);
        settings.edit().putString("token", "").apply();
        settings.edit().putString("token_secret", "").apply();
        settings.edit().putString("userid", "").apply();
        settings.edit().putString("section_id", "").apply();
        settings.edit().putString("section_name", "").apply();

        this.isInitializedFinished = false;
        this.isAuthed = false;
    }

    public Boolean getTempToken() {

        if (isAuthed()) {
            Log.e(LOG_TAG, "[getTempToken] Already authenticated.");
            return true;
        }

        new Thread() {
            @Override
            public void run() {

                String params = "app_id=" + appID + "&app_secret=" + vo42iw5me4vxz;
                Map<String, String> list = rai.invokeRESTAPIForMap(Path.AUTH_REQUEST_TOKEN, params);

                if (null == list) {
                    Log.e(LOG_TAG, "[getTempToken] failed.");
                    sm(CommandID.CMD_GET_TEMP_TOKEN, 0, 0, new HashMap<String, String>());
                    return;
                }
                sm(CommandID.CMD_GET_TEMP_TOKEN, 1, 0, list.get("token"));
            }
        }.start();

        return true;
    }

    public void clearAllDBRecords() {
        uidbh.clean();
        adbh.clean();
        lidbh.clean();
        sdbh.clean();
        edbh.clean();
        midbh.clean();
        asdbh.clean();
        iedbh.clean();
        bddbh.clean();
    }

	/*
	private String getPin(String token){

		String params = "token=" + token;
		Map<String, String> list = invokeRESTAPIForMap(Path.AUTH_AUTHORIZE, params);

		return list.get("pin");
	}
	 */

    public Boolean getAccessToken(String token, String pin) {

		/*
		if(isAuthed()){
			Log.e(LOG_TAG, "[getAccessToken] Already authenticated.");
			return false;
		}
		 */

        new GetAccessTokenTaskThread(token, pin).start();

        return true;
    }

    private class GetAccessTokenTaskThread extends Thread {

        private final String token;
        private final String pin;

        GetAccessTokenTaskThread(String token, String pin) {
            super();
            this.token = token;
            this.pin = pin;
        }

        @Override
        public void run() {

            String params = "app_id=" + appID + "&app_secret=" + vo42iw5me4vxz + "&token=" + token + "&pin=" + pin;
            //Log.d(LOG_TAG, "[GetAccessTokenTaskThread] PARAMS : " + params);
            Map<String, String> list = rai.invokeRESTAPIForMap(Path.AUTH_ACCESS_TOKEN, params);

            if (null == list) {
                Log.e(LOG_TAG, "[GetAccessTokenTaskThread] failed.");
                sm(CommandID.CMD_GET_ACCESS_TOKEN, 0, 0, new HashMap<String, String>());
                return;
            }

            String tokenSecret = list.get("token_secret");
            if (null != tokenSecret &&
                    !tokenSecret.isEmpty()) {
                isAuthed = true;
            }

            sm(CommandID.CMD_GET_ACCESS_TOKEN, 1, 0, list);
        }
    }

    public boolean getAllSections(Boolean forceUpdate) {

        if (!isAuthed()) {
            Log.e(LOG_TAG, "[getAllSections] Already authenticated.");
            return false;
        }

        new GetAllSectionsTaskThread(forceUpdate).start();
        return true;
    }

    private class GetAllSectionsTaskThread extends Thread {

        final Boolean forceUpdate;

        GetAllSectionsTaskThread(Boolean forceUpdate) {
            this.forceUpdate = forceUpdate;
        }

        @Override
        public void run() {

            Collection<Section> list = new ArrayList<>();

            if ((!forceUpdate) &&
                    (sdbh.hasData())) {
                Log.d(LOG_TAG, "[All Sections] Providing Section from Cache");
                list = sdbh.getAllSections();
                sm(CommandID.CMD_GET_SECTIONS, 1, 0, list);
                return;
            }

            JSONObject json = invokeRESTAPI(HTTPMethod.GET, Path.SECTIONS_ALL, "");

            if (null == json) {
                Log.e(LOG_TAG, "[All Sections] Error response - null returned");
                sm(CommandID.CMD_GET_SECTIONS, 0, 0, list);
                return;
            }

            try {
                if (!json.get("code").toString().startsWith("2")) {
                    Log.e(LOG_TAG, "[All Sections] Error response - " + json.get("message").toString());
                    sm(CommandID.CMD_GET_SECTIONS, 0, 0, list);
                    return;
                }

                JSONObject results = json.getJSONObject("results");

                Iterator<String> iterator = results.keys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    JSONObject section = results.getJSONObject(key);

                    if (section.has("isolation") &&
                            0 == section.getString("isolation").compareToIgnoreCase("y")) {
                        continue;
                    }

                    list.add(new Section(section));
                }
                sm(CommandID.CMD_GET_SECTIONS, 1, 0, list);

            } catch (JSONException e) {
                Log.e(LOG_TAG, "[All Sections] Error response - null returned");
                sm(CommandID.CMD_GET_SECTIONS, 0, 0, new ArrayList<Section>());
            }
        }
    }

    public boolean getUserInfo(boolean forceUpdate) {

        if ((!forceUpdate) &&
                (!isAuthed())) {
            Log.e(LOG_TAG, "[User Info] Already authenticated.");
            return false;
        }

        new GetUserInfoTaskThread(forceUpdate).start();

        return true;
    }

    private class GetUserInfoTaskThread extends Thread {

        final Boolean forceUpdate;

        GetUserInfoTaskThread(Boolean forceUpdate) {
            this.forceUpdate = forceUpdate;
        }

        @Override
        public void run() {

            UserInfo info = new UserInfo();

            if ((!forceUpdate) &&
                    (uidbh.hasData())) {
                Log.d(LOG_TAG, "[User Info] Providing User Information from Cache");
                sm(CommandID.CMD_GET_USER_INFO, 1, 0, uidbh.get());
                return;
            }

            JSONObject json = invokeRESTAPI(HTTPMethod.GET, Path.USER_INFO, "");

            if (null == json) {
                Log.e(LOG_TAG, "[User Info] Error response - null returned");
                sm(CommandID.CMD_GET_USER_INFO, 0, 0, null);
                return;
            }

            try {
                if (!json.getString("code").startsWith("2")) {
                    Log.e(LOG_TAG, "[User Info] Error response - " + json.getString("message"));
                    sm(CommandID.CMD_GET_USER_INFO, 0, 0, null);
                    return;
                }

                setRemainedAPICall(json.getString("rest_of_api"));
                JSONObject result = json.getJSONObject("results");

                info = new UserInfo(result);
                uidbh.cleanAndInsert(info);
                sm(CommandID.CMD_GET_USER_INFO, 1, 0, info);

            } catch (JSONException e) {
                Log.e(LOG_TAG, "[User Info] Error response - null returned");
                sm(CommandID.CMD_GET_USER_INFO, 0, 0, null);
            }
        }
    }

    public boolean getDefaultSections(Boolean forceUpdate) {

        if (!isAuthed()) {
            Log.e(LOG_TAG, "[Default Sections] No authentication.");
            return false;
        }

        new GetDefaultSectionTaskThread(forceUpdate).start();
        return true;
    }

    private class GetDefaultSectionTaskThread extends Thread {

        final Boolean forceUpdate;

        GetDefaultSectionTaskThread(Boolean forceUpdate) {
            this.forceUpdate = forceUpdate;
        }

        @Override
        public void run() {

            Collection<Section> list = new ArrayList<>();

            if ((!forceUpdate) &&
                    (sdbh.hasData())) {
                Log.d(LOG_TAG, "[Default Sections] Providing Section from Cache");
                list = sdbh.getAllSections();
                defaultSectionID = ((Section) list.toArray()[0]).getId();
                defaultSectionName = ((Section) list.toArray()[0]).getTitle();
                context.get().getSharedPreferences(settingsKey, Context.MODE_PRIVATE).edit().putString("section_id", defaultSectionID).commit();
                context.get().getSharedPreferences(settingsKey, Context.MODE_PRIVATE).edit().putString("section_name", defaultSectionName).apply();
                sm(CommandID.CMD_GET_SECTIONS_DEFAULT, 1, 0, list);
                return;
            }

            JSONObject json = invokeRESTAPI(HTTPMethod.GET, Path.SECTIONS_DEFAULT, "");

            if (null == json) {
                Log.e(LOG_TAG, "[Default Sections] Error response - null returned");
                sm(CommandID.CMD_GET_SECTIONS_DEFAULT, 0, 0, list);
                return;
            }

            try {
                if (!json.getString("code").startsWith("2")) {
                    Log.e(LOG_TAG, "[Default Sections] Error response - " + json.getString("message"));
                    sm(CommandID.CMD_GET_SECTIONS_DEFAULT, 0, 0, list);
                    return;
                }

                setRemainedAPICall(json.getString("rest_of_api"));
                JSONObject results = json.getJSONObject("results");

                if (results.has("isolation") &&
                        0 == results.getString("isolation").compareToIgnoreCase("n")) {
                    Log.e(LOG_TAG, "[Default Sections] No Default Sections");
                    sm(CommandID.CMD_GET_SECTIONS_DEFAULT, 0, 0, list);
                    return;
                }
                Section section = new Section(results);
                sdbh.insert(section);
                list = sdbh.getAllSections();
                defaultSectionID = ((Section) list.toArray()[0]).getId();
                defaultSectionName = ((Section) list.toArray()[0]).getTitle();
                context.get().getSharedPreferences(settingsKey, Context.MODE_PRIVATE).edit().putString("section_id", defaultSectionID).apply();
                context.get().getSharedPreferences(settingsKey, Context.MODE_PRIVATE).edit().putString("section_name", defaultSectionName).apply();
                Log.d(LOG_TAG, "[Default Sections] Providing Section from Server");
                sm(CommandID.CMD_GET_SECTIONS_DEFAULT, 1, 0, list);

            } catch (JSONException e) {
                Log.e(LOG_TAG, "[Default Sections] Error response - null returned");
                sm(CommandID.CMD_GET_SECTIONS_DEFAULT, 0, 0, list);
            }
        }
    }

    public boolean getAllAccounts(String dateFilter, boolean forceUpdate) {

        if ((forceUpdate) &&
                (!isInitializedFinished())) {
            Log.e(LOG_TAG, "[Account] Initialization is on progressing.");
            return false;
        }
		/*
		if(!apiAvailableSemaphore.get("getAllAccounts").tryAcquire()){
			return true;
		}
		 */
        new GetAllAccountsTaskThread(defaultSectionID, dateFilter, forceUpdate).start();
        return true;
    }

    private class GetAllAccountsTaskThread extends Thread {

        final String sectionID;
        final String dateFilter;
        final Boolean forceUpdate;

        GetAllAccountsTaskThread(String sectionID, String dateFilter, Boolean forceUpdate) {
            this.sectionID = sectionID;
            this.dateFilter = dateFilter;
            this.forceUpdate = forceUpdate;
        }

        @Override
        public void run() {

            if ((!forceUpdate) &&
                    adbh.hasData()) {

                Collection<Account> list = filterOutAccounts();
                Log.d(LOG_TAG, "[Account] Providing FILTERRED GetAllAccountsTaskThread from Cache!!!");
                sm(CommandID.CMD_GET_ACCOUNT_ALL, 1, 0, list);
                return;
            }

            Collection<Account> list = new ArrayList<>();

            if (null == sectionID ||
                    sectionID.isEmpty()) {
                Log.e(LOG_TAG, "[Account] Initialization is on progressing !!!");
                sm(CommandID.CMD_GET_ACCOUNT_ALL, 0, 0, list);
                return;
            }

            String path = "?section_id=" + sectionID;

            try {
                JSONObject json = invokeRESTAPI(HTTPMethod.GET, Path.ACCOUNT_ALL + path, "");
                if (null == json) {
                    Log.e(LOG_TAG, "[Account] Error response - null returned");
                    sm(CommandID.CMD_GET_ACCOUNT_ALL, 0, 0, list);
                    return;
                }

                if (!json.get("code").toString().startsWith("2")) {
                    Log.e(LOG_TAG, "[Account] Error response - " + json.get("message").toString());
                    sm(CommandID.CMD_GET_ACCOUNT_ALL, 0, 0, list);
                    return;
                }

                setRemainedAPICall(json.getString("rest_of_api"));
                JSONObject results = json.getJSONObject("results");
                int seq = 1;

                Iterator<String> iterator = results.keys();
                while (iterator.hasNext()) {
                    String type = iterator.next();

                    JSONArray accountType = results.getJSONArray(type);

                    for (int i = 0; i < accountType.length(); i++) {

                        JSONObject account = accountType.getJSONObject(i);

                        Account newlyAccount = new Account(type, account);
                        newlyAccount.setSeq(seq++);
                        //Log.e(LOG_TAG, "---" + newlyAccount.toString());
                        list.add(newlyAccount);
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "[Account] Failed - GetAllAccountsTaskThread!!!");
                e.printStackTrace();
                sm(CommandID.CMD_GET_ACCOUNT_ALL, 0, 0, list);
                return;
            }

            if (adbh.getAllAccounts().size() != list.size()) {
                Log.e(LOG_TAG, "[Account] account is add/removed!!!");
                adbh.clean();
            }
            adbh.insert(list);

            {
                Collection<Account> filtredList = filterOutAccounts();
                Log.d(LOG_TAG, "[Account] Providing GetAllAccountsTaskThread from Server!!!");
                sm(CommandID.CMD_GET_ACCOUNT_ALL, 1, 0, filtredList);
            }

        }

        private synchronized Collection<Account> filterOutAccounts() {

            Collection<Account> accountList = adbh.getAllAccounts();
            Collection<Account> list = new ArrayList<>();
            Date itemDate;

            /*
            for (Account acc : accountList) {
                Log.e(LOG_TAG, "Account = " + acc.getId() +", " + acc.getTitle());
            }
            */

            try {
                sdf.setLenient(false);
                itemDate = sdf.parse(dateFilter);
            } catch (Exception e) {
                Log.d(LOG_TAG, "[Account] Filtering failed !!! date fileter=" + dateFilter);
                return accountList;
            }

            for (Account item : accountList) {
                String open = item.getOpenedDate();
                String closed = item.getClosedDate();

                try {
                    Date openDate = sdf.parse(open);
                    if (closed.startsWith("2999")) {
                        if (itemDate.getTime() >= openDate.getTime()) {
                            list.add(item);
                        }
                    } else {
                        Date closedDate = sdf.parse(closed);
                        if (itemDate.getTime() >= openDate.getTime() &&
                                itemDate.getTime() <= closedDate.getTime()) {
                            list.add(item);
                        }
                    }
                } catch (Exception e) {
                    Log.d(LOG_TAG, "[Account] Filtering failed !!! with item=" + item.getTitle() + ", open=" + open + ", close=" + closed);
                    list.add(item);
                    continue;
                }
            }
            return list;
        }

    }

    public boolean getStoredEntries() {
        return em.getStoredEntries();
    }

    public Entry getEntry(String entryID) {

        return edbh.getEntry(entryID);
    }

    public boolean getAllEntries(String latestDate, String oldestDate) {

        if (!isInitializedFinished()) {
            Log.e(LOG_TAG, "[getAllEntries] Initialization is on progressing.");
            return false;
        }

        if (!apiAvailableSemaphore.get("getAllEntries").tryAcquire()) {
            return true;
        }

        return em.getAllEntries(defaultSectionID, latestDate, oldestDate);
    }

    public boolean getAllEntries(String latestDate, String oldestDate, int count) {

        if (!isInitializedFinished()) {
            Log.e(LOG_TAG, "[getAllEntries] Initialization is on progressing.");
            return false;
        }

        if (!apiAvailableSemaphore.get("getAllEntries").tryAcquire()) {
            return true;
        }

        return em.getAllEntries(defaultSectionID, latestDate, oldestDate, count);
    }


    public boolean getLatestEntries(int count, boolean noDuplicate) {

        if (!isInitializedFinished()) {
            Log.e(LOG_TAG, "[getLatestEntries] Initialization is on progressing.");
            return false;
        }

        return em.getLatestEntries(defaultSectionID, count, noDuplicate);
    }

    public boolean makeEntry(Long date, Account left, Account right,
                             String title, Double amount, String memo) {
        if (!isInitializedFinished()) {
            Log.e(LOG_TAG, "[makeEntry] Initialization is on progressing.");
            return false;
        }

        return em.makeEntry(defaultSectionID, date, left, right, title, amount, memo);
    }

    public boolean modifyEntry(String entryID, String date, Account left, Account right,
                               String title, Double amount, String memo) {
        if (!isInitializedFinished()) {
            Log.e(LOG_TAG, "[modifyEntry] Initialization is on progressing.");
            return false;
        }

        return em.modifyEntry(defaultSectionID, entryID, date, left, right, title, amount, memo);
    }

    public boolean removeEntry(String entryID) {
        if (!isInitializedFinished()) {
            Log.e(LOG_TAG, "[removeEntry] Initialization is on progressing.");
            return false;
        }

        return em.removeEntry(defaultSectionID, entryID);
    }

    public boolean getFrequentItems() {
        if (!isInitializedFinished()) {
            Log.e(LOG_TAG, "[getFrequentItems] Initialization is on progressing.");
            return false;
        }

        return im.getFrequentItems(defaultSectionID);
    }

    public boolean getLatestItems() {
		/*
		if(!isInitializedFinished()){
			Log.e(LOG_TAG, "[getLatestItems] Initialization is on progressing.");
			return false;
		}
		 */

        return im.getLatestItems(defaultSectionID, false);
    }

    public boolean getLatestItems(boolean forceUpdate) {

        if ((forceUpdate) &&
                (!isInitializedFinished())) {
            Log.e(LOG_TAG, "[getLatestItems] Initialization is on progressing.");
            return false;
        }

        return im.getLatestItems(defaultSectionID, forceUpdate);
    }

    public Item getMonthlyItem(String itemID) {

        midbh.print();
        return midbh.getItem(itemID);
    }

    public boolean getMonthlyItems() {
		/*
		if(!isInitializedFinished()){
			Log.e(LOG_TAG, "[getMonthlyItems] Initialization is on progressing.");
			return false;
		}
		 */

        return im.getMonthlyItems(defaultSectionID, false);
    }

    public boolean getMonthlyItems(boolean forceUpdate) {
        if (!isInitializedFinished()) {
            Log.e(LOG_TAG, "[getMonthlyItems] Initialization is on progressing.");
            return false;
        }

        return im.getMonthlyItems(defaultSectionID, forceUpdate);
    }

    public boolean removeMonthlyItem(String itemID) {
        if (!isInitializedFinished()) {
            Log.e(LOG_TAG, "[removeMonthlyItem] Initialization is on progressing.");
            return false;
        }

        return im.removeMonthlyItem(defaultSectionID, itemID);
    }

    public String getAccountName(String accountCode) {
        String name = "?";

        try {
            Collection<Account> accountList = adbh.getAllAccounts();

            for (Account account : accountList) {
                if (0 == accountCode.compareTo(account.getId())) {
                    return account.getTitle();
                }
            }
        } catch (Exception e) {
            return name;
        }

        return name;
    }


    public boolean getFinancialState(String date, boolean forceUpdate) {
		/*
		if(!isInitializedFinished()){
			return false;
		}
		 */

        if (!apiAvailableSemaphore.get("getFinancialState").tryAcquire()) {
            return true;
        }

        new GetFinancialStateTaskThread(defaultSectionID, date, forceUpdate).start();
        return true;
    }

    private class GetFinancialStateTaskThread extends Thread {

        final String sectionID;
        final String date;
        final boolean forceUpdate;

        GetFinancialStateTaskThread(String sectionID, String date, boolean forceUpdate) {
            this.sectionID = sectionID;
            this.date = date;
            this.forceUpdate = forceUpdate;
        }

        @Override
        public void run() {

            try {

                if ((!forceUpdate) &&
                        asdbh.hasData()) {

                    Collection<AccountState> list;
                    list = asdbh.getAllAccountStates();

                    Log.d(LOG_TAG, "[FState] Providing FILTERRED GetFinancialStateTaskThread from Cache!!!");
                    sm(CommandID.CMD_GET_FINANCIAL_STATE, 1, 0, list);
                    return;
                }

                Collection<AccountState> list = new ArrayList<>();

                if (null == sectionID ||
                        sectionID.isEmpty()) {
                    Log.e(LOG_TAG, "[FState] Initialization is on progressing !!!");
                    sm(CommandID.CMD_GET_FINANCIAL_STATE, 0, 0, list);
                    return;
                }

                String path = "?section_id=" + sectionID;

                if (!date.isEmpty()) {
                    try {
                        sdf.setLenient(false);
                        sdf.parse(date);
                        path += "&end_date=" + date;
                    } catch (Exception e) {
                        e.printStackTrace();
                        path += "&end_date=" + date;
                    }
                }

                try {
                    JSONObject json = invokeRESTAPI(HTTPMethod.GET, Path.FINANCIAL_STATE + path, "");
                    if (null == json) {
                        Log.e(LOG_TAG, "[FState] Error response - null returned");
                        sm(CommandID.CMD_GET_FINANCIAL_STATE, 0, 0, list);
                        return;
                    }

                    if (!json.get("code").toString().startsWith("2")) {
                        Log.e(LOG_TAG, "[FState] Error response - " + json.get("message").toString());
                        sm(CommandID.CMD_GET_FINANCIAL_STATE, 0, 0, list);

                        int code = Integer.parseInt(json.get("code").toString());
                        handleRESTErrorResponse(code);
                        return;
                    }

                    setRemainedAPICall(json.get("rest_of_api").toString());
                    JSONObject results = json.getJSONObject("results");
                    Iterator<String> iterator = results.keys();
                    while (iterator.hasNext()) {
                        String type = iterator.next();

                        JSONObject accountType = results.getJSONObject(type);

                        Iterator<String> iterName = accountType.keys();
                        while (iterName.hasNext()) {
                            String name = iterName.next();

                            if (0 == name.compareTo("accounts")) {
                                JSONArray rows = accountType.getJSONArray(name);

                                for (int i = 0; i < rows.length(); i++) {

                                    JSONObject row = rows.getJSONObject(i);
                                    AccountState as = new AccountState(row, type);

                                    Account account = adbh.getAccountName(as.getAccountID());
                                    if (null == account) {
                                        continue;
                                    }

                                    //Log.d(LOG_TAG, "---[" + account.getId() + "], " + account.getTitle() + ", date=" +
                                    //		account.getOpenedDate() + " - " + account.getClosedDate());
                                    as.setGroup(0 == account.getType().compareTo("group"));
                                    as.setAccountName(account.getTitle());
                                    as.setSeq(i);
                                    list.add(as);
                                }
                            }

                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "[FState] Failed - GetFinancialStateTaskThread!!!");
                    e.printStackTrace();
                    sm(CommandID.CMD_GET_FINANCIAL_STATE, 0, 0, list);
                    return;
                }

                if (asdbh.getAllAccountStates().size() != list.size()) {
                    Log.e(LOG_TAG, "[FState] FinancialState is updated!!!");
                    asdbh.clean();
                }
                asdbh.insert(list);

                Log.d(LOG_TAG, "[FState] Providing GetFinancialStateTaskThread from Server!!!");
                sm(CommandID.CMD_GET_FINANCIAL_STATE, 1, 0, list);

            } finally {
                apiAvailableSemaphore.get("getFinancialState").release();
            }
        }

    }


    public boolean getIncomeAndExpense(String startDate, String endDate, boolean forceUpdate) {
		/*
		if(!isInitializedFinished()){
			return false;
		}
		 */

        if (!apiAvailableSemaphore.get("getIncomeAndExpense").tryAcquire()) {
            return true;
        }

        new GetIncomeAndExpenseTaskThread(defaultSectionID, startDate, endDate, forceUpdate).start();
        return true;
    }

    private class GetIncomeAndExpenseTaskThread extends Thread {

        final String sectionID;
        final String startDate;
        final String endDate;
        final boolean forceUpdate;

        GetIncomeAndExpenseTaskThread(String sectionID, String startDate, String endDate, boolean forceUpdate) {
            this.sectionID = sectionID;
            this.startDate = startDate;
            this.endDate = endDate;
            this.forceUpdate = forceUpdate;
        }

        @Override
        public void run() {


            try {


                if ((!forceUpdate) &&
                        iedbh.hasData()) {

                    Collection<AccountState> list;
                    list = iedbh.getAllAccountStates();

                    Log.d(LOG_TAG, "[InE] Providing FILTERRED GetIncomeAndExpenseTaskThread from Cache!!!");
                    sm(CommandID.CMD_GET_INCOME_AND_EXPENSE, 1, 0, list);
                    return;
                }

                Collection<AccountState> list = new ArrayList<>();

                if (null == sectionID ||
                        sectionID.isEmpty()) {
                    Log.e(LOG_TAG, "[InE] Initialization is on progressing !!!");
                    sm(CommandID.CMD_GET_INCOME_AND_EXPENSE, 0, 0, list);
                    return;
                }

                String path = "?section_id=" + sectionID;

                try {
                    sdf.setLenient(false);
                    sdf.parse(startDate);
                    path += "&start_date=" + startDate;
                } catch (Exception e) {
                    e.printStackTrace();
                    path += "&start_date=" + startDate;
                }

                try {
                    sdf.setLenient(false);
                    sdf.parse(endDate);
                    path += "&end_date=" + endDate;
                } catch (Exception e) {
                    e.printStackTrace();
                    path += "&end_date=" + endDate;
                }

                try {
                    JSONObject json = invokeRESTAPI(HTTPMethod.GET, Path.INCOME_AND_EXPENSE + path, "");
                    if (null == json) {
                        Log.e(LOG_TAG, "[InE] Error response - null returned");
                        sm(CommandID.CMD_GET_INCOME_AND_EXPENSE, 0, 0, list);
                        return;
                    }

                    if (!json.get("code").toString().startsWith("2")) {
                        Log.e(LOG_TAG, "[InE] Error response - " + json.get("message").toString());
                        sm(CommandID.CMD_GET_INCOME_AND_EXPENSE, 0, 0, list);

                        int code = Integer.parseInt(json.get("code").toString());
                        handleRESTErrorResponse(code);
                        return;
                    }

					/*
	"results" : {
		"income" : {
			"total" : 11621000,
			"total_steady" : 10920000,
			"total_floating" : 701000,
			"accounts" : [
				{
					"account_id" : "x21",
					"money" : 500000
				},
				{
					"account_id" : "x22",
					"money" : 201000
				}
				...
			]
		},
		"expenses" : {
			...
		}
	}
					 */

                    setRemainedAPICall(json.get("rest_of_api").toString());
                    JSONObject results = json.getJSONObject("results");
                    Iterator<String> iterator = results.keys();
                    while (iterator.hasNext()) {
                        String type = iterator.next();

                        JSONObject accountType = results.getJSONObject(type);

                        Iterator<String> iterName = accountType.keys();
                        while (iterName.hasNext()) {
                            String name = iterName.next();

							/*
							if(0 == name.toString().compareTo("total")){

							}else if(0 == name.toString().compareTo("total_steady")){


							}else if(0 == name.toString().compareTo("total_floating")){

							}else*/
                            if (0 == name.compareTo("accounts")) {
                                JSONArray rows = accountType.getJSONArray(name);

                                for (int i = 0; i < rows.length(); i++) {

                                    JSONObject row = rows.getJSONObject(i);
                                    AccountState as = new AccountState(row, type);

                                    Account account = adbh.getAccountName(as.getAccountID());
                                    if (null == account) {
                                        continue;
                                    }

                                    //Log.d(LOG_TAG, "---[" + account.getId() + "], " + account.getTitle() + ", date=" +
                                    //		account.getOpenedDate() + " - " + account.getClosedDate());
                                    as.setGroup(0 == account.getType().compareTo("group"));
                                    as.setAccountName(account.getTitle());
                                    as.setSeq(i);
                                    list.add(as);
                                }
                            }

                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "[InE] Failed - GetIncomeAndExpenseTaskThread!!!");
                    e.printStackTrace();
                    sm(CommandID.CMD_GET_INCOME_AND_EXPENSE, 0, 0, list);
                    return;
                }

                if (iedbh.getAllAccountStates().size() != list.size()) {
                    Log.e(LOG_TAG, "[InE] IncomeAndExpense is updated!!!");
                    iedbh.clean();
                }
                iedbh.insert(list);

                Log.d(LOG_TAG, "[InE] Providing GetIncomeAndExpenseTaskThread from Server!!!");
                sm(CommandID.CMD_GET_INCOME_AND_EXPENSE, 1, 0, list);


            } finally {
                apiAvailableSemaphore.get("getIncomeAndExpense").release();
            }

        }

    }


    public boolean getBudget(Boolean isIncome, String startDate, String endDate, boolean forceUpdate) {
		/*
		if(!isInitializedFinished()){
			return false;
		}
		 */

        if (isIncome) {
            if (!apiAvailableSemaphore.get("getIncomeBudget").tryAcquire()) {
                return true;
            }
        } else {
            if (!apiAvailableSemaphore.get("getExpenseBudget").tryAcquire()) {
                return true;
            }
        }

        new GetBudgetTaskThread(defaultSectionID, isIncome, startDate, endDate, forceUpdate).start();
        return true;
    }

    private class GetBudgetTaskThread extends Thread {

        final String sectionID;
        final Boolean isIncome;
        final String startDate;
        final String endDate;
        final boolean forceUpdate;

        GetBudgetTaskThread(String sectionID, Boolean isIncome,
                            String startDate, String endDate, boolean forceUpdate) {
            this.sectionID = sectionID;
            this.isIncome = isIncome;
            this.startDate = startDate;
            this.endDate = endDate;
            this.forceUpdate = forceUpdate;
        }

        @Override
        public void run() {

            try {

                if ((!forceUpdate) &&
                        bddbh.hasData()) {

                    Collection<Budget> list;
                    list = bddbh.getAllBudgets(isIncome);

                    Map<String, Budget> map = new HashMap<>();

                    for (Budget budget : list) {
                        map.put(budget.getAccountID(), budget);
                    }
                    Log.d(LOG_TAG, "[InE] Providing FILTERRED GetBudgetTaskThread from Cache!!! " + isIncome);
                    sm(CommandID.CMD_GET_BUDGET, 1, isIncome ? 1 : 0, map);
                    return;
                }

                Map<String, Budget> map = new HashMap<>();

                if (null == sectionID ||
                        sectionID.isEmpty()) {
                    Log.e(LOG_TAG, "[Budget] Initialization is on progressing !!!");
                    sm(CommandID.CMD_GET_BUDGET, 0, isIncome ? 1 : 0, map);
                    return;
                }

                String path = "?section_id=" + sectionID;

                try {
                    sdf.setLenient(false);
                    sdf.parse(startDate);
                    path += "&start_date=" + startDate;
                } catch (Exception e) {
                    e.printStackTrace();
                    path += "&start_date=" + startDate;
                }

                try {
                    sdf.setLenient(false);
                    sdf.parse(endDate);
                    path += "&end_date=" + endDate;
                } catch (Exception e) {
                    e.printStackTrace();
                    path += "&end_date=" + endDate;
                }

                try {
                    JSONObject json;

                    if (isIncome) {
                        json = invokeRESTAPI(HTTPMethod.GET, Path.BUDGET_INCOME + path, "");

                    } else {
                        json = invokeRESTAPI(HTTPMethod.GET, Path.BUDGET_EXPENSES + path, "");

                    }
                    if (null == json) {
                        Log.e(LOG_TAG, "[Budget] Error response - null returned");
                        sm(CommandID.CMD_GET_BUDGET, 0, isIncome ? 1 : 0, map);
                        return;
                    }

                    if (!json.get("code").toString().startsWith("2")) {
                        Log.e(LOG_TAG, "[Budget] Error response - " + json.get("message").toString());
                        sm(CommandID.CMD_GET_BUDGET, 0, isIncome ? 1 : 0, map);

                        int code = Integer.parseInt(json.get("code").toString());
                        handleRESTErrorResponse(code);
                        return;
                    }

					/*
	"results" : {
		"aggregate" : {
			"total" : {
				"budget" : 9929000,
				"money" : 2399000,
				"remains" : 7530000
			}
			"total_steady" : {
				"budget" : 2983023,
				"money" : 182000,
				"remains" : 2304000
			}
			"total_floating" : {
				"budget" : 6945977,
				"money" : 234000,
				"remains" 44320023
			}
			"accounts" : [
				{
					"account_id" : "x12",
					"budget" : 120000,
					"money" : 234002,
					"remains" : -59203
				}
				{
					"account_id" : "x13",
					"budget" : 120000,
					"money" : 234002,
					"remains" : -59203
				}
				.
				.
				.
			],
			"misc" : {
				"standard" : 2939,
				"possibility" : 29.239892,
				"daily_remains" : 23983,
				"weekly_remains" : 82983,
				"today" : {
					"budget" : 2300,
					"money" : 300,
					"remains" : 2000
				}
			}
		},					 */

                    setRemainedAPICall(json.get("rest_of_api").toString());
                    JSONObject results = json.getJSONObject("results");
                    Iterator<String> iterator = results.keys();
                    while (iterator.hasNext()) {
                        String type = iterator.next();

                        if (0 == type.compareTo("aggregate")) {

                            JSONObject accountType = results.getJSONObject(type);
                            //String category = type.toString();

                            Iterator<String> iterName = accountType.keys();
                            while (iterName.hasNext()) {
                                String name = iterName.next();

                                if (0 == name.compareTo("total")) {

                                    JSONObject rows = accountType.getJSONObject(name);

                                    double budget = 0;
                                    double money = 0;
                                    double remains = 0;

                                    Iterator<String> iterTotalKey = rows.keys();
                                    while (iterTotalKey.hasNext()) {
                                        String totalKey = iterTotalKey.next();

                                        Long totalValue = Long.parseLong("" + rows.get(totalKey));

                                        if (0 == totalKey.compareTo("budget")) {
                                            budget = totalValue;
                                        } else if (0 == totalKey.compareTo("money")) {
                                            money = totalValue;
                                        } else if (0 == totalKey.compareTo("remains")) {
                                            remains = totalValue;
                                        }
                                    }

                                    Budget bd = new Budget(Budget.SUMMARYACCOUNTID, budget, money, remains, Budget.TYPE_ACCOUNT);
                                    map.put(Budget.SUMMARYACCOUNTID, bd);

									/*
							}else if(0 == name.toString().compareTo("total_steady")){


							}else if(0 == name.toString().compareTo("total_floating")){
									 */
                                } else if (0 == name.compareTo("accounts")) {
                                    JSONArray rows = accountType.getJSONArray(name);

                                    for (int i = 0; i < rows.length(); i++) {

                                        JSONObject row = rows.getJSONObject(i);
                                        Budget bd = new Budget(row);

                                        Account account = adbh.getAccountName(bd.getAccountID());
                                        if (null != account) {
                                            if (0 == account.getType().compareTo("group")) {
                                                bd.setType(Budget.TYPE_GROUP);
                                            }
                                        }

                                        map.put(bd.getAccountID(), bd);
                                    }
                                }

                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "[Budget] Failed - GetBudgetTaskThread!!!");
                    e.printStackTrace();
                    sm(CommandID.CMD_GET_BUDGET, 0, isIncome ? 1 : 0, map);
                    return;
                }

                bddbh.insert(map);

                Log.d(LOG_TAG, "[Budget] Providing GetBudgetTaskThread from Server!!!");
                sm(CommandID.CMD_GET_BUDGET, 1, isIncome ? 1 : 0, map);


            } finally {
                if (isIncome) {
                    apiAvailableSemaphore.get("getIncomeBudget").release();
                } else {
                    apiAvailableSemaphore.get("getExpenseBudget").release();
                }
            }
        }

    }


    public boolean postNews(String subject, String contents) {

        if (subject.isEmpty() ||
                contents.isEmpty()) {
            return false;
        }

        if (!apiAvailableSemaphore.get("postNews").tryAcquire()) {
            return true;
        }

        new PostNewsTaskThread(subject, contents).start();
        return true;
    }

    private class PostNewsTaskThread extends Thread {

        final String subject;
        final String contents;

        PostNewsTaskThread(String subject, String contents) {
            this.subject = subject;
            this.contents = contents;
        }

        @Override
        public void run() {

            try {

                String postingContent;
                String formatNewsPost = "subject=%s&contents=%s";

                postingContent = String.format(formatNewsPost, TextUtils.htmlEncode(subject), TextUtils.htmlEncode(contents));

                try {
                    JSONObject json = invokeRESTAPI(HTTPMethod.POST, Path.MONEYNEWS, postingContent);
                    if (null == json) {
                        Log.e(LOG_TAG, "[PostNews] Error response - null returned");
                        sm(CommandID.CMD_POST_NEWS, 0, 0, "");
                        return;
                    }

                    if (!json.optString("code").startsWith("2")) {
                        Log.e(LOG_TAG, "[PostNews] Error response - " + json.get("message").toString());
                        sm(CommandID.CMD_POST_NEWS, 0, 0, "");

                        int code = Integer.parseInt(json.get("code").toString());
                        handleRESTErrorResponse(code);
                        return;
                    }

                } catch (Exception e) {
                    Log.e(LOG_TAG, "[PostNews] Failed - PostNewsTaskThread!!!");
                    e.printStackTrace();
                    sm(CommandID.CMD_POST_NEWS, 0, 0, "");
                    return;
                }
                Log.d(LOG_TAG, "[PostNews] Posted News!!!");
                sm(CommandID.CMD_POST_NEWS, 1, 0, "");

            } finally {
                apiAvailableSemaphore.get("postNews").release();
            }
        }

    }


    public boolean postPayments(String message, String sender) {

        Log.e(LOG_TAG, "[PostPayments] start");

        if (message.isEmpty() ||
                sender.isEmpty()) {
            return false;
        }

        if (!apiAvailableSemaphore.get("postPayments").tryAcquire()) {
            return true;
        }

        new PostPaymentsTaskThread(defaultSectionID, message, sender).start();
        return true;
    }

    private class PostPaymentsTaskThread extends Thread {

        final String section;
        final String message;
        final String sender;

        PostPaymentsTaskThread(String section, String message, String sender) {
            this.section = section;
            this.message = message;
            this.sender = sender;
        }

        @Override
        public void run() {

            try {
                String postingContent = "section_id=";
                postingContent += section;
                postingContent += "&rows=";
                postingContent += TextUtils.htmlEncode(message);

                try {
                    JSONObject json = invokeRESTAPI(HTTPMethod.POST, Path.POST_PAYMENT, postingContent);
                    if (null == json) {
                        Log.e(LOG_TAG, "[PostPayments] Error response - null returned");
                        sm(CommandID.CMD_POST_PAYMENTS, 0, 0, "");
                        return;
                    }

                    if (!json.optString("code").startsWith("2")) {
                        Log.e(LOG_TAG, "[PostPayments] Error response - " + json.get("message").toString());
                        sm(CommandID.CMD_POST_PAYMENTS, 0, 0, "");

                        int code = Integer.parseInt(json.get("code").toString());
                        if (400 == code) {
                            Log.e(LOG_TAG, "[PostPayments] Unrecognized message, reporting it!");
                            invokeRESTAPI(HTTPMethod.POST, Path.REPORT_FAILED_PAYMENT, sender + "\n" + postingContent);
                        }
						/*
						// handleRESTErrorResponse can be show a dialog, so it should be avoided.
						else{
							handleRESTErrorResponse(code);	
						}
						 */
                        return;
                    }

                } catch (Exception e) {
                    Log.e(LOG_TAG, "[PostPayments] Failed - PostNewsTaskThread!!!");
                    e.printStackTrace();
                    sm(CommandID.CMD_POST_PAYMENTS, 0, 0, "");
                    return;
                }
                Log.d(LOG_TAG, "[PostPayments] Posted Payments!!!");
                sm(CommandID.CMD_POST_PAYMENTS, 1, 0, "");

            } finally {
                apiAvailableSemaphore.get("postPayments").release();
            }
        }
    }


    @Override
    public Integer getRemainedAPICall() {
        return countOfRemainedAPICall;
    }

    @Override
    public void setRemainedAPICall(String count) {

        try {
            countOfRemainedAPICall = Integer.parseInt(count);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(LOG_TAG, "Remained API call count = " + countOfRemainedAPICall);
    }

    @Override
    public ItemDBHandler getLatestItemDBHandler() {
        return lidbh;
    }

    @Override
    public EntryDBHandler getEntryDBHandler() {
        return edbh;
    }

    @Override
    public ItemDBHandler getMonthlyItemDBHandler() {
        return midbh;
    }

    private String getProfilePath() {

        String filename = this.userID;
        if (filename.contains("@")) {
            filename = filename.replace("@", "_");
        }

		/*
		String packageName = context.get().getPackageName();
		File externalPath = Environment.getExternalStorageDirectory();		

		String storagePath = externalPath.getAbsolutePath() +
                "/Android/data/" + packageName + "/files";
		 */
        File filePath = context.get().getFilesDir();
        String storagePath = filePath.getAbsolutePath();

        if (!storagePath.endsWith("/")) {
            storagePath += "/";
        }

        return storagePath + filename + ".bin";
    }

    public Bitmap getProfilePicture() {
        try {
            Bitmap b = DrawableBitmapCache.getBitmap(getProfilePath(), 80, 80);
            if (b == null)
                return DrawableBitmapCache.getBitmap(R.drawable.user);
            else
                return ImageUtils.getCircleBitmap(b);
            //return b;
        } catch (Exception e) {
            return DrawableBitmapCache.getBitmap(R.drawable.user);
        } catch (Error e) {
            return DrawableBitmapCache.getBitmap(R.drawable.user);
        }
    }

    private class ProfileDownloadTaskThread extends Thread {

        private final String url;

        ProfileDownloadTaskThread(String url) {
            this.url = url;
        }

        public void run() {

            String sPath = getProfilePath();
            if (url.isEmpty() || url.equals("")) {
                Log.e(LOG_TAG, "Invalid Profile Image");
                return;
            }

            if (RemoteContent.getInstance().download(url, sPath, true)) {
                Log.d(LOG_TAG, "Profile Image download is succeed!!!");
            } else {
                Log.e(LOG_TAG, "Profile Image download is failed!!!");
            }

            sm(CommandID.CMD_PROFILE_PICTURE_UPDATED, 1, 0, "");
        }
    }
}
