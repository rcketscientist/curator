package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Relation;

import java.util.List;

public class SubjectResult extends SubjectEntity
{
	@Relation(entity = SynonymEntity.class, projection = SynonymEntity.SYNONYM, entityColumn = SynonymEntity.SUBJECT_ID, parentColumn = _ID)
	public List<String> synonyms;

	SubjectResult(long id, String path, int depth, String name, String recent)
	{
		super(id, path, depth, name, recent);
	}
}
