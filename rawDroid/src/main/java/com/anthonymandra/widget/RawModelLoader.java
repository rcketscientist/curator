package com.anthonymandra.widget;

import android.content.Context;
import android.net.Uri;

import com.anthonymandra.util.ImageUtils;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.data.ExifOrientationStream;
import com.bumptech.glide.load.model.stream.StreamModelLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class RawModelLoader implements StreamModelLoader<Uri>
{
	private final Context context;
	public RawModelLoader(Context c)
	{
		context = c;
	}

	//TODO: they introduced handles 2/15/16, but not in aar yet
//	@Override
//	public boolean handles(Uri model) {
//		return true;
//	}

	@Override
	public DataFetcher<InputStream> getResourceFetcher(Uri model, int width, int height)
	{
		return new RawFetcher(context, model);
	}

	private class RawFetcher implements DataFetcher<InputStream>
	{
		private final Uri uri;
		private final Context context;

		public RawFetcher(Context context, Uri uri) {
			this.context = context.getApplicationContext();
			this.uri = uri;
		}

		@Override
		public InputStream loadData(Priority priority) throws Exception
		{
			byte[] image = ImageUtils.getThumb(context, uri);
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
			return uri.toString();
		}

		@Override
		public void cancel()
		{
			// Do nothing.
		}
	}
}
