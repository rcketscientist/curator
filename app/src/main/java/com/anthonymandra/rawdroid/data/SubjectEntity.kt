package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.Index

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
    @Ignore override var depth: Int = 0): PathEntity()
