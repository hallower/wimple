package kr.blogspot.charlie0301.wimple.impl.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;

import java.lang.ref.WeakReference;
import java.util.Date;

public class DrawableBitmapCache {

    private static class BitmapInfo {
        private Bitmap bitmap;
        private int used = 1;
        private Date last = null;

        public BitmapInfo(Bitmap bitmap) {
            super();
            this.bitmap = bitmap;
            this.last = new Date(System.currentTimeMillis());
        }

        public final Bitmap getBitmap() {
            used++;
            last = new Date(System.currentTimeMillis());
            return bitmap;
        }

        public void clearBitmap() {
            this.bitmap.recycle();
            this.bitmap = null;
            used = 0;
        }

        public int getUsed() {
            return used;
        }

        public long getUnusedMilliSeconds() {
            Date now = new Date(System.currentTimeMillis());

            return (last.getTime() - now.getTime());
        }


    }

    ;

    private static final String LOG_TAG = "DrawableBitmapCache";
    private static final SparseArray<BitmapInfo> bitmaps = new SparseArray<BitmapInfo>();
    private static WeakReference<Resources> resources = null;

    public static void clear() {

        int key = 0;
        for (int i = 0; i < bitmaps.size(); i++) {
            key = bitmaps.keyAt(i);
            BitmapInfo bitmap = bitmaps.get(key);
            bitmap.clearBitmap();
        }

        bitmaps.clear();
    }

    public static void clear(String path) {

        int key = 0;
        for (int i = 0; i < bitmaps.size(); i++) {
            key = bitmaps.keyAt(i);

            int hashValue = path.hashCode();

            if (hashValue != key) {
                continue;
            }

            BitmapInfo bitmap = bitmaps.get(key);
            bitmap.clearBitmap();
            bitmaps.delete(key);
            break;
        }
    }

    public static void setResource(Resources resources) {
        if (null != DrawableBitmapCache.resources) {
            DrawableBitmapCache.resources.clear();
            DrawableBitmapCache.resources = null;
        }

        DrawableBitmapCache.resources = new WeakReference<Resources>(resources);
    }

    private static final Bitmap findBitmap(int id) {
        if (0 > bitmaps.indexOfKey(id))
            return null;
        return bitmaps.get(id).getBitmap();
    }

    private static void clearUnusedBitmaps() {
        Log.e(LOG_TAG, "Out of memory, will clear unused memories!!!");
        Log.e(LOG_TAG, "Out of memory, will clear unused memories!!!");
        Log.e(LOG_TAG, "Out of memory, will clear unused memories!!!");

        int key = 0;
        for (int i = 0; i < bitmaps.size(); i++) {
            key = bitmaps.keyAt(i);
            BitmapInfo bitmap = bitmaps.get(key);
            long ms = bitmap.getUnusedMilliSeconds();
            // TODO : calculate proper time.
            if (ms > 500) {
                Log.e(LOG_TAG, "id=" + key + "is recycled");
                bitmaps.delete(key);
            }
        }

        for (int i = 0; i < bitmaps.size(); i++) {
            key = bitmaps.keyAt(i);
            BitmapInfo bitmap = bitmaps.get(key);
            int count = bitmap.getUsed();
            // TODO : calculate proper number of count.
            if (count < 3) {
                Log.e(LOG_TAG, "id=" + key + "is recycled");
                bitmaps.delete(key);
            }
        }
    }

    public static Bitmap getBitmap(int resourceID) {

        if (null == resources) {
            Log.e(LOG_TAG, "Please set Resources first!!!");
            return null;
        }

        {
            Bitmap b = findBitmap(resourceID);

            if (null != b) {
                return b;
            }
        }

        Drawable drawable = null;
        try {
            drawable = DrawableBitmapCache.resources.get().getDrawable(resourceID);
        } catch (OutOfMemoryError e) {
            Log.e(LOG_TAG, "Out of memory, will clear unused memories!!!");
            clearUnusedBitmaps();
            return null;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Invalid Resource ID!!!");
            return null;
        }

        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            BitmapInfo bi = new BitmapInfo(bitmap);
            // insert!!!
            bitmaps.put(resourceID, bi);
            return bitmap;
        }

        int width = drawable.getIntrinsicWidth();
        width = width > 0 ? width : 1;
        int height = drawable.getIntrinsicHeight();
        height = height > 0 ? height : 1;

        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            Log.e(LOG_TAG, "Out of memory, will clear unused memories!!!");
            clearUnusedBitmaps();
            return null;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Invalid bitmap!!!");
            return null;
        }

        if (null == bitmap) {
            Log.e(LOG_TAG, "Invalid bitmap!!!");
            return null;
        }
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        // insert!!!
        BitmapInfo bi = new BitmapInfo(bitmap);
        bitmaps.put(resourceID, bi);

        return bitmap;
    }

    public static Bitmap getBitmap(int resourceID, int width, int height) {

        if (null == resources) {
            Log.e(LOG_TAG, "Please set Resources first!!!");
            return null;
        }

        {
            Bitmap b = findBitmap(resourceID);

            if (null != b) {
                return b;
            }
        }

        Drawable drawable = null;
        try {
            drawable = DrawableBitmapCache.resources.get().getDrawable(resourceID);
        } catch (OutOfMemoryError e) {
            Log.e(LOG_TAG, "Out of memory, will clear unused memories!!!");
            clearUnusedBitmaps();
            try {
                drawable = DrawableBitmapCache.resources.get().getDrawable(resourceID);
            } catch (OutOfMemoryError ee) {
                Log.e(LOG_TAG, "Out of memory, oops not enough memory even cleaning!!!");
                return null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Invalid Resource ID!!!");
            return null;
        }

        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            Log.e(LOG_TAG, "Out of memory, will clear unused memories!!!");
            clearUnusedBitmaps();
            try {
                bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            } catch (OutOfMemoryError ee) {
                Log.e(LOG_TAG, "Out of memory, oops not enough memory even cleaning!!!");
                return null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Invalid bitmap!!!");
            return null;
        }

        if (null == bitmap) {
            Log.e(LOG_TAG, "Invalid bitmap!!!");
            return null;
        }
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        // insert!!!
        BitmapInfo bi = new BitmapInfo(bitmap);
        bitmaps.put(resourceID, bi);

        return bitmap;
    }

    public synchronized static Bitmap getBitmap(String path, final int targetWidth, final int targetHeight) {

        if (null == path) {
            Log.e(LOG_TAG, "path is null!");
            return null;
        }

        int hashValue = path.hashCode();

        {
            Bitmap b = findBitmap(hashValue);

            if (null != b) {
                return b;
            }
        }

        Bitmap bitmap = null;

        try {
            bitmap = LocalFile.setImage(path, targetWidth, targetHeight);
        } catch (OutOfMemoryError e) {
            Log.e(LOG_TAG, "Out of memory, will clear unused memories! [" + path + "]");
            clearUnusedBitmaps();
            return null;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception: " + e.toString() + " [" + path + "]");
            return null;
        }

        if (null == bitmap) {
            Log.e(LOG_TAG, "image file processing error! [" + path + "]");
            return null;
        }

        // insert!!!
        BitmapInfo bi = new BitmapInfo(bitmap);
        bitmaps.put(hashValue, bi);
        return bitmap;
    }


    public static int convertDpToPixels(Context context, int nDP) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return nDP * (metrics.densityDpi / 160);
    }
}
