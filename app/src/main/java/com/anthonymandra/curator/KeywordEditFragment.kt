package com.anthonymandra.curator

class KeywordEditFragment : KeywordBaseFragment() {
    override val keywordGridLayout: Int
        get() = R.layout.xmp_subject_edit

    init {
        mCascadeSelection = true
    }
    // TODO: We could likely just eliminate this extension and handle it with nest fragments
    //TODO: Handle Add/Edit
}
