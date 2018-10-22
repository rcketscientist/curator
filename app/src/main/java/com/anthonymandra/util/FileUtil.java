package com.anthonymandra.util;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for helping parsing file systems.
 */
public class FileUtil
{
	private static final String TAG = FileUtil.class.getSimpleName();

	public static boolean isContentScheme(Uri uri)
	{
		return ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme());
	}

	public static boolean isFileScheme(Uri uri)
	{
		return ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme());
	}

	public static String getCanonicalPathSilently(File file)
	{
		try
		{
			return file.getCanonicalPath();
		}
		catch (IOException e)
		{
			return file.getPath();
		}
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	/**
	 * Opens an InputStream to uri.  Checks if it's a local file to create a FileInputStream,
	 * otherwise resorts to using the ContentResolver to request a stream.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 */
	public static InputStream getInputStream(final Context context, final Uri uri) throws FileNotFoundException, IllegalArgumentException
	{
		if (isFileScheme(uri))
		{
			return new FileInputStream(uri.getPath());
		}
		else
		{
			return context.getContentResolver().openInputStream(uri);
		}
	}

	/**
	 * Opens an InputStream to uri.  Checks if it's a local file to create a FileInputStream,
	 * otherwise resorts to using the ContentResolver to request a stream.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 */
	public static ParcelFileDescriptor getParcelFileDescriptor(final Context context, final Uri uri, String mode) throws FileNotFoundException
	{
		if (isFileScheme(uri))
		{
			int m = ParcelFileDescriptor.MODE_READ_ONLY;
			if ("w".equalsIgnoreCase(mode) || "rw".equalsIgnoreCase(mode)) m = ParcelFileDescriptor.MODE_READ_WRITE;
			else if ("rwt".equalsIgnoreCase(mode)) m = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE;

			//TODO: Is this any faster?  Otherwise could just rely on resolver
			return ParcelFileDescriptor.open(new File(uri.getPath()), m);
		}
		else
		{
			return context.getContentResolver().openFileDescriptor(uri, mode);
		}
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
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
		{
			cache = context.getExternalCacheDir();
		}
		if (cache == null)
			cache = context.getCacheDir();

		return new File(cache, uniqueName);
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
		final StatFs stats = new StatFs(path.getPath());
		return stats.getBlockSizeLong() * stats.getAvailableBlocksLong();
	}

	public static String swapExtention(String filename, String ext)
	{
		if (filename == null)
			return null;
		return filename.replaceFirst("[.][^.]+$", "") + "." + ext;
	}

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

	public static void copy(Context context, Uri source, Uri destination) throws IOException {
		try (
			InputStream inStream = context.getContentResolver().openInputStream(source);
			OutputStream outStream = context.getContentResolver().openOutputStream(destination)) {
			copy(inStream, outStream);
		}
		catch(ArithmeticException e) {
			Log.d(TAG, "File larger than 2GB copied.");
		}
		catch(Exception e) {
			throw new IOException("Failed to copy " + source.getPath() + ": " + e.toString());
		}
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
			Util.closeSilently(bos);
			Util.closeSilently(is);
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
}
