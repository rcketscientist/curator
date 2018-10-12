package com.anthonymandra.rawdroid

import android.os.Parcelable
import com.anthonymandra.rawdroid.data.SubjectEntity
import kotlinx.android.parcel.Parcelize

@Parcelize
data class XmpValues(var rating: Int? = null,
										 var label: String? = null,
										 var subject: List<SubjectEntity> = emptyList()) : Parcelable

