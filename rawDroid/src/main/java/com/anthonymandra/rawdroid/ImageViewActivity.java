package com.anthonymandra.rawdroid;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaItem;
import com.anthonymandra.framework.License;

import java.util.WeakHashMap;

public class ImageViewActivity extends PhotoPage
{
	private static final String TAG = ImageViewActivity.class.getSimpleName();

    @Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener(this);
	}

    @Override
    public int getContentView()
    {
        return R.layout.viewer_twopane;
    }

    @Override
	protected void onImageSetChanged()
	{
        mPhotoView.setUpdateForContentChange(true);
        notifyContentChanged();
	}

	@Override
	public void onSingleTapConfirmed()
	{
		togglePanels();
	}

	@Override
	/**
	 * This seems to occur when the first image is loaded.
	 */
	public void onCurrentImageUpdated()
	{
        super.onCurrentImageUpdated();
        if (mRequiresHistogramUpdate)
            updateHistogram(getCurrentBitmap());
	}

    @Override
    public void onCommitDeleteImage(Uri toDelete)
    {
        deleteImage(toDelete);
    }

    private WeakHashMap<ContentListener, Object> mListeners =
            new WeakHashMap<>();

    // NOTE: The MediaSet only keeps a weak reference to the listener. The
    // listener is automatically removed when there is no other reference to
    // the listener.
    public void addContentListener(ContentListener listener) {
        mListeners.put(listener, null);
    }

    public void removeContentListener(ContentListener listener) {
        mListeners.remove(listener);
    }

    // This should be called by subclasses when the content is changed.
    public void notifyContentChanged() {
        for (ContentListener listener : mListeners.keySet()) {
            listener.onContentDirty();
        }
    }

    @Override
    public Uri getCurrentItem() {
        return mModel.getCurrentItem();
    }

    @Override
    public Bitmap getCurrentBitmap() {
        return mModel.getCurrentBitmap();
    }

    @Override
    public void goToPrevPicture() {
        mPhotoView.goToPrevPicture();
    }

    @Override
    public void goToNextPicture() {
        mPhotoView.goToNextPicture();
    }

    @Override
    public void goToFirstPicture() {
        mModel.moveTo(0);
    }

    @Override
    protected void setLicenseState(License.LicenseState state)
    {
        super.setLicenseState(state);
        mPhotoView.setLicenseState(state);
    }
}