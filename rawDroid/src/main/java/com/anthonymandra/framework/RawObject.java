package com.anthonymandra.framework;

import android.net.Uri;

public interface RawObject {

    String getName();
    Uri getUri();
    String getMimeType();

    byte[] getThumb();

    Uri getSwapUri();
}
