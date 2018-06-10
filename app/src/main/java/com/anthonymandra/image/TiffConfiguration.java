package com.anthonymandra.image;

public class TiffConfiguration extends ImageConfiguration {
    private boolean compress;

    @Override
    public ImageType getType() {
        return ImageType.tiff;
    }

    @Override
    public String getExtension() {
        return "tiff";
    }

    @Override
    protected void parse(String config) {
        compress = Boolean.parseBoolean(config);
    }

    @Override
    protected String convertToPreference() {
        return String.valueOf(compress);
    }

    public boolean getCompress() {
        return compress;
    }

    public void setCompress(boolean compress) {
        this.compress = compress;
    }
}
