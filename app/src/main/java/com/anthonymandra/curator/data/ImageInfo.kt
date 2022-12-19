package com.anthonymandra.curator.data

import androidx.room.Relation
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class ImageInfo(
	@Relation(
		parentColumn = "id",
		entityColumn = "metaId",
		projection = ["subjectId"],
		entity = SubjectJunction::class)
	var subjectIds: List<Long> = emptyList()) : MetadataEntity(), Parcelable {

	companion object {
		fun fromMetadataEntity(meta: MetadataEntity): ImageInfo {
			val result = ImageInfo()
			result.id = meta.id
			result.name = meta.name
			result.type = meta.type
			result.size = meta.size
			result.processed = meta.processed
			result.uri = meta.uri
			result.documentId = meta.documentId
			result.parentId = meta.parentId
			result.rating = meta.rating
			result.label = meta.label
			result.timestamp = meta.timestamp
			result.make = meta.make
			result.model = meta.model
			result.aperture = meta.aperture
			result.exposure = meta.exposure
			result.flash = meta.flash
			result.focalLength = meta.focalLength
			result.iso = meta.iso
			result.whiteBalance = meta.whiteBalance
			result.height = meta.height
			result.width = meta.width
			result.latitude = meta.latitude
			result.longitude = meta.longitude
			result.altitude = meta.altitude
			result.orientation = meta.orientation
			result.lens = meta.lens
			result.driveMode = meta.driveMode
			result.exposureMode = meta.exposureMode
			result.exposureProgram = meta.exposureProgram
			return result
		}
	}
}

