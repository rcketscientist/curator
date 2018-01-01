package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Entity;

@Entity(tableName = "image_parent")
public class FolderEntity extends PathEntity
{
	FolderEntity(String documentUri, Long id, String path, int depth, Long parent)
	{
		super(id, path, depth, parent);
		this.documentUri = documentUri;
	}

	public String documentUri;   // TODO: This might not be necessary, prolly should have uri though

	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof FolderEntity)
		{
			FolderEntity compare = (FolderEntity) obj;
			return super.equals(obj) && documentUri.equals(compare.documentUri);
		}
		return false;
	}
}
