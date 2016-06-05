package com.anthonymandra.rawdroid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.anthonymandra.imageprocessor.ImageProcessor;

import java.io.File;

public class TestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        testNativeRegionDecoder();
    }

    private static void testNativeRegionDecoder()
    {
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(new File("/sdcard/Orion_Tulips-11.CR2"), ParcelFileDescriptor.MODE_READ_ONLY);
            int fd = pfd.getFd();
            BitmapRegionDecoder decoder = ImageProcessor.getRawDecoder(fd);
            Bitmap bmp = decoder.decodeRegion(new Rect(0, 0, 100, 100), new BitmapFactory.Options());
            int width = bmp.getWidth();
        }
        catch (Exception e)
        {
            Log.e("TEST", e.toString());
        }
    }
}
