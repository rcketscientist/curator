package com.anthonymandra.rawdroid.data

import androidx.room.Entity
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "xmp_subject",
    indices = [ Index(value = [ "id" ]) ])
data class SubjectEntity(
    var name: String = "",
    var recent: Long = 0
): PathEntity(), Parcelable
