package com.anthonymandra.rawdroid

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.*

@Parcelize
data class ImageFilter(
    val ratings: List<Int> = emptyList(),
    val labels: List<String> = emptyList(),
    val subjectIds: List<Long> = emptyList(),
    var andTrueOrFalse: Boolean = false,
    val sortAscending: Boolean = true,
    val segregateByType: Boolean = false,
    val sortColumn: SortColumns = SortColumns.Name,
    val hiddenFolderIds: Set<Long> = Collections.emptySet()) : Parcelable {

    enum class SortColumns {
        Name,
        Date
    }
}
