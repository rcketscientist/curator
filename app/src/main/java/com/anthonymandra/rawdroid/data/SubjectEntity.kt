package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index

@Entity(
    tableName = "xmp_subject",
    indices = [ Index(value = [ "id" ]) ])
data class SubjectEntity(
    var name: String = "",
    var recent: Long = 0): PathEntity()
