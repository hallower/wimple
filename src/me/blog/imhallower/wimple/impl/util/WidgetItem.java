package me.blog.imhallower.wimple.impl.util;

import android.graphics.Bitmap;
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

}
