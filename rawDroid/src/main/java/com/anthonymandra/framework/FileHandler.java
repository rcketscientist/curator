package com.anthonymandra.framework;

import android.content.ContentValues;
import android.net.Uri;

import com.anthonymandra.content.ResponseHandler;

import java.io.IOException;

public class FileHandler implements ResponseHandler
{
	private String mId;
	private String mCacheDir;

	public FileHandler(String cacheDir, String id)
	{
		mCacheDir = cacheDir;
		mId = id;
	}

	public String getFileName(String ID)
	{
		return mCacheDir + "/" + ID;
	}

	public void handleResponse(ContentValues values, Uri uri) throws IOException
	{
		/*
		 * InputStream urlStream = response.getEntity().getContent(); FileOutputStream fout = new FileOutputStream(getFileName(mId)); byte[] bytes =
		 * new byte[256]; int r = 0; do { r = urlStream.read(bytes); if (r >= 0) { fout.write(bytes, 0, r); } } while (r >= 0); urlStream.close();
		 * fout.close();
		 */
	}
}
