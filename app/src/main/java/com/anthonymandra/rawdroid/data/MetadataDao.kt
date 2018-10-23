package com.anthonymandra.rawdroid.data

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
abstract class MetadataDao {

    @get:Query("SELECT * FROM meta")
    abstract val synchAllImages: List<MetadataEntity>

    @get:Query("SELECT uri FROM meta")
    abstract val uris: List<String>

	@Query("SELECT COUNT(*) FROM meta")
	abstract fun synchCount(): Int

    // Count doesn't care if the subject changes
    @RawQuery(observedEntities = [ MetadataEntity::class ])
    abstract fun count(query: SupportSQLiteQuery): LiveData<Int>

    @RawQuery(observedEntities = [ MetadataEntity::class ])
    abstract fun ids(query: SupportSQLiteQuery): LongArray

    @RawQuery(observedEntities = [ MetadataEntity::class, SubjectJunction::class ])
    abstract fun getImages(query: SupportSQLiteQuery): LiveData<List<MetadataTest>>

    @RawQuery(observedEntities = [ MetadataEntity::class, SubjectJunction::class ])
    abstract fun imageBlocking(query: SupportSQLiteQuery): List<MetadataTest>

    @RawQuery(observedEntities = [ MetadataEntity::class, SubjectJunction::class ])
    abstract fun getImageFactory(query: SupportSQLiteQuery): DataSource.Factory<Int, MetadataTest>

    @Transaction
    @Query("SELECT * FROM meta WHERE id IN (:ids)")
    abstract fun getImagesById(ids: List<Long>): DataSource.Factory<Int, MetadataTest>

    @Transaction
    @Query("SELECT * FROM meta WHERE uri = :uri")
    abstract operator fun get(uri: String): LiveData<MetadataTest>

    @WorkerThread
    @Query("SELECT * FROM meta WHERE uri IN (:uris)")
    abstract fun synchImages(uris: Array<String>): List<MetadataTest>

    @WorkerThread
    @Query("SELECT * FROM meta WHERE id IN (:ids)")
    abstract fun synchImages(ids: LongArray): List<MetadataTest>

    @WorkerThread
    @Query("SELECT * FROM meta WHERE uri = :uri")
    abstract fun synchImage(uri: String): MetadataTest

    @Transaction
    @Query("SELECT * FROM meta WHERE uri IN (:uris)")
    abstract fun stream(uris: List<String>): LiveData<List<MetadataTest>>

    // If there's a conflict we'll just skip the conflicted row
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(datum: MetadataEntity): Long

    // If there's a conflict we'll just skip the conflicted row
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(vararg datums: MetadataEntity): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun replace(datum: MetadataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun replace(vararg datums: MetadataEntity): List<Long>

    @Update
    abstract fun update(vararg datums: MetadataEntity)

    @Delete
    abstract fun delete(vararg datums: MetadataEntity)

    @Query("DELETE FROM meta WHERE id = :id")
    abstract fun delete(id: Long): Int

    @Query("DELETE FROM meta WHERE id IN (:ids)")
    abstract fun delete(ids: LongArray): Int

    @Query("DELETE FROM meta WHERE uri IN (:uris)")
    abstract fun delete(uris: Array<String>): Int

    @Query("DELETE FROM meta")
    abstract fun deleteAll()
}
