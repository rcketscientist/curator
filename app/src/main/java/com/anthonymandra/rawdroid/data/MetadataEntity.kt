package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.ForeignKey.CASCADE
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey


@Entity(tableName = "meta",
    foreignKeys = [ForeignKey(
        entity = FolderEntity::class,
        parentColumns = ["id"],
        childColumns = ["parentId"],
        onDelete = CASCADE)],
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["documentId"], unique = true),
        Index(value = ["parentId"])])
abstract class MetadataEntity {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
    var name: String? = null
    var type: Int = 0
    var processed: Boolean = false
    var uri: String = ""
    // Unique documentId we don't want duplicates from different root permissions
    var documentId: String = ""
    var parentId: Long = -1
    var rating: Float? = null
    var label: String? = null
    var timestamp: String? = null
    var make: String? = null
    // Make and Model tables only need model make is implicit http://en.tekstenuitleg.net/articles/software/database-design-tutorial/second-normal-form.html
    var model: String? = null
    var aperture: String? = null
    var exposure: String? = null
    var flash: String? = null
    var focalLength: String? = null
    var iso: String? = null
    var whiteBalance: String? = null
    var height: Int = 0
    var width: Int = 0
    var latitude: String? = null
    var longitude: String? = null
    var altitude: String? = null
    var orientation: Int = 0
    var lens: String? = null//todo: table
    var driveMode: String? = null//todo: table
    var exposureMode: String? = null//todo: table
    var exposureProgram: String? = null//todo: table
}


