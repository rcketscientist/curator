package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.TypeConverter
import android.arch.persistence.room.TypeConverters
import java.util.*

@TypeConverters(MetadataResult::class)
class MetadataResult : MetadataEntity() {
    var keywords: List<String> = Collections.emptyList()
    /**
     * This is a join from [FolderEntity]
     */
    // Relation requires set which is unnecessary
    //    @Relation(
    //        parentColumn = "parentId",
    //        entityColumn = "documentUri",
    //        entity = FolderEntity.class)
    var parentUri: String? = null

    @TypeConverter
    fun fromGroupConcat(keywords: String): List<String> = keywords.split(",")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as MetadataResult

        if (keywords != other.keywords) return false

        return true
    }
}
