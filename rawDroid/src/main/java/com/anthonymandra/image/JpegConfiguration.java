package com.anthonymandra.image;

public class JpegConfiguration extends ImageConfiguration {
    private int quality;

    @Override
    public ImageType getType() {
        return ImageType.jpeg;
    }

    @Override
    public String getExtension() {
        return "jpg";
    }

    @Override
    protected void parse(String config) {
        quality = Integer.parseInt(config);
    }

    @Override
    protected String convertToPreference() {
        return String.valueOf(quality);
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }
}
