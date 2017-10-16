package com.anthonymandra.rawdroid.data.entity;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.provider.BaseColumns;

@Entity(tableName = SubjectEntity.DATABASE)
public class SubjectEntity extends PathEntity
{
	public static final String DATABASE = "xmp_subject";
	public static final String _ID = BaseColumns._ID;
	public static final String NAME = "name";
	public static final String RECENT = "recent";

	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = _ID)
	public long id;

	@ColumnInfo(name = NAME)
	public String name;

	@ColumnInfo(name = RECENT)
	public String recent;


}
