package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Index;

@Entity(tableName = "xmp_subject",
		indices = @Index(value = "id"))
public class SubjectEntity extends PathEntity
{
	SubjectEntity() {}
	SubjectEntity(String path, int depth, long parent, String name, Long recent)
	{
		super(path, depth, parent);
		this.name = name;
		this.recent = recent;
	}

	SubjectEntity(Long id, String path, int depth, long parent, String name, Long recent)
	{
		super(id, path, depth, parent);
		this.name = name;
		this.recent = recent;
	}

	public String name;
	public Long recent;
}
