package com.anthonymandra.curator

class KeywordFilterFragment : KeywordBaseFragment() {
    override val keywordGridLayout: Int
        get() = R.layout.xmp_subject

    init {
        mCascadeSelection = false
    }
}
