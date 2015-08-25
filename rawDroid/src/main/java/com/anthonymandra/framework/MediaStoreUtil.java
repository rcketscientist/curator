package com.anthonymandra.framework;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;

import java.io.File;

//https://github.com/jeisfeld/Augendiagnose/

/**
 * Utility class for handling the media store.
 */
public class MediaStoreUtil
{
	/**
	 * The size of a mini thumbnail.
	 */
	public static final int MINI_THUMB_SIZE = 512;

	/**
	 * Hide default constructor.
	 */
	private MediaStoreUtil() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Get a real file path from the URI of the media store.
	 *
	 * @param contentUri
	 *            Thr URI of the media store
	 * @return the file path.
	 */
	@SuppressWarnings("static-access")
	public static String getRealPathFromUri(final Context context, final Uri contentUri) {
		Cursor cursor = null;
		try {
			String[] proj = { MediaStore.Images.Media.DATA };
			cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
			int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			cursor.moveToFirst();
			return cursor.getString(columnIndex);
		}
		catch (Exception e) {
			return null;
		}
		finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	/**
	 * Retrieve a the image id of an image in the Mediastore from the path.
	 *
	 * @param path
	 *            The path of the image
	 * @return the image id.
	 * @throws ImageNotFoundException
	 *             thrown if the image is not found in the media store.
	 */
	@SuppressWarnings("static-access")
	private static int getImageId(final Context context, final String path) throws ImageNotFoundException {
		ContentResolver resolver = context.getContentResolver();

		try {
			Cursor imagecursor = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					new String[] { MediaStore.Images.Media._ID }, MediaStore.Images.Media.DATA + " = ?",
					new String[] { path }, MediaStore.Images.Media.DATE_ADDED + " desc");
			imagecursor.moveToFirst();

			if (!imagecursor.isAfterLast()) {
				int imageId = imagecursor.getInt(imagecursor.getColumnIndex(MediaStore.Images.Media._ID));
				imagecursor.close();
				return imageId;
			}
			else {
				imagecursor.close();
				throw new ImageNotFoundException();
			}
		}
		catch (Exception e) {
			throw new ImageNotFoundException(e);
		}
	}

	/**
	 * Get an Uri from an file path.
	 *
	 * @param path
	 *            The file path.
	 * @return The Uri.
	 */
	public static Uri getUriFromFile(final Context context, final String path) {
		ContentResolver resolver = context.getContentResolver();

		Cursor filecursor = resolver.query(MediaStore.Files.getContentUri("external"),
				new String[] { BaseColumns._ID }, MediaStore.MediaColumns.DATA + " = ?",
				new String[] { path }, MediaStore.MediaColumns.DATE_ADDED + " desc");
		filecursor.moveToFirst();

		if (filecursor.isAfterLast()) {
			filecursor.close();
			ContentValues values = new ContentValues();
			values.put(MediaStore.MediaColumns.DATA, path);
			return resolver.insert(MediaStore.Files.getContentUri("external"), values);
		}
		else {
			int imageId = filecursor.getInt(filecursor.getColumnIndex(BaseColumns._ID));
			Uri uri = MediaStore.Files.getContentUri("external").buildUpon().appendPath(
					Integer.toString(imageId)).build();
			filecursor.close();
			return uri;
		}
	}

	/**
	 * Get the Album Id from an Audio file.
	 *
	 * @param file
	 *            The audio file.
	 * @return The Album ID.
	 */
	@SuppressWarnings("resource")
	public static int getAlbumIdFromAudioFile(final Context context, final File file) {
		ContentResolver resolver = context.getContentResolver();
		Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				new String[] { MediaStore.Audio.AlbumColumns.ALBUM_ID },
				MediaStore.MediaColumns.DATA + "=?",
				new String[] { file.getAbsolutePath() }, null);
		if (cursor == null || !cursor.moveToFirst()) {
			// Entry not available - create entry.
			if (cursor != null) {
				cursor.close();
				cursor = null;
			}
			ContentValues values = new ContentValues();
			values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
			values.put(MediaStore.MediaColumns.TITLE, "{MediaWrite Workaround}");
			values.put(MediaStore.MediaColumns.SIZE, file.length());
			values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
			values.put(MediaStore.Audio.AudioColumns.IS_MUSIC, true);
			resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
		}
		cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				new String[] { MediaStore.Audio.AlbumColumns.ALBUM_ID },
				MediaStore.MediaColumns.DATA + "=?",
				new String[] { file.getAbsolutePath() }, null);
		if (cursor == null) {
			return 0;
		}
		if (!cursor.moveToFirst()) {
			cursor.close();
			return 0;
		}
		int albumId = cursor.getInt(0);
		cursor.close();
		return albumId;
	}

	/**
	 * Add a picture to the media store (via scanning).
	 *
	 * @param path
	 *            the path of the image.
	 */
	public static void addFileToMediaStore(final Context context, final String path) {
		Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		File file = new File(path);
		Uri contentUri = Uri.fromFile(file);
		mediaScanIntent.setData(contentUri);
		context.sendBroadcast(mediaScanIntent);
	}

	/**
	 * Retrieve a thumbnail of a bitmap from the mediastore.
	 *
	 * @param path
	 *            The path of the image
	 * @param maxSize
	 *            The maximum size of this bitmap (used for selecting the sample size)
	 * @return the thumbnail.
	 */
	public static Bitmap getThumbnailFromPath(final Context context, final String path, final int maxSize) {
		ContentResolver resolver = context.getContentResolver();

		try {
			int imageId = getImageId(context, path);

			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = MINI_THUMB_SIZE / maxSize;
			options.inDither = true;
			return MediaStore.Images.Thumbnails.getThumbnail(resolver, imageId, MediaStore.Images.Thumbnails.MINI_KIND,
					options);

		}
		catch (ImageNotFoundException e) {
			return null;
		}
	}

	/**
	 * Add a picture to the media store (via scanning).
	 *
	 * @param path
	 *            the path of the image.
	 */
	public static void addPictureToMediaStore(final Context context, final String path) {
		Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		File file = new File(path);
		Uri contentUri = Uri.fromFile(file);
		mediaScanIntent.setData(contentUri);
		context.sendBroadcast(mediaScanIntent);
	}

	/**
	 * Delete the thumbnail of a bitmap.
	 *
	 * @param path
	 *            The path of the image
	 */
	public static void deleteThumbnail(final Context context, final String path) {
		ContentResolver resolver = context.getContentResolver();

		try {
			int imageId = getImageId(context, path);
			resolver.delete(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
					MediaStore.Images.Thumbnails.IMAGE_ID + " = ?", new String[] { "" + imageId });
		}
		catch (ImageNotFoundException e) {
			// ignore
		}
	}

	/**
	 * Utility exception to be thrown if an image cannot be found.
	 */
	private static class ImageNotFoundException extends Exception {
		/**
		 * The default serial version id.
		 */
		private static final long serialVersionUID = 1L;

		public ImageNotFoundException() {
		}

		public ImageNotFoundException(final Throwable e) {
			super(e);
		}
	}

}
