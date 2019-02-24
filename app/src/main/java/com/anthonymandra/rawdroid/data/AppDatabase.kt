package com.anthonymandra.rawdroid.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [
    FolderEntity::class,
    MetadataEntity::class,
    RecycleBinEntity::class,
    SubjectEntity::class,
    SubjectJunction::class,
    SynonymEntity::class],
    version = 3)
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun metadataDao(): MetadataDao
    abstract fun recycleBinDao(): RecycleBinDao
    abstract fun subjectDao(): SubjectDao
    abstract fun subjectJunctionDao(): SubjectJunctionDao

    companion object {
        private const val DB_NAME = "curator.db"
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
					INSTANCE ?:
						Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
							.addMigrations(MIGRATION_2_3)
							.build()
							.also { INSTANCE = it }
            }

		 val MIGRATION_2_3 = object : Migration(2, 3) {
			 override fun migrate(database: SupportSQLiteDatabase) {
				 database.execSQL("CREATE TABLE IF NOT EXISTS `recycle_bin` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `path` TEXT NOT NULL)")
			 }
		 }
    }
}