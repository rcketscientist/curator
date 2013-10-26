package com.anthonymandra.framework;

import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

/**
 * Created by amand_000 on 8/9/13.
 */
public interface RawObject {

    public boolean isDirectory();

    public boolean delete();

    public String getName();

    public String getFilePath();

    public Uri getUri();

    public boolean canDecode();

    public InputStream getImage();

    public InputStream getThumb();
	public byte[] getThumbWithWatermark(byte[] watermark, int waterWidth, int waterHeight);

    public long getFileSize();

    public boolean rename(String baseName);

    public boolean copy(File destination);

    public boolean copyThumb(File destination);

    public Uri getSwapUri();


}
