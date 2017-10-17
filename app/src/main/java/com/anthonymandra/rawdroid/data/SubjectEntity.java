package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.Relation;
import android.provider.BaseColumns;

import java.util.List;

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

	@Relation(projection = SynonymEntity.SYNONYM, parentColumn = _ID, entityColumn = SynonymEntity.SUBJECT_ID)
	public List<String> synonyms;
}
