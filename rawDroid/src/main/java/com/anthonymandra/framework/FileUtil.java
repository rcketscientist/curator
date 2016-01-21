package com.anthonymandra.framework;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.provider.MediaStore;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.widget.Toast;

import com.anthonymandra.rawdroid.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

//https://github.com/jeisfeld/Augendiagnose/

/**
 * Utility class for helping parsing file systems.
 */
public class FileUtil
{
	private static final String TAG = FileUtil.class.getSimpleName();
	/**
	 * The name of the primary volume (LOLLIPOP).
	 */
	private static final String PRIMARY_VOLUME_NAME = "primary";

	private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

	/**
	 * Hide default constructor.
	 */
	private FileUtil() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Determine the camera folder. There seems to be no Android API to work for real devices, so this is a best guess.
	 *
	 * @return the default camera folder.
	 */
	public static String getDefaultCameraFolder() {
		File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
		if (path.exists()) {
			File test1 = new File(path, "Camera/");
			if (test1.exists()) {
				path = test1;
			}
			else {
				File test2 = new File(path, "100ANDRO/");
				if (test2.exists()) {
					path = test2;
				}
				else {
					File test3 = new File(path, "100MEDIA/");
					path = test3;
				}
			}
		}
		else {
			File test3 = new File(path, "Camera/");
			path = test3;
		}
		return path.getAbsolutePath();
	}

	/**
	 * Copy a file. The target file may even be on external SD card for Kitkat.
	 *
	 * @param source
	 *            The source file
	 * @param target
	 *            The target file
	 * @return true if the copying was successful.
	 */
	public static boolean copyFile(final Context context, final File source, final File target) {
		FileInputStream inStream = null;
		OutputStream outStream = null;
		FileChannel inChannel = null;
		FileChannel outChannel = null;
		try {
			inStream = new FileInputStream(source);

			// First try the normal way
			if (isWritable(target)) {
				// standard way
				outStream = new FileOutputStream(target);
				inChannel = inStream.getChannel();
				outChannel = ((FileOutputStream) outStream).getChannel();
				inChannel.transferTo(0, inChannel.size(), outChannel);
			}
			else {
				if (Util.hasLollipop()) {
					// Storage Access Framework
					DocumentFile targetDocument = getLollipopDocument(context, target, false, true);
					if (targetDocument == null)
						return false;
					outStream =
							context.getContentResolver().openOutputStream(targetDocument.getUri());
				}
				else if (Util.hasKitkat()) {
					// Workaround for Kitkat ext SD card
					Uri uri = MediaStoreUtil.getUriFromFile(context, target.getAbsolutePath());
					outStream = context.getContentResolver().openOutputStream(uri);
				}
				else {
					return false;
				}

				if (outStream != null) {
					// Both for SAF and for Kitkat, write to output stream.
					byte[] buffer = new byte[4096]; // MAGIC_NUMBER
					int bytesRead;
					while ((bytesRead = inStream.read(buffer)) != -1) {
						outStream.write(buffer, 0, bytesRead);
					}
				}

			}
		}
		catch (Exception e) {
			Log.e(TAG,
					"Error when copying file from " + source.getAbsolutePath() + " to " + target.getAbsolutePath(), e);
			return false;
		}
		finally {
			try {
				inStream.close();
			}
			catch (Exception e) {
				// ignore exception
			}
			try {
				outStream.close();
			}
			catch (Exception e) {
				// ignore exception
			}
			try {
				inChannel.close();
			}
			catch (Exception e) {
				// ignore exception
			}
			try {
				outChannel.close();
			}
			catch (Exception e) {
				// ignore exception
			}
		}
		return true;
	}

	/**
	 * Delete a file. May be even on external SD card.
	 *
	 * @param file
	 *            the file to be deleted.
	 * @return True if successfully deleted.
	 */
	public static boolean deleteFile(final Context context, final File file) {
		// First try the normal deletion.
		if (file.delete()) {
			return true;
		}

		// Try with Storage Access Framework.
		if (Util.hasLollipop()) {
			DocumentFile document = getLollipopDocument(context, file, false, true);
			if (document == null)
				return false;
			return document.delete();
		}

		// Try the Kitkat workaround.
		if (Util.hasKitkat()) {
			ContentResolver resolver = context.getContentResolver();

			try {
				Uri uri = MediaStoreUtil.getUriFromFile(context, file.getAbsolutePath());
				resolver.delete(uri, null, null);
				return !file.exists();
			}
			catch (Exception e) {
				Log.e(TAG, "Error when deleting file " + file.getAbsolutePath(), e);
				return false;
			}
		}

		return !file.exists();
	}

	/**
	 * Move a file. The target file may even be on external SD card.
	 *
	 * @param source
	 *            The source file
	 * @param target
	 *            The target file
	 * @return true if the copying was successful.
	 */
	public static boolean moveFile(final Context context, final File source, final File target) {
		// First try the normal rename.
		if (source.renameTo(target)) {
			return true;
		}

		boolean success = copyFile(context, source, target);
		if (success) {
			success = deleteFile(context, source);
		}
		return success;
	}

	/**
	 * Rename a folder. In case of extSdCard in Kitkat, the old folder stays in place, but files are moved.
	 *
	 * @param source
	 *            The source folder.
	 * @param target
	 *            The target folder.
	 * @return true if the renaming was successful.
	 */
	public static boolean renameFolder(final Context context, final File source, final File target) {
		// First try the normal rename.
		if (source.renameTo(target)) {
			return true;
		}
		if (target.exists()) {
			return false;
		}

		// Try the Storage Access Framework if it is just a rename within the same parent folder.
		if (Util.hasLollipop() && source.getParent().equals(target.getParent())) {
			DocumentFile document = getLollipopDocument(context, source, true, true);
			if (document == null)
				return false;
			if (document.renameTo(target.getName())) {
				return true;
			}
		}

		// Try the manual way, moving files individually.
		if (!mkdir(context, target)) {
			return false;
		}

		File[] sourceFiles = source.listFiles();

		if (sourceFiles == null) {
			return true;
		}

		for (File sourceFile : sourceFiles) {
			String fileName = sourceFile.getName();
			File targetFile = new File(target, fileName);
			if (!copyFile(context, sourceFile, targetFile)) {
				// stop on first error
				return false;
			}
		}
		// Only after successfully copying all files, delete files on source folder.
		for (File sourceFile : sourceFiles) {
			if (!deleteFile(context, sourceFile)) {
				// stop on first error
				return false;
			}
		}
		return true;
	}

	/**
	 * Get a temp file.
	 *
	 * @param file
	 *            The base file for which to create a temp file.
	 * @return The temp file.
	 */
	public static File getTempFile(final Context context, final File file) {
		File extDir = context.getExternalFilesDir(null);
		File tempFile = new File(extDir, file.getName());
		return tempFile;
	}

	/**
	 * Create a folder. The folder may even be on external SD card for Kitkat.
	 *
	 * @param file
	 *            The folder to be created.
	 * @return True if creation was successful.
	 */
	public static boolean mkdir(final Context context, final File file) {
		if (file.exists()) {
			// nothing to create.
			return file.isDirectory();
		}

		// Try the normal way
		if (file.mkdir()) {
			return true;
		}

		// Try with Storage Access Framework.
		if (Util.hasLollipop()) {
			DocumentFile document = getLollipopDocument(context, file, true, true);
			if (document == null)
				return false;
			// getLollipopDocument implicitly creates the directory.
			return document.exists();
		}

		// Try the Kitkat workaround.
		if (Util.hasKitkat()) {
			ContentResolver resolver = context.getContentResolver();
			File tempFile = new File(file, "dummyImage.jpg");

			File dummySong = copyDummyFiles(context);
			int albumId = MediaStoreUtil.getAlbumIdFromAudioFile(context, dummySong);
			Uri albumArtUri = Uri.parse("content://media/external/audio/albumart/" + albumId);

			ContentValues contentValues = new ContentValues();
			contentValues.put(MediaStore.MediaColumns.DATA, tempFile.getAbsolutePath());
			contentValues.put(MediaStore.Audio.AlbumColumns.ALBUM_ID, albumId);

			if (resolver.update(albumArtUri, contentValues, null, null) == 0) {
				resolver.insert(Uri.parse("content://media/external/audio/albumart"), contentValues);
			}
			try {
				ParcelFileDescriptor fd = resolver.openFileDescriptor(albumArtUri, "r");
				fd.close();
			}
			catch (Exception e) {
				Log.e(TAG, "Could not open file", e);
				return false;
			}
			finally {
				FileUtil.deleteFile(context, tempFile);
			}

			return true;
		}

		return false;
	}

	/**
	 * Delete a folder.
	 *
	 * @param file
	 *            The folder name.
	 *
	 * @return true if successful.
	 */
	public static boolean rmdir(final Context context, final File file) {
		if (!file.exists()) {
			return true;
		}
		if (!file.isDirectory()) {
			return false;
		}
		String[] fileList = file.list();
		if (fileList != null && fileList.length > 0) {
			// Delete only empty folder.
			return false;
		}

		// Try the normal way
		if (file.delete()) {
			return true;
		}

		// Try with Storage Access Framework.
		if (Util.hasLollipop()) {
			DocumentFile document = getLollipopDocument(context, file, true, true);
			if (document == null)
				return false;
			return document.delete();
		}

		// Try the Kitkat workaround.
		if (Util.hasKitkat()) {
			ContentResolver resolver = context.getContentResolver();
			ContentValues values = new ContentValues();
			values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
			resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

			// Delete the created entry, such that content provider will delete the file.
			resolver.delete(MediaStore.Files.getContentUri("external"), MediaStore.MediaColumns.DATA + "=?",
					new String[] { file.getAbsolutePath() });
		}

		return !file.exists();
	}

	/**
	 * Delete all files in a folder.
	 *
	 * @param folder
	 *            the folder
	 * @return true if successful.
	 */
	public static boolean deleteFilesInFolder(final Context context, final File folder) {
		boolean totalSuccess = true;

		String[] children = folder.list();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				File file = new File(folder, children[i]);
				if (!file.isDirectory()) {
					boolean success = FileUtil.deleteFile(context, file);
					if (!success) {
						Log.w(TAG, "Failed to delete file" + children[i]);
						totalSuccess = false;
					}
				}
			}
		}
		return totalSuccess;
	}

//	/**
//	 * Delete a directory asynchronously.
//	 *
//	 * @param activity
//	 *            The activity calling this method.
//	 * @param file
//	 *            The folder name.
//	 * @param postActions
//	 *            Commands to be executed after success.
//	 */
//	public static void rmdirAsynchronously(final Activity activity, final File file, final Runnable postActions) {
//		new Thread() {
//			@Override
//			public void run() {
//				int retryCounter = 5; // MAGIC_NUMBER
//				while (!FileUtil.rmdir(activity.getApplicationContext(), file) && retryCounter > 0) {
//					try {
//						Thread.sleep(100); // MAGIC_NUMBER
//					}
//					catch (InterruptedException e) {
//						// do nothing
//					}
//					retryCounter--;
//				}
//				if (file.exists()) {
//					Toast.makeText(activity.getApplicationContext(), R.string.error_deleting_folder, Toast.LENGTH_SHORT).show();
////					DialogUtil.displayError(activity, R.string.message_dialog_failed_to_delete_folder, false,
////							file.getAbsolutePath());
//				}
//				else {
//					activity.runOnUiThread(postActions);
//				}
//
//			}
//		}.start();
//	}

	public static boolean isContentScheme(Uri uri)
	{
		return ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme());
	}

	public static boolean isFileScheme(Uri uri)
	{
		return ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme());
	}

	/**
	 * Returns a uri to a child file within a folder.  This can be used to get an assumed uri
	 * to a child within a folder.  This avoids heavy calls to DocumentFile.listFiles or
	 * write-locked createFile
	 *
	 * This will only work with a uri that is an heriacrchical tree similar to SCHEME_FILE
	 * @param heirarchicalTreeUri folder to install into
	 * @param filename filename of child file
	 * @return Uri to the child file
	 */
	public static Uri getChildUri(Uri heirarchicalTreeUri, String filename)
	{
		String childUriString = heirarchicalTreeUri.toString() + "/" + filename;
		return Uri.parse(childUriString);
	}

	/**
	 * Check is a file is writable. Detects write issues on external SD card.
	 *
	 * @param file
	 *            The file
	 * @return true if the file is writable.
	 */
	public static boolean isWritable(final File file) {
		boolean isExisting = file.exists();

		try {
			FileOutputStream output = new FileOutputStream(file, true);
			try {
				output.close();
			}
			catch (IOException e) {
				// do nothing.
			}
		}
		catch (FileNotFoundException e) {
			return false;
		}
		boolean result = file.canWrite();

		// Ensure that file is not created during this process.
		if (!isExisting) {
			file.delete();
		}

		return result;
	}

	// Utility methods for Android 5

	/**
	 * Check for a directory if it is possible to create files within this directory, either via normal writing or via
	 * Storage Access Framework.
	 *
	 * @param folder
	 *            The directory
	 * @return true if it is possible to write in this directory.
	 */
	public static boolean isWritableNormalOrSaf(final Context context, final File folder) {
		// Verify that this is a directory.
		if (!folder.exists() || !folder.isDirectory()) {
			return false;
		}

		// Find a non-existing file in this directory.
		int i = 0;
		File file;
		do {
			String fileName = "AugendiagnoseDummyFile" + (++i);
			file = new File(folder, fileName);
		}
		while (file.exists());

		// First check regular writability
		if (isWritable(file)) {
			return true;
		}

		// Next check SAF writability.
		DocumentFile document = getLollipopDocument(context, file, false, false);

		if (document == null) {
			return false;
		}

		// This should have created the file - otherwise something is wrong with access URL.
		boolean result = document.canWrite() && file.exists();

		// Ensure that the dummy file is not remaining.
		document.delete();

		return result;
	}

	/**
	 * Get a list of external SD card paths. (Kitkat or higher.)
	 *
	 * @return A list of external SD card paths.
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	private static String[] getExtSdCardPaths(Context context) {
		List<String> paths = new ArrayList<String>();
		for (File file : context.getExternalFilesDirs("external")) {
			if (file != null && !file.equals(context.getExternalFilesDir("external"))) {
				int index = file.getAbsolutePath().lastIndexOf("/Android/data");
				if (index < 0) {
					Log.w(TAG, "Unexpected external file dir: " + file.getAbsolutePath());
				}
				else {
					String path = file.getAbsolutePath().substring(0, index);
					try {
						path = new File(path).getCanonicalPath();
					}
					catch (IOException e) {
						// Keep non-canonical path.
					}
					paths.add(path);
				}
			}
		}
		return paths.toArray(new String[0]);
	}

	/**
	 * Determine the main folder of the external SD card containing the given file.
	 *
	 * @param file
	 *            the file.
	 * @return The main folder of the external SD card containing this file, if the file is on an SD card. Otherwise,
	 *         null is returned.
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static String getExtSdCardFolder(final Context context, final File file) {
		String[] extSdPaths = getExtSdCardPaths(context);
		try {
			for (int i = 0; i < extSdPaths.length; i++) {
				if (file.getCanonicalPath().startsWith(extSdPaths[i])) {
					return extSdPaths[i];
				}
			}
		}
		catch (IOException e) {
			return null;
		}
		return null;
	}

	/**
	 * Determine if a file is on external sd card. (Kitkat or higher.)
	 *
	 * @param file
	 *            The file.
	 * @return true if on external sd card.
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static boolean isOnExtSdCard(final Context context, final File file) {
		return getExtSdCardFolder(context, file) != null;
	}

	/**
	 * Get a DocumentFile corresponding to the given file (for writing on ExtSdCard on Android 5). If the file is not
	 * existing, it is created.
	 *
	 * @param file
	 *            The file.
	 * @param isDirectory
	 *            flag indicating if the file should be a directory.
	 * @param createDirectories
	 *            flag indicating if intermediate path directories should be created if not existing.
	 * @return The DocumentFile
	 */
	public static DocumentFile getDocumentFile(final Context context, final File file,
	                                           final boolean isDirectory,
	                                           final boolean createDirectories)
	{
		// First try the normal way
		if (isWritable(file))
		{
			return DocumentFile.fromFile(file);
		}
		else if (Util.hasLollipop())
		{
			return getLollipopDocument(context, file, isDirectory, createDirectories);
		}
		else if (Util.hasKitkat())
		{
			// Workaround for Kitkat ext SD card
			//TODO: This probably doesn't work
			Uri uri = MediaStoreUtil.getUriFromFile(context, file.getAbsolutePath());
			DocumentFile.fromSingleUri(context, uri);
		}
		return null;
	}

	/**
	 * Get a DocumentFile corresponding to the given file (for writing on ExtSdCard on Android 5). If the file is not
	 * existing, it is created.
	 *
	 * @param file
	 *            The file.
	 * @param isDirectory
	 *            flag indicating if the file should be a directory.
	 * @param createDirectories
	 *            flag indicating if intermediate path directories should be created if not existing.
	 * @return The DocumentFile
	 */
	private static DocumentFile getLollipopDocument(final Context context, final File file,
	                                                final boolean isDirectory,
	                                                final boolean createDirectories) {
		Uri treeUri = getTreeUri(context);

		if (treeUri == null)
			return null;

		String fullPath = null;
		try {
			fullPath = file.getCanonicalPath();
		}
		catch (IOException e) {
			return null;
		}

		String baseFolder = null;

		// First try to get the base folder via unofficial StorageVolume API from the URIs.
		String treeBase = getFullPathFromTreeUri(context, treeUri);
		if (fullPath.startsWith(treeBase)) {
			baseFolder = treeBase;
		}

		if (baseFolder == null) {
			// Alternatively, take root folder from device and assume that base URI works.
			baseFolder = getExtSdCardFolder(context, file);
		}

		if (baseFolder == null) {
			return null;
		}

		String relativePath = fullPath.substring(baseFolder.length() + 1);

		// start with root of SD card and then parse through document tree.
		DocumentFile document = DocumentFile.fromTreeUri(context, treeUri);

		String[] parts = relativePath.split("\\/");
		for (int i = 0; i < parts.length; i++) {
			DocumentFile nextDocument = document.findFile(parts[i]);

			if (nextDocument == null) {
				if (i < parts.length - 1) {
					if (createDirectories) {
						nextDocument = document.createDirectory(parts[i]);
					}
					else {
						return null;
					}
				}
				else if (isDirectory) {
					nextDocument = document.createDirectory(parts[i]);
				}
				else {
					nextDocument = document.createFile("image", parts[i]);
				}
			}
			document = nextDocument;
		}

		return document;
	}

	/**
	 * Get the full path of a document from its tree URI.
	 *
	 * @param treeUri
	 *            The tree RI.
	 * @return The path (without trailing file separator).
	 */
	private static String getFullPathFromTreeUri(final Context context, final Uri treeUri) {
		if (treeUri == null) {
			return null;
		}
		String volumePath = FileUtil.getVolumePath(context, FileUtil.getVolumeIdFromTreeUri(treeUri));
		if (volumePath == null) {
			return File.separator;
		}
		if (volumePath.endsWith(File.separator)) {
			volumePath = volumePath.substring(0, volumePath.length() - 1);
		}

		String documentPath = FileUtil.getDocumentPathFromTreeUri(treeUri);
		if (documentPath.endsWith(File.separator)) {
			documentPath = documentPath.substring(0, documentPath.length() - 1);
		}

		if (documentPath != null && documentPath.length() > 0) {
			if (documentPath.startsWith(File.separator)) {
				return volumePath + documentPath;
			}
			else {
				return volumePath + File.separator + documentPath;
			}
		}
		else {
			return volumePath;
		}
	}

	/**
	 * Get the path of a certain volume.
	 *
	 * @param volumeId
	 *            The volume id.
	 * @return The path.
	 */
	private static String getVolumePath(final Context context, final String volumeId) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			return null;
		}

		try {
			StorageManager mStorageManager =
					(StorageManager) context.getSystemService(Context.STORAGE_SERVICE);

			Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");

			Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
			Method getUuid = storageVolumeClazz.getMethod("getUuid");
			Method getPath = storageVolumeClazz.getMethod("getPath");
			Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
			Object result = getVolumeList.invoke(mStorageManager);

			final int length = Array.getLength(result);
			for (int i = 0; i < length; i++) {
				Object storageVolumeElement = Array.get(result, i);
				String uuid = (String) getUuid.invoke(storageVolumeElement);
				Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);

				// primary volume?
				if (primary.booleanValue() && PRIMARY_VOLUME_NAME.equals(volumeId)) {
					return (String) getPath.invoke(storageVolumeElement);
				}

				// other volumes?
				if (uuid != null) {
					if (uuid.equals(volumeId)) {
						return (String) getPath.invoke(storageVolumeElement);
					}
				}
			}

			// not found.
			return null;
		}
		catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Get the volume ID from the tree URI.
	 *
	 * @param treeUri
	 *            The tree URI.
	 * @return The volume ID.
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static String getVolumeIdFromTreeUri(final Uri treeUri) {
		final String docId = DocumentsContract.getTreeDocumentId(treeUri);
		final String[] split = docId.split(":");

		if (split.length > 0) {
			return split[0];
		}
		else {
			return null;
		}
	}

	/**
	 * Get the document path (relative to volume name) for a tree URI (LOLLIPOP).
	 *
	 * @param treeUri
	 *            The tree URI.
	 * @return the document path.
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static String getDocumentPathFromTreeUri(final Uri treeUri) {
		final String docId = DocumentsContract.getTreeDocumentId(treeUri);
		final String[] split = docId.split(":");
		if ((split.length >= 2) && (split[1] != null)) {
			return split[1];
		}
		else {
			return File.separator;
		}
	}

	// Utility methods for Kitkat

	/**
	 * Copy a resource file into a private target directory, if the target does not yet exist. Required for the Kitkat
	 * workaround.
	 *
	 * @param resource
	 *            The resource file.
	 * @param folderName
	 *            The folder below app folder where the file is copied to.
	 * @param targetName
	 *            The name of the target file.
	 * @return the dummy file.
	 * @throws IOException
	 *             thrown if there are issues while copying.
	 */
	private static File copyDummyFile(final Context context, final int resource, final String folderName, final String targetName)
			throws IOException {
		File externalFilesDir = context.getExternalFilesDir(folderName);
		if (externalFilesDir == null) {
			return null;
		}
		File targetFile = new File(externalFilesDir, targetName);

		if (!targetFile.exists()) {
			InputStream in = null;
			OutputStream out = null;
			try {
				in = context.getResources().openRawResource(resource);
				out = new FileOutputStream(targetFile);
				byte[] buffer = new byte[4096]; // MAGIC_NUMBER
				int bytesRead;
				while ((bytesRead = in.read(buffer)) != -1) {
					out.write(buffer, 0, bytesRead);
				}
			}
			finally {
				if (in != null) {
					try {
						in.close();
					}
					catch (IOException ex) {
						// do nothing
					}
				}
				if (out != null) {
					try {
						out.close();
					}
					catch (IOException ex) {
						// do nothing
					}
				}
			}
		}
		return targetFile;
	}

	/**
	 * Copy the dummy image and dummy mp3 into the private folder, if not yet there. Required for the Kitkat workaround.
	 *
	 * @return the dummy mp3.
	 */
	private static File copyDummyFiles(Context context) {
		try {
			copyDummyFile(context, R.raw.albumart, "mkdirFiles", "albumart.jpg");
			return copyDummyFile(context, R.raw.silence, "mkdirFiles", "silence.mp3");

		}
		catch (IOException e) {
			Log.e(TAG, "Could not copy dummy files.", e);
			return null;
		}
	}

	/**
	 * Get the stored tree URI.
	 *
	 * @return The tree URI.
	 */
	public static Uri getTreeUri(Context context) {
		return getSharedPreferenceUri(context, R.string.KEY_SD_CARD_ROOT);
	}

	/**
	 * Store a persistent SAF tree uri
	 * @param context
	 */
	public static void setTreeUri(Context context, Uri treeUri)
	{
		setSharedPreferenceUri(context, R.string.KEY_SD_CARD_ROOT, treeUri);
	}

	/**
	 * Retrieve an Uri shared preference.
	 *
	 * @param preferenceId
	 *            the id of the shared preference.
	 * @return the corresponding preference value.
	 */
	public static Uri getSharedPreferenceUri(final Context context, final int preferenceId) {
		String uriString = PreferenceManager.getDefaultSharedPreferences(context)
				.getString(context.getString(preferenceId), null);

		if (uriString == null) {
			return null;
		}
		else {
			return Uri.parse(uriString);
		}
	}

	/**
	 * Set a shared preference for an Uri.
	 *
	 * @param preferenceId
	 *            the id of the shared preference.
	 * @param uri
	 *            the target value of the preference.
	 */
	public static void setSharedPreferenceUri(final Context context, final int preferenceId, final Uri uri) {
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
		if (uri == null) {
			editor.putString(context.getString(preferenceId), null);
		}
		else {
			editor.putString(context.getString(preferenceId), uri.toString());
		}
		editor.commit();
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
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 * @author paulburke
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 * @author paulburke
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 * @author paulburke
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
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
	 * @author paulburke
	 */
	public static String getDataColumn(Context context, Uri uri, String selection,
	                                   String[] selectionArgs) {

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {
				column
		};

		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}

	/**
	 * Get a file path from a Uri. This will get the the path for Storage Access
	 * Framework Documents, as well as the _data field for the MediaStore and
	 * other file-based ContentProviders.<br>
	 * <br>
	 * Callers should check whether the path is local before assuming it
	 * represents a local file.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @author paulburke
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static String getPath(final Context context, final Uri uri) {
		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

		// DocumentProvider
		if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}

				// TODO handle non-primary volumes
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
						Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[] {
						split[1]
				};

				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if (isContentScheme(uri)) {

			// Return the remote address
			if (isGooglePhotosUri(uri))
				return uri.getLastPathSegment();

			return getDataColumn(context, uri, null, null);
		}
		// File
		else if (isFileScheme(uri)) {
			return uri.getPath();
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
			if ("rw".equals(mode)) m = ParcelFileDescriptor.MODE_READ_WRITE;
			else if ("rwt".equals(mode)) m = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE;

			//TODO: Is this any faster?  Otherwise could just rely on resolver
			return ParcelFileDescriptor.open(new File(uri.getPath()), m);
		}
		else
		{
			return context.getContentResolver().openFileDescriptor(uri, mode);
		}
	}


	// copy from InputStream
	//-----------------------------------------------------------------------
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
}
