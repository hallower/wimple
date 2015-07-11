package kr.blogspot.charlie0301.widget;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.widget.DatePicker;
import android.widget.TextView;

public class DatePickerFragment extends DialogFragment
implements DatePickerDialog.OnDateSetListener {

	private static final Locale locale = new Locale("ko", "KR");
	private static final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd E", locale);

	private Long current;
	private int year;
	private int month;
	private int day;
	
	private boolean isDateChanged = false;

	private WeakReference<TextView> tv = null;
	
	 public interface OnDateSetListener {
	        void onDateSet(Long date);
	    }
	 
	private OnDateSetListener listener;
	
	public void setOnDateSetListener(OnDateSetListener listener){
		this.listener = listener;
	}
	
	public DatePickerFragment() {
		super();

		// Use the current date as the default date in the picker
		final Calendar c = Calendar.getInstance();
		current = c.getTimeInMillis();
		setDate(current);
	}


	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		return new DatePickerDialog(getActivity(), this, year, month, day);
	}

	@Override
	public void onDateSet(DatePicker view, int year, int month, int day) {
		isDateChanged = true;
		
		this.year = year;
		this.month = month;
		this.day = day;

		Calendar cal = Calendar.getInstance();
		cal.set(year, month, day);
		this.current = cal.getTimeInMillis();

		setWidgetText(false);
		
		if(null != this.listener){
			this.listener.onDateSet(this.current);
		}
	}
	
	public void setDate(Long date) {
		isDateChanged = false;
		
		Calendar cal = Calendar.getInstance();		
		cal.setTime(new Date(date));		
		this.current = cal.getTimeInMillis();
		this.year = cal.get(Calendar.YEAR);
		this.month = cal.get(Calendar.MONTH);
		this.day = cal.get(Calendar.DAY_OF_MONTH);
		
		if(this.year == Calendar.getInstance().get(Calendar.YEAR) &&
			this.month == Calendar.getInstance().get(Calendar.MONTH) &&
			this.day == Calendar.getInstance().get(Calendar.DAY_OF_MONTH)){
			setWidgetText(true);
		}else{
			setWidgetText(false);
		}
	}

	public Long getSelectedDate(){
		return this.current;    	
	}	

	public int getYear() {
		return year;
	}

	public int getMonth() {
		return month;
	}

	public int getDay() {
		return day;
	}

	public boolean isDateChanged() {
		return isDateChanged;
	}
	
	public void setTextViewWidget(TextView tv){
		this.tv = new WeakReference<TextView>(tv);	
		setWidgetText(false);  
	}

	private void setWidgetText(boolean isBold){
		if(null != tv){						
			if(isBold){
				SpannableStringBuilder sb = new SpannableStringBuilder();
				String str = sdf.format(current);
				sb.append(str);
				sb.setSpan(new StyleSpan(Typeface.BOLD), 0, str.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				tv.get().setText(sb);
			}else{
				tv.get().setText(sdf.format(current));	
			}
		}
	}
	public void setColorOfTextViewWidget(int color){
		this.tv.get().setBackgroundColor(color);
	}
	public void setDrawableOfTextViewWidget(int resID){
		this.tv.get().setBackgroundResource(resID);
	}
}