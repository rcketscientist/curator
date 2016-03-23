package com.anthonymandra.framework;

import android.net.Uri;

import com.anthonymandra.rawprocessor.LibRaw;

import java.io.InputStream;

public interface RawObject {

    String getName();
    Uri getUri();
    String getMimeType();

    byte[] getThumb();

    Uri getSwapUri();
}
