package kr.blogspot.charlie0301.wimple.widget

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.widget.DatePicker
import android.widget.TextView
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

class DatePickerFragment : androidx.fragment.app.DialogFragment(), DatePickerDialog.OnDateSetListener {

    var selectedDate: Long
        private set
    private var year: Int = 0
    private var month: Int = 0
    private var day: Int = 0

    var isDateChanged = false
        private set

    private var tv: WeakReference<TextView>? = null
    private var listener: OnDateSetListener? = null

    interface OnDateSetListener {
        fun onDateSet(date: Long?)
    }

    fun setOnDateSetListener(listener: OnDateSetListener) {
        this.listener = listener
    }

    init {
        // Use the current date as the default date in the picker
        val c = Calendar.getInstance()
        selectedDate = c.timeInMillis
        setDate(selectedDate)
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return DatePickerDialog(context, this, year, month, day)
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, day: Int) {
        isDateChanged = true

        this.year = year
        this.month = month
        this.day = day

        val cal = Calendar.getInstance()
        cal.set(year, month, day)
        this.selectedDate = cal.timeInMillis

        setWidgetText(false)

        if (null != this.listener) {
            this.listener!!.onDateSet(this.selectedDate)
        }
    }

    fun setDate(date: Long?) {
        isDateChanged = false

        val cal = Calendar.getInstance()
        cal.time = Date(date!!)
        this.selectedDate = cal.timeInMillis
        this.year = cal.get(Calendar.YEAR)
        this.month = cal.get(Calendar.MONTH)
        this.day = cal.get(Calendar.DAY_OF_MONTH)

        if (this.year == Calendar.getInstance().get(Calendar.YEAR) &&
                this.month == Calendar.getInstance().get(Calendar.MONTH) &&
                this.day == Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) {
            setWidgetText(true)
        } else {
            setWidgetText(false)
        }
    }

    fun setTextViewWidget(tv: TextView) {
        this.tv = WeakReference(tv)
        setWidgetText(false)
    }

    private fun setWidgetText(isBold: Boolean) {
        if (null != tv) {
            if (isBold) {
                val sb = SpannableStringBuilder()
                val str = sdf.format(selectedDate)
                sb.append(str)
                sb.setSpan(StyleSpan(Typeface.BOLD), 0, str.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                tv!!.get()!!.text = sb
            } else {
                tv!!.get()!!.text = sdf.format(selectedDate)
            }
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        private val locale = Resources.getSystem().configuration.locale
        private val sdf = SimpleDateFormat("MM-dd E", locale)
    }

}