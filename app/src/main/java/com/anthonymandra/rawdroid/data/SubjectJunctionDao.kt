package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Delete
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query

@Dao
abstract class SubjectJunctionDao {
    @get:Query("SELECT * FROM meta_subject_junction")
    abstract val all: List<SubjectJunction>

    @Query("SELECT subjectId FROM META_SUBJECT_JUNCTION WHERE metaId = :metaId")
    abstract fun getSubjectsFor(metaId: Long?): List<Long>

    @Query("SELECT metaId FROM META_SUBJECT_JUNCTION WHERE subjectId = :subjectId")
    abstract fun getImagesWith(subjectId: Long?): List<Long>

    @Insert
    abstract fun insert(vararg entries: SubjectJunction)

    @Delete
    abstract fun delete(vararg entries: SubjectJunction)

    @Query("DELETE FROM meta_subject_junction WHERE metaId IN (:metaIds)")
    abstract fun delete(metaIds: List<Long>)
}
