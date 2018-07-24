package com.anthonymandra.rawdroid

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.*

@Parcelize
data class XmpFilter(
    val xmp: XmpValues? = null,
    var andTrueOrFalse: Boolean = false,
    val sortAscending: Boolean = true,
    val segregateByType: Boolean = false,
    val sortColumn: SortColumns = SortColumns.Name,
    val hiddenFolders: Set<Long> = Collections.emptySet()) : Parcelable {

    enum class SortColumns {
        Name,
        Date
    }
}
