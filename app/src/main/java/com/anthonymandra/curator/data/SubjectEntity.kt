package com.anthonymandra.curator.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "xmp_subject",
    indices = [ Index(value = [ "id" ]) ])
data class SubjectEntity(
    var name: String = "",
    var recent: Long = 0,
    // PathEntity
    @Ignore override var id: Long = 0,
    @Ignore override var path: String = "",
    @Ignore override var parent: Long = -1,
    @Ignore override var depth: Int = 0): PathEntity(), Parcelable
