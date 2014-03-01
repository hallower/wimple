package kr.blogspot.charlie0301.impl.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;

public class WidgetItem {

	public static void recycleBitmapOfImageView(ImageView iv, boolean recycle) {
		if (null == iv) {
			return;
		}

		if (true == recycle) {
			Drawable drawable = iv.getDrawable();
			if (drawable instanceof BitmapDrawable) {
				Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
				if (null != bitmap) {
					bitmap.recycle();
					bitmap = null;
				}
			}
		}
	}

	public static void replaceBitmapOfImageView(ImageView iv, Bitmap bm,
			boolean recycle) {
		if (null == iv) {
			return;
		}

		if (true == recycle) {
			Drawable drawable = iv.getDrawable();
			if (drawable instanceof BitmapDrawable) {
				Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
				if (null != bitmap) {
					bitmap.recycle();
					bitmap = null;
				}
			}
		}

		iv.setImageBitmap(bm);
	}

	@SuppressWarnings("deprecation")
	public static void recycleRecursive(View root) {
		if (null == root)
			return;

		root.setBackgroundDrawable(null);

		if (root instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) root;
			int count = group.getChildCount();
			for (int i = 0; i < count; i++) {
				recycleRecursive(group.getChildAt(i));
			}

			if (!(root instanceof AdapterView<?>)) {
				group.removeAllViews();
			}
		}

		if (root instanceof ImageView) {
			((ImageView) root).setImageDrawable(null);
		}

		root = null;
		return;
	}

	public static int[] predefinedColors = new int[] { 
		Color.rgb(0x00, 0x5C, 0xA9), Color.rgb(0x13, 0x86, 0xC8), Color.rgb(0x31, 0xBC, 0xEC), Color.rgb(0x80, 0xDF, 0xF8),
		Color.rgb(0xBD, 0xF8, 0xFF), Color.rgb(0x7F, 0xC4, 0x12), Color.rgb(0xEA, 0xC5, 0x0F), Color.rgb(0xE5, 0x51, 0x10),
		Color.rgb(0xC6, 0x14, 0x6A), Color.rgb(0x62, 0x00, 0xA8)
	}; 

}
