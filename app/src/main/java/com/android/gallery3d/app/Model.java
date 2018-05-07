package com.android.gallery3d.app;

import android.graphics.Bitmap;

import com.android.gallery3d.ui.PhotoView;
import com.anthonymandra.rawdroid.data.MetadataTest;

public interface Model extends PhotoView.Model {
    void resume();
    void pause();
    boolean isEmpty();
    void setCurrentPhoto(MetadataTest path, int indexHint);
    Bitmap getCurrentBitmap();
}
