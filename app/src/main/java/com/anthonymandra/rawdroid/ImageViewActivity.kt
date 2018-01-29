package com.anthonymandra.rawdroid

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import com.android.gallery3d.app.PhotoPage
import com.android.gallery3d.data.ContentListener
import com.anthonymandra.framework.License
import java.util.*

class ImageViewActivity : PhotoPage() {

    public override val contentView: Int
        get() = R.layout.viewer_twopane

    private val mListeners = WeakHashMap<ContentListener, Any>()

    override val currentItem: Uri?
        get() = mModel.currentItem

    override val currentBitmap: Bitmap
        get() = mModel.currentBitmap

    override fun updateMessage(message: String?) {

    }

    override fun setMaxProgress(max: Int) {

    }

    override fun incrementProgress() {

    }

    override fun endProgress() {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        settings.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onImageSetChanged() {
        // TODO: This should be handled in ViewerActivity and requery
        mPhotoView.setUpdateForContentChange(true)
        notifyContentChanged()
    }

    override fun onSingleTapConfirmed() {
        togglePanels()
    }

    override fun onCurrentImageUpdated() {
        super.onCurrentImageUpdated()
        if (mRequiresHistogramUpdate)
            updateHistogram(currentBitmap)
    }

    override fun onCommitDeleteImage(toDelete: Uri) {
        deleteImage(toDelete)
    }

    // NOTE: The MediaSet only keeps a weak reference to the listener. The
    // listener is automatically removed when there is no other reference to
    // the listener.
    override fun addContentListener(listener: ContentListener) {
        mListeners[listener] = null
    }

    override fun removeContentListener(listener: ContentListener) {
        mListeners.remove(listener)
    }

    // This should be called by subclasses when the content is changed.
    protected fun notifyContentChanged() {
        for (listener in mListeners.keys) {
            listener.onContentDirty()
        }
    }

    override fun goToPrevPicture() {
        mPhotoView.goToPrevPicture()
    }

    override fun goToNextPicture() {
        mPhotoView.goToNextPicture()
    }

    override fun setLicenseState(state: License.LicenseState) {
        super.setLicenseState(state)
        mPhotoView.setLicenseState(state)
    }

    companion object {

        private val TAG = ImageViewActivity::class.java.simpleName
    }
}