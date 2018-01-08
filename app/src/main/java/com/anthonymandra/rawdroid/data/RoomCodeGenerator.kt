package com.anthonymandra.rawdroid.data

import java.io.File
import java.lang.reflect.Method

object RoomCodeGenerator {

    val staticQuery =
        "\n\t\"SELECT *,  \" +" +
            "\n\t\t\"(SELECT GROUP_CONCAT(name) \" +" +
                "\n\t\t\t\"FROM meta_subject_junction \" +" +
                "\n\t\t\t\"JOIN xmp_subject \" +" +
                "\n\t\t\t\"ON xmp_subject.id = meta_subject_junction.subjectId \" +" +
                "\n\t\t\t\"WHERE meta_subject_junction.metaId = meta.id) AS keywords, \" +" +
            "\n\t\t\"(SELECT documentUri \" +" +
                "\n\t\t\t\"FROM image_parent \" +" +
                "\n\t\t\t\"WHERE meta.parentId = image_parent.id ) AS parentUri \" +" +
        "\n\t\"FROM meta \""

    val dynamicWhere = arrayListOf(
            "\n\t\":link meta.label IN (:labels) \" +",
            "\n\t\":link meta.rating IN (:ratings) \" +",
            "\n\t\":link meta_subject_junction.subjectId IN (:subjects) \" +",
            "\n\t\":link meta.parentId NOT IN (:hiddenFolderIds) \" +")


    enum class Linker {AND, OR}
    enum class OrderBy {NAME, TIMESTAMP}
    enum class SortBy {ASC, DESC}
    val labelOptions = arrayListOf(true, false)
    val ratingOptions = arrayListOf(true, false)
    val folderOptions = arrayListOf(true, false)
    val subjectOptions = arrayListOf(true, false)
    val typeOptions = arrayListOf(true, false)

    // Order strings
    private val SEGREGATE = "type COLLATE NOCASE ASC,"
    private val NAME = "meta.name COLLATE NOCASE"
    private val TIME = "meta.timestamp COLLATE NOCASE"
    private val ASC = "ASC"
    private val DESC = "DESC"

    private var count = 0

    val pkg = "package com.anthonymandra.rawdroid.data;\n\n"
    val imp =
            "import android.arch.lifecycle.LiveData;\n" +
            "import android.arch.persistence.room.Dao;\n" +
            "import android.arch.persistence.room.Query;\n" +
            "import java.util.HashMap;\n" +
            "import java.util.Map;\n" +
            "import java.util.List;\n\n"

    val className = "MetadataDao_Gen"
    val classDec = "@Dao\n" +
            "public abstract class $className {\n"

    val result = "LiveData<List<MetadataEntity>>"
    val file = "/home/anthony/proj/rawdroid/app/src/main/java/com/anthonymandra/rawdroid/data/${className}.java"

    val fullParameters = "boolean andOr, boolean nameTime, boolean segregate, boolean ascDesc, " +
            "List<String> labels, List<String> subjects, List<Long> hiddenFolderIds, List<Integer> ratings"
    val passCollections = "labels, subjects, hiddenFolderIds, ratings"
    val passParameters = "andOr, nameTime, segregate, ascDesc, $passCollections"

    val queryIdMethod =
            "private int getQueryId($fullParameters) {\n" +
                    "\tint link = andOr ?                           0b00000000 : 0b00000001;\n" +
                    "\tint order = nameTime ?                       0b00000000 : 0b00000010;\n" +
                    "\tint sort = ascDesc ?                         0b00000000 : 0b00000100;\n" +
                    "\tint label = labels.size() > 0 ?              0b00000000 : 0b00001000;\n" +
                    "\tint rating = ratings.size() > 0 ?            0b00000000 : 0b00010000;\n" +
                    "\tint folder = hiddenFolderIds.size() > 0 ?    0b00000000 : 0b00100000;\n" +
                    "\tint subject = subjects.size() > 0 ?          0b00000000 : 0b01000000;\n" +
                    "\tint type = segregate ?                       0b00000000 : 0b10000000;\n" +
                    "\treturn link + order + sort + label + rating + folder + subject + type;\n}\n\n"

    var queryMapMethod =
            "private $result getImages($fullParameters) {\n" +
                    "\tint id = getQueryId($passParameters);\n" +
                    "\tswitch(id) {\n"

    fun GenerateDao() {
        val output = File(file)
        output.createNewFile()
        output.writer().use { out ->

            // Create class wrapper
            out.write(pkg)
            out.write(imp)
            out.write(classDec)

            // Create static query constant
            out.write("\tprivate static final String staticQuery =$staticQuery;\n\n")

            // Generate queries
            val queryCore = "@Query(" + "staticQuery +"
            Linker.values().forEach { linker ->
            OrderBy.values().forEach { order ->
            SortBy.values().forEach { sort ->
            labelOptions.forEach { label ->
            ratingOptions.forEach { rating ->
            folderOptions.forEach { folder ->
            subjectOptions.forEach { subject ->
            typeOptions.forEach { type ->
                val methodName = "getImages$count"
                queryMapMethod += "\t\tcase $count: return $methodName(${getDynamicParameters(label, subject, folder, rating)});\n"
                out.write(queryCore + getDynamicQuery(linker, label, rating, folder, subject, type, order, sort, methodName))

                ++count
                out.write("\n\n")
            }}}}}}}}

            out.write(queryIdMethod)
            out.write(queryMapMethod + "\t}return null;\n}\n\n")
            // Close class
            out.write("\n}")
        }
    }

    data class Parameters(val linker: Linker, val labels: Boolean, val ratings: Boolean,
                          val hiddenFolders: Boolean, val subjects: Boolean,
                          val segregate: Boolean, val order: OrderBy, val sort: SortBy)

    val methodMap = HashMap<Parameters, Method>()

    fun getDynamicQuery(linker: Linker, labels: Boolean, ratings: Boolean,
                        hiddenFolders: Boolean, subjects: Boolean,
                        segregate: Boolean, order: OrderBy, sort: SortBy, method: String): String {
        var query = getWhereClause(linker, labels, ratings, hiddenFolders, subjects)
        query += getOrderClause(segregate, order, sort)
        query += getMethod(labels, ratings, hiddenFolders, subjects, method)
        return query
    }

    fun getDynamicParameters(labels: Boolean, subjects: Boolean, folders: Boolean, ratings: Boolean): String {
        var param = ""
        if (labels) param += "labels, "
        if (subjects) param += "subjects, "
        if (folders) param += "hiddenFolderIds, "
        if (ratings) param += "ratings, "

        param = param.removeSuffix(", ")
        return param
    }

    fun getMethod(labels: Boolean, ratings: Boolean,
                  hiddenFolders: Boolean, subjects: Boolean, method: String): String {
        var method = "\nabstract $result $method("
        if (labels) method += "List<String> labels, "
        if (subjects) method += "List<String> subjects, "
        if (hiddenFolders) method += "List<Long> hiddenFolderIds, "
        if (ratings) method += "List<Integer> ratings, "
        method = method.removeSuffix(", ")
        method += ");"
        return method
    }

    fun getWhereClause(linker: Linker, labels: Boolean, ratings: Boolean,
                       hiddenFolders: Boolean, subjects: Boolean): String {
        var whereClause = ""
        var initialWhere = true
        dynamicWhere.forEach { clause ->
            val link = if (initialWhere) "WHERE" else linker.toString()
            if (labels && clause.contains(":labels")) {
                whereClause += clause.replace(":link", link)
                initialWhere = false
            }
            if (ratings && clause.contains(":ratings")) {
                whereClause += clause.replace(":link", link)
                initialWhere = false
            }
            if (hiddenFolders && clause.contains(":hiddenFolderIds")) {
                whereClause += clause.replace(":link", link)
                initialWhere = false
            }
            if (subjects && clause.contains(":subjects")) {
                whereClause += clause.replace(":link", link)
                initialWhere = false
            }
        }
        return whereClause
    }

    fun getOrderClause(segregate: Boolean, order: OrderBy, sort: SortBy): String {
        val typeClause = if (segregate) SEGREGATE else ""
        val orderClause = when (order) {
            OrderBy.NAME -> NAME
            OrderBy.TIMESTAMP -> TIME
        }
        val sortClause = when (sort) {
            SortBy.ASC -> ASC
            SortBy.DESC -> DESC
        }

        return "\n\t\"ORDER BY $typeClause $orderClause $sortClause\")"
    }
}