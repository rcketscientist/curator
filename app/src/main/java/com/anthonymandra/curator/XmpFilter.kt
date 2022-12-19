package com.anthonymandra.curator

import android.os.Parcelable
import com.anthonymandra.curator.data.SubjectEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class XmpFilter(val rating: List<Int> = emptyList(),
					 val label: List<String> = emptyList(),
					 val subject: List<SubjectEntity> = emptyList()) : Parcelable

