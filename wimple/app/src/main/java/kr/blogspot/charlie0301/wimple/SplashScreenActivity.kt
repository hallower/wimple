package kr.blogspot.charlie0301.wimple


import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import kotlinx.android.synthetic.main.activity_splash_screen.*
import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID
import kr.blogspot.charlie0301.wimple.impl.IWimpleResponseListener
import kr.blogspot.charlie0301.wimple.impl.IWimpleStatusListener
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import kr.blogspot.charlie0301.wimple.model.*
import java.util.*
import java.util.concurrent.Executors


class SplashScreenActivity : AppCompatActivity() {

    private val wimple = WimpleImpl.getInstance()
    private lateinit var settings: SharedPreferences

    private var storedTempToken: String = ""
    private var cacheRefreshed = false
    private var isLogInProcessDone = false
    private var isBiometricAuthDone = true

    override fun onResume() {
        settings = applicationContext.getSharedPreferences(WimpleImpl.settingsKey, Context.MODE_PRIVATE)
        Log.i(LOG_TAG, "csk, SplashScreen - onResume!!!")

        super.onResume()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)

        settings = applicationContext.getSharedPreferences(WimpleImpl.settingsKey, Context.MODE_PRIVATE)

        Log.i(LOG_TAG, "SplashScreen - onCreate!!!")

        // double check if app is restarted by force
        val intent = intent
        if (intent.hasExtra("auth_again")) {
            Log.e(LOG_TAG, "Need to do Auth again, clean auth!!!")

            webview.clearHistory()
            webview.clearCache(true)
            webview.loadUrl("about:blank")

            Toast.makeText(applicationContext, applicationContext.resources.getString(R.string.notice_need_auth), Toast.LENGTH_LONG).show()
            wimple.cleanAuth()
        }

        setupHandler()
        setupWimpleImpl()

        sm(CommandID.SHOW_STATUS, applicationContext.resources.getString(R.string.loggin_auth))

        if (wimple.tempToken!!) {

            // Check biometric authentication preference
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val isNeedBiometricAuthentication = sharedPref.getBoolean(SettingsFragment.KEY_BIOMETRIC_OPTION, false)
            if (isNeedBiometricAuthentication) {
                isBiometricAuthDone = false

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(applicationContext.resources.getString(R.string.biometric_title))
                            .setSubtitle(applicationContext.resources.getString(R.string.biometric_sign_in_description))
                            .setNegativeButtonText(applicationContext.resources.getString(R.string.user_cancel))
                            .build()
                    val biometricPrompt = BiometricPrompt(this, Executors.newSingleThreadExecutor(),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationError(errorCode: Int,
                                                                   errString: CharSequence) {
                                    super.onAuthenticationError(errorCode, errString)
                                    runOnUiThread {
                                        // Exit
                                        exitApplication("${applicationContext.resources.getString(R.string.need_biometric_authentication)} ( $errString )")
                                    }
                                }

                                override fun onAuthenticationSucceeded(
                                        result: BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    // Move to main activity
                                    if(isLogInProcessDone)
                                        moveToMain()
                                    isBiometricAuthDone = true
                                }
                            })

                    biometricPrompt.authenticate(promptInfo)

            }

            // Already Logged-in
            val savedSectionID = settings.getString("section_id", null)
            if (null == savedSectionID || savedSectionID.isEmpty()) {
                Log.d(LOG_TAG, "logged in - get default section")
                wimple.getDefaultSections(true)
            } else {
                Log.d(LOG_TAG, "logged in - get all section, section id = $savedSectionID")
                wimple.getAllSections(true)
            }
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        webview.addJavascriptInterface(WebAppInterface(this), "Android")
        webview.webViewClient = UriWebViewClient()
        webview.webChromeClient = WebChromeClient()

        val webSettings = webview.settings
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.setSupportMultipleWindows(true)

        webSettings.javaScriptEnabled = true;
        webSettings.domStorageEnabled = true;
        webSettings.loadWithOverviewMode = true;
        webSettings.useWideViewPort = true;
        webSettings.builtInZoomControls = true;
        webSettings.displayZoomControls = false;
        webSettings.setSupportZoom(true);
        webSettings.defaultTextEncodingName = "utf-8";



    }

    private fun refreshCache() {
        if (this.cacheRefreshed) {
            return
        }
        this.cacheRefreshed = true
        wimple.getAllAccounts(DateFormatUtils.getServerDateFormat().format(Calendar.getInstance().timeInMillis), true)
        wimple.getLatestItems(true)
        wimple.getMonthlyItems(true)
    }

    private fun setupWimpleImpl() {
        wimple.setApplicationContext(applicationContext)
        wimple.setStatusListener(object : IWimpleStatusListener {

            override fun onLoggedIn(status: Boolean) {
                if (status) {
                    sm(CommandID.SHOW_STATUS, applicationContext.resources.getString(R.string.loggin_success))
                    sm(CommandID.WIMPLE_LOGGIN_SUCCESS, "")
                    refreshCache()
                } else {
                    sm(CommandID.SHOW_STATUS, applicationContext.resources.getString(R.string.loggin_failed))
                    sm(CommandID.WIMPLE_LOGGIN_FAILED, "")
                }
            }

            override fun onLoggedOut() {
                sm(CommandID.WIMPLE_LOGGOUT, "")
            }

            override fun onNetworkConnectionEstablished() {}

            override fun onNetworkConnectionLost() {}

            override fun onProfilePictureUpdated() {}

        })

        wimple.setResponseListener(object : IWimpleResponseListener {

            override fun onGetAuthTempToken(status: Boolean, tempToken: String?) {

                if (!status) {
                    exitApplication(applicationContext.resources.getString(R.string.fatal_error))
                    return
                }

                if (null == tempToken || tempToken.isEmpty()) {
                    exitApplication(applicationContext.resources.getString(R.string.fatal_error))
                    return
                }

                storedTempToken = tempToken
                sm(CommandID.GET_PIN, tempToken)
            }

            override fun onGetAuthAccessToken(status: Boolean,
                                              result: Map<String, String>) {

                if (result.isEmpty()) {
                    Log.e(LOG_TAG, "Auth is failed.")
                    exitApplication(applicationContext.resources.getString(R.string.fatal_error))
                    return
                }

                val tokenSecret = result["token_secret"]
                if (null == tokenSecret || tokenSecret.isEmpty()) {
                    Log.e(LOG_TAG, "Auth is failed.")
                    exitApplication(applicationContext.resources.getString(R.string.fatal_error))
                    return
                }

                //sm( CommandID.SHOW_STATUS, applicationContext.resources.getString(R.string.loggin_user_info));

                val savedSectionID = settings.getString("section_id", null)
                if (null == savedSectionID || savedSectionID.isEmpty()) {
                    Log.d(LOG_TAG, "not logged in - get default section")
                    wimple.getDefaultSections(false)
                } else {
                    Log.d(LOG_TAG, "not logged in - get all section, section id = $savedSectionID")
                    wimple.getAllSections(false)
                    //wimple.getDefaultSections(false)
                }
            }

            override fun onGetUserInfoResponseReceived(status: Boolean, info: UserInfo) {
                if (status) {
                    Log.i(LOG_TAG, info.toString())
                }
            }

            override fun onGetAllSectionResponseReceived(status: Boolean, list: Collection<Section>) {
                sm(CommandID.GET_ALL_SECTION_RECEIVED, list)
            }

            override fun onGetAllAccountResponseReceived(status: Boolean, list: Collection<Account>) {
                sm(CommandID.GET_ALL_ACCOUNT_RECEIVED, list)
            }

            override fun onGetEntriesResponseReceived(status: Boolean, list: Collection<Entry>) {
                sm(CommandID.GET_ENTRIES_RECEIVED, list)
            }

            override fun onGetLatestEntriesResponseReceived(status: Boolean, list: Collection<Entry>) {
                sm(CommandID.GET_LATEST_ENTRY_RESPONSE_RECEIVED, status)
            }

            override fun onMakeEntryResponseReceived(status: Boolean, entryDate: String) {
                sm(CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED, status)
            }

            override fun onGetFrequentItemsResponseReceived(status: Boolean,
                                                            list: Collection<Item>) {
                sm(CommandID.GET_FREQUENT_ITEMS_RESPONSE_RECEIVED, list)
            }

            override fun onGetLatestItemsResponseReceived(status: Boolean,
                                                          list: Collection<Item>) {
                sm(CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED, list)
            }

            override fun onModifyEntryResponseReceived(status: Boolean, entry: Entry) {}

            override fun onGetMonthlyItemsResponseReceived(status: Boolean,
                                                           list: ArrayList<Item>) {
            }

            override fun onRemoveEntryResponseReceived(status: Boolean, id: String) {}

            override fun onRemoveMonthlyItemResponseReceived(status: Boolean, id: String) {}

            override fun onGetFinancialStateResponseReceived(status: Boolean,
                                                             list: Collection<AccountState>) {
            }

            override fun onGetIncomeAndExpenseResponseReceived(status: Boolean,
                                                               list: Collection<AccountState>) {
            }

            override fun onGetBudgetResponseReceived(status: Boolean, isIncome: Boolean,
                                                     list: Map<String, Budget>) {
            }

            override fun onPostNewsResponseReceived(status: Boolean, id: String) {}

            override fun onPostPaymentsResponseReceived(status: Boolean) {}

        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if (requestCode == PIN_NUMBER_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                val tempToken = data?.extras!!.getString("temp_token")
                val pin = data.extras!!.getString("pin")

                if (null != tempToken && null != pin) {
                    wimple.getAccessToken(tempToken, pin)
                    return
                }
            }

            // Exit
            exitApplication(applicationContext.resources.getString(R.string.program_exit))
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }


    @SuppressLint("HandlerLeak")
    private fun setupHandler() {
        mainHandler = object : Handler() {

            override fun handleMessage(msg: Message) {

                val command = msg.what
                val obj = msg.obj


                when (command) {

                    CommandID.TOAST_LONG -> Toast.makeText(applicationContext, obj.toString(), Toast.LENGTH_LONG).show()

                    CommandID.TOAST_SHORT -> Toast.makeText(applicationContext, obj.toString(), Toast.LENGTH_SHORT).show()

                    CommandID.FATAL_ERROR -> Toast.makeText(applicationContext, applicationContext.resources.getString(R.string.fatal_error), Toast.LENGTH_LONG).show()

                    CommandID.SHOW_STATUS -> splash_status.text = obj as String

                    CommandID.GET_PIN -> {
                        /*
					Intent intent = new Intent(context, WebViewActivity.class);
					intent.putExtra("temp_token", obj.toString());
					startActivityForResult(intent, PIN_NUMBER_REQUEST);
					 */
                        webview.loadUrl("$target_url?token=$storedTempToken")
                        webview.visibility = View.VISIBLE
                    }

                    CommandID.GET_ALL_ACCOUNT_RECEIVED -> Log.d(LOG_TAG, "All Account Information received!")

                    CommandID.GET_LATEST_ITEMS_RESPONSE_RECEIVED -> Log.d(LOG_TAG, "Latest Items received")

                    CommandID.GET_ALL_SECTION_RECEIVED -> finishedAuthentication()

                    else -> {
                        Log.d(LOG_TAG, "Invalid Command ID=$command")
                    }
                }
                super.handleMessage(msg)
            }


        }
    }

    private fun moveToMain() {
        val intent = Intent(applicationContext, WimpleActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun finishedAuthentication() {
        isLogInProcessDone = true

        sm(CommandID.SHOW_STATUS, applicationContext.resources.getString(R.string.loggin_end))
        if(isBiometricAuthDone){
            moveToMain()
        }else{
            sm(CommandID.SHOW_STATUS, applicationContext.resources.getString(R.string.loggin_wait_for_authentication))
        }
    }

    private fun exitApplication(toastMessage: String) {
        sm(CommandID.TOAST_LONG, toastMessage)
        finish()
    }

    private inner class UriWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, webRequest: WebResourceRequest): Boolean {
            val url = webRequest.url.toString()
            val host = webRequest.url.host
            //Log.d(LOG_TAG, "URL=" + url);
            if (host!!.startsWith(target_url_prefix)) {
                if (url.contains("pin=")) {

                    val startPos = url.indexOf("pin=") + 4
                    val endPos = url.indexOf("&", startPos)
                    val pin: String

                    pin = if (-1 == endPos) {
                        url.substring(startPos, url.length)
                    } else {
                        url.substring(startPos, endPos)
                    }

                    webview.visibility = View.INVISIBLE

                    wimple.getAccessToken(storedTempToken, pin)
                    return true
                }

                if (url.contains("logout")) {
                    // Exit
                    exitApplication(applicationContext.resources.getString(R.string.program_exit))
                }
                return false
            }

            // Otherwise, the link is not for a page on my site, so launch
            // another Activity that handles URLs
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            return true
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler,
                                        error: SslError) {
            Log.d(LOG_TAG, "onReceivedSslError")
            //super.onReceivedSslError(view, handler, error);
        }
    }

    class WebAppInterface(private val mContext: Context) {

        @JavascriptInterface
        fun showToast(toast: String) {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {

        private const val LOG_TAG = "SplashScreenActivity"
        private var mainHandler: Handler? = null

        // WebView for Login
        private const val target_url = "https://whooing.com/app_auth/authorize"
        private const val target_url_prefix = "whooing.com"

        // Data
        private const val PIN_NUMBER_REQUEST = 1379

        fun sm(cmd: Int, msg: Any?) {
            this.mainHandler!!.sendMessage(Message.obtain(this.mainHandler, cmd, 1, 0, msg))
        }
    }
}
