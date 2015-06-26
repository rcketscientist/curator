package com.anthonymandra.framework;

import android.net.Uri;

import com.anthonymandra.dcraw.LibRaw.Margins;

import java.io.File;
import java.io.InputStream;

public interface RawObject {

    public boolean isDirectory();

    public boolean delete();

    public String getName();

    public String getFilePath();

    public Uri getUri();

    @Deprecated
    public boolean canDecode();

    public InputStream getImageStream();
    public byte[] getImage();

    public boolean moveImage(File location);

    public byte[] getThumb();
	public byte[] getThumbWithWatermark(byte[] watermark, int waterWidth, int waterHeight, Margins margins);

    public long getFileSize();

    public boolean rename(String baseName);

    public boolean copy(File destination);

    public Uri getSwapUri();
    
    public boolean writeThumb(File destination);
    public boolean writeThumbWatermark(File destination, byte[] waterMap, int waterWidth, int waterHeight, Margins waterMargins);
}
