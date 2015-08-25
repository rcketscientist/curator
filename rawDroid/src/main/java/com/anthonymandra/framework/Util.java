/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.anthonymandra.framework;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.content.Meta;
import com.anthonymandra.rawdroid.R;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class containing some static utility methods.
 */
public class Util
{
    private static final String TAG = Util.class.getSimpleName();

    public static boolean hasFroyo()
    {
        // Can use static final constants like FROYO, declared in later versions
        // of the OS since they are inlined at compile time. This is guaranteed behavior.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    }

    public static boolean hasGingerbread()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    public static boolean hasHoneycomb()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean hasHoneycombMR1()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
    }

    public static boolean hasIceCreamSandwich()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public static boolean hasJellyBean()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    public static boolean hasKitkat()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    public static boolean hasLollipop()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * Get a usable cache directory (external if available, internal otherwise).
     *
     * @param context    The context to use
     * @param uniqueName A unique directory name to append to the cache dir
     * @return The cache dir
     */
    public static File getDiskCacheDir(Context context, String uniqueName)
    {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        File cache = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !isExternalStorageRemovable())
        {
            cache = context.getExternalCacheDir();
        }
        if (cache == null)
            cache = context.getCacheDir();

        return new File(cache, uniqueName);
    }

    /**
     * Check if external storage is built-in or removable.
     *
     * @return True if external storage is removable (like an SD card), false otherwise.
     */
    @TargetApi(9)
    public static boolean isExternalStorageRemovable()
    {
        if (Util.hasGingerbread())
        {
            return Environment.isExternalStorageRemovable();
        }
        return true;
    }

    /**
     * Check how much usable space is available at a given path.
     *
     * @param path The path to check
     * @return The space available in bytes
     */
    @TargetApi(9)
    public static long getUsableSpace(File path)
    {
        if (Util.hasGingerbread())
        {
            return path.getUsableSpace();
        }
        final StatFs stats = new StatFs(path.getPath());
        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
    }

    public static String swapExtention(String filename, String ext)
    {
        return filename.replaceFirst("[.][^.]+$", "") + ext;
    }

    public static boolean isTabDelimited(String filepath)
    {
        int numberOfLevels = 0;
        BufferedReader readbuffer = null;
        try
        {
            readbuffer = new BufferedReader(new FileReader(filepath));
            String line;
            while ((line = readbuffer.readLine()) != null)
            {
                String tokens[] = line.split("\t");
                numberOfLevels = Math.max(numberOfLevels, tokens.length);
            }
        } catch (IOException e)
        {
            return false;
        }
        finally
        {
            Utils.closeSilently(readbuffer);
        }
        return numberOfLevels > 0;
    }

//	public static boolean isNativeImage(RawObject file)
//	{
//		String filename = file.getName();
//		int dotposition = filename.lastIndexOf(".");
//		String ext = filename.substring(dotposition + 1, filename.length()).toLowerCase();
//
//		// Compare against supported android image formats
//		return (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("bmp") || ext.equals("gif"));
//	}

    public static int getImageType(File file)
    {
        if (isRaw(file)) return Meta.RAW;
        if (isNative(file)) return Meta.COMMON;
        if (isTiffImage(file)) return Meta.TIFF;
        return -1;
    }

    public static class JpegFilter implements FileFilter
    {
        @Override
        public boolean accept(File file) { return Util.isJpeg(file); }
    }

    public static class RawFilter implements FileFilter
    {
        @Override
        public boolean accept(File file) { return Util.isRaw(file); }
    }

    public static class ImageFilter implements FileFilter
    {
        @Override
        public boolean accept(File file)
        {
            return Util.isRaw(file) || Util.isJpeg(file);
        }
    }


    public static boolean isRaw(File file)
    {
        return containsString(ImageConstants.RAW_EXT, file);
    }

    public static boolean isJpeg(File file)
    {
        return containsString(ImageConstants.JPEG_EXT, file);
    }

    public static boolean isNative(File file)
    {
        return containsString(ImageConstants.COMMON_EXT, file);
    }

    public static boolean isTiffImage(File file)
    {
        return containsString(ImageConstants.TIFF_EXT, file);
    }

    private static boolean containsString(String[] extensions, File file)
    {
        for (String ext : extensions)
        {
            if (file.getName().toLowerCase().endsWith(ext.toLowerCase()))
                return true;
        }
        return false;
    }

    /**
     * Gets an exact sample size, should not be used for large images since certain ratios generate "white images"
     *
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static int getExactSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth)
        {
            if (width > height)
            {
                inSampleSize = Math.round((float) height / (float) reqHeight);
            } else
            {
                inSampleSize = Math.round((float) width / (float) reqWidth);
            }

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger
            // inSampleSize).
            final float totalPixels = width * height;

            // Anything more than 2x the requested pixels we'll sample down
            // further.
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap)
            {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }

    /**
     * Legacy sample size, more reliable for multiple devices (no "white image")
     *
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static int getLargeSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        int imageWidth = options.outWidth;
        int imageHeight = options.outHeight;

        int scaleH = 1, scaleW = 1;
        if (imageHeight > reqHeight || imageWidth > reqWidth)
        {
            scaleH = (int) Math.pow(2, (int) Math.ceil(Math.log(reqHeight / (double) imageHeight) / Math.log(0.5)));
            scaleW = (int) Math.pow(2, (int) Math.ceil(Math.log(reqWidth / (double) imageWidth) / Math.log(0.5)));
        }
        return Math.max(scaleW, scaleH);
    }

    public static Bitmap createBitmapLarge(byte[] image, int viewWidth, int viewHeight, boolean minSize)
    {
        Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, o);
        o.inSampleSize = Util.getLargeSampleSize(o, viewWidth, viewHeight);
        // setScalingPow2(image, viewWidth, viewHeight, o, minSize);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(image, 0, image.length, o);
    }

    public static Bitmap createBitmapLarge(InputStream data, int viewWidth, int viewHeight, boolean minSize)
    {
        Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(data, null, o);
        o.inSampleSize = Util.getLargeSampleSize(o, viewWidth, viewHeight);
        // setScalingPow2(image, viewWidth, viewHeight, o, minSize);
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeStream(data, null, o);
    }

    public static Bitmap createBitmapToSize(InputStream data, int width, int height)
    {
        Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(data, null, o);
        o.inSampleSize = Util.getExactSampleSize(o, width, height);
        o.inJustDecodeBounds = false;

        try
        {
            // TODO: This works, but is there a better way?
            if (data instanceof FileInputStream)
                ((FileInputStream) data).getChannel().position(0);
            else
                data.reset();
        } catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }

        return BitmapFactory.decodeStream(data, null, o);
    }

    public static Bitmap createBitmapToSize(byte[] image, int width, int height)
    {
        Bitmap result = null;
        Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, o);
        o.inSampleSize = Util.getExactSampleSize(o, width, height);
        o.inJustDecodeBounds = false;
        result = BitmapFactory.decodeByteArray(image, 0, image.length, o);
        return result;
    }

    /**
     * Get the size in bytes of a bitmap.
     *
     * @param bitmap
     * @return size in bytes
     */
    @TargetApi(12)
    public static int getBitmapSize(Bitmap bitmap)
    {
        if (Util.hasHoneycombMR1())
        {
            return bitmap.getByteCount();
        }
        // Pre HC-MR1
        return bitmap.getRowBytes() * bitmap.getHeight();
    }

    /**
     * Copies a file from source to destination. If copying images see {@link #copy(File, File)}
     *
     * @param source
     * @param destination
     * @throws IOException
     */
    public static boolean copy(InputStream source, File destination)
    {
        byte[] buf = new byte[1024];
        int len;
        OutputStream out = null;
        boolean success = true;
        try
        {
            out = new BufferedOutputStream(new FileOutputStream(destination));
            while ((len = source.read(buf)) > 0)
            {
                out.write(buf, 0, len);
            }
        } catch (IOException e)
        {
            success = false;
        }
        finally
        {
            try
            {
                if (out != null)
                    out.close();
            } catch (IOException e)
            {
                success = false;
            }
        }
        return success;
    }

    public static boolean copy(File source, File destination)
    {
        FileChannel in = null;
        FileChannel out = null;
        try
        {
            in = new FileInputStream(source).getChannel();
            out = new FileOutputStream(destination).getChannel();
            in.transferTo(0, in.size(), out);
        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
            return false;
        } catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }
        finally
        {
            if (in != null)
                Utils.closeSilently(in);
            if (out != null)
                Utils.closeSilently(out);
        }
        return true;
    }

    public static Bitmap addWatermark2(Context context, Bitmap src)
    {
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, src.getConfig());

        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(src, 0, 0, null);
        int id = R.drawable.watermark1024;
        if (width < 3072)
            id = R.drawable.watermark512;
        else if (width < 1536)
            id = R.drawable.watermark256;
        else if (width < 768)
            id = R.drawable.watermark128;
        Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap watermark = BitmapFactory.decodeResource(context.getResources(), id, o);
        watermark.setDensity(result.getDensity());
        canvas.drawBitmap(watermark, width / 4 * 3, height / 4 * 3, null);

        return result;
    }

    public static Bitmap addWatermark(Context context, File file, Bitmap src)
    {
        int width = src.getWidth();
        int height = src.getHeight();

        int id = R.drawable.watermark1024;
        if (width < 3072)
            id = R.drawable.watermark512;
        else if (width < 1536)
            id = R.drawable.watermark256;
        else if (width < 768)
            id = R.drawable.watermark128;


        BitmapFactory.Options o = new Options();
        o.inScaled = false;
        Bitmap watermark = BitmapFactory.decodeResource(context.getResources(), id, o);

        int startX = width / 4 * 3;
        int startY = height / 4 * 3;

        int watermarkWidth = watermark.getWidth();
        int watermarkHeight = watermark.getHeight();

        int pixels = watermarkWidth * watermarkHeight;
        int[] source = new int[width * height];
        int[] mark = new int[pixels];

        watermark.getPixels(mark, 0, watermarkWidth, 0, 0, watermarkWidth, watermarkHeight);
        src.getPixels(source, 0, width, 0, 0, width, height);

        int i = 0;
        for (int y = startY; y < startY + watermarkHeight; ++y)
        {
            for (int x = startX; x < startX + watermarkWidth; ++x)
            {
                int index = y * width + x;
                // Applying a 50% opacity on top of the given opacity.  Somewhat arbitrary, but looks the same as the canvas method.
                // Perhaps this is because the canvas applies 50% to stacked images, maybe just luck...
                float opacity = Color.alpha(mark[i]) / 510f;
                source[index] = Color.argb(
                        Color.alpha(source[index]),
                        Math.min(Color.red(source[index]) + (int) (Color.red(mark[i]) * opacity), 255),
                        Math.min(Color.green(source[index]) + (int) (Color.green(mark[i]) * opacity), 255),
                        Math.min(Color.blue(source[index]) + (int) (Color.blue(mark[i]) * opacity), 255));
                ++i;
            }
        }

//        src.setPixels(source, 0, width, 0, 0, width, height);

        return Bitmap.createBitmap(source, width, height, Bitmap.Config.ARGB_8888);
    }

    public static Bitmap getDemoWatermark(Context context, int srcWidth)
    {
        int id;
        if (srcWidth > 5120)
            id = R.drawable.watermark1024;
        else if (srcWidth > 2560)
            id = R.drawable.watermark512;
        else if (srcWidth > 1280)
            id = R.drawable.watermark256;
        else
            id = R.drawable.watermark128;

        BitmapFactory.Options o = new Options();
        o.inScaled = false;
        return BitmapFactory.decodeResource(context.getResources(), id, o);
    }

    public static byte[] getBitmapBytes(Bitmap src)
    {
        ByteBuffer dst = ByteBuffer.allocate(Util.getBitmapSize(src));
        dst.order(ByteOrder.nativeOrder());
        src.copyPixelsToBuffer(dst);
        return dst.array();
    }

    public static Bitmap addWatermark(Context context, Bitmap src)
    {
        int width = src.getWidth();
        int height = src.getHeight();

        int id = R.drawable.watermark1024;
        if (width < 3072)
            id = R.drawable.watermark512;
        else if (width < 1536)
            id = R.drawable.watermark256;
        else if (width < 768)
            id = R.drawable.watermark128;

        BitmapFactory.Options o = new Options();
        o.inScaled = false;
        Bitmap watermark = BitmapFactory.decodeResource(context.getResources(), id, o);

        int startX = width / 4 * 3;
        int startY = height / 4 * 3;

        int watermarkWidth = watermark.getWidth();
        int watermarkHeight = watermark.getHeight();

        int pixels = watermarkWidth * watermarkHeight;
        int[] source = new int[width * height];
        int[] mark = new int[pixels];

        watermark.getPixels(mark, 0, watermarkWidth, 0, 0, watermarkWidth, watermarkHeight);
        src.getPixels(source, 0, width, 0, 0, width, height);

        int i = 0;
        for (int y = startY; y < startY + watermarkHeight; ++y)
        {
            for (int x = startX; x < startX + watermarkWidth; ++x)
            {
                int index = y * width + x;
                // Applying a 50% opacity on top of the given opacity.  Somewhat arbitrary, but looks the same as the canvas method.
                // Perhaps this is because the canvas applies 50% to stacked images, maybe just luck...
                float opacity = Color.alpha(mark[i]) / 510f;
                source[index] = Color.argb(
                        Color.alpha(source[index]),
                        Math.min(Color.red(source[index]) + (int) (Color.red(mark[i]) * opacity), 255),
                        Math.min(Color.green(source[index]) + (int) (Color.green(mark[i]) * opacity), 255),
                        Math.min(Color.blue(source[index]) + (int) (Color.blue(mark[i]) * opacity), 255));
                ++i;
            }
        }

//        src.setPixels(source, 0, width, 0, 0, width, height);

        return Bitmap.createBitmap(source, width, height, Bitmap.Config.ARGB_8888);
    }

    public static Bitmap addCustomWatermark(Bitmap src, String watermark, int alpha,
                                            int size, String location)
    {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap result = Bitmap.createBitmap(w, h, src.getConfig());

        int x = 0, y = 0;

        // We center the text in their respective quadrants
        switch (location)
        {
            case "Center":
                x = w / 2;
                y = h / 2;
                break;
            case "Lower Left":
                x = w / 4;
                y = h / 4 * 3;
                break;
            case "Lower Right":
                x = w / 4 * 3;
                y = h / 4 * 3;
                break;
            case "Upper Left":
                x = w / 4;
                y = h / 4;
                break;
            case "Upper Right":
                x = w / 4 * 3;
                y = h / 4;
                break;
        }

        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(src, 0, 0, null);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(1, 1, 1, Color.BLACK);
        paint.setAlpha(alpha);
        paint.setTextSize(size);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(watermark, x, y, paint);

        return result;
    }

    public static Bitmap getWatermarkText(String text, int alpha, int size, String location)
    {
        if (text.isEmpty())
            return null;

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(1, 1, 1, Color.BLACK);
        paint.setAlpha(alpha);
        paint.setTextSize(size);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.LEFT);

        int width = (int) (paint.measureText(text) + 0.5f); // round
        float baseline = (int) (-paint.ascent() + 0.5f); // ascent() is negative
        int height = (int) (baseline + paint.descent() + 0.5f);

        Bitmap watermark = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(watermark);
        canvas.drawText(text, 0, baseline, paint);

        return watermark;
    }

    /**
     * Returns intent that  opens app in Google Play or Amazon Appstore
     *
     * @param context
     * @param packageName
     * @return null if no market available, otherwise intent
     */
    public static Intent getStoreIntent(Context context, String packageName)
    {
        Intent i = new Intent(Intent.ACTION_VIEW);
        String url = "market://details?id=" + packageName;
        i.setData(Uri.parse(url));

        if (isIntentAvailable(context, i))
        {
            return i;
        }

        i.setData(Uri.parse("http://www.amazon.com/gp/mas/dl/android?p=" + packageName));
        if (isIntentAvailable(context, i))
        {
            return i;
        }
        return null;

    }

    public static boolean isIntentAvailable(Context context, Intent intent)
    {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static void write(File destination, InputStream is)
    {
        BufferedOutputStream bos = null;
        byte[] data = null;
        try
        {
            bos = new BufferedOutputStream(new FileOutputStream(destination));
            data = new byte[is.available()];
            is.read(data);
            bos.write(data);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            Utils.closeSilently(bos);
            Utils.closeSilently(is);
        }
    }

    public static String getRealPathFromURI(Context context, Uri contentUri)
    {
        Cursor cursor = null;
        try
        {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
    }

    public static boolean isSymlink(File file) {
        try
        {
            File canon;
            if (file.getParent() == null)
            {
                canon = file;
            } else
            {
                File canonDir = file.getParentFile().getCanonicalFile();
                canon = new File(canonDir, file.getName());
            }
            return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public static File[] getStorageRoots()
    {
        File mnt = new File("/storage");
        if (!mnt.exists())
            mnt = new File("/mnt");

        File[] roots = mnt.listFiles(new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory() && pathname.exists()
                        && pathname.canWrite() && !pathname.isHidden()
                        && !isSymlink(pathname);
            }
        });
        return roots;
    }

    public static List<File> getStoragePoints(File root)
    {
        List<File> matches = new ArrayList<>();

        if (root == null)
            return matches;

        File[] contents = root.listFiles();
        if (contents == null)
            return matches;

        for (File sub : contents)
        {
            if (sub.isDirectory())
            {
                if (isSymlink(sub))
                    continue;

                if (sub.exists()
                    && sub.canWrite()
                    && !sub.isHidden())
                {
                    matches.add(sub);
                }
                else
                {
                    matches.addAll(getStoragePoints(sub));
                }
            }
        }
        return matches;
    }

    public static List<File> getStorageRoots(String[] roots)
    {
        List<File> valid = new ArrayList<>();
        for (String root : roots)
        {
            File check = new File(root);
            if (check.exists())
            {
                valid.addAll(getStoragePoints(check));
            }
        }
        return valid;
    }

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
