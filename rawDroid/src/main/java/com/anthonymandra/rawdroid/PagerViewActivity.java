package com.anthonymandra.rawdroid;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.gallery3d.data.ContentListener;
import com.anthonymandra.framework.License;
import com.anthonymandra.framework.ViewerActivity;
import com.anthonymandra.util.ImageUtil;
import com.eftimoff.viewpagertransformers.DepthPageTransformer;
import com.shizhefei.view.largeimage.LargeImageView;
import com.shizhefei.view.largeimage.factory.InputStreamBitmapDecoderFactory;
import com.shizhefei.view.viewpager.RecyclingPagerAdapter;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public class PagerViewActivity extends ViewerActivity
{
	@SuppressWarnings("unused")
    private static final String TAG = PagerViewActivity.class.getSimpleName();

	private RecyclingPagerAdapter mAdapter;

	private class ImageAdapter extends RecyclingPagerAdapter
	{
		private final List<Uri> mItems = new ArrayList<>();

		ImageAdapter(List<Uri> images)
		{
			mItems.addAll(images);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup container) {
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(R.layout.viewer_image, container, false);
			}
			LargeImageView largeImageView = (LargeImageView) convertView;
			Uri image = mItems.get(position);
			byte[] imageData = ImageUtil.getThumb(largeImageView.getContext(), image);
			largeImageView.setImage(new InputStreamBitmapDecoderFactory(new ByteArrayInputStream(imageData)));
			return largeImageView;
		}

		@Override
		public int getCount() {
			return mItems.size();
		}
	};

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

    @Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener(this);

		ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
		mAdapter = new ImageAdapter(mMediaItems);
		viewPager.setAdapter(mAdapter);
		viewPager.setCurrentItem(mImageIndex);
		viewPager.setPageTransformer(true, new DepthPageTransformer());
	}

    @Override
    public int getContentView()
    {
        return R.layout.viewer_pager;
    }

    @Override
	protected void onImageSetChanged()
	{
//        mPhotoView.setUpdateForContentChange(true);
//        notifyContentChanged();
	}

//	@Override
//	public void onSingleTapConfirmed()
//	{
//		togglePanels();
//	}
//
//	@Override
//	public void onCurrentImageUpdated()
//	{
//        super.onCurrentImageUpdated();
//        if (mRequiresHistogramUpdate)
//            updateHistogram(getCurrentBitmap());
//	}

//    @Override
//    public void onCommitDeleteImage(Uri toDelete)
//    {
//        deleteImage(toDelete);
//    }

//    private final WeakHashMap<ContentListener, Object> mListeners = new WeakHashMap<>();
//
//    // NOTE: The MediaSet only keeps a weak reference to the listener. The
//    // listener is automatically removed when there is no other reference to
//    // the listener.
//    public void addContentListener(ContentListener listener) {
//        mListeners.put(listener, null);
//    }
//
//    public void removeContentListener(ContentListener listener) {
//        mListeners.remove(listener);
//    }

    // This should be called by subclasses when the content is changed.
//    protected void notifyContentChanged() {
//        for (ContentListener listener : mListeners.keySet()) {
//            listener.onContentDirty();
//        }
//    }

    @Override
    public Uri getCurrentItem() {
	    return null;
//        return mModel.getCurrentItem();
    }

    @Override
    public Bitmap getCurrentBitmap() {
	    return null;
//        return mModel.getCurrentBitmap();
    }

    @Override
    public void goToPrevPicture() {
//        mPhotoView.goToPrevPicture();
    }

    @Override
    public void goToNextPicture() {
//        mPhotoView.goToNextPicture();
    }

    @Override
    protected void setLicenseState(License.LicenseState state)
    {
        super.setLicenseState(state);
//        mPhotoView.setLicenseState(state);
    }
}