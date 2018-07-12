package com.anthonymandra.rawdroid

import java.util.*

data class XmpFilter(
    val xmp: XmpValues? = null,
    var andTrueOrFalse: Boolean = false,
    val sortAscending: Boolean = true,
    val segregateByType: Boolean = false,
    val sortColumn: SortColumns = SortColumns.Name,
    val hiddenFolders: Set<Long> = Collections.emptySet()) {

    enum class SortColumns {
        Name,
        Date
    }
}
