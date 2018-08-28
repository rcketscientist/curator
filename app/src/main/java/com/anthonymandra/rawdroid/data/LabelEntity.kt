package com.anthonymandra.rawdroid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class LabelEntity (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val label: Label,
    val customLabel: String
)