package com.anthonymandra.rawdroid;

import android.graphics.Bitmap;
import android.net.Uri;

import com.android.gallery3d.ui.PhotoView;

/**
 * Created by amand_000 on 7/28/13.
 */
public interface ImageModel extends PhotoView.Model
{
    public void resume();

    public void refresh();

    public void pause();

    public boolean isEmpty();

    public void setCurrentPhoto(Uri path, int index);

    public Bitmap getCurrentBitmap();
}
