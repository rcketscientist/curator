package com.anthonymandra.rawdroid

import java.util.*

data class XmpFilter(
    val xmp: XmpValues? = null,
    val andTrueOrFalse: Boolean = false,
    val sortAscending: Boolean = false,
    val segregateByType: Boolean = false,
    val sortColumn: SortColumns = SortColumns.Name,
    val hiddenFolders: Set<String> = Collections.emptySet()) {

    enum class SortColumns {
        Name,
        Date
    }
}
