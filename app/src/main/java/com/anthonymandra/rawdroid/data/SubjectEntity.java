package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.Relation;
import android.provider.BaseColumns;

import java.util.List;

@Entity(tableName = SubjectEntity.DATABASE)
public abstract class SubjectEntity extends PathEntity
{
	public static final String DATABASE = "xmp_subject";
	public static final String NAME = "name";
	public static final String RECENT = "recent";

	SubjectEntity(long id, String path, int depth, String name, String recent)
	{
		super(id, path, depth);
		this.name = name;
		this.recent = recent;
	}

	@ColumnInfo(name = NAME)
	public String name;

	@ColumnInfo(name = RECENT)
	public String recent;

	@Relation(projection = SynonymEntity.SYNONYM, entityColumn = _ID, parentColumn = SynonymEntity.SUBJECT_ID)
	public List<String> synonyms;
}
