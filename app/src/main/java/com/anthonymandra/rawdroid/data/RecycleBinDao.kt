package com.anthonymandra.rawdroid.data

import androidx.room.*

@Dao
abstract class RecycleBinDao {
	@Query("SELECT * FROM recycle_bin WHERE id IN (:ids)")
	abstract fun images(vararg ids: Long): List<RecycleBinEntity>

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	abstract fun insert(row: RecycleBinEntity): Long

	@Delete
	abstract fun delete(vararg entities: RecycleBinEntity)

	@Query("DELETE FROM recycle_bin WHERE id = :id")
	abstract fun delete(id: Long)

	@Query("DELETE FROM recycle_bin")
	abstract fun deleteAll()
}
