package com.anthonymandra.framework;

public interface MetaObject {

    String getAperture();

    String getExposure();

    String getImageHeight();

    String getImageWidth();

    String getFocalLength();

    String getFlash();

    String getShutterSpeed();

    String getWhiteBalance();

    String getExposureProgram();

    String getExposureMode();

    String getLensMake();

    String getLensModel();

    String getDriveMode();

    String getIso();

    String getFNumber();

    String getDateTime();

    String getMake();

    String getModel();

    int getOrientation();

    String getAltitude();

    String getLatitude();

    String getLongitude();

    double getRating();

    String getLabel();

    String[] getSubject();

    void setRating(double rating);

    void setLabel(String label);

    void setSubject(String[] subject);

    int getWidth();

    void setWidth(int width);

    int getHeight();

    void setHeight(int height);

    int getThumbWidth();

    void setThumbWidth(int thumbWidth);

    int getThumbHeight();

    void setThumbHeight(int thumbHeight);
}
