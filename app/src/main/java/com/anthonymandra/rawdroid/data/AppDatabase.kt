package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context

@Database(entities = [
    FolderEntity::class,
    MetadataEntity::class,
    SubjectEntity::class,
    SubjectJunction::class,
    SynonymEntity::class],
    version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun metadataDao(): MetadataDao
    abstract fun subjectDao(): SubjectDao
    abstract fun subjectJunctionDao(): SubjectJunctionDao

    companion object {
        private const val DB_NAME = "curator.db"
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?:
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DB_NAME)
                        .build()
                        .also { INSTANCE = it }
            }

    }
}