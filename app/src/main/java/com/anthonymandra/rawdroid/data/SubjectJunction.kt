package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.Index

import android.arch.persistence.room.ForeignKey.CASCADE

@Entity(
    tableName = "meta_subject_junction",
    primaryKeys = [ "metaId", "subjectId" ],
    indices = [
        Index(value = ["subjectId"]),
        Index(value = ["metaId"]) ],
    foreignKeys = [
        ForeignKey(
            entity = MetadataEntity::class,
            parentColumns = [ "id" ],
            childColumns = [ "metaId" ],
            onDelete = CASCADE),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = [ "id" ],
            childColumns = [ "subjectId" ],
            onDelete = CASCADE)])
data class SubjectJunction(
        var metaId: Long,
        var subjectId: Long)
