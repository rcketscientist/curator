//package com.anthonymandra.content;
//
//import java.io.File;
//
//import android.content.ContentValues;
//import android.database.sqlite.SQLiteDatabase;
//import android.net.Uri;
//import android.webkit.MimeTypeMap;
//
///**
// * Parses YouTube Entity data and and inserts it into the finch video content provider.
// */
//public class RawResponseHandler implements ResponseHandler
//{
//	private RESTfulContentProvider mRawVideoProvider;
//
//	private String mQueryText;
//	private boolean isEntry;
//
//	public RawResponseHandler(RESTfulContentProvider restfulProvider)
//	{
//		mRawVideoProvider = restfulProvider;
//	}
//
//	/* Handles the response from the YouTube gdata server, which is in the form of an RSS feed containing references to YouTube videos. */
//	// @Override
//	public void handleResponse(ContentValues values, Uri raw)
//	{
//		parseDecodeData(values, raw);
//	}
//
//	/*
//	 * private void deleteOld() { // delete any old elements, not just ones that match the current query. Cursor old = null; try { SQLiteDatabase db =
//	 * mRawVideoProvider.getDatabase(); old = db.query(RawImage.RawImages.RAW_IMAGE, null, "video." + FinchVideo.Videos.TIMESTAMP +
//	 * " < strftime('%s', 'now', '-" + FLUSH_TIME + "')", null, null, null, null); int c = old.getCount(); if (old.getCount() > 0) { StringBuffer sb =
//	 * new StringBuffer(); boolean next; if (old.moveToNext()) { do { String ID = old.getString(FinchVideo.ID_COLUMN); sb.append(BaseColumns._ID);
//	 * sb.append(" = "); sb.append(ID); // get rid of associated cached thumb files mFinchVideoProvider.deleteFile(ID); next = old.moveToNext(); if
//	 * (next) { sb.append(" OR "); } } while (next); } String where = sb.toString(); db.delete(FinchVideo.Videos.VIDEO, where, null);
//	 * Log.d(Finch.LOG_TAG, "flushed old query results: " + c); } } finally { if (old != null) { old.close(); } } }
//	 */
//
//	/*
//	 * private int parseYoutubeEntity(HttpEntity entity) throws IOException { InputStream youTubeContent = entity.getContent(); InputStreamReader
//	 * inputReader = new InputStreamReader(youTubeContent); int inserted = 0; try { XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
//	 * factory.setNamespaceAware(false); XmlPullParser xpp = factory.newPullParser(); xpp.setInput(inputReader); int eventType = xpp.getEventType();
//	 * String startName = null; ContentValues mediaEntry = null; // iterative pull parsing is a useful way to extract data from // streams, since we
//	 * dont have to hold the DOM model in memory // during the parsing step. while (eventType != XmlPullParser.END_DOCUMENT) { if (eventType ==
//	 * XmlPullParser.START_DOCUMENT) { } else if (eventType == XmlPullParser.END_DOCUMENT) { } else if (eventType == XmlPullParser.START_TAG) {
//	 * startName = xpp.getName(); if ((startName != null)) { if ((ENTRY).equals(startName)) { mediaEntry = new ContentValues();
//	 * mediaEntry.put(FinchVideo.Videos.QUERY_TEXT_NAME, mQueryText); } if ((MEDIA + ":" + CONTENT).equals(startName)) { int c =
//	 * xpp.getAttributeCount(); String mediaUri = null; boolean isMobileFormat = false; for (int i = 0; i < c; i++) { String attrName =
//	 * xpp.getAttributeName(i); String attrValue = xpp.getAttributeValue(i); if ((attrName != null) && URI.equals(attrName)) { mediaUri = attrValue; }
//	 * if ((attrName != null) && (YT + ":" + FORMAT). equals(MOBILE_FORMAT)) { isMobileFormat = true; } } if (isMobileFormat && (mediaUri != null)) {
//	 * mediaEntry.put(URI, mediaUri); } } if ((MEDIA + ":" + THUMBNAIL).equals(startName)) { int c = xpp.getAttributeCount(); for (int i = 0; i < c;
//	 * i++) { String attrName = xpp.getAttributeName(i); String attrValue = xpp.getAttributeValue(i); if (attrName != null) { if
//	 * ("url".equals(attrName)) { mediaEntry.put( FinchVideo.Videos. THUMB_URI_NAME, attrValue); } else if (WIDTH.equals(attrName)) { mediaEntry.put(
//	 * FinchVideo.Videos. THUMB_WIDTH_NAME, attrValue); } else if (HEIGHT.equals(attrName)) { mediaEntry.put( FinchVideo.Videos. THUMB_HEIGHT_NAME,
//	 * attrValue); } } } } if (ENTRY.equals(startName)) { isEntry = true; } } } else if(eventType == XmlPullParser.END_TAG) { String endName =
//	 * xpp.getName(); if (endName != null) { if (ENTRY.equals(endName)) { isEntry = false; } else if (endName.equals(MEDIA + ":" + GROUP)) { // insert
//	 * the complete media group inserted++; // Directly invoke insert on the finch video // provider, without using content resolver. We // would not
//	 * want the content provider to sync this // data back to itself. SQLiteDatabase db = mFinchVideoProvider.getDatabase(); String mediaID = (String)
//	 * mediaEntry.get( FinchVideo.Videos.MEDIA_ID_NAME); // insert thumb uri String thumbContentUri = FinchVideo.Videos.THUMB_URI + "/" + mediaID;
//	 * mediaEntry.put(FinchVideo.Videos. THUMB_CONTENT_URI_NAME, thumbContentUri); String cacheFileName = mFinchVideoProvider.getCacheName(mediaID);
//	 * mediaEntry.put(FinchVideo.Videos._DATA, cacheFileName); Uri providerUri = mFinchVideoProvider. insert(FinchVideo.Videos.CONTENT_URI,
//	 * mediaEntry, db); if (providerUri != null) { String thumbUri = (String) mediaEntry. get(FinchVideo.Videos.THUMB_URI_NAME); // We might consider
//	 * lazily downloading the // image so that it was only downloaded on // viewing. Downloading more aggressively, // could also improve performance.
//	 * mFinchVideoProvider. cacheUri2File(String.valueOf(mediaID), thumbUri); } } } } else if (eventType == XmlPullParser.TEXT) { // newline can turn
//	 * into an extra text event String text = xpp.getText(); if (text != null) { text = text.trim(); if ((startName != null) && (!"".equals(text))){
//	 * if (ID.equals(startName) && isEntry) { int lastSlash = text.lastIndexOf("/"); String entryId = text.substring(lastSlash + 1);
//	 * mediaEntry.put(FinchVideo.Videos.MEDIA_ID_NAME, entryId); } else if ((MEDIA + ":" + TITLE). equals(startName)) { mediaEntry.put(TITLE, text); }
//	 * else if ((MEDIA + ":" + DESCRIPTION).equals(startName)) { mediaEntry.put(DESCRIPTION, text); } } } } eventType = xpp.next(); } // an alternate
//	 * notification scheme, might be to notify only after // all entries have been inserted. } catch (XmlPullParserException e) { Log.d(Ch12.LOG_TAG,
//	 * "could not parse video feed", e); } catch (IOException e) { Log.d(Ch12.LOG_TAG, "could not process video stream", e); } return inserted; }
//	 */
//
//	private boolean isNativeImage(String filename)
//	{
//		int lastDot = filename.lastIndexOf(".");
//		String ext = filename.substring(lastDot + 1);
//
//		String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
//		return mime != null && mime.startsWith("image");
//	}
//
//	private boolean thumbExists(String filename)
//	{
//		File cacheImage = new File(mRawVideoProvider.getCacheThumb(filename));
//
//		return isNativeImage(filename) || cacheImage.exists();
//	}
//
//	public void convertThumb(String filename)
//	{
//		// File destination = FileManager.getStoragePath();
//		// final File ppmFile = new File(destination + "/" + filename.replaceFirst("[.][^.]+$", "") + ".thumb.ppm");
//		// if (ppmFile.exists())
//		// {
//		// try
//		// {
//		// PPMtoBMP.ReadBitmapFromPPM(ppmFile, destination);
//		// new Thread()
//		// {
//		// @Override
//		// public void run()
//		// {
//		// ppmFile.delete();
//		// }
//		// }.start();
//		// }
//		// catch (IOException e)
//		// {
//		// // TODO Auto-generated catch block
//		// e.printStackTrace();
//		// }
//		// }
//	}
//
//	private void parseDecodeData(ContentValues values, Uri raw)
//	{
//		SQLiteDatabase db = mRawVideoProvider.getDatabase();
//		mRawVideoProvider.insert(RawImage.RawImages.CONTENT_URI, values, db);
//	}
//}
