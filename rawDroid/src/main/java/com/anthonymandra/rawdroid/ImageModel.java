package com.anthonymandra.rawdroid;

import android.graphics.Bitmap;
import android.net.Uri;

import com.android.gallery3d.ui.PhotoView;

public interface ImageModel extends PhotoView.Model
{
    public void resume();
    public void pause();
    public boolean isEmpty();
    public void setCurrentPhoto(Uri path, int index);
    public Bitmap getCurrentBitmap();
}
