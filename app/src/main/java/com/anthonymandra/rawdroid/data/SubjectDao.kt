package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query
import android.util.SparseArray
import java.io.BufferedReader
import java.io.IOException
import java.io.Reader

@Dao
abstract class SubjectDao : PathDao<SubjectEntity>() {

    @get:Query("SELECT * FROM xmp_subject ORDER BY recent DESC, name ASC")
    abstract val all: LiveData<List<SubjectEntity>>

    @Query("SELECT COUNT(*) FROM xmp_subject")
    abstract fun count(): Int

    @Query("SELECT * FROM xmp_subject WHERE id= :id ")
    internal abstract fun get(id: Long?): SubjectEntity

    @Query("SELECT id FROM xmp_subject WHERE path LIKE :path || '%'")
    abstract override fun getDescendantIds(path: String): List<Long>

    @Query("SELECT id FROM xmp_subject WHERE :path LIKE path || '%'")
    abstract override fun getAncestorIds(path: String): List<Long>

    @Query("SELECT * FROM xmp_subject WHERE path LIKE :path || '%'")
    abstract fun getDescendants(path: String): List<SubjectEntity>

    @Query("SELECT * FROM xmp_subject WHERE :path LIKE path || '%'")
    abstract fun getAncestors(path: String): List<SubjectEntity>

    @Query("SELECT * FROM xmp_subject " +
        "INNER JOIN meta_subject_junction " +
        "ON meta_subject_junction.subjectId = xmp_subject.id " +
        "WHERE id = :metaId")
    abstract fun subjectsForImage(metaId: Long?): List<SubjectEntity>

    @Query("DELETE FROM xmp_subject")
    abstract fun deleteAll()

    fun getDescendants(id: Long): List<SubjectEntity> {
        val pd = get(id)
        return getDescendants(pd.path)
    }

    fun getAncestors(id: Long): List<SubjectEntity> {
        val pd = get(id)
        return getAncestors(pd.path)
    }

    //TODO: This belongs in a data repository
    @Throws(IOException::class)
    fun importKeywords(keywordList: Reader) {
        //Ex:
        //National Park
        //		Badlands
        //		Bryce Canyon
        //		Grand Teton
        //		Haesindang Park
        //			{Penis Park}

        // Clear the existing database
        deleteAll()

        val readBuffer = BufferedReader(keywordList)

        val parents = SparseArray<Long>()
        readBuffer.forEachLine { line ->
            val tokens = line.split("\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val depth = tokens.size - 1
            var name = tokens[depth]

            // If the entry is a synonym ex: {bread} then trim and add to parent
            if (name.startsWith("{") && name.endsWith("}")) {
                name = name.substring(1, name.length - 1)
                val synonym = SynonymEntity()
                synonym.subjectId = parents.get(depth - 1)

                // FIXME: insert synonym
                return@forEachLine
            }

            val keyword = SubjectEntity()
            keyword.name = name

            val id = parents.get(depth - 1)
            if (id != null) {
                keyword.parent = id
            }

            val childId = insert(keyword)

            parents.put(depth, childId)
        }
    }
}
