package com.anthonymandra.rawdroid

import android.os.Parcelable
import com.anthonymandra.rawdroid.data.SubjectEntity
import kotlinx.android.parcel.Parcelize

@Parcelize
data class XmpFilter(val rating: List<Int> = emptyList(),
										 val label: List<String> = emptyList(),
										 val subject: List<SubjectEntity> = emptyList()) : Parcelable

