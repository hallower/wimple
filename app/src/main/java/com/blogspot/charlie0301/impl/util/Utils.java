package com.blogspot.charlie0301.impl.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.blogspot.charlie0301.WimpleActivity;
import android.util.TypedValue;

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
		} catch (Exception e){
			e.printStackTrace();
		}
		return null;
	}

	public static int getDPSize(int dp){
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, WimpleActivity.context.getResources().getDisplayMetrics());
	}
}
