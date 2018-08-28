package com.anthonymandra.rawdroid.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
