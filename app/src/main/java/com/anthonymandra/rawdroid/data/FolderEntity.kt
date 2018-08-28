package com.anthonymandra.rawdroid.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "image_parent",
        indices = [(Index(value = ["documentUri"], unique = true))])
data class FolderEntity @JvmOverloads constructor(
        var documentUri: String = "",
        var visible: Boolean = true,
        var excluded: Boolean = false,
        @PrimaryKey(autoGenerate = true)
        var id: Long = 0)

