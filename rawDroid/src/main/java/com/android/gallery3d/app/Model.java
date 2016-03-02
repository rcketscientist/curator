package com.android.gallery3d.app;

import android.graphics.Bitmap;
import android.net.Uri;

import com.android.gallery3d.ui.PhotoView;

public interface Model extends PhotoView.Model {
    void resume();
    void pause();
    boolean isEmpty();
    void setCurrentPhoto(Uri path, int indexHint);
    Bitmap getCurrentBitmap();
}
