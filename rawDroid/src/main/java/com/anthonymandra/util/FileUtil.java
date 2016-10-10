package com.anthonymandra.util;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.MediaStore;

import com.android.gallery3d.common.Utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @param selection (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	public static String getDataColumn(Context context, Uri uri, String selection,
	                                   String[] selectionArgs) {

		final String column = "_data";
		final String[] projection = {
				column
		};

		try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
					null))
		{
			if (cursor != null && cursor.moveToFirst()) {
				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		}
		return null;
	}

	/**
	 * Opens an InputStream to uri.  Checks if it's a local file to create a FileInputStream,
	 * otherwise resorts to using the ContentResolver to request a stream.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 */
	public static InputStream getInputStream(final Context context, final Uri uri) throws FileNotFoundException
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
		if (filename == null)
			return null;
		return filename.replaceFirst("[.][^.]+$", "") + "." + ext;
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
		final String[] proj = {MediaStore.Images.Media.DATA};
		try (Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null))
		{
			int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			cursor.moveToFirst();
			return cursor.getString(column_index);
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
