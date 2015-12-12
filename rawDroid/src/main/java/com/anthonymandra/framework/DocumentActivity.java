package com.anthonymandra.framework;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.rawdroid.R;
import com.crashlytics.android.Crashlytics;

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

public abstract class DocumentActivity extends AppCompatActivity
{
	private static final String TAG = DocumentActivity.class.getSimpleName();
	private static final int REQUEST_PREFIX = 1000;
	private static final int REQUEST_CODE_WRITE_PERMISSION = REQUEST_PREFIX + 1;

	private static final String PREFERENCE_SKIP_WRITE_WARNING = "skip_write_warning";

	protected Enum mCallingMethod;
	protected Object[] mCallingParameters;

	/**
	 * This error is thrown when the application does not have appropriate permission to write.<br><br>
	 *
	 * It is recommended that any process that can catch this exception save the calling method via:<br>
	 * {@link #setWriteMethod(Enum)},<br>
	 * {@link #setWriteParameters(Object[])},<br>
	 * {@link #setWriteResume(Enum, Object[])} (convenience method)<br><br>
	 *
	 * When this error is thrown the {@link DocumentActivity} will attempt
	 * to request permission causing the activity to break at that point.  Upon receiving a
	 * successful result it will attempt to restart a saved method.<br><br>
	 *
	 * As the described process requires a break to the activity in the form of
	 * {@link Activity#startActivityForResult(Intent, int)} it is recommended that the
	 * calling method break upon receiving this exception with the intention that it will
	 * be completed by {@link DocumentActivity#onResumeWriteAction(Enum, Object[])}
	 * after receiving permission.  It is the responsibility of the {@link DocumentActivity} s
	 * ubclass to define {@link DocumentActivity#onResumeWriteAction(Enum, Object[])}.
	 */
	public class WritePermissionException extends IOException
	{
		public WritePermissionException(String message)
		{
			super(message);
		}
	}

	/**
	 * The name of the primary volume (LOLLIPOP).
	 */
	private static final String PRIMARY_VOLUME_NAME = "primary";

	/**
	 * Copy a file. The target file may even be on external SD card for Kitkat.
	 *
	 * @param source
	 *            The source file
	 * @param target
	 *            The target file
	 * @return true if the copying was successful.
	 */
	public boolean copyFile(final File source, final File target)
			throws WritePermissionException
	{
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
					DocumentFile targetDocument = getLollipopDocument(target, false, true);
					if (targetDocument == null)
						return false;
					outStream =
							getContentResolver().openOutputStream(targetDocument.getUri());
				}
				else if (Util.hasKitkat()) {
					// Workaround for Kitkat ext SD card
					Uri uri = MediaStoreUtil.getUriFromFile(this, target.getAbsolutePath());
					outStream = getContentResolver().openOutputStream(uri);
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
					"Error when copying file to " + target.getAbsolutePath(), e);
			return false;
		}
		finally {
			Utils.closeSilently(inStream);
			Utils.closeSilently(outStream);
			Utils.closeSilently(inChannel);
			Utils.closeSilently(outChannel);
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
	public boolean deleteFile(final File file)
			throws WritePermissionException
	{
		// First try the normal deletion.
		if (file.delete()) {
			return true;
		}

		// Try with Storage Access Framework.
		if (Util.hasLollipop()) {
			DocumentFile document = getLollipopDocument(file, false, true);
			if (document == null)
				return false;
			return document.delete();
		}

		// Try the Kitkat workaround.
		if (Util.hasKitkat()) {
			ContentResolver resolver = getContentResolver();

			try {
				Uri uri = MediaStoreUtil.getUriFromFile(this, file.getAbsolutePath());
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
	public boolean moveFile(final File source, final File target) throws WritePermissionException
	{
		// First try the normal rename.
		if (source.renameTo(target)) {
			return true;
		}

		boolean success = copyFile(source, target);
		if (success) {
			success = deleteFile(source);
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
	public boolean renameFolder(final File source,
	                            final File target)
			throws WritePermissionException
	{
		// First try the normal rename.
		if (source.renameTo(target)) {
			return true;
		}
		if (target.exists()) {
			return false;
		}

		// Try the Storage Access Framework if it is just a rename within the same parent folder.
		if (Util.hasLollipop() && source.getParent().equals(target.getParent())) {
			DocumentFile document = getLollipopDocument(source, true, true);
			if (document == null)
				return false;
			if (document.renameTo(target.getName())) {
				return true;
			}
		}

		// Try the manual way, moving files individually.
		if (!mkdir(target)) {
			return false;
		}

		File[] sourceFiles = source.listFiles();

		if (sourceFiles == null) {
			return true;
		}

		for (File sourceFile : sourceFiles) {
			String fileName = sourceFile.getName();
			File targetFile = new File(target, fileName);
			if (!copyFile(sourceFile, targetFile)) {
				// stop on first error
				return false;
			}
		}
		// Only after successfully copying all files, delete files on source folder.
		for (File sourceFile : sourceFiles) {
			if (!deleteFile(sourceFile)) {
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
	public boolean mkdir(final File file)
			throws WritePermissionException
	{
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
			DocumentFile document = getLollipopDocument(file, true, true);
			if (document == null)
				return false;
			// getLollipopDocument implicitly creates the directory.
			return document.exists();
		}

		// Try the Kitkat workaround.
		if (Util.hasKitkat()) {
			ContentResolver resolver = getContentResolver();
			File tempFile = new File(file, "dummyImage.jpg");

			File dummySong = copyDummyFiles(this);
			int albumId = MediaStoreUtil.getAlbumIdFromAudioFile(this, dummySong);
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
				deleteFile(tempFile);
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
	public boolean rmdir(final File file)
			throws WritePermissionException
	{
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
			DocumentFile document = getLollipopDocument(file, true, true);
			if (document == null)
				return false;
			return document.delete();
		}

		// Try the Kitkat workaround.
		if (Util.hasKitkat()) {
			ContentResolver resolver = getContentResolver();
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
	public boolean deleteFilesInFolder(final Context context, final File folder)
			throws WritePermissionException
	{
		boolean totalSuccess = true;

		String[] children = folder.list();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				File file = new File(folder, children[i]);
				if (!file.isDirectory()) {
					boolean success = deleteFile(file);
					if (!success) {
						Log.w(TAG, "Failed to delete file" + children[i]);
						totalSuccess = false;
					}
				}
			}
		}
		return totalSuccess;
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

	/**
	 * Get a list of external SD card paths. (Kitkat or higher.)
	 *
	 * @return A list of external SD card paths.
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	private String[] getExtSdCardPaths() {
		List<String> paths = new ArrayList<String>();
		for (File file : getExternalFilesDirs("external")) {
			if (file != null && !file.equals(getExternalFilesDir("external"))) {
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
	public String getExtSdCardFolder(final File file) {
		String[] extSdPaths = getExtSdCardPaths();
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
	public boolean isOnExtSdCard(final File file) {
		return getExtSdCardFolder(file) != null;
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
	public DocumentFile getDocumentFile(final File file,
	                                    final boolean isDirectory,
	                                    final boolean createDirectories)
			throws WritePermissionException
	{
		// First try the normal way
		if (isWritable(file))
		{
			return DocumentFile.fromFile(file);
		}
		else if (Util.hasLollipop())
		{
			return getLollipopDocument(file, isDirectory, createDirectories);
		}
		else if (Util.hasKitkat())
		{
			// Workaround for Kitkat ext SD card
			//TODO: This probably doesn't work
			Uri uri = MediaStoreUtil.getUriFromFile(this, file.getAbsolutePath());
			DocumentFile.fromSingleUri(this, uri);
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
	private DocumentFile getLollipopDocument(final File file,
	                                         final boolean isDirectory,
	                                         final boolean createDirectories)
			throws WritePermissionException
	{
		Uri treeUri = getTreeUri();

		if (treeUri == null)
		{
			requestWritePermission();
			throw new WritePermissionException("Write permission not found.");
		}

		String fullPath = null;
		try {
			fullPath = file.getCanonicalPath();
		}
		catch (IOException e) {
			return null;
		}

		String baseFolder = null;

		// First try to get the base folder via unofficial StorageVolume API from the URIs.
		String treeBase = getFullPathFromTreeUri(treeUri);
		if (fullPath.startsWith(treeBase)) {
			baseFolder = treeBase;
	}

		if (baseFolder == null) {
			// Alternatively, take root folder from device and assume that base URI works.
			baseFolder = getExtSdCardFolder(file);
		}

		if (baseFolder == null) {
			return null;
		}

		String relativePath = fullPath.substring(baseFolder.length() + 1);

		// start with root of SD card and then parse through document tree.
		DocumentFile document = DocumentFile.fromTreeUri(this, treeUri);
		Crashlytics.setInt("Build", Build.VERSION.SDK_INT);
		Crashlytics.setBool("documentIsNull", document == null );
		Crashlytics.setString("relativePath", relativePath );
		String[] parts = relativePath.split("\\/");
		Crashlytics.setBool("partsIsNull", parts == null );
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
	private String getFullPathFromTreeUri(final Uri treeUri) {
		if (treeUri == null) {
			return null;
		}
		String volumePath = getVolumePath(getVolumeIdFromTreeUri(treeUri));
		if (volumePath == null) {
			return File.separator;
		}
		if (volumePath.endsWith(File.separator)) {
			volumePath = volumePath.substring(0, volumePath.length() - 1);
		}

		String documentPath = getDocumentPathFromTreeUri(treeUri);
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
	private String getVolumePath(final String volumeId) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			return null;
		}

		try {
			StorageManager mStorageManager =
					(StorageManager) getSystemService(Context.STORAGE_SERVICE);

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
	public Uri getTreeUri() {
		// TODO: We could use getPersistedUriPermssions, much more complicated, but ideal
		Uri rootUri = getSharedPreferenceUri(R.string.KEY_SD_CARD_ROOT);
		return getSharedPreferenceUri(R.string.KEY_SD_CARD_ROOT);
	}

	/**
	 * Store a persistent SAF tree uri
	 */
	public void setTreeUri(Uri treeUri)
	{
		setSharedPreferenceUri(R.string.KEY_SD_CARD_ROOT, treeUri);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	protected void checkWriteAccess()
	{
		final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		boolean skipWarning = settings.getBoolean(PREFERENCE_SKIP_WRITE_WARNING, false);
		if (skipWarning)
			return;

		if (Util.hasLollipop())
		{
			List<UriPermission> permissions = getContentResolver().getPersistedUriPermissions();

			android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
			builder.setTitle(R.string.writeAccessTitle);
			builder.setMessage(R.string.requestWriteAccess);
			builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					// Do nothing
				}
			});
			builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					requestWritePermission();
				}
			});
			builder.show();

		}
		else if (Util.hasKitkat())
		{
			android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
			builder.setTitle(R.string.writeAccessTitle);
			builder.setMessage(R.string.kitkatWriteIssue);
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					// Do nothing, just a warning
				}
			});
			builder.show();
		}

		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean(PREFERENCE_SKIP_WRITE_WARNING, true);
		editor.apply();
	}

	@TargetApi(21)
	protected void requestWritePermission()
	{
		if (Util.hasLollipop())
		{
			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					ImageView image = new ImageView(DocumentActivity.this);
					image.setImageDrawable(getDrawable(R.drawable.document_api_guide));
					AlertDialog.Builder builder =
							new AlertDialog.Builder(DocumentActivity.this)
									.setTitle(R.string.dialogWriteRequestTitle)
									.setView(image);
					final AlertDialog dialog = builder.create();
					image.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View v)
						{
							Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
							startActivityForResult(intent, REQUEST_CODE_WRITE_PERMISSION);
							dialog.dismiss();
						}
					});
					dialog.show();
				}
			});
		}
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	@Override
	protected void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode)
		{
			case REQUEST_CODE_WRITE_PERMISSION:
				if (resultCode == RESULT_OK && data != null)
				{
					Uri treeUri = data.getData();
					getContentResolver().takePersistableUriPermission(treeUri,
							Intent.FLAG_GRANT_READ_URI_PERMISSION |
									Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

					setTreeUri(treeUri);

					// This will resume any actions pending write permission
					onResumeWriteAction(mCallingMethod, mCallingParameters);
				}
				break;
		}
	}

	protected abstract void onResumeWriteAction(Enum callingMethod, Object[] callingParameters);
	protected void setWriteMethod(Enum callingMethod)
	{
		mCallingMethod = callingMethod;
	}

	protected void setWriteParameters(Object[] callingParameters)
	{
		mCallingParameters = callingParameters;
	}
	protected void setWriteResume(Enum callingMethod, Object[] callingParameters)
	{
		setWriteMethod(callingMethod);
		setWriteParameters(callingParameters);
	}
	protected void clearWriteResume()
	{
		setWriteMethod(null);
		setWriteParameters(null);
	}

	/**
	 * Retrieve an Uri shared preference.
	 *
	 * @param preferenceId
	 *            the id of the shared preference.
	 * @return the corresponding preference value.
	 */
	public Uri getSharedPreferenceUri(final int preferenceId) {
		String uriString = PreferenceManager.getDefaultSharedPreferences(this)
				.getString(getString(preferenceId), null);

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
	public void setSharedPreferenceUri(final int preferenceId, final Uri uri) {
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		if (uri == null) {
			editor.putString(getString(preferenceId), null);
		}
		else {
			editor.putString(getString(preferenceId), uri.toString());
		}
		editor.commit();
	}
}
