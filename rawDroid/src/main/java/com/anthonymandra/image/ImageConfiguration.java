package com.anthonymandra.image;

public interface ImageConfiguration {
    enum  ImageType
    {
        jpeg,
        tiff,
        raw
    }

    ImageType getType();
    String getExtension();
}
