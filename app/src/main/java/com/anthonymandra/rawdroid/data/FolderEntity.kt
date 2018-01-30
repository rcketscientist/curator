package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore

@Entity(tableName = "image_parent")
data class FolderEntity(
    var documentUri: String = "",
    // PathEntity
    @Ignore override var id: Long = 0,
    @Ignore override var path: String = "",
    @Ignore override var parent: Long = -1,
    @Ignore override var depth: Int = 0) : PathEntity()

