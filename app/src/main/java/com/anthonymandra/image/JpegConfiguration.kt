package com.anthonymandra.image

class JpegConfiguration(val quality: Int = 0) : ImageConfiguration() {	// TODO: 0 is not a good default
	override val parameters = quality.toString()
	override val type = ImageConfiguration.ImageType.jpeg
	override val extension ="jpg"

	constructor(preference: String) : this(preference.toInt())
}
