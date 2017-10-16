package com.anthonymandra.rawdroid.data.dao;

import android.arch.persistence.room.Dao;

import com.anthonymandra.rawdroid.data.entity.FolderEntity;

@Dao
public abstract class FolderDao extends PathDao
{
	protected final static String DATABASE = FolderEntity.DATABASE;
}
