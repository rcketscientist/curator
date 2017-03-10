package com.anthonymandra.widget;

import android.content.Context;
import android.net.Uri;

import com.anthonymandra.content.Meta;
import com.anthonymandra.util.ImageUtil;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.data.ExifOrientationStream;
import com.bumptech.glide.load.model.stream.StreamModelLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

class RawModelLoader implements StreamModelLoader<RawModelLoader.ImageInfo>
{
	private final Context context;
	RawModelLoader(Context c)
	{
		context = c;
	}

	//TODO: they introduced handles 2/15/16, but not in aar yet
//	@Override
//	public boolean handles(Uri model) {
//		return true;
//	}

	public static class ImageInfo
	{
		Uri ImageUri;
		Meta.ImageType ImageType;
	}

	@Override
	public DataFetcher<InputStream> getResourceFetcher(ImageInfo model, int width, int height)
	{
		return new RawFetcher(context, model);
	}

	private class RawFetcher implements DataFetcher<InputStream>
	{
		private final ImageInfo info;
		private final Context context;

		RawFetcher(Context context, ImageInfo info) {
			this.context = context.getApplicationContext();
			this.info = info;
		}

		@Override
		public InputStream loadData(Priority priority) throws Exception
		{
			byte[] image = ImageUtil.getThumb(context, info.ImageUri, info.ImageType);
			return new ExifOrientationStream(new ByteArrayInputStream(image), 0);   //Wrap to turn off orientation
		}

		@Override
		public void cleanup()
		{
			// Do nothing. It's safe to leave a ByteArrayInputStream open.
		}

		@Override
		public String getId()
		{
			return info.ImageUri.toString();
		}

		@Override
		public void cancel()
		{
			// Do nothing.
		}
	}
}
