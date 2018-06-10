package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey

@Entity(
    indices = [
        Index(value = ["id"]),
        Index(value = ["subjectId"])],
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"])])
data class SynonymEntity (
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var subjectId: Long = 0,
    var synonym: String = "")
