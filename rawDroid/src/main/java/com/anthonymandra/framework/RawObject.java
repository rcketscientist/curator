package com.anthonymandra.framework;

import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import com.anthonymandra.dcraw.LibRaw.Margins;

/**
 * Created by amand_000 on 8/9/13.
 */
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

    public byte[] getThumb();
	public byte[] getThumbWithWatermark(byte[] watermark, int waterWidth, int waterHeight, Margins margins);

    public long getFileSize();

    public boolean rename(String baseName);

    public boolean copy(File destination);

    public Uri getSwapUri();
    
    public boolean writeThumb(File destination);
    public boolean writeThumbWatermark(File destination, byte[] waterMap, int waterWidth, int waterHeight, Margins waterMargins);
}
