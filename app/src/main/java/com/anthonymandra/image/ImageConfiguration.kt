package com.anthonymandra.image

import android.content.Context
import android.preference.PreferenceManager

import com.anthonymandra.rawdroid.settings.StorageSettingsFragment

abstract class ImageConfiguration {

	abstract val type: ImageType
	abstract val extension: String
	abstract val parameters: String

	enum class ImageType {
		JPEG,
		TIFF,
		RAW
	}

	fun savePreference(c: Context) {
		val pref = PreferenceManager.getDefaultSharedPreferences(c)
		val editor = pref.edit()
		editor.putString(StorageSettingsFragment.KEY_DefaultSaveType, type.toString())
		editor.putString(StorageSettingsFragment.KEY_DefaultSaveConfig, parameters)
		editor.apply()
	}

	companion object {

		fun from(type:ImageType, config: String?): ImageConfiguration {
			return when (type) {
				ImageType.JPEG -> if (config != null) JpegConfiguration(config) else JpegConfiguration()
				ImageType.TIFF -> if (config != null) TiffConfiguration(config) else TiffConfiguration()
				else -> if (config != null) JpegConfiguration(config) else JpegConfiguration()
			}
		}

		fun fromPreference(c: Context): ImageConfiguration? {
			val pref = PreferenceManager.getDefaultSharedPreferences(c)
			val typeString = pref.getString(StorageSettingsFragment.KEY_DefaultSaveType, null) ?: return null
			val config = pref.getString(StorageSettingsFragment.KEY_DefaultSaveConfig, null)

			val type = ImageType.valueOf(typeString)
			return from(type, config)
		}
	}
}
