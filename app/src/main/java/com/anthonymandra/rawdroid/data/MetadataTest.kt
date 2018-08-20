package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Relation
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class MetadataTest(
//    @Embedded
//    var metadata: MetadataEntity = MetadataEntity(),
    @Relation(
            parentColumn = "id",
            entityColumn = "metaId",
            projection = ["subjectId"],
            entity = SubjectJunction::class)
    var subjectIds: List<Long> = emptyList()) :  MetadataEntity(), Parcelable

