package com.anthonymandra.rawdroid

import com.anthonymandra.rawdroid.data.SubjectEntity

data class XmpValues(val rating: Collection<Int> = emptyList(),
                     val label: Collection<String> = emptyList(),
                     val subject: Collection<SubjectEntity> = emptyList())

