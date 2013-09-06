package com.anthonymandra.rawdroid;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.widget.ShareActionProvider.OnShareTargetSelectedListener;
import com.android.gallery3d.app.PhotoDataAdapter.DataListener;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.io.File;
import java.util.WeakHashMap;

public class ImageViewActivity extends PhotoPage implements DataListener
{
	private static final String TAG = ImageViewActivity.class.getSimpleName();

	// private DecodeRawTask decodeTask;
	// private FrameLayout decodeProgress;

    @Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

        initialize();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
	}

	@Override
	public void onResume()
	{
		super.onResume();
	}

	@Override
	public void onPause()
	{
		super.onPause();
	}

	@Override
	protected void updateAfterDelete()
	{
		updateImageSource();
		// updateViewerItems();
		// if (mVisibleItems.size() == 0)
		// {
		// onBackPressed();
		// }
		//
		// if (mCurrentIndex >= mVisibleItems.size())
		// {
		// swap out the deleted image
		// previousImage();
		// previous();
		// mCurrentIndex = mVisibleItems.size() -1;
		// mModel.setCurrentPhoto(getCurrentImage().getUri(), mCurrentIndex);
		// }
		// else
		// {
		// swap out the deleted image
		// com.android.gallery3d.util.Utils.swap(screenNails, INDEX_CURRENT, INDEX_PREVIOUS);
		// then tap the existing next functionality by stepping back index
		// decrementImageIndex();
		// next();
		// TODO: this will fail!
		// mModel.setCurrentPhoto(getCurrentImage().getUri(), mCurrentIndex);
		// }
		// deleteToNext();
	}

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // This would stop the auto hide, but currently it can be restarted in the two different calls
        // Currently goofs up the actionbar
//        if (autoHide != null)
//            autoHide.cancel();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
	protected void updateAfterRestore()
	{
		updateImageSource();
	}

	private void updateImageSource()
	{
		updateViewerItems();
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
		updateImageDetails();
	}

	@Override
	/**
	 * This occurs whenever the current image changes.
	 */
	public void onPhotoChanged(int index, Uri path)
	{
        super.onPhotoChanged(index, path);
		updateImageDetails();
	}

    private WeakHashMap<ContentListener, Object> mListeners =
            new WeakHashMap<ContentListener, Object>();

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
    public MediaItem getCurrentItem() {
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
}