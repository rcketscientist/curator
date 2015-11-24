package com.anthonymandra.framework;

import android.net.Uri;

import com.anthonymandra.rawprocessor.LibRaw;

import java.io.InputStream;

public interface RawObject {

    public boolean isDirectory();

    public String getName();

    public String getFilePath();

    public Uri getUri();

    @Deprecated
    public boolean canDecode();

    public InputStream getImageStream();
    public byte[] getImage();

    public byte[] getThumb();
	public byte[] getThumbWithWatermark(byte[] watermark, int waterWidth, int waterHeight, LibRaw.Margins margins);

    public long getFileSize();

    public Uri getSwapUri();
}
