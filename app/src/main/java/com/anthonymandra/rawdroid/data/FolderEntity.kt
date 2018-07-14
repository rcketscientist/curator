package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey

@Entity(tableName = "image_parent",
        indices = [(Index(value = ["documentUri"], unique = true))])
data class FolderEntity @JvmOverloads constructor(
        var documentUri: String = "",
        var visible: Boolean = true,
        var excluded: Boolean = false,
        @PrimaryKey(autoGenerate = true)
        var id: Long = 0)

