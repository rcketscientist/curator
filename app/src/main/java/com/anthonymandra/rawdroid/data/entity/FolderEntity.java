package com.anthonymandra.rawdroid.data.entity;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.provider.BaseColumns;

@Entity(tableName = FolderEntity.DATABASE)
public class FolderEntity extends PathEntity
{
	public static final String DATABASE = "image_parent";
	public static final String _ID = BaseColumns._ID;
	public static final String DOCUMENT_ID = "name";
	public static final String PATH = "path";
	public static final String NAME = "name";

	@ColumnInfo(name = DOCUMENT_ID)
	public long documentId;
}
