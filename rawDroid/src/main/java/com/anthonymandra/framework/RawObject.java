package com.anthonymandra.framework;

import android.net.Uri;

import com.anthonymandra.rawprocessor.LibRaw;

import java.io.InputStream;

public interface RawObject {

    boolean isDirectory();

    String getName();
    Uri getUri();
    String getMimeType();

    @Deprecated
    boolean canDecode();

    InputStream getImageStream();
    byte[] getImage();

    byte[] getThumb();
	byte[] getThumbWithWatermark(byte[] watermark, int waterWidth, int waterHeight, LibRaw.Margins margins);

    long getFileSize();

    Uri getSwapUri();
}
