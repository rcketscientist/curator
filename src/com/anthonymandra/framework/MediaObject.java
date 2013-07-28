package com.anthonymandra.framework;

import java.io.BufferedInputStream;
import java.io.File;

import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;

import com.android.gallery3d.util.ThreadPool.Job;

public interface MediaObject
{
	public static final long INVALID_DATA_VERSION = -1;
	public static long sVersion = 0;

	// NOTE: These type numbers are stored in the image cache, so it should not
	// not be changed without resetting the cache.
	public static final int TYPE_THUMBNAIL = 1;
	public static final int TYPE_MICROTHUMBNAIL = 2;

	public boolean isDirectory();

	public boolean delete();

	public String getName();

	public String getPath();

	public Uri getUri();

	public boolean canDecode();

	public byte[] getImage();

	public byte[] getThumb();

	public BufferedInputStream getThumbInputStream();

	public BufferedInputStream getImageInputStream();

	public long getFileSize();

	public boolean rename(String baseName);

	public boolean copy(File destination);

	public boolean copyThumb(File destination);

	Job<Bitmap> requestImage(com.android.gallery3d.app.GalleryActivity app, int type);

	Job<BitmapRegionDecoder> requestLargeImage();

	public long getDataVersion();
}
