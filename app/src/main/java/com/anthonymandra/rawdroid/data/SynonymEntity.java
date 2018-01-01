package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

@Entity(
	indices = {
		@Index(value = "id"),
		@Index(value = "subjectId")},
	foreignKeys =
		@ForeignKey(entity = SubjectEntity.class,
					parentColumns ="id",
					childColumns = "subjectId"))
public class SynonymEntity
{
	@PrimaryKey(autoGenerate = true)
	public long id;

	public long subjectId;

	public String synonym;
}
