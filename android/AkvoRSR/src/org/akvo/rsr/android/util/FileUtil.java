
package org.akvo.rsr.android.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;

import org.akvo.rsr.android.R;
import org.akvo.rsr.android.dao.RsrDbAdapter;
import org.akvo.rsr.android.xml.Downloader;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

public class FileUtil {

    public static String TAG = "FileUtil";

    /**
     * Reads a file into a byte array
     * @param file
     * @return the bytes of the file
     */
    public static byte[] readFile(File file) throws IOException {
        // Open file
        RandomAccessFile f = new RandomAccessFile(file, "r");
        try {
            // Get and check length
            long longlength = f.length();
            int length = (int) longlength;
            if (length != longlength)
                throw new IOException("File size >= 2 GB");
            // Read file and return data
            byte[] data = new byte[length];
            f.readFully(data);
            return data;
        } finally {
            f.close();
        }
    }

    public static byte[] readFile(String file) throws IOException {
        return readFile(new File(file));
    }

    /**
     * Get the external app image directory.
     * 
     * @param context The context to use
     * @return The external cache dir
     */
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static File getExternalCacheDir(Context context) {
        if (hasExternalCacheDir()) {
            File cacheDir = context.getExternalCacheDir();
            if (cacheDir != null) {
                return cacheDir;
            }
        }

        // Before Froyo we need to construct the external cache dir ourselves
        // AND it will not be automatically created
        final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }

    /**
     * Get the external app image directory.
     * 
     * @param context The context to use
     * @return The external cache dir
     */
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static File getExternalPhotoDir(Context context) {
        if (hasExternalCacheDir()) {
            File cacheDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (cacheDir != null) {
                return cacheDir;
            }
        }

        // Before Froyo we need to construct the external files dir ourselves
        // AND it will not be automatically created
        final String cacheDir = "/Android/data/" + context.getPackageName() + "/files/Pictures";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }

    /**
     * Checks if OS version has built-in external cache dir method.
     */
    public static boolean hasExternalCacheDir() {
        return hasFroyo();
    }

    public static boolean hasFroyo() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    }

    public static boolean hasGingerbread() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    /* TODO extend as target version advances
    public static boolean hasHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    } 
    public static boolean hasHoneycombMR1() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
    }

    public static boolean hasICS() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }
    public static boolean hasJellyBean() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }
*/
    

    /**
     *  Shows a thumbnail from a URL and a filename
     *  @param imgView where to show it
     *  @param url where to fetch it from
     *  @param fn filename in the image cache
     *  @param projectId non-null if this is a Project
     *  @param updateId non/null if this is an Update
     *  
     *  show different fallback images depending on case:
     *   0 Image good and shown
     *   1 No image set
     *   2 Image not loaded (setting)
     *   3 Image load failed (currently treated as 2, would need to remember fetch sts)
     *   4 Image loaded, but unreadable
     *   5 Image loaded, but cleared from cache (should be treated as 2)
     */
    public static void setPhotoFile(ImageView imgView, String url, String fn, String projectId,
            String updateId) {

        if (url == null) { // not set
            imgView.setImageResource(R.drawable.thumbnail_noimage);
        } else if (fn == null) { // Not fetched
            imgView.setImageResource(R.drawable.thumbnail_load);
            // set tags so we will know what to load on a click
            if (projectId != null || updateId != null) {
                imgView.setTag(R.id.thumbnail_url_tag, url);
                // set one of these, so we can update db:
                if (projectId != null)
                    imgView.setTag(R.id.project_id_tag, projectId);
                if (updateId != null)
                    imgView.setTag(R.id.update_id_tag, updateId);
                // make it clickable
                imgView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DoLateIconLoad((ImageView) v);
                    }
                });
            }
        } else { // in cache, try to display it
            File f = new File(fn);
            if (!f.exists()) { // cache corruption
                imgView.setImageResource(R.drawable.thumbnail_error);
            } else { // have file
                // DialogUtil.infoAlert(this, "Photo returned", "Got a photo");
                // make thumbnail and show it on page
                // shrink to save memory
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(fn, o);
                // The new size we want to scale to
                final int REQUIRED_SIZE = 140;

                // Find the correct scale value. It should be a power of 2.
                int width_tmp = o.outWidth, height_tmp = o.outHeight;
                int scale = 1;
                while (true) {
                    if (width_tmp / 2 < REQUIRED_SIZE
                            || height_tmp / 2 < REQUIRED_SIZE) {
                        break;
                    }
                    width_tmp /= 2;
                    height_tmp /= 2;
                    scale *= 2;
                }

                // Decode with inSampleSize
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = scale;

                Bitmap bm = BitmapFactory.decodeFile(fn, o2);
                if (bm == null) {
                    imgView.setImageResource(R.drawable.thumbnail_error);
                } else {
                    imgView.setImageBitmap(bm);
                }
            }
        }

    }

    
    /*
     *  shrinks an image file (to save upload bandwidth)
     * the quick way, by a power-of-2 integer factor
     * This will lose any EXIF information
     */
    public static boolean shrinkImageFileQuickly(String filename, int maxSize) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filename, o);
        int width_tmp = o.outWidth, height_tmp = o.outHeight;
        if (width_tmp < 0 || height_tmp < 0)
            return false;
        if (width_tmp <= maxSize && height_tmp <= maxSize)
            return true;

        // Find the correct scale value. It should be a power of 2.
        int scale = 1;
        while (true) {
            if (width_tmp <= maxSize &&
                    height_tmp <= maxSize) {
                break;
            }
            width_tmp /= 2;
            height_tmp /= 2;
            scale *= 2;
        }

        // Decode with inSampleSize
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;

        Bitmap bm = BitmapFactory.decodeFile(filename, o2);
        if (bm == null) {
            return false;
        } else {
            // save it back
            try {
                FileOutputStream stream = new FileOutputStream(filename);
                if (bm.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                    stream.close();
                    Log.i(TAG, "Shrunk image by a factor of " + scale);
                    return true;
                }
                return false;
            } catch (IOException e) {
                Log.e(TAG, "Could not write resized image: ", e);
                return false;
            }
        }
    }

    
    /*
     * shrinks an image file so long edge becomes exactly maxSize pixels
     * if already smaller, do nothing unless flag is set
     * 
     * This will lose any EXIF information
     * Rotation will be normalized (if library is well-written)
     */
    public static boolean shrinkImageFileExactly(String filename, int maxSize, boolean alwaysRewrite) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        Bitmap bm = BitmapFactory.decodeFile(filename, o);
        float width = o.outWidth, height = o.outHeight;
        if (bm == null || width < 0 || height < 0)
            return false;
        if (!alwaysRewrite && width <= maxSize && height <= maxSize)
            return true;

        float xFactor;
        if (width > height) {
            xFactor = maxSize / width;
        } else {
            xFactor = maxSize / height;
        }
        if (xFactor > 1.0f) {
            xFactor = 1.0f; // never enlarge
        }

        int nHeight = (int) (xFactor * height); // preserve aspect ratio
        int nWidth = (int) (xFactor * width);

        Bitmap bm2 = Bitmap.createScaledBitmap(bm, nWidth, nHeight, true);
        File file = new File(filename);
        try {
            FileOutputStream ostream = new FileOutputStream(file);
            bm2.compress(Bitmap.CompressFormat.JPEG, 100, ostream);
            ostream.close();
        } catch (Exception e) {
            Log.e(TAG, "Could not write resized image: ", e);
            return false;
        }
        return true;
    }

    /**
     *  fetches and displays a delayed-load icon when clicked
     */
    static void DoLateIconLoad(final ImageView iv) {
        // fetch *must* happen in another thread than main on Android API 11 and later
        new Thread(new Runnable() {
            public void run() {

                try {
                    final String url = (String) iv.getTag(R.id.thumbnail_url_tag);
                    final String pid = (String) iv.getTag(R.id.project_id_tag);
                    final String uid = (String) iv.getTag(R.id.update_id_tag);

                    URL curl = new URL(SettingsUtil.host(iv.getContext()));
                    String directory = FileUtil.getExternalCacheDir(iv.getContext()).toString();

                    if (url == null || (pid == null && uid == null)) {
                        Log.w(TAG, "Insufficient data for late load ");
                    } else {
                        RsrDbAdapter dba = new RsrDbAdapter(iv.getContext());
                        dba.open();
                        final String fn;
                        if (pid != null) {
                            fn = Downloader.httpGetToNewFile(new URL(curl, url), directory, "prj"
                                    + pid + "_");
                            dba.updateProjectThumbnailFile(pid, fn);
                        } else if (uid != null) {
                            fn = Downloader.httpGetToNewFile(new URL(curl, url), directory, "upd"
                                    + uid + "_");
                            dba.updateUpdateThumbnailFile(uid, fn);
                        } else {
                            fn = null;
                        }
                        dba.close();

                        // post UI work back to main thread
                        iv.post(new Runnable() {
                            public void run() {
                                setPhotoFile(iv, url, fn, null, null); // prevent
                                                                       // infinite
                                                                       // recursion
                            }
                        });

                    }
                } catch (Exception e) {
                    iv.post(new Runnable() {
                        public void run() {
                            iv.setImageResource(R.drawable.thumbnail_error); // boo!
                        }
                    });
                    // DialogUtil.errorAlert(ctx,
                    // "Error fetching proj image from URL " + url, e);
                    Log.e(TAG, "DoLateIconLoad Error", e);
                }
            }
        }).start();

    }

    /**
     * counts size of all files in the image cache
     */
    public static long countCacheMB(Context context) {
        File f = getExternalCacheDir(context);
        File[] files = f.listFiles();
        long sizeSum = 0;
        if (files != null) { // dir might not exist
            for (int i = 0; i < files.length; i++) {
                sizeSum += files[i].length();
            }
        }
        return sizeSum / (1024 * 1024);
    }

    /**
     * remove all files in the image cache
     */
    public static void clearCache(Context context) {
        RsrDbAdapter dba = new RsrDbAdapter(context);
        dba.open();
        dba.clearProjectThumbnailFiles();
        dba.clearUpdateThumbnailFiles();
        dba.close();
        File f = getExternalCacheDir(context);
        File[] files = f.listFiles();
        if (files != null) { // dir might not exist
            long sizeSum = 0;
            for (int i = 0; i < files.length; i++) {
                sizeSum += files[i].length();
                files[i].delete();
            }
            DialogUtil.infoAlert(context, "Cache cleared", files.length + " files deleted ("
                    + sizeSum / (1024 * 1024) + " MB)");
        }
    }

}
