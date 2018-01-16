package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity

@Entity(tableName = "image_parent")
data class FolderEntity(
    var documentUri: String) : PathEntity()
