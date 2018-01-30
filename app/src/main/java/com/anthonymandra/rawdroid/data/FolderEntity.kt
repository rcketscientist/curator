package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity

@Entity(tableName = "image_parent")
class FolderEntity @JvmOverloads constructor(
    var documentUri: String = "",
    // PathEntity
    id: Long = 0,
    path: String = "",
    parent: Long = -1,
    depth: Int = 0) : PathEntity(id, path, parent, depth)

