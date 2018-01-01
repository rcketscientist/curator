package com.anthonymandra.rawdroid.data;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;
import android.content.Context;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

@Dao
public abstract class SubjectDao extends PathDao<SubjectEntity>
{
	@Query("SELECT COUNT(*) FROM xmp_subject")
	public abstract int count();

	@Query("SELECT * FROM xmp_subject WHERE id= :id ")
	abstract SubjectEntity get(Long id);

	@Query("SELECT id FROM xmp_subject WHERE path LIKE :path || '%'")
	public abstract List<Long> getDescendantIds(String path);

	@Query("SELECT id FROM xmp_subject WHERE :path LIKE path || '%'")
	public abstract List<Long> getAncestorIds(String path);

	@Query("SELECT * FROM xmp_subject WHERE path LIKE :path || '%'")
	public abstract List<SubjectEntity> getDescendants(String path);

	@Query("SELECT * FROM xmp_subject WHERE :path LIKE path || '%'")
	public abstract List<SubjectEntity> getAncestors(String path);

	@Query("SELECT * FROM xmp_subject " +
			"INNER JOIN meta_subject_junction " +
			"ON meta_subject_junction.subjectId = xmp_subject.id " +
			"WHERE id = :metaId")
	public abstract List<SubjectEntity> subjectsForImage(Long metaId);

	@Query("SELECT * FROM xmp_subject ORDER BY recent DESC, name ASC")
	public abstract LiveData<List<SubjectEntity>> getAll();

	@Insert
	abstract Long insertInternal(SubjectEntity entities);

	@Update
	public abstract void update(SubjectEntity entities);

	@Update
	public abstract void update(List<SubjectEntity> entities);

	@Update
	public abstract void update(SubjectEntity... entities);

	@Delete
	public abstract void delete(SubjectEntity... entities);

	@Query("DELETE FROM xmp_subject")
	public abstract void deleteAll();

	public List<SubjectEntity> getDescendants(long id) {
		PathEntity pd = get(id);
		return getDescendants(pd.path);
	}

	public List<SubjectEntity> getAncestors(long id) {
		PathEntity pd = get(id);
		return getAncestors(pd.path);
	}

	//TODO: This belongs in a data repository
	public void importKeywords(Context context, Reader keywordList) throws IOException {
		//Ex:
		//National Park
		//		Badlands
		//		Bryce Canyon
		//		Grand Teton
		//		Haesindang Park
		//			{Penis Park}

		// Clear the existing database
		deleteAll();

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

			final SubjectEntity keyword = new SubjectEntity();
			keyword.name = name;

			Long id = parents.get(depth - 1);
			if (id != null) {
				keyword.parent = id;
			}

			long childId = insert(keyword);

			parents.put(depth, childId);
		}
	}
}
