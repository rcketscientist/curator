package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Relation;
import android.arch.persistence.room.TypeConverter;
import android.arch.persistence.room.TypeConverters;

import java.util.Arrays;
import java.util.List;

@TypeConverters({MetadataResult.class})
public class MetadataResult extends MetadataEntity {
//    public String keyword;
    public List<String> keywords;
    /**
     * This is a join from {@link FolderEntity}
     */
    // Relation requires set which is unnecessary
//    @Relation(
//        parentColumn = "parentId",
//        entityColumn = "documentUri",
//        entity = FolderEntity.class)
    public String parentUri;

    @TypeConverter
    public List<String> fromGroupConcat(String keywords) {
        return Arrays.asList(keywords.split(","));
    }
}
