//package com.anthonymandra.content;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.FileReader;
//import java.io.IOException;
//import java.util.Map;
//
//import android.content.ContentValues;
//import android.media.ExifInterface;
//import android.net.Uri;
//
//import com.anthonymandra.content.RawImage.RawImages;
//import com.anthonymandra.rawdroid.FileManager;
//
//public class DecodeRawClient
//{
//	public class DecodeResponse
//	{
//		Uri mRaw;
//		Uri mThumb;
//		Map<String, String> mMeta;
//
//		DecodeResponse(Uri raw, Uri thumb, Map<String, String> meta)
//		{
//			mRaw = raw;
//			mThumb = thumb;
//			mMeta = meta;
//		}
//
//		public Uri getRawUri()
//		{
//			return mRaw;
//		}
//
//		public Uri getThumbUri()
//		{
//			return mThumb;
//		}
//
//		public Map<String, String> getMetaData()
//		{
//			return mMeta;
//		}
//	}
//
//	RESTfulContentProvider mRawProvider;
//
//	public DecodeRawClient(RESTfulContentProvider rawProvider)
//	{
//		mRawProvider = rawProvider;
//	}
//
//	public Uri decode(Uri raw)
//	{
//		return null;
//	}
//
//	public ContentValues extract(Uri raw) throws IOException
//	{
//		String path = raw.getPath();
//		File rawImage = new File(path);
//		String filename = rawImage.getName();
//		String name = filename.split("\\.")[0];
//
//		ContentValues mediaEntry = new ContentValues();
//
//		if (!thumbExists(filename))
//		{
//			// Dcraw.ExtractThumb(raw.getFilePath(), FileManager.getStoragePath().getFilePath());
//		}
//
//		// convertThumb(filename); // Convert format if necessary
//
//		File thumb = new File(mRawProvider.getCacheThumb(filename));
//		if (!thumb.exists())
//		{
//			throw new IOException(String.format("Thumb extraction failed (%1)", filename));
//		}
//
//		// Ignore errors related to exif, since a failure won't impact
//		// useability
//		// Dcraw.ExtractExif(path, mRawProvider.getCacheLocation());
//		ExifInterface exif;
//		try
//		{
//			exif = new ExifInterface(path);
//			mediaEntry.put(RawImages.ALTITUDE, exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE));
//			mediaEntry.put(RawImages.FLASH, exif.getAttribute(ExifInterface.TAG_FLASH));
//			mediaEntry.put(RawImages.LATITUDE, ExifInterface.TAG_GPS_LATITUDE);
//			mediaEntry.put(RawImages.LONGITUDE, exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE));
//			mediaEntry.put(RawImages.WHITE_BALANCE, exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE));
//		}
//		catch (IOException e)
//		{
//		}
//
//		BufferedReader reader = null;
//		try
//		{
//			reader = new BufferedReader(new FileReader(FileManager.getStoragePath() + File.separator + "meta.dat"));
//			String line;
//			while ((line = reader.readLine()) != null)
//			{
//				String[] field = line.split(",");
//
//				if (field[0].equals("Timestamp"))
//					mediaEntry.put(RawImages.TIMESTAMP, field[1]);
//				else if (field[0].equals("Camera"))
//					mediaEntry.put(RawImages.MODEL, field[1]);
//				else if (field[0].equals("ISO speed"))
//					mediaEntry.put(RawImages.ISO, field[1]);
//				else if (field[0].equals("Shutter"))
//					mediaEntry.put(RawImages.EXPOSURE, field[1]);
//				else if (field[0].equals("Aperture"))
//					mediaEntry.put(RawImages.APERTURE, field[1]);
//				else if (field[0].equals("Focal length"))
//					mediaEntry.put(RawImages.FOCAL_LENGTH, field[1]);
//				else if (field[0].equals("Full size"))
//					mediaEntry.put(RawImages.WIDTH, field[1]);
//			}
//		}
//		catch (FileNotFoundException e)
//		{
//		}
//		finally
//		{
//			if (reader != null)
//				reader.close();
//		}
//
//		String thumbnail = mRawProvider.getCacheThumb(filename);
//
//		mediaEntry.put(RawImages.MEDIA_ID, filename);
//		mediaEntry.put(RawImages.NAME, name);
//		mediaEntry.put(RawImages.THUMB, thumbnail);
//		mediaEntry.put(RawImages.THUMB_URI, RawImages.RAW_THUMB_URI + "/" + filename);
//		mediaEntry.put(RawImages.URI, raw.toString());
//
//		// _data will hold the most relevant image.
//		// thumbnail first
//		// full decode late and on-the-fly
//		mediaEntry.put(RawImages._DATA, thumbnail);
//		return mediaEntry;
//	}
//
//	private boolean isNativeImage(String filename)
//	{
//		int lastDot = filename.lastIndexOf(".");
//		String ext = filename.substring(lastDot + 1, filename.length()).toLowerCase();
//
//		// Compare against supported android image formats
//		return (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("bmp") || ext.equals("gif"));
//		// String mime =
//		// MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
//		// return mime != null && mime.startsWith("image");
//	}
//
//	private boolean thumbExists(String filename)
//	{
//		File cacheImage = new File(mRawProvider.getCacheThumb(filename));
//
//		return isNativeImage(filename) || cacheImage.exists();
//	}
//
//	// public void convertThumb(String filename)
//	// {
//	// File destination = FileManager.getStoragePath();
//	// final File ppmFile = new File(destination + "/" + filename.replaceFirst("[.][^.]+$", "") + ".thumb.ppm");
//	// if (ppmFile.exists())
//	// {
//	// try
//	// {
//	// PPMtoBMP.ReadBitmapFromPPM(ppmFile, destination);
//	// new Thread()
//	// {
//	// @Override
//	// public void run()
//	// {
//	// ppmFile.delete();
//	// }
//	// }.start();
//	// }
//	// catch (IOException e)
//	// {
//	// // TODO Auto-generated catch block
//	// e.printStackTrace();
//	// }
//	// }
//	// }
//}
