package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Relation

data class MetadataTest (
//    @Embedded
//    var metadata: MetadataEntity = MetadataEntity(),
    @Relation(
            parentColumn = "id",
            entityColumn = "metaId",
            projection = ["subjectId"],
            entity = SubjectJunction::class)
    var subjectIds: List<Long> = emptyList()
) : MetadataEntity()
