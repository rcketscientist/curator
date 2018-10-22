package com.anthonymandra.rawdroid.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.android.parcel.Parcelize

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
open class MetadataEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var name: String = "",
    var type: Int = 0,
    var size: Long = 0,
    var processed: Boolean = false,
    var uri: String = "",
    var documentId: String = "",
    var parentId: Long = -1,
    var rating: Float? = null,
    var label: String? = null,   //TODO: This should be a table with nickname red: delete, etc.
    var timestamp: Long? = null,
    var make: String? = null,
    // Make and Model tables only need model make is implicit http://en.tekstenuitleg.net/articles/software/database-design-tutorial/second-normal-form.html
    var model: String? = null,
    var aperture: String? = null,
    var exposure: String? = null,
    var flash: String? = null,
    var focalLength: String? = null,
    var iso: String? = null,
    var whiteBalance: String? = null,
    var height: Int = 0,
    var width: Int = 0,
    var latitude: String? = null,
    var longitude: String? = null,
    var altitude: String? = null,
    var orientation: Int = 0,
    var lens: String? = null,//todo: table
    var driveMode: String? = null,//todo: table
    var exposureMode: String? = null,//todo: table
    var exposureProgram: String? = null//todo: table
) {
    // Simple equals regarding only gallery items
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MetadataEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (uri != other.uri) return false
        if (rating != other.rating) return false
        if (label != other.label) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun toString(): String {
        return "MetadataEntity($documentId)"
    }
}


