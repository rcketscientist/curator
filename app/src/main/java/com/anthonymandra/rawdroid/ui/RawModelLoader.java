package com.anthonymandra.rawdroid.ui;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.anthonymandra.content.Meta;
import com.anthonymandra.rawdroid.data.MetadataTest;
import com.anthonymandra.util.ImageUtil;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.data.ExifOrientationStream;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.signature.ObjectKey;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

class RawModelLoader implements ModelLoader<MetadataTest, InputStream>
{
	private final Context context;
	RawModelLoader(Context c)
	{
		context = c;
	}

	@Nullable
	@Override
	public LoadData<InputStream> buildLoadData(@NonNull MetadataTest metadataTest, int width, int height, @NonNull Options options) {
		Key diskCacheKey = new ObjectKey(metadataTest.getUri());

		return new LoadData<>(diskCacheKey, new RawFetcher(context, metadataTest));
	}

	@Override
	public boolean handles(@NonNull MetadataTest metadataTest) {
		return true;
	}

	private class RawFetcher implements DataFetcher<InputStream>
	{
		private final MetadataTest source;
		private final Context context;

		RawFetcher(Context context, MetadataTest image) {
			this.context = context.getApplicationContext();
			this.source = image;
		}

		@Override
		public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
			try {
				byte[] image = ImageUtil.getThumb(
					context, Uri.parse(source.getUri()), Meta.ImageType.fromInt(source.getType()));
				callback.onDataReady(new ExifOrientationStream(new ByteArrayInputStream(image), 0));
			} catch (Exception e) {
				callback.onLoadFailed(e);
				e.printStackTrace();
			}
		}

		@Override
		public void cleanup() {
			// Do nothing. It's safe to leave a ByteArrayInputStream open.
		}

		@Override
		public void cancel() {
			// Do nothing.
		}

		@NonNull
		@Override
		public Class<InputStream> getDataClass() {
			return InputStream.class;
		}

		@NonNull
		@Override
		public DataSource getDataSource() {
			// TODO: Different caching for local and remote, however, due to decode overhead it's possible we should prefer remote
			// TODO: https://bumptech.github.io/glide/tut/custom-modelloader.html#getdatasource

			return DataSource.LOCAL;
		}
	}
}
