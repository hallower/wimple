package me.blog.imhallower.wimple.impl.util;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
}
