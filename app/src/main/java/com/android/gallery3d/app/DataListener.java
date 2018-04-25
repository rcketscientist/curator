package com.android.gallery3d.app;

import com.anthonymandra.rawdroid.data.MetadataTest;

public interface DataListener extends LoadingListener {
    void onPhotoChanged(int index, MetadataTest images);
}
