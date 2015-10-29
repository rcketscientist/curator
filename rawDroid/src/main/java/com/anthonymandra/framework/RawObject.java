package com.anthonymandra.framework;

import android.net.Uri;

import com.anthonymandra.dcraw.LibRaw.Margins;

import java.io.File;
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
	public byte[] getThumbWithWatermark(byte[] watermark, int waterWidth, int waterHeight, Margins margins);

    public long getFileSize();

    public boolean rename(String baseName);

    public Uri getSwapUri();
}
