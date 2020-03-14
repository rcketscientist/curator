package com.anthonymandra.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.anthonymandra.framework.RecycleBin;
import com.anthonymandra.framework.UsefulDocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.anthonymandra.rawdroid.settings.StorageSettingsFragment.DEFAULT_RECYCLE_BIN;
import static com.anthonymandra.rawdroid.settings.StorageSettingsFragment.KEY_RecycleBinSize;

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

	public static boolean copy(Context context, Uri source, Uri destination) throws IOException {
		// TODO: Clean this up and protect that NPE
		UsefulDocumentFile destinationFile = UsefulDocumentFile.fromUri(context, destination);

		UsefulDocumentFile parent = destinationFile.getParentFile();
		if (parent == null) {
			return false;
		}
		parent.createFile(null, destinationFile.getName());

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
		return true;
	}

	/**
	 * Move a file within the constraints of SAF.
	 *
	 * @param source The source uri
	 * @param target The target uri
	 * @return true if the copying was successful.
	 * @throws IOException if an I/O error occurs
	 */
	public static boolean move(final Context context, final Uri source, final Uri target) throws IOException
	{
		boolean success = copy(context, source, target);
		if (success) {
			UsefulDocumentFile sourceFile = UsefulDocumentFile.fromUri(context, source);
			success = sourceFile.delete();
		}
		return success;
	}

	public static RecycleBin getRecycleBin(Context context) {
		int binSizeMb;
		try {
			binSizeMb = PreferenceManager.getDefaultSharedPreferences(context).getInt(
						KEY_RecycleBinSize, DEFAULT_RECYCLE_BIN);
		} catch (NumberFormatException e) {
			binSizeMb = DEFAULT_RECYCLE_BIN;
		}

		return RecycleBin.getInstance(context, binSizeMb * 1024 * 1024L);
	}
}
