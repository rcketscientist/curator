//package com.anthonymandra.rawdroid.ui
//
//import android.support.v7.util.ListUpdateCallback
//import android.support.v7.widget.RecyclerView
//
//class AdapterListUpdateCallback: ListUpdateCallback {
//    /**
//     * Creates an AdapterListUpdateCallback that will dispatch update events to the given adapter.
//     *
//     * @param adapter The Adapter to send updates to.
//     */
//    private val mAdapter: PagedFragmentStatePagerAdapter<*>
//
//    constructor(adapter: PagedFragmentStatePagerAdapter<*>) {
//        mAdapter = adapter
//    }
//
//    /** {@inheritDoc}  */
//    override fun onInserted(position: Int, count: Int) {
//        mAdapter.notifyItemRangeInserted(position, count)
//    }
//
//    /** {@inheritDoc}  */
//    override fun onRemoved(position: Int, count: Int) {
//        mAdapter.notifyItemRangeRemoved(position, count)
//    }
//
//    /** {@inheritDoc}  */
//    override fun onMoved(fromPosition: Int, toPosition: Int) {
//        mAdapter.notifyItemMoved(fromPosition, toPosition)
//    }
//
//    /** {@inheritDoc}  */
//    override fun onChanged(position: Int, count: Int, payload: Any) {
//        mAdapter.notifyItemRangeChanged(position, count, payload)
//    }
//}