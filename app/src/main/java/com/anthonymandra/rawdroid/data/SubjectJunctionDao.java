package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public abstract class SubjectJunctionDao {
    @Query("SELECT subjectId FROM META_SUBJECT_JUNCTION WHERE metaId = :metaId")
    public abstract List<Long> getSubjectsFor(Long metaId);
    @Query("SELECT metaId FROM META_SUBJECT_JUNCTION WHERE subjectId = :subjectId")
    public abstract List<Long> getImagesWith(Long subjectId);
    @Query("SELECT * FROM meta_subject_junction")
    public abstract List<SubjectJunction> getAll();

    @Insert
    public abstract void insert(SubjectJunction... entries);
    @Delete
    public abstract void delete(SubjectJunction... entries);
}
