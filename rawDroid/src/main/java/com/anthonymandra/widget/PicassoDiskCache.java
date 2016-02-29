//package com.anthonymandra.widget;
//
//import android.app.FragmentManager;
//import android.net.Uri;
//
//import com.anthonymandra.framework.DiskCache;
//import com.anthonymandra.framework.DiskCacheManager;
//import com.squareup.picasso.Downloader;
//import com.squareup.picasso.NetworkPolicy;
//
//import java.io.IOException;
//import java.io.InputStream;
//
//public class PicassoDiskCache extends DiskCacheManager implements Downloader
//{
//	//TODO: THIS IS NONFUNCTIONAL, disk cache in picasso without HTTP is a PIA
//	private final String cacheDir = "_thumb";
//
//	/**
//	 * Creating a new ImageCache object using the specified parameters.
//	 *
//	 * @param cacheParams
//	 *            The cache parameters to use to initialize the cache
//	 */
//	public PicassoDiskCache(FragmentManager fm, DiskCache.CacheParams cacheParams)
//	{
//		addImageCache(fm, cacheParams);
//	}
//
//	@Override
//	public Response load(Uri uri, int networkPolicy) throws IOException
//	{
//		final String dataString = String.valueOf(uri);
//		if (NetworkPolicy.isOfflineOnly(networkPolicy))
//		{
//			InputStream stream = mCache.getStreamFromDiskCache(dataString);
//			if (stream != null)
//			{
//				return new Response(stream, true, stream.available());
//			}
//			return null;
//		}
//
//		if (NetworkPolicy.shouldReadFromDiskCache(networkPolicy))
//		{
//			InputStream stream = mCache.getStreamFromDiskCache(dataString);
//			if (stream != null)
//			{
//				return new Response(stream, true, stream.available());
//			}
//		}
//		return null;
//
//
//	}
//
//	@Override
//	public void shutdown()
//	{
//
//	}
//}
