package com.android.gallery3d.app;

import android.graphics.Bitmap;
import android.net.Uri;

import com.android.gallery3d.ui.PhotoView;

/**
 * Created by amand_000 on 8/14/13.
 */
public interface Model extends PhotoView.Model {
    public void resume();
    public void pause();
    public boolean isEmpty();
    public void setCurrentPhoto(Uri path, int indexHint);
    public Bitmap getCurrentBitmap();
}
