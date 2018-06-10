package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Embedded
import android.arch.persistence.room.Relation
import java.util.*

data class MetadataXmp @JvmOverloads constructor(
    @Embedded
    val metadata:MetadataEntity,
    @Relation(
            parentColumn = "id",
            entityColumn = "metaId",
            projection = ["subjectId"],
            entity = SubjectJunction::class)
    var subjectIds: List<Long> = Collections.emptyList(),

    @Relation(
            parentColumn = "parentId",
            entityColumn = "id",
            projection = ["documentUri"],
            entity = FolderEntity::class)
    var parentUris: List<String> = Collections.emptyList()
//    val parentUri: String?
//        get() { return parentUris.elementAtOrNull(0) }

//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//        if (!super.equals(other)) return false
//
//        other as MetadataXmp
//
//        if (subjectIds != other.subjectIds) return false
//
//        return true
//    }
)
