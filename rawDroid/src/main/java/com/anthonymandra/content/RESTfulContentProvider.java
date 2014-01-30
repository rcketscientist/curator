//package com.anthonymandra.content;
//
//import java.io.UnsupportedEncodingException;
//import java.net.URLEncoder;
//import java.util.HashMap;
//import java.util.Map;
//
//import android.content.ContentProvider;
//import android.content.ContentValues;
//import android.database.sqlite.SQLiteDatabase;
//import android.net.Uri;
//import android.os.Environment;
//import android.util.Log;
//
//import com.anthonymandra.framework.FileHandlerFactory;
//
///**
// * Encapsulates functions for asynchronous RESTful requests so that subclass content providers can use them for initiating request while still using
// * custom methods for interpreting REST based content such as, RSS, ATOM, JSON, etc.
// */
//public abstract class RESTfulContentProvider extends ContentProvider
//{
//	protected FileHandlerFactory mFileHandlerFactory;
//	private Map<String, RawRequestTask> mRequestsInProgress = new HashMap<String, RawRequestTask>();
//
//	private static String storagePath;
//	private static boolean mExternalStorageAvailable = false;
//	private static boolean mExternalStorageWriteable = false;
//	static
//	{
//		String state = Environment.getExternalStorageState();
//
//		if (Environment.MEDIA_MOUNTED.equals(state))
//		{
//			// We can read and write the media
//			mExternalStorageAvailable = mExternalStorageWriteable = true;
//		}
//		else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
//		{
//			// We can only read the media
//			mExternalStorageAvailable = true;
//			mExternalStorageWriteable = false;
//		}
//		else
//		{
//			// Something else is wrong, but all we need to know is we can
//			// neither read nor write
//			mExternalStorageAvailable = mExternalStorageWriteable = false;
//		}
//	}
//
//	@Override
//	public boolean onCreate()
//	{
//		if (mExternalStorageAvailable && mExternalStorageWriteable)
//		{
//			storagePath = getContext().getExternalCacheDir().getPath();
//		}
//		else
//		{
//			storagePath = getContext().getCacheDir().getPath();
//		}
//		mFileHandlerFactory = new FileHandlerFactory(storagePath);
//		return true;
//	}
//
//	public abstract Uri insert(Uri uri, ContentValues cv, SQLiteDatabase db);
//
//	private RawRequestTask getRequestTask(String queryText)
//	{
//		return mRequestsInProgress.get(queryText);
//	}
//
//	/**
//	 * Allows the subclass to define the database used by a response handler.
//	 *
//	 * @return database passed to response handler.
//	 */
//	public abstract SQLiteDatabase getDatabase();
//
//	public void requestComplete(String mQueryText)
//	{
//		synchronized (mRequestsInProgress)
//		{
//			mRequestsInProgress.remove(mQueryText);
//		}
//	}
//
//	/**
//	 * Abstract method that allows a subclass to define the type of handler that should be used to parse the response of a given request.
//	 *
//	 * @param requestTag
//	 *            unique tag identifying this request.
//	 * @return The response handler created by a subclass used to parse the request response.
//	 */
//	protected abstract ResponseHandler newResponseHandler(String requestTag);
//
//	/*
//	 * RawRequestTask newQueryTask(String requestTag, String url) { RawRequestTask requestTask; final HttpGet get = new HttpGet(url); ResponseHandler
//	 * handler = newResponseHandler(requestTag); requestTask = new RawRequestTask(requestTag, this, get, handler, getContext());
//	 * mRequestsInProgress.put(requestTag, requestTask); return requestTask; }
//	 */
//
//	RawRequestTask newDecodeTask(String requestTag, Uri rawUri)
//	{
//		RawRequestTask requestTask;
//
//		ResponseHandler handler = newResponseHandler(requestTag);
//		requestTask = new RawRequestTask(requestTag, this, rawUri, handler, getContext());
//
//		mRequestsInProgress.put(requestTag, requestTask);
//		return requestTask;
//	}
//
//	// DecodeHandler factory???
//	public void asyncDecodeRequest(String rawTag, Uri rawUri)
//	{
//		synchronized (mRequestsInProgress)
//		{
//			RawRequestTask requestTask = getRequestTask(rawTag);
//			if (requestTask == null)
//			{
//				requestTask = newDecodeTask(rawTag, rawUri);
//				Thread t = new Thread(requestTask);
//				// allows other requests to run in parallel.
//				t.start();
//			}
//		}
//	}
//
//	/**
//	 * Creates a new worker thread to carry out a RESTful network invocation.
//	 *
//	 * @param queryTag
//	 *            unique tag that identifies this request.
//	 *
//	 * @param queryUri
//	 *            the complete URI that should be access by this request.
//	 */
//	/*
//	 * public void asyncQueryRequest(String queryTag, String queryUri) { synchronized (mRequestsInProgress) { RawRequestTask requestTask =
//	 * getRequestTask(queryTag); if (requestTask == null) { requestTask = newQueryTask(queryTag, queryUri); Thread t = new Thread(requestTask); //
//	 * allows other requests to run in parallel. t.start(); } } }
//	 */
//
//	/**
//	 * Spawns a thread to download bytes from a url and store them in a file, such as for storing a thumbnail.
//	 *
//	 * @param id
//	 *            the database id used to reference the downloaded url.
//	 */
//	/*
//	 * public void cacheUri2File(String id, String url) { // use media id as a unique request tag final HttpGet get = new HttpGet(url); RawRequestTask
//	 * requestTask = new RawRequestTask(get, mFileHandlerFactory.newFileHandler(id), getContext()); Thread t = new Thread(requestTask); t.start(); }
//	 */
//
//	public void deleteFile(String raw)
//	{
//		mFileHandlerFactory.delete(raw);
//	}
//
//	public String getCacheThumb(String raw)
//	{
//		return mFileHandlerFactory.getThumbFileName(raw);
//	}
//
//	public String getCacheRaw(String raw)
//	{
//		return mFileHandlerFactory.getDecodeFileName(raw);
//	}
//
//	public String getCacheLocation()
//	{
//		return storagePath;
//	}
//
//	public static String encode(String gDataQuery)
//	{
//		try
//		{
//			return URLEncoder.encode(gDataQuery, "UTF-8");
//		}
//		catch (UnsupportedEncodingException e)
//		{
//			Log.d("rawdroid", "could not decode UTF-8," + " this should not happen");
//		}
//		return null;
//	}
//}
