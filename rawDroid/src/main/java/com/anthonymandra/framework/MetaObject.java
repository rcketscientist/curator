package com.anthonymandra.framework;

import android.content.ContentValues;

import java.io.FileNotFoundException;

/**
 * Created by amand_000 on 8/10/13.
 */
public interface MetaObject {

    public void clearXmp();

    public void writeXmp() throws FileNotFoundException;

    public boolean readMetadata();

    public String getAperture();

    public String getExposure();

    public String getImageHeight();

    public String getImageWidth();

    public String getFocalLength();

    public String getFlash();

    public String getShutterSpeed();

    public String getWhiteBalance();

    public String getExposureProgram();

    public String getExposureMode();

    public String getLensMake();

    public String getLensModel();

    public String getDriveMode();

    public String getIso();

    public String getFNumber();

    public String getDateTime();

    public String getMake();

    public String getModel();

    public int getOrientation();

    public String getAltitude();

    public String getLatitude();

    public String getLongitude();

    public double getRating();

    public String getLabel();

    public String[] getSubject();

    public void setRating(double rating);

    public void setLabel(String label);

    public void setSubject(String[] subject);

    public int getWidth();

    public void setWidth(int width);

    public int getHeight();

    public void setHeight(int height);

    public int getThumbWidth();

    public void setThumbWidth(int thumbWidth);

    public int getThumbHeight();

    public void setThumbHeight(int thumbHeight);

    ContentValues getContentValues();
}
