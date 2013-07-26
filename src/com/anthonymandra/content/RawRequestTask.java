package com.anthonymandra.content;

import java.io.IOException;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

/**
 * Provides a runnable that uses an HttpClient to asynchronously load a given URI. After the network content is loaded, the task delegates handling of
 * the request to a ResponseHandler specialized to handle the given content.
 */
public class RawRequestTask implements Runnable
{
	private Uri mRequest;
	private ResponseHandler mHandler;

	protected Context mAppContext;

	private RESTfulContentProvider mSiteProvider;
	private String mRequestTag;

	public RawRequestTask(Uri request, ResponseHandler handler, Context appContext)
	{
		this(null, null, request, handler, appContext);
	}

	public RawRequestTask(String requestTag, RESTfulContentProvider siteProvider, Uri request, ResponseHandler handler, Context appContext)
	{
		mRequestTag = requestTag;
		mSiteProvider = siteProvider;
		mRequest = request;
		mHandler = handler;
		mAppContext = appContext;
	}

	/**
	 * Carries out the request on the complete URI as indicated by the protocol, host, and port contained in the configuration, and the URI supplied
	 * to the constructor.
	 */
	public void run()
	{
		try
		{
			ContentValues response = execute(mRequest);
			mHandler.handleResponse(response, mRequest);
		}
		catch (IOException e)
		{
			Log.w("rawdroid", "exception processing asynch request", e);
		}
		finally
		{
			if (mSiteProvider != null)
			{
				mSiteProvider.requestComplete(mRequestTag);
			}
		}
	}

	private ContentValues execute(Uri mRequest) throws IOException
	{
		DecodeRawClient client = new DecodeRawClient(mSiteProvider);
		return client.extract(mRequest);
		// HttpClient client = new DefaultHttpClient();
		// return client.execute(mRequest);
	}

	/* public Uri getUri() { return Uri.parse(mRequest.getURI().toString()); } */
}
