package com.anthonymandra.rawdroid;

import android.content.Context;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.android.gallery3d.data.MediaItem;
import com.android.legacy.ui.GLCanvas;
import com.android.legacy.ui.GLRootView;
import com.android.legacy.ui.GLView;
import com.android.legacy.ui.ImageViewer;
import com.android.legacy.ui.ImageViewer.ImageData;
import com.anthonymandra.content.Meta;
import com.anthonymandra.dcraw.LibRaw;
import com.anthonymandra.framework.AsyncTask;
import com.anthonymandra.framework.LocalImage;
import com.anthonymandra.framework.MetaWakefulReceiver;
import com.anthonymandra.framework.Util;
import com.anthonymandra.framework.ViewerActivity;
import com.anthonymandra.framework.ViewlessCursorAdapter;

import java.io.IOException;

public class LegacyViewerActivity extends ViewerActivity
{
	private static final String TAG = LegacyViewerActivity.class.getSimpleName();

	private ImageViewer mImageViewer;
	private MyImageViewerModel mModel;

	private GLRootView mGLRootView;
	private GLView mRootPane = new GLView()
	{

		@Override
		protected void renderBackground(GLCanvas view)
		{
			view.clearBuffer();
		}

		@Override
		protected void onLayout(boolean changed, int left, int top, int right, int bottom)
		{
			if (mImageViewer != null)
			{
				mImageViewer.layout(0, 0, right - left, bottom - top);
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener(this);

		mImageViewer = new ImageViewer(this);
		mImageViewer.setModel(mModel);
		mImageViewer.setScaleListener(this);
		mRootPane.addComponent(mImageViewer);
		mModel = new MyImageViewerModel(this, null);
		mModel.requestNextImageWithMeta();
	}

	@Override
	public int getContentView()
	{
		return R.layout.legacy_twopane;
	}

	@Override
    protected void lookupViews() {
        super.lookupViews();
        mGLRootView = (GLRootView) findViewById(R.id.gl_root_view);
    }

	@Override
	protected void onZoomLockChanged(boolean locked)
	{
		mImageViewer.setZoomLock(locked);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mGLRootView.onResume();
		mGLRootView.setContentPane(mRootPane);

		if (mModel.isRecycled())
		{
			mModel.resetBitmaps();
			mModel.requestNextImageWithMeta();
		}
		mImageViewer.prepareTextures();
	}

	@Override
	public void onPhotoChanged(int index, Uri item)
	{
		super.onPhotoChanged(index, item);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mGLRootView.onPause();
		mGLRootView.lockRenderThread();
		try
		{
			mImageViewer.freeTextures();
		}
		finally
		{
			mGLRootView.unlockRenderThread();
		}
	}

	public void loadExif()
	{
		// If the meta is processed populate it
		Cursor c = getMetaCursor();
		c.moveToFirst();
		if (c.getInt(Meta.PROCESSED_COLUMN) != 0)
		{
			populateMeta(c);
		}
		else
		{
			// Otherwise, queue a high priority parse
			MetaWakefulReceiver.startPriorityMetaService(this, mCurrentUri);
		}
		c.close();
	}

    @Override
	public MediaItem getCurrentItem()
	{
		if (mImageIndex < 0 || mImageIndex >= mModel.getCount())
		{
			Toast.makeText(this, "Error 2x01: Failed to load requested image.  If this continues please email details leading to this bug!", Toast.LENGTH_LONG).show();
			if (mModel.getCount() == 0)
			{
				finish();
			}
			else
			{
				mImageIndex = 0;
			}
		}
		return new LocalImage(this, mModel.getCursor());
	}

    @Override
    public Bitmap getCurrentBitmap() {
        return null;    // Not needed since we manage the histogram manually
    }

    @Override
	protected void updateAfterDelete()
	{
		mMediaItems.remove(getCurrentItem()); //TODO: This is dangerous! Just a kludge for now.
		if (mModel.getCount() == 0)
		{
			onBackPressed();
		}

		deleteToNext();
	}

	@Override
	protected void updateAfterRestore()
	{
//		updateViewerItems();
		mModel.refresh();
	}

	public void decrementImageIndex()
	{
		--mImageIndex;
	}

	public void incrementImageIndex()
	{
		++mImageIndex;
	}

	@Override
	public void onLoadingStarted()
	{
		//unneeded
	}

	@Override
	public void onLoadingFinished(boolean loadingFailed)
	{
		//unneeded
	}

	private class MyImageViewerModel extends ViewlessCursorAdapter implements ImageViewer.Model
	{
		private BitmapRegionDecoder mLargeBitmap;
		private Bitmap mScreenNails[] = new Bitmap[3]; // prev, curr, next

		public MyImageViewerModel(Context context, Cursor cursor)
		{
			super(context, cursor);
		}

		public BitmapRegionDecoder getLargeBitmap()
		{
			return mLargeBitmap;
		}

		public boolean isRecycled()
		{
			for (Bitmap screennail : mScreenNails)
			{
				if (screennail != null && screennail.isRecycled())
					return true;
			}
			return false;
		}

		public void resetCurrent()
		{
			if (mScreenNails[INDEX_CURRENT] != null)
			{
				mScreenNails[INDEX_CURRENT].recycle();
				mScreenNails[INDEX_CURRENT] = null;
			}
		}

		public void resetNext()
		{
			if (mScreenNails[INDEX_NEXT] != null)
			{
				mScreenNails[INDEX_NEXT].recycle();
				mScreenNails[INDEX_NEXT] = null;
			}
		}

		public void resetPrevious()
		{
			if (mScreenNails[INDEX_PREVIOUS] != null)
			{
				mScreenNails[INDEX_PREVIOUS].recycle();
				mScreenNails[INDEX_PREVIOUS] = null;
			}
		}

		public void resetBitmaps()
		{
			resetPrevious();
			resetCurrent();
			resetNext();
		}

		public void refresh()
		{
			resetBitmaps();
			requestNextImageWithMeta();
		}

		public ImageData getImageData(int which)
		{
			Bitmap screennail = mScreenNails[which];
			if (screennail == null)
				return null;

			int width;
			int height;

			if (which == INDEX_CURRENT && mLargeBitmap != null)
			{
				width = mLargeBitmap.getWidth();
				height = mLargeBitmap.getHeight();
			}
			else
			{
				// We cannot get the size of image before getting the
				// full-size image. In the future, we should add the data to
				// database or get it from the header in runtime. Now, we
				// just use the thumb-nail image to estimate the size
				float scaleW = (float) displayWidth / screennail.getWidth();
				float scaleH = (float) displayHeight / screennail.getHeight();
				float scale = Math.min(scaleW, scaleH);
				// float scale = (float) TARGET_LENGTH / Math.max(screennail.getWidth(), screennail.getHeight());
				width = Math.round(screennail.getWidth() * scale);
				height = Math.round(screennail.getHeight() * scale);
			}
			return new ImageData(width, height, screennail);
		}

		public void next()
		{
			if (mImageIndex >= mCursor.getCount() - 1)
			{
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						Toast.makeText(LegacyViewerActivity.this, R.string.lastImage, Toast.LENGTH_SHORT).show();
					}

				});
				return;
			}
			incrementImageIndex();
			Bitmap[] screenNails = mScreenNails;

			if (screenNails[INDEX_PREVIOUS] != null)
			{
				screenNails[INDEX_PREVIOUS].recycle();
				screenNails[INDEX_PREVIOUS] = null;
			}
			screenNails[INDEX_PREVIOUS] = screenNails[INDEX_CURRENT];
			screenNails[INDEX_CURRENT] = screenNails[INDEX_NEXT];
			screenNails[INDEX_NEXT] = null;

			if (mLargeBitmap != null)
			{
				mLargeBitmap.recycle();
				mLargeBitmap = null;
			}

			requestNextImageWithMeta();
		}

		public void previous()
		{
			if (mImageIndex == 0)
			{
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						Toast.makeText(LegacyViewerActivity.this, R.string.firstImage, Toast.LENGTH_SHORT).show();
					}
				});
				return;
			}
			decrementImageIndex();
			Bitmap[] screenNails = mScreenNails;

			if (screenNails[INDEX_NEXT] != null)
			{
				screenNails[INDEX_NEXT].recycle();
				screenNails[INDEX_NEXT] = null;
			}
			screenNails[INDEX_NEXT] = screenNails[INDEX_CURRENT];
			screenNails[INDEX_CURRENT] = screenNails[INDEX_PREVIOUS];
			screenNails[INDEX_PREVIOUS] = null;

			if (mLargeBitmap != null)
			{
				mLargeBitmap.recycle();
				mLargeBitmap = null;
			}

			requestNextImageWithMeta();
		}

		public void deleteToPrevious()
		{
			Bitmap[] screenNails = mScreenNails;

			if (screenNails[INDEX_CURRENT] != null)
			{
				screenNails[INDEX_CURRENT].recycle();
				screenNails[INDEX_CURRENT] = null;
			}
			screenNails[INDEX_CURRENT] = screenNails[INDEX_PREVIOUS];
			screenNails[INDEX_PREVIOUS] = null;

			if (mLargeBitmap != null)
			{
				mLargeBitmap.recycle();
				mLargeBitmap = null;
			}

			mImageViewer.initiateDeleteTransition();
		}

		public void deleteToNext()
		{
			Bitmap[] screenNails = mScreenNails;

			if (mImageIndex >= mCursor.getCount())
			{
				// swap out the deleted image
				previous();
			}
			else
			{
				// swap out the deleted image
				com.android.legacy.util.Utils.swap(screenNails, INDEX_CURRENT, INDEX_PREVIOUS);
				// then tap the existing next functionality by stepping back index
				decrementImageIndex();
				next();
			}
		}

		public void updateScreenNail(int index, Bitmap screenNail)
		{
			int offset = (index - mImageIndex) + 1; // Zero-based -1,0,1 (0,1,2)

			if (screenNail != null)
			{
				if (offset < 0 || offset > 2)
				{
					screenNail.recycle();
					return;
				}
				mScreenNails[offset] = screenNail;
				mImageViewer.notifyScreenNailInvalidated(offset);
			}
		}

		public void updateLargeImage(int index, BitmapRegionDecoder largeBitmap)
		{
			int offset = (index - mImageIndex) + 1;

			if (largeBitmap != null)
			{
				if (offset != INDEX_CURRENT)
				{
					largeBitmap.recycle();
					return;
				}

				mLargeBitmap = largeBitmap;
				mImageViewer.notifyLargeBitmapInvalidated();
				// We need to update the estimated width and height
				mImageViewer.notifyScreenNailInvalidated(INDEX_CURRENT);
			}
		}

		public void requestNextImageWithMeta()
		{
            setShareUri(getCurrentItem().getSwapUri());
			loadExif();

			// First request the current screen nail
			if (mScreenNails[INDEX_CURRENT] == null)
			{
                MediaItem current = getCurrentItem();
				if (current != null)
				{
					CurrentImageLoader cml = new CurrentImageLoader();
					cml.executeOnExecutor(LibRaw.EXECUTOR, mImageIndex, current);
				}
			}
			else
			{
                updateHistogram(mScreenNails[INDEX_CURRENT]);
			}

			// Next, the next screen nail if not last image
			if (mScreenNails[INDEX_NEXT] == null && !(mImageIndex >= mCursor.getCount() - 1))
			{
				mCursor.moveToPosition(mImageIndex + 1);
                MediaItem next = new LocalImage(LegacyViewerActivity.this, mCursor);
				if (next != null)
				{
					SmallImageLoader sml = new SmallImageLoader();
					sml.executeOnExecutor(LibRaw.EXECUTOR, mImageIndex + 1, next);
				}
			}

			// Next, the previous screen nail if not the first image
			if (mScreenNails[INDEX_PREVIOUS] == null && mImageIndex > 0)
			{
				mCursor.moveToPosition(mImageIndex - 1);
                MediaItem previous = new LocalImage(LegacyViewerActivity.this, mCursor);
				if (previous != null)
				{
					SmallImageLoader sml = new SmallImageLoader();
					sml.executeOnExecutor(LibRaw.EXECUTOR, mImageIndex - 1, previous);
				}
			}

			// Next, the full size image
			if (mLargeBitmap == null)
			{
                MediaItem current = getCurrentItem();
				if (current != null)
				{
					LargeImageLoader lml = new LargeImageLoader();
					lml.executeOnExecutor(LibRaw.EXECUTOR, mImageIndex, current);
				}
			}
		}
	}

    @Override
	public void goToNextPicture()
	{
		mModel.next();
	}

    @Override
    public void goToFirstPicture() {
        mImageIndex = 0;
        mModel.requestNextImageWithMeta();
    }

    public void deleteToNext()
	{
		mModel.deleteToNext();
	}

	public void deleteToPrevious()
	{
		mModel.deleteToPrevious();
	}

    @Override
	public void goToPrevPicture()
	{
		mModel.previous();
	}

	private class CurrentImageLoader extends SmallImageLoader
	{
		@Override
		protected void onPostExecute(Bitmap result)
		{
			super.onPostExecute(result);
			updateHistogram(result);
		}
	}

	private class SmallImageLoader extends AsyncTask<Object, Void, Bitmap>
	{
		int mIndex;

		@Override
		protected Bitmap doInBackground(Object... params)
		{
			mIndex = (Integer) params[0];
            MediaItem mMedia = (MediaItem) params[1];
            byte[] imageData = mMedia.getThumb();
			if (imageData == null)
				return null;

            return  Util.createBitmapLarge(imageData,
                    LegacyViewerActivity.displayWidth,
                    LegacyViewerActivity.displayHeight,
                    true);
        }

		@Override
		protected void onPostExecute(Bitmap result)
		{
			mGLRootView.lockRenderThread();
			mModel.updateScreenNail(mIndex, result);
			mGLRootView.unlockRenderThread();
		}
	}

	private class LargeImageLoader extends AsyncTask<Object, Void, BitmapRegionDecoder>
	{
		int mIndex;

		@Override
		protected BitmapRegionDecoder doInBackground(Object... params)
		{
			mIndex = (Integer) params[0];
            MediaItem mMedia = (MediaItem) params[1];

            byte[] imageData = mMedia.getThumb();
            if (imageData == null)
                return null;

			BitmapRegionDecoder brd = null;
			try {
				brd = BitmapRegionDecoder.newInstance(imageData, 0, imageData.length, false);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return brd;         
		}

		@Override
		protected void onPostExecute(BitmapRegionDecoder result)
		{
			mGLRootView.lockRenderThread();
			mModel.updateLargeImage(mIndex, result);
			mGLRootView.unlockRenderThread();
		}
	}
}