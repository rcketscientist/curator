package com.anthonymandra.rawdroid.data

import androidx.room.TypeConverter
import androidx.room.TypeConverters

@TypeConverters(MetadataResult::class)
class MetadataResult : MetadataEntity() {
    var keywords: List<String>? = null

    @TypeConverter
    fun fromGroupConcat(keywords: String?): List<String>? = keywords?.split(",")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as MetadataResult

        if (keywords != other.keywords) return false

        return true
    }
}
