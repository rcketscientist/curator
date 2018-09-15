package com.anthonymandra.rawdroid.data

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
abstract class MetadataDao {

    @get:Query("SELECT * FROM meta")
    abstract val allImages: LiveData<List<MetadataEntity>>

    // Count doesn't care if the subject changes
    @RawQuery(observedEntities = [ MetadataEntity::class ])
    abstract fun count(query: SupportSQLiteQuery): LiveData<Int>

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

    @Query("SELECT * FROM meta WHERE uri IN (:uris)")
    abstract fun _images(uris: Array<String>): List<MetadataEntity>

    @Query("SELECT * FROM meta WHERE id IN (:ids)")
    abstract fun _images(ids: LongArray): List<MetadataEntity>

    @Query("SELECT * FROM meta WHERE uri = :uri")
    abstract fun _images(uri: String): MetadataEntity

    @Transaction
    @Query("SELECT * FROM meta WHERE uri IN (:uris)")
    abstract fun stream(uris: List<String>): LiveData<List<MetadataEntity>>

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
    abstract fun delete(id: Long)

    @Query("DELETE FROM meta WHERE id IN (:ids)")
    abstract fun delete(ids: List<Long>)

    @Query("DELETE FROM meta")
    abstract fun deleteAll()
}
