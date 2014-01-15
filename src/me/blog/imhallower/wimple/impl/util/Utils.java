package me.blog.imhallower.wimple.impl.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class Utils {


	public static String sha1(String data) {
		try
		{
			byte[] b = data.getBytes();
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.reset();
			md.update(b);
			byte messageDigest[] = md.digest();
			StringBuilder result = new StringBuilder();
			for (int i = 0; i < messageDigest.length; i++)
			{
				result.append(Integer.toString((messageDigest[i] & 0xff) + 0x100, 16).substring(1));
			}

			return result.toString();

		} catch (NoSuchAlgorithmException e)
		{

			//  Log.e("ARTags", "SHA1 is not a supported algorithm");
		}
		return null;
	}
	
	private static final Locale locale = new Locale("ko", "KR");
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", locale);
	private static final SimpleDateFormat sdfGUI = new SimpleDateFormat("yy-MM-dd E", locale);
	private static final NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
	private static final DecimalFormat formatCalcNum = (DecimalFormat)nf;
	
	static {
		formatCalcNum.applyPattern("###,###.####");
	}

	public static final SimpleDateFormat getServerDateFormat(){
		return sdf;
	}
	
	public static final SimpleDateFormat getGUIDateFormat(){
		return sdfGUI;
	}

	public static final NumberFormat getNumberFormat(){
		return nf;
	}

	public static final DecimalFormat getDecimalFormat(){
		return formatCalcNum;
	}

	public static final String getCurrentDateString(){
		Long today = Calendar.getInstance().getTimeInMillis();
		return getServerDateFormat().format(today);
	}

	public static final String getServerDateString(Long date){		
		return getServerDateFormat().format(date);
	}

	public static final String getLastMonthDateString(Long today){
		Calendar cal = Calendar.getInstance();
		
		if(today != 0L){
			cal.setTime(new Date(today));	
		}
	
		cal.add(Calendar.MONTH, -1);

		return getServerDateString(cal.getTimeInMillis());
	}
}
