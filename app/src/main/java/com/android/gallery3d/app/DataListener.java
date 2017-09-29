package com.android.gallery3d.app;

import android.net.Uri;

public interface DataListener extends LoadingListener {
    void onPhotoChanged(int index, Uri path);
}
