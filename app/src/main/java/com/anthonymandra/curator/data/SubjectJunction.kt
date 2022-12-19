package com.anthonymandra.curator.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

import androidx.room.ForeignKey.CASCADE

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
