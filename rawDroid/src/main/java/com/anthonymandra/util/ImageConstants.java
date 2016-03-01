package com.anthonymandra.util;

public class ImageConstants
{
    public static final String[] RAW_EXT = new String[]
    {   ".cr2", ".nef", ".arw", ".srw", ".x3f", ".orf", ".pef", ".ptx", // Most common
        ".crw", ".nrw", ".3fr", ".ari", ".bay", ".cap", ".dcs", ".dcr",
        ".dng", ".drf", ".eip", ".erf", ".fff", ".iiq", ".k25", ".kdc",
        ".mdc", ".mef", ".mos", ".mrw", ".obm", ".pxn", ".r3d", ".raf",
        ".raw", ".rwl", ".rw2", ".rwz", ".sr2", ".srf",
    };

    public static final String[] JPEG_EXT = new String[] { ".jpg", "jpeg" };

    public static final String[] COMMON_EXT = new String[] { ".jpg", "jpeg", ".png", ".bmp"};

    public static final String[] TIFF_EXT = new String[] {".tiff", ".tif" };

    public static final String JPEG_MIME = "image/jpeg";
    public static final String PNG_MIME = "image/png";
    public static final String BMP_MIME = "image/bmp";
    public static final String TIFF_MIME = "image/tiff";

    public static final String[] ANDROID_IMAGE_MIME = new String[]
            { JPEG_MIME, PNG_MIME, BMP_MIME };

}
