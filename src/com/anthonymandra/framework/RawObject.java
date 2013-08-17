package com.anthonymandra.framework;

import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.File;

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

    public byte[] getImage();

    public byte[] getThumb();

    public BufferedInputStream getThumbInputStream();

    public BufferedInputStream getImageInputStream();

    public long getFileSize();

    public boolean rename(String baseName);

    public boolean copy(File destination);

    public boolean copyThumb(File destination);
}
