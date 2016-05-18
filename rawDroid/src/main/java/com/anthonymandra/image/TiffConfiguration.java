package com.anthonymandra.image;

public class TiffConfiguration implements ImageConfiguration {
    boolean compress;

    @Override
    public ImageType getType() {
        return ImageType.tiff;
    }

    @Override
    public String getExtension() {
        return "tiff";
    }

    public boolean getCompress() {
        return compress;
    }

    public void setCompress(boolean compress) {
        this.compress = compress;
    }
}
