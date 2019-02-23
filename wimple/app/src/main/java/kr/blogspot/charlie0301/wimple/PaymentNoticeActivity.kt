package kr.blogspot.charlie0301.wimple

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import java.util.*

class PaymentNoticeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_payment_notice)

        val settings = applicationContext.getSharedPreferences(SETTING_KEY, Context.MODE_PRIVATE)
        var value: Long = settings.getLong(NOMORE_TIME, 0)
        if (value != 0L) {
            value -= Calendar.getInstance().timeInMillis
            if (value < 1000 * 60 * 60 * 12) {
                Log.d("PaymentNoticeActivity", "within 12 hours, user clicked no more payment notice activity.")
                PaymentNoticeActivity.noMore = true
                finish()
                return
            }
        }

        findViewById<View>(R.id.fullscreen_go_to_payment).setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(paymentURL)
            startActivity(i)
            finish()
        }

        findViewById<View>(R.id.fullscreen_close).setOnClickListener {
            PaymentNoticeActivity.noMore = true
            //val settings = applicationContext.getSharedPreferences(SETTING_KEY, Context.MODE_PRIVATE)
            settings.edit().putLong(NOMORE_TIME, Calendar.getInstance().timeInMillis).apply()

            finish()
        }
    }

    companion object {

        private const val paymentURL = "https://whooing.com/#account/payment"
        var noMore = false

        private const val SETTING_KEY = "wimple.settings"
        private const val NOMORE_TIME = "nomoretime"
    }

}
