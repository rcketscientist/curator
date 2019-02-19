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

package com.anthonymandra.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.anthonymandra.rawdroid.GalleryActivity;
import com.anthonymandra.rawdroid.R;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;

import androidx.core.app.NotificationCompat;

/**
 * Class containing some static utility methods.
 */
public class Util
{
    private static final String TAG = Util.class.getSimpleName();

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
                closeSilently(in);
            if (out != null)
                closeSilently(out);
        }
        return true;
    }

    // copy from InputStream
    //-----------------------------------------------------------------------
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    /**
     * Copy bytes from an <code>InputStream</code> to an
     * <code>OutputStream</code>.
     * <p>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedInputStream</code>.
     * <p>
     * Large streams (over 2GB) will return a bytes copied value of
     * <code>-1</code> after the copy has completed since the correct
     * number of bytes cannot be returned as an int. For large streams
     * use the <code>copyLarge(InputStream, OutputStream)</code> method.
     *
     * @param input  the <code>InputStream</code> to read from
     * @param output  the <code>OutputStream</code> to write to
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException if an I/O error occurs
     * @throws ArithmeticException if the byte count is too large
     * @since Commons IO 1.1
     */
    public static int copy(InputStream input, OutputStream output) throws IOException {
        long count = copyLarge(input, output);
        if (count > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) count;
    }

    /**
     * Copy bytes from a large (over 2GB) <code>InputStream</code> to an
     * <code>OutputStream</code>.
     * <p>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedInputStream</code>.
     *
     * @param input  the <code>InputStream</code> to read from
     * @param output  the <code>OutputStream</code> to write to
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException if an I/O error occurs
     * @since Commons IO 1.3
     */
    public static long copyLarge(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    // read toByteArray
    //-----------------------------------------------------------------------
    /**
     * Get the contents of an <code>InputStream</code> as a <code>byte[]</code>.
     * <p>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedInputStream</code>.
     *
     * @param input  the <code>InputStream</code> to read from
     * @return the requested byte array
     * @throws NullPointerException if the input is null
     * @throws IOException if an I/O error occurs
     */
    public static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
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

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static void closeSilently(Closeable closeable)
    {
        try
        {
            closeable.close();
        }
        catch (Throwable t)
        {
            Log.w(TAG, "close fail", t);
        }
    }

    /**
     * Create a Notification that is shown as a heads-up notification if possible.
     */
    public static void createNotificationChannel(
            Context context,
            String channelId,
            String name,
            String description) {

        // Make a channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			  NotificationManager notificationManager =
				  (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

			  if (notificationManager.getNotificationChannel(channelId) == null) {
				  NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH);
				  channel.setDescription(description);
				  notificationManager.createNotificationChannel(channel);
			  }
		  }
    }
    public static NotificationCompat.Builder createNotification(
            Context context,
            String channelId,
            String title,
            String message) {

        // Create the notification
        return new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        new Intent(context, GalleryActivity.class),0))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(new long[0]);
    }
}
