package com.anthonymandra.widget;

import android.content.ContentValues;
import android.content.Context;

import com.anthonymandra.content.Meta;
import com.anthonymandra.util.ImageUtils;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.data.ExifOrientationStream;
import com.bumptech.glide.load.model.stream.StreamModelLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class RawModelLoader implements StreamModelLoader<ContentValues>
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
	public DataFetcher<InputStream> getResourceFetcher(ContentValues model, int width, int height)
	{
		return new RawFetcher(context, model);
	}

	private class RawFetcher implements DataFetcher<InputStream>
	{
		private final ContentValues values;
		private final Context context;

		public RawFetcher(Context context, ContentValues uri) {
			this.context = context.getApplicationContext();
			this.values = uri;
		}

		@Override
		public InputStream loadData(Priority priority) throws Exception
		{
			byte[] image = ImageUtils.getThumb(context, values);
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
			return values.getAsString(Meta.URI);
		}

		@Override
		public void cancel()
		{
			// Do nothing.
		}
	}
}
