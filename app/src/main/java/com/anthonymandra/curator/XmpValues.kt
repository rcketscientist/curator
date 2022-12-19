package com.anthonymandra.curator

import android.os.Parcelable
import com.anthonymandra.curator.data.SubjectEntity
import kotlinx.android.parcel.Parcelize

@Parcelize
data class XmpValues(var rating: Int? = null,
										 var label: String? = null,
										 var subject: List<SubjectEntity> = emptyList()) : Parcelable

