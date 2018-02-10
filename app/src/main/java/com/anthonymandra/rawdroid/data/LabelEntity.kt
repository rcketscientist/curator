package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
data class LabelEntity (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val label: Label,
    val customLabel: String
)