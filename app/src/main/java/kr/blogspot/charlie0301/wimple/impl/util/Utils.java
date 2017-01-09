package kr.blogspot.charlie0301.wimple.impl.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import kr.blogspot.charlie0301.wimple.WimpleActivity;
import android.util.TypedValue;

import org.json.JSONObject;

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

	public static String getStringFromInputStream(InputStream is) {
		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();

		String line;
		try {
			br = new BufferedReader(new InputStreamReader(is));
			while (null != (line = br.readLine())) {
				sb.append(line);
			}
		} catch (IOException e) {
			//e.printStackTrace();
		} finally {
			if (null != br) {
				try {
					br.close();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
		}
		return sb.toString();
	}
}
