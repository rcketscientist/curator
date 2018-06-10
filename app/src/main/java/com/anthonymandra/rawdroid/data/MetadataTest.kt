package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Relation

class MetadataTest:  MetadataEntity() {
//    @Embedded
//    var metadata: MetadataEntity = MetadataEntity(),
    @Relation(
            parentColumn = "id",
            entityColumn = "metaId",
            projection = ["subjectId"],
            entity = SubjectJunction::class)
    var subjectIds: List<Long> = emptyList()

}
