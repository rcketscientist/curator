package com.anthonymandra.framework;

import android.net.Uri;

import com.anthonymandra.rawprocessor.LibRaw;

import java.io.InputStream;

public interface RawObject {

    String getName();
    Uri getUri();
    String getMimeType();

    byte[] getThumb();
	byte[] getThumbWithWatermark(byte[] watermark, int waterWidth, int waterHeight, LibRaw.Margins margins);

    long getFileSize();

    Uri getSwapUri();
}
