package com.anthonymandra.framework;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

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
	 * Copy a file within the constraints of SAF.
	 *
	 * @param source
	 *            The source uri
	 * @param target
	 *            The target uri
	 * @return true if the copying was successful.
	 */
	public boolean copyFile(final Uri source, final Uri target)
			throws WritePermissionException
	{
		ParcelFileDescriptor sourcePfd = null;
		DocumentFile targetDoc;
		FileInputStream inStream = null;
		OutputStream outStream = null;

		try
		{
			sourcePfd = FileUtil.getParcelFileDescriptor(this, source, "r");
			targetDoc = getLollipopDocument(target, false, true);
			inStream = new FileInputStream(sourcePfd.getFileDescriptor());
			outStream = getContentResolver().openOutputStream(targetDoc.getUri());

			if (outStream != null)
			{
				byte[] buffer = new byte[4096]; // MAGIC_NUMBER
				int bytesRead;
				while ((bytesRead = inStream.read(buffer)) != -1) {
					outStream.write(buffer, 0, bytesRead);
				}
			}
		}
		catch (IOException e)
		{
			return false;
		}
		finally
		{
			Utils.closeSilently(sourcePfd);
			Utils.closeSilently(inStream);
			Utils.closeSilently(outStream);
		}
		return true;
	}

	/**
	 * Copy a file within the constraints of SAF.
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
			if (isWritable(target))
			{
				// standard way
				outStream = new FileOutputStream(target);
				inChannel = inStream.getChannel();
				outChannel = ((FileOutputStream) outStream).getChannel();
				inChannel.transferTo(0, inChannel.size(), outChannel);
			}
			else {
				if (Util.hasLollipop())
				{
					// Storage Access Framework
					DocumentFile targetDocument = getLollipopDocument(target, false, true);
					if (targetDocument == null)
						return false;
					outStream =
							getContentResolver().openOutputStream(targetDocument.getUri());
				}
				else
				{
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
	 * Delete a file within the constraints of SAF.
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

		return !file.exists();
	}

	/**
	 * Delete a file within the constraints of SAF.  This delete is oriented to files that are
	 * backed ONLY by a URI, such as USB drives on API 23 (6.0).
	 *
	 * {@link #deleteFile(File)} will be more efficient in most cases if a {@link File} is available.
	 *
	 * @param file
	 *            the uri to be deleted.
	 * @return True if successfully deleted.
	 */
	public boolean deleteFile(final Uri file)
			throws WritePermissionException
	{
		DocumentFile document = getLollipopDocument(file, false, true);
		if (document == null)
			return false;
		return document.delete();
	}

	/**
	 * Move a file within the constraints of SAF.
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
	 * Move a file within the constraints of SAF.
	 *
	 * This move is oriented to files that are backed ONLY by a URI, such as USB drives on API 23 (6.0).
	 *
	 * {@link #moveFile(File, File)} will be more efficient in most cases if a {@link File} is available.
	 *
	 * @param source
	 *            The source uri
	 * @param target
	 *            The target uri
	 * @return true if the copying was successful.
	 */
	public boolean moveFile(final Uri source, final Uri target) throws WritePermissionException
	{
		boolean success = copyFile(source, target);
		if (success) {
			success = deleteFile(source);
		}
		return success;
	}

	/**
	 * Rename a folder within the constraints of SAF.
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
	 * Create a folder within the constraints of the SAF.
	 *
	 * @param folder
	 *            The folder to be created.
	 * @return True if creation was successful.
	 */
	public boolean mkdir(final File folder)
			throws WritePermissionException
	{
		if (folder.exists()) {
			// nothing to create.
			return folder.isDirectory();
		}

		// Try the normal way
		if (folder.mkdir()) {
			return true;
		}

		// Try with Storage Access Framework.
		if (Util.hasLollipop()) {
			DocumentFile document = getLollipopDocument(folder, true, true);
			if (document == null)
				return false;
			// getLollipopDocument implicitly creates the directory.
			return document.exists();
		}

		return false;
	}

	/**
	 * Create a folder within the constraints of the SAF.
	 *
	 * @param folder
	 *            The folder to be created.
	 * @return True if creation was successful.
	 */
	public boolean mkdir(final Uri folder)
			throws WritePermissionException
	{
		DocumentFile document = getLollipopDocument(folder, true, true);
		if (document == null)
			return false;
		// getLollipopDocument implicitly creates the directory.
		return document.exists();
	}

	/**
	 * Delete a folder within the constraints of SAF
	 *
	 * @param folder
	 *            The folder
	 *
	 * @return true if successful.
	 */
	public boolean rmdir(final File folder)
			throws WritePermissionException
	{
		if (!folder.exists()) {
			return true;
		}
		if (!folder.isDirectory()) {
			return false;
		}
		String[] fileList = folder.list();
		if (fileList != null && fileList.length > 0) {
			// Delete only empty folder.
			return false;
		}

		// Try the normal way
		if (folder.delete()) {
			return true;
		}

		// Try with Storage Access Framework.
		if (Util.hasLollipop()) {
			DocumentFile document = getLollipopDocument(folder, true, true);
			if (document == null)
				return false;
			return document.delete();
		}

		return !folder.exists();
	}

	/**
	 * Delete a folder within the constraints of SAF
	 *
	 * @param folder
	 *            The folder
	 *
	 * @return true if successful.
	 */
	public boolean rmdir(final Uri folder)
			throws WritePermissionException
	{
		DocumentFile folderDoc = getLollipopDocument(folder, true, true);
		if (!folderDoc.exists()) {
			return true;
		}
		if (!folderDoc.isDirectory()) {
			return false;
		}

		if (folderDoc.listFiles().length > 0)
			return false;

		return folderDoc.delete();
	}

	/**
	 * Delete all files in a folder.
	 *
	 * @param folder
	 *            the folder
	 * @return true if successful.
	 */
	public boolean deleteFilesInFolder(final File folder)
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
	 * Delete all files in a folder.
	 *
	 * @param folder
	 *            the folder
	 * @return true if successful.
	 */
	public boolean deleteFilesInFolder(final Uri folder)
			throws WritePermissionException
	{
		boolean totalSuccess = true;
		DocumentFile folderDoc = getLollipopDocument(folder, true, true);
		DocumentFile[] children = folderDoc.listFiles();
		for (DocumentFile child : children)
		{
			if (!child.isDirectory())
			{
				if (!child.delete())
				{
					Log.w(TAG, "Failed to delete file" + child);
					totalSuccess = false;
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
	public DocumentFile getDocumentFile(final Uri file,
	                                    final boolean isDirectory,
	                                    final boolean createDirectories)
			throws WritePermissionException
	{
		return getLollipopDocument(file, isDirectory, createDirectories);
	}

	/**
	 * Get a DocumentFile corresponding to the given file (for writing on ExtSdCard on Android 5). If the file is not
	 * existing, it is created.
	 *
	 * @param uri
	 *            The target uri.
	 * @param isDirectory
	 *            flag indicating if the file should be a directory.
	 * @param createDirectories
	 *            flag indicating if intermediate path directories should be created if not existing.
	 * @return The DocumentFile
	 */
	private DocumentFile getLollipopDocument(final Uri uri,
											 final boolean isDirectory,
											 final boolean createDirectories)
			throws WritePermissionException
	{
		Uri treeUri = getTreeUri();

		if (treeUri == null)
		{
			requestWritePermission();
			throw new WritePermissionException(
					"Write permission not found.  This indicates a SAF write permission was requested.  " +
					"The app should store any parameters necessary to resume write here.");
		}

		DocumentFile target;
		if (isDirectory)
		{
			target = DocumentFile.fromTreeUri(this, uri);
		}
		else
		{
			target = DocumentFile.fromSingleUri(this, uri);
		}

		if (target.exists())
		{
			return target;
		}

		DocumentFile permissionRoot = DocumentFile.fromTreeUri(this, treeUri);
		DocumentFile parent = target.getParentFile();

		// If needed create the file or directory
		if (isDirectory)
		{
			parent.createDirectory(target.getName());
		}
		else
		{
			// TODO: is null mime an issue? RawDocumentFile.createFle will simply not append an ext
			// So if the name contains the desired extension this is fine.  Another handler could
			// be an issue.  Since Android mime support is pretty awful/silly, best off not dealing with mime.
			parent.createFile(null, target.getName());
		}

		// If desired create the tree up to the root working backwards
		if (createDirectories)
		{
			// Stop if the parent exists or we've reached the permission root
			while (!parent.exists() && !parent.equals(permissionRoot))
			{
				DocumentFile opa = parent.getParentFile();
				opa.createDirectory(parent.getName());
				parent = opa;
			}
		}

		return target;
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
			throw new WritePermissionException(
					"Write permission not found.  This indicates a SAF write permission was requested.  " +
					"The app should store any parameters necessary to resume write here.");
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

		String[] parts = relativePath.split("\\/");
		for (int i = 0; i < parts.length; i++) {
			if (document == null)
			{
				Crashlytics.setInt("Build", Build.VERSION.SDK_INT);
				Crashlytics.setString("relativePath", relativePath );
				Crashlytics.setString("parts[i-1]: ", i > 0 ? parts[i-1] : "n/a" );
				Crashlytics.setString("parts[i]: ", parts[i] );
				Crashlytics.logException(new Exception("Null Document"));
				return null;
			}
			// FIXME: Find file can be exceptionally slow, attempting alternative method in uri version
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
							intent.putExtra(DocumentsContract.EXTRA_PROMPT, getString(R.string.allowWrite));
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
		Crashlytics.setString("WriteMethod", callingMethod.toString());
		mCallingMethod = callingMethod;
	}

	protected void setWriteParameters(Object[] callingParameters)
	{
		Crashlytics.setBool("WriteParametersIsNull", callingParameters == null);
		mCallingParameters = callingParameters;
	}
	protected void setWriteResume(Enum callingMethod, Object[] callingParameters)
	{
		setWriteMethod(callingMethod);
		setWriteParameters(callingParameters);
	}
	protected void clearWriteResume()
	{
		mCallingMethod = null;
		mCallingParameters = null;
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
