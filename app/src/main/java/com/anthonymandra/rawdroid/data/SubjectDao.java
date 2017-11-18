package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.SparseArray;

import com.anthonymandra.content.KeywordProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

@Dao
public abstract class SubjectDao extends PathDao<SubjectEntity>
{
	protected final static String DATABASE = SubjectEntity.DATABASE;

	@Override
	String getDatabase()
	{
		return DATABASE;
	}

	@Query("SELECT * FROM " + DATABASE + " WHERE " + SubjectEntity._ID + "= :id ")
	abstract SubjectEntity getPath(Long id);

	@Query("SELECT " + SubjectEntity._ID + " FROM " + DATABASE +
			" WHERE " + SubjectEntity.PATH + " LIKE :path || '%'")
	public abstract List<Long> getDescendants(String path);

	@Query("SELECT " + SubjectEntity._ID + " FROM " + DATABASE +
			" WHERE :path LIKE " + SubjectEntity.PATH + " || '%'")
	public abstract List<Long> getAncestors(String path);

	@Query("SELECT * FROM " + DATABASE +
			" INNER JOIN " + SubjectJunction.DATABASE + " AS sj" +
			" ON sj." + SubjectEntity._ID + "=sj." + SubjectJunction.SUBJECT_ID +
			" WHERE " + "sj." + SubjectJunction.META_ID + "=:metaId")
	public abstract List<SubjectEntity> subjectsForImage(Long metaId);

	@Insert
	abstract Long insertInternal(SubjectEntity entities);

	@Update
	public abstract void update(SubjectEntity entities);

	@Update
	public abstract void update(SubjectEntity... entities);

	@Delete
	public abstract void delete(SubjectEntity... entities);

	public boolean importKeywords(Context context, Reader keywordList)
	{
		//Ex:
		//National Park
		//		Badlands
		//		Bryce Canyon
		//		Grand Teton
		//		Haesindang Park
		//			{Penis Park}

		// Clear the existing database
		delete();       //TODO: This might not be defined as delete table yet.

		try
		{
			BufferedReader readbuffer = new BufferedReader(keywordList);

			String line;
			SparseArray<Long> parents = new SparseArray<>();
			while ((line = readbuffer.readLine()) != null)
			{
				String tokens[] = line.split("\t");
				int depth = tokens.length - 1;
				String name = tokens[depth];

				// If the entry is a synonym ex: {bread} then trim and add to parent
				if (name.startsWith("{") && name.endsWith("}"))
				{
					name = name.substring(1, name.length()-1);
					SynonymEntity synonym = new SynonymEntity();
					synonym.subjectId = parents.get(depth - 1);

					// FIXME: insert synonym
					continue;
				}

				ContentValues cv = new ContentValues();
				cv.put(KeywordProvider.Data.KEYWORD_NAME, name);
				Long id = parents.get(depth - 1);
				if (id != null)
				{
					cv.put(KeywordProvider.Data.KEYWORD_PARENT, id);
				}

				Uri uri = context.getContentResolver().insert(
						KeywordProvider.Data.CONTENT_URI,
						cv);

				long childId = uri != null ? ContentUris.parseId(uri) : -1;
				parents.put(depth, childId);
			}
		} catch (IOException e)
		{
			return false;
		}
		return true;

	}

}
