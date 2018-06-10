package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity(tableName = "image_parent")
data class FolderEntity @JvmOverloads constructor(
    var documentUri: String = "",
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0)
//    // PathEntity
//    @Ignore override var id: Long = 0,
//    @Ignore override var path: String = "",
//    @Ignore override var parent: Long = -1,
//    @Ignore override var depth: Int = 0) : PathEntity()

