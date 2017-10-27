package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.Relation;
import android.provider.BaseColumns;

import java.util.List;

@Entity(tableName = SubjectEntity.DATABASE,
		indices = @Index(value = SubjectEntity._ID))
public class SubjectEntity extends PathEntity
{
	public static final String DATABASE = "xmp_subject";
	public static final String NAME = "name";
	public static final String RECENT = "recent";

	SubjectEntity(long id, String path, int depth, String name, String recent, long parent)
	{
		super(id, path, depth, parent);
		this.name = name;
		this.recent = recent;
	}

	@ColumnInfo(name = NAME)
	public String name;

	@ColumnInfo(name = RECENT)
	public String recent;
}
