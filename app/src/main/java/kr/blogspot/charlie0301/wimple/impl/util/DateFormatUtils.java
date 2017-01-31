package kr.blogspot.charlie0301.wimple.impl.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateFormatUtils {

	private static final Locale locale = new Locale("ko", "KR");
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", locale);
	private static final SimpleDateFormat sdfGUI = new SimpleDateFormat("yy-MM-dd E", locale);
	private static final SimpleDateFormat sdfDB = new SimpleDateFormat("yyyy-MM-dd", locale);
	private static final SimpleDateFormat sdfSMS = new SimpleDateFormat("MM/dd HH:mm", locale);
	private static final NumberFormat nf = NumberFormat.getCurrencyInstance(locale);	
	private static final DecimalFormat formatCalcNum = (DecimalFormat)nf;
	private static final NumberFormat nf2 = NumberFormat.getCurrencyInstance(locale);	
	private static final DecimalFormat formatCalcNumNoPoint = (DecimalFormat)nf2;

	static {
		formatCalcNum.applyPattern("###,###.####");
		formatCalcNumNoPoint.applyPattern("###,###");
	}

	public static final Locale getDefaultLocale(){
		return locale;
	}

	public static final SimpleDateFormat getServerDateFormat(){
		return sdf;
	}

	public static final SimpleDateFormat getGUIDateFormat(){
		return sdfGUI;
	}

	public static final SimpleDateFormat getDBDateFormat(){
		return sdfDB;
	}

	public static final SimpleDateFormat getSMSDateFormat(){
		return sdfSMS;
	}
	
	public static final NumberFormat getNumberFormat(){
		return nf;
	}

	public static final DecimalFormat getDecimalFormat(){
		return formatCalcNum;
	}
	
	public static final DecimalFormat getNoPointDecimalFormat(){
		return formatCalcNumNoPoint;
	}

	public static final String getCurrentDateString(){
		Long today = Calendar.getInstance().getTimeInMillis();
		return getServerDateFormat().format(today);
	}
	
	public static final String getCurrentDateStringForSMS(){
		Long today = Calendar.getInstance().getTimeInMillis();
		return sdfSMS.format(today);
	}

	public static final String getServerDateString(Long date){		
		return getServerDateFormat().format(date);
	}

	public static final String getServerDateString(String today){
		Calendar cal = Calendar.getInstance();

		if(!today.isEmpty()){
			try{
				String dateString = today;
				int pos = dateString.indexOf(".");
				if(pos > 0){
					dateString = dateString.substring(0, pos);					
				}

				Date date = DateFormatUtils.getServerDateFormat().parse(dateString);
				cal.setTime(date);

			} catch (Exception e) {
			}
		}

		return getServerDateString(cal.getTimeInMillis());
	}
	
	public static final String getServerDateString(String today, int days){
		Calendar cal = Calendar.getInstance();

		if(!today.isEmpty()){
			try{
				String dateString = today;
				int pos = dateString.indexOf(".");
				if(pos > 0){
					dateString = dateString.substring(0, pos);					
				}

				Date date = DateFormatUtils.getServerDateFormat().parse(dateString);
				cal.setTime(date);

			} catch (Exception e) {
			}
		}
		cal.add(Calendar.DAY_OF_MONTH, days);
		return getServerDateString(cal.getTimeInMillis());
	}

	public static final String getYesterdayDateString(Long today){
		Calendar cal = Calendar.getInstance();

		if(today != 0L){
			cal.setTime(new Date(today));	
		}

		cal.add(Calendar.DAY_OF_MONTH, -1);

		return getServerDateString(cal.getTimeInMillis());
	}

	public static final String getLastMonthDateString(Long today){
		Calendar cal = Calendar.getInstance();

		if(today != 0L){
			cal.setTime(new Date(today));	
		}

		cal.add(Calendar.MONTH, -1);

		return getServerDateString(cal.getTimeInMillis());
	}

	public static final String getLastMonthDateString(String today){
		Calendar cal = Calendar.getInstance();

		if(!today.isEmpty()){
			try{
				String dateString = today;
				int pos = dateString.indexOf(".");
				if(pos > 0){
					dateString = dateString.substring(0, pos);					
				}

				Date date = DateFormatUtils.getServerDateFormat().parse(dateString);
				cal.setTime(date);

			} catch (Exception e) {
			}
		}

		cal.add(Calendar.MONTH, -1);
		return getServerDateString(cal.getTimeInMillis());
	}

	public static final Long getDifferenceDays(Long date){
		Calendar thatDay = Calendar.getInstance();
		thatDay.setTime(new Date(date));
		Calendar today = Calendar.getInstance();

		long diff = thatDay.getTimeInMillis() - today.getTimeInMillis();
		return (diff / (24 * 60 * 60 * 1000));
	}


}
