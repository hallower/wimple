package kr.blogspot.charlie0301.wimple

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.text.Html
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.Toast
import kr.blogspot.charlie0301.wimple.impl.IWimpleResponseListener
import kr.blogspot.charlie0301.wimple.databinding.ActivityPostNewsBinding // 1. Import the binding class
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.RemoteContent
import kr.blogspot.charlie0301.wimple.model.*
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.*

class PostNewsActivity : AppCompatActivity() {

    private val wimple = WimpleImpl.getInstance()
    private lateinit var binding: ActivityPostNewsBinding

    private inner class DownloadWebPageTask : AsyncTask<String, Void, String>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg urls: String): String {
            var response = ""

            for (url in urls) {

                Log.d(LOG_TAG, "submitted url is $url")

                val lcURL = url.lowercase(Locale.US)
                var targetURL = url
                if (!lcURL.startsWith("http")) {
                    targetURL = url.substring(url.indexOf("http"))
                }
                response = RemoteContent.getInstance().getTitlePartOfPage(targetURL)
            }
            return response
        }

        @SuppressLint("ObsoleteSdkInt")
        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: String) {
            //Log.d(LOG_TAG, "charset = " + charset + ", " + result);

            var startPos = result.indexOf("<title")
            if (startPos < 0) {
                startPos = result.indexOf("<TITLE")
            }
            startPos = result.indexOf(">", startPos + 1)

            var endPos = result.indexOf("</title>")
            if (endPos < 0) {
                endPos = result.indexOf("</TITLE>")
            }

            if (startPos < 0 ||
                    endPos < 0 ||
                    endPos > result.length) {
                Log.d(LOG_TAG, "Invalid web page!!!, Cant get title")
                return
            }

            val exportedTitle = result.substring(startPos + 1, endPos)

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                @Suppress("DEPRECATION")
                showTitleSelectionWindow(Html.fromHtml(exportedTitle).toString())
            } else {
                showTitleSelectionWindow(Html.fromHtml(exportedTitle, Html.FROM_HTML_MODE_LEGACY).toString())
            }

        }
    }

    internal fun showTitleSelectionWindow(exportedTitle: String) {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setMessage(resources.getString(R.string.post_news_set_title) + "\n\n\"" + exportedTitle + "\"")
        alertDialog.setCancelable(false).setPositiveButton("Yes") { _, _ ->
            binding.postNewsSubject.setText(exportedTitle)
        }
        alertDialog.setNegativeButton("No") { dialog, _ ->
            dialog.cancel()
        }

        val alert = alertDialog.create()
        alert.setTitle(resources.getString(R.string.post_news_title_imported))
        //alert.setIcon(R.drawable.icon);
        alert.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 3. Inflate the layout and set the content view
        binding = ActivityPostNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // intent check
        var url: String? = null

        // double check if app is restarted forcedly
        val intent = intent
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if (type.startsWith("text/") || type.startsWith("plain/")) {
                url = intent.getStringExtra(Intent.EXTRA_TEXT)
            }
        }

        if (null == url || url.isEmpty()) {
            Toast.makeText(applicationContext, resources.getString(R.string.post_invalid_news_share_method), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWimpleImpl()

        // Wimple login check
        if (!wimple.isAuthed) {
            Toast.makeText(applicationContext, resources.getString(R.string.program_exit), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Widget
        binding.postNewsUrl.text = url

        val task = DownloadWebPageTask()
        task.execute(url)

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            //if(clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)){
            val item = clipboard.primaryClip!!.getItemAt(0)
            binding.postNewsSubject.setText(item.text)
            //}
        }

        findViewById<View>(R.id.post_news_do_post).setOnClickListener(OnClickListener {
            val escapedSubject: String
            val escapedURL: String
            val escapedComment: String

            try {
                escapedSubject = URLEncoder.encode(binding.postNewsSubject.text.toString(), "UTF-8")
                escapedURL = URLEncoder.encode(binding.postNewsUrl.text.toString(), "UTF-8")
                escapedComment = URLEncoder.encode(binding.postNewsContent.text.toString(), "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                Toast.makeText(applicationContext, resources.getString(R.string.post_invalid_news_url), Toast.LENGTH_SHORT).show()
                finish()
                return@OnClickListener
            }

            var newsContents = escapedURL
            newsContents += " %0A%0A"
            newsContents += escapedComment
            newsContents += " %0A%0A"
            newsContents += " posted by Wimple (https://whooing.com/zS2h)"

            dialog = ProgressDialog.show(this@PostNewsActivity, "",
                    applicationContext.resources.getText(R.string.post_news_wait_for_while), true)

            wimple.postNews(escapedSubject, newsContents)
        })

    }

    private fun setupWimpleImpl() {
        wimple.setApplicationContext(applicationContext)

        wimple.setResponseListener(object : IWimpleResponseListener {
            override fun onGetAuthTempToken(status: Boolean, tempToken: String) {}
            override fun onGetAuthAccessToken(status: Boolean, result: Map<String, String>) {}
            override fun onGetUserInfoResponseReceived(status: Boolean, info: UserInfo) {}
            override fun onGetAllSectionResponseReceived(status: Boolean, list: Collection<Section>) {}
            override fun onGetAllAccountResponseReceived(status: Boolean, list: Collection<Account>) {}
            override fun onGetEntriesResponseReceived(status: Boolean, list: Collection<Entry>) {}
            override fun onGetLatestEntriesResponseReceived(status: Boolean, list: Collection<Entry>) {}
            override fun onMakeEntryResponseReceived(status: Boolean, entryDate: String) {}
            override fun onGetFrequentItemsResponseReceived(status: Boolean, list: Collection<Item>) {}
            override fun onGetLatestItemsResponseReceived(status: Boolean, list: Collection<Item>) {}
            override fun onModifyEntryResponseReceived(status: Boolean, entry: Entry) {}
            override fun onGetMonthlyItemsResponseReceived(status: Boolean, list: ArrayList<Item>) {}
            override fun onRemoveEntryResponseReceived(status: Boolean, id: String) {}
            override fun onRemoveMonthlyItemResponseReceived(status: Boolean, id: String) {}
            override fun onGetFinancialStateResponseReceived(status: Boolean, list: Collection<AccountState>) {}
            override fun onGetIncomeAndExpenseResponseReceived(status: Boolean, list: Collection<AccountState>) {}
            override fun onGetBudgetResponseReceived(status: Boolean, isIncome: Boolean, list: Map<String, Budget>) {}
            override fun onPostNewsResponseReceived(status: Boolean, id: String) {
                dialog?.dismiss()
                dialog = null

                if (status) {
                    Toast.makeText(applicationContext, resources.getString(R.string.post_news_succeed), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(applicationContext, resources.getString(R.string.post_news_failed), Toast.LENGTH_SHORT).show()
                }
                finish()
            }

            override fun onPostPaymentsResponseReceived(status: Boolean) {}
        })
    }

    companion object {
        private const val LOG_TAG = "PostNewsActivity"
        private var dialog: ProgressDialog? = null
    }

}
