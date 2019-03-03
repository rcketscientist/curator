package com.anthonymandra.rawdroid.models

import com.anthonymandra.rawdroid.data.Label

class ColorKeys {
	var blue: String = "Blue"
	var red: String = "Red"
	var green: String = "Green"
	var yellow: String = "Yellow"
	var purple: String = "Purple"

	fun customValue(label: Label): String {
		return when (label) {
			Label.Blue -> blue
			Label.Red -> red
			Label.Green -> green
			Label.Yellow -> yellow
			Label.Purple -> purple
		}
	}

	fun label(customString: String): Label? {
		return when (customString) {
			blue -> Label.Blue
			red -> Label.Red
			green -> Label.Green
			yellow -> Label.Yellow
			purple -> Label.Purple
			else -> null
		}
	}
}

