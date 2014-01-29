package com.anthonymandra.framework;

import java.io.File;

public class FileHandlerFactory
{
	private String mCacheDir;

	public FileHandlerFactory(String cacheDir)
	{
		mCacheDir = cacheDir;
		init();
	}

	private void init()
	{
		File cacheDir = new File(mCacheDir);
		if (!cacheDir.exists())
		{
			cacheDir.mkdir();
		}
	}

	public FileHandler newFileHandler(String id)
	{
		return new FileHandler(mCacheDir, id);
	}

	// not really used since ContentResolver uses _data field.
	public File getFile(String raw)
	{
		String cachePath = getDecodeFileName(raw);

		File cacheFile = new File(cachePath);
		if (cacheFile.exists())
		{
			return cacheFile;
		}
		return null;
	}

	public void delete(String raw)
	{
		// Thumb
		File cacheFile = new File(getThumbFileName(raw));
		if (cacheFile.exists())
		{
			cacheFile.delete();
		}

		// Full decode
		cacheFile = new File(getDecodeFileName(raw));
		if (cacheFile.exists())
		{
			cacheFile.delete();
		}
	}

	public String getDecodeFileName(String raw)
	{
		return mCacheDir + "/" + raw.replaceFirst("[.][^.]+$", "") + ".jpg";
	}

	public String getThumbFileName(String raw)
	{
		return mCacheDir + "/" + raw.replaceFirst("[.][^.]+$", "") + ".thumb.jpg";
	}
}
