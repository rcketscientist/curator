package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Embedded;

import java.util.List;

public class GalleryResult
{
    @Embedded MetadataEntity metadata;
    List<String> subjects;
}
