package com.anthonymandra.rawdroid

class KeywordFilterFragment : KeywordBaseFragment() {
    override val keywordGridLayout: Int
        get() = R.layout.xmp_subject

    init {
        mCascadeSelection = false
    }
}
