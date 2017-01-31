package kr.blogspot.charlie0301.wimple.impl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Bitmap.Config;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;



public class LocalFile {

	private static final String LOG_TAG = "LocalFile";
	private static final LocalFile INSTANCE = new LocalFile();

	public static LocalFile getInstance(){
		return INSTANCE;
	}

	private LocalFile(){
	}	

	public static boolean isExist(String path){
		File file = new File(path);
		return file.isFile();		
	}
	
	public static String getRealImagePath (Context context, Uri uriPath)
	{
		String path = "";
		Cursor cursor = null;
		
		try{
			cursor = context.getContentResolver().query(uriPath, null, null, null, null);
			cursor.moveToNext();
			path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
			
		}catch(Exception e){
			e.printStackTrace();
			return "";
		} finally {
			if(null != cursor){
				cursor.close();	
			}			
		}

		return path;
	}

	public static boolean copy(String source, String target) {
		File sourceFile = new File( source );
		FileInputStream inputStream = null;
		FileOutputStream outputStream = null;
		FileChannel fcin = null;
		FileChannel fcout = null;

		try {

			inputStream = new FileInputStream(sourceFile);
			outputStream = new FileOutputStream(target);

			fcin = inputStream.getChannel();
			fcout = outputStream.getChannel();

			long size = fcin.size();
			fcin.transferTo(0, size, fcout);

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			try{
				fcout.close();
			}catch(Exception ioe){}
			try{
				fcin.close();
			}catch(Exception ioe){}
			try{
				outputStream.close();
			}catch(Exception ioe){}
			try{
				inputStream.close();
			}catch(Exception ioe){}
		}
		return true;
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	public static Bitmap loadBackgroundBitmap(Context context,
			String imgFilePath) throws Exception, OutOfMemoryError {

		File f = new File(imgFilePath);

		if (!f.isFile()) {
			Log.e(LOG_TAG, "Can't frind the image : " + imgFilePath);
			throw new FileNotFoundException("Can't frind the image : " + imgFilePath);
		}

		Display display = ((WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		int displayWidth = size.x;
		int displayHeight = size.y;

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Config.RGB_565;
		options.inJustDecodeBounds = true;
		Bitmap bm = BitmapFactory.decodeFile(imgFilePath, options);

		float widthScale = options.outWidth / displayWidth;
		float heightScale = options.outHeight / displayHeight;
		float scale = widthScale > heightScale ? widthScale : heightScale;

		if(scale >= 8) {
			options.inSampleSize = 8;
		} else if(scale >= 6) {
			options.inSampleSize = 6;
		} else if(scale >= 4) {
			options.inSampleSize = 4;
		} else if(scale >= 2) {
			options.inSampleSize = 2;
		} else {
			options.inSampleSize = 1;
		}
		options.inJustDecodeBounds = false;

		Bitmap old = bm;
		bm = BitmapFactory.decodeFile(imgFilePath, options);
		recycleIfDifferent(old, bm);
		return bm;
	}
	
	private static void recycleIfDifferent(Bitmap oldbm, Bitmap newbm){
		if(oldbm.hashCode() != newbm.hashCode()){
			try{
				oldbm.recycle();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public static Bitmap setImage(String path, final int targetWidth, final int targetHeight) 
			throws OutOfMemoryError {
		Bitmap bitmap = null;
		Bitmap old = null;
		int orientation_val = 0;

		{
			File file = new File(path);
			if(!file.exists()){
				return null;
			}
		}
		
		try {
			ExifInterface exif = new ExifInterface(path);
			int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
			if (orientation == 6) {
				orientation_val = 90;
			}
			else if (orientation == 3) {
				orientation_val = 180;
			}
			else if (orientation == 8) {
				orientation_val = 270;
			}
		}
		catch (Exception e) {
		}

		try {
			final BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			bitmap = BitmapFactory.decodeFile(path, options);

			int sourceWidth, sourceHeight;
			if (orientation_val == 90 || orientation_val == 270) {
				sourceWidth = options.outHeight;
				sourceHeight = options.outWidth;
			} else {
				sourceWidth = options.outWidth;
				sourceHeight = options.outHeight;
			}

			old = bitmap;
			
			if (sourceWidth > targetWidth || sourceHeight > targetHeight) {
				float widthRatio = (float)sourceWidth / (float)targetWidth;
				float heightRatio = (float)sourceHeight / (float)targetHeight;
				float maxRatio = Math.max(widthRatio, heightRatio);
				options.inJustDecodeBounds = false;
				options.inSampleSize = (int)maxRatio;
				bitmap = BitmapFactory.decodeFile(path, options);
			} else {
				bitmap = BitmapFactory.decodeFile(path);
			}
			
			recycleIfDifferent(old, bitmap);
			old = bitmap;

			if (orientation_val > 0) {
				Matrix matrix = new Matrix();
				matrix.postRotate(orientation_val);
				bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
			}
			
			recycleIfDifferent(old, bitmap);
			old = bitmap;

			sourceWidth = bitmap.getWidth();
			sourceHeight = bitmap.getHeight();
			if (sourceWidth != targetWidth || sourceHeight != targetHeight) {
				float widthRatio = (float)sourceWidth / (float)targetWidth;
				float heightRatio = (float)sourceHeight / (float)targetHeight;
				float maxRatio = Math.max(widthRatio, heightRatio);
				sourceWidth = (int)((float)sourceWidth / maxRatio);
				sourceHeight = (int)((float)sourceHeight / maxRatio);
				bitmap = Bitmap.createScaledBitmap(bitmap, sourceWidth,     sourceHeight, true);
			}
			
			recycleIfDifferent(old, bitmap);
			old = bitmap;
			
		} catch (OutOfMemoryError e){
			throw new OutOfMemoryError();
		} catch (Exception e) {
			// ignore
		}
		return bitmap;
	}
}
