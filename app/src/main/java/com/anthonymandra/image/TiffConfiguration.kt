package com.anthonymandra.image

class TiffConfiguration(val compress: Boolean = false) : ImageConfiguration() {
	override val type = ImageConfiguration.ImageType.TIFF
	override val extension ="tiff"
	override val parameters = compress.toString()

	constructor(preference: String) : this(preference.toBoolean())
}
