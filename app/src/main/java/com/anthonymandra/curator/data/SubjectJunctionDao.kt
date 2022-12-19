package com.anthonymandra.curator.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

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
