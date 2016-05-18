package com.anthonymandra.image;

public class JpegConfiguration implements ImageConfiguration {
    int quality;

    @Override
    public ImageType getType() {
        return ImageType.jpeg;
    }

    @Override
    public String getExtension() {
        return "jpg";
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }
}
