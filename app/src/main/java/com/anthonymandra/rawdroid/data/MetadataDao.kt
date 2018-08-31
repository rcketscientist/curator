package com.anthonymandra.rawdroid.data

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
abstract class MetadataDao {

    @get:Query("SELECT * FROM meta")
    abstract val allImages: LiveData<List<MetadataEntity>>

    @Query("SELECT COUNT(*) FROM meta")
    abstract fun count(): Int

    @RawQuery(observedEntities = [ MetadataEntity::class, SubjectJunction::class ])
    abstract fun getImages(query: SupportSQLiteQuery): LiveData<List<MetadataTest>>

    @RawQuery(observedEntities = [ MetadataEntity::class, SubjectJunction::class ])
    abstract fun getImageFactory(query: SupportSQLiteQuery): DataSource.Factory<Int, MetadataTest>

    @Transaction
    @Query("SELECT * FROM meta WHERE id IN (:ids)")
    abstract fun getImagesById(ids: List<Long>): DataSource.Factory<Int, MetadataTest>

    @Transaction
    @Query("SELECT * FROM meta WHERE uri = :uri")
    abstract operator fun get(uri: String): LiveData<MetadataTest>

    @Transaction
    @Query("SELECT * FROM meta WHERE uri IN (:uris)")
    abstract fun blocking(uris: List<String>): List<MetadataTest>

    @Transaction
    @Query("SELECT * FROM meta WHERE uri = :uri")
    abstract fun blocking(uri: String): MetadataTest

    @Transaction
    @Query("SELECT * FROM meta WHERE uri IN (:uris)")
    abstract fun stream(uris: List<String>): LiveData<List<MetadataTest>>

    @Transaction
    @Query("SELECT * FROM meta WHERE processed = 0")    // 0 = true
    abstract fun unprocessedImages() : List<MetadataTest>    // TODO: maybe page this?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(datum: MetadataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(vararg datums: MetadataEntity): List<Long>

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
