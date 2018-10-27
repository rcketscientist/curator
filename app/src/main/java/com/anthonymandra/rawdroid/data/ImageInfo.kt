package com.anthonymandra.rawdroid.data

import androidx.room.Relation
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class ImageInfo(
//    @Embedded
//    var metadata: MetadataEntity = MetadataEntity(),
    @Relation(
            parentColumn = "id",
            entityColumn = "metaId",
            projection = ["subjectId"],
            entity = SubjectJunction::class)
    var subjectIds: List<Long> = emptyList()) :  MetadataEntity(), Parcelable

