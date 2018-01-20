package com.anthonymandra.rawdroid.ui

import android.arch.paging.PagedListAdapter
import android.net.Uri
import android.provider.BaseColumns
import android.support.v7.recyclerview.extensions.DiffCallback
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataResult
import java.util.*

class GalleryAdapter() : PagedListAdapter<MetadataResult, RecyclerView.ViewHolder>(POST_COMPARATOR)
{
    private val mSelectedPositions = TreeSet<Int>()

    private var mSelectionListener: OnSelectionUpdatedListener? = null
    /**
     * @return The callback to be invoked with an item in this AdapterView has
     * been clicked, or null id no callback has been set.
     */
    /**
     * Register a callback to be invoked when an item in this AdapterView has
     * been clicked.
     *
     * @param listener The callback that will be invoked.
     */
    var onItemClickListener: OnItemClickListener? = null
    /**
     * @return The callback to be invoked with an item in this AdapterView has
     * been clicked and held, or null id no callback as been set.
     */
    /**
     * Register a callback to be invoked when an item in this AdapterView has
     * been clicked and held
     *
     * @param listener The callback that will run
     */
    var onItemLongClickListener: OnItemLongClickListener? = null

    val selectedItems: Collection<Uri>
        get() = mSelectedPositions
            .map { getUri(it) }
            .filterNotNull()

    val selectedItemCount: Int
        get() = mSelectedPositions.size

    //	@Override
    //	public String getSectionTitle(int position)
    //	{
    //		Cursor c = (Cursor)getItem(position);   // This is the adapter cursor, don't close
    //		if (c == null)
    //			return null;
    //		int index = c.getColumnIndex(Meta.NAME);
    //		final String name = c.getString(index);
    //		if (name == null)
    //			return null;
    //		return name.substring(0,1);
    //	}

    /**
     * Interface definition for a callback to be invoked when an item in this
     * AdapterView has been clicked.
     */
    interface OnItemClickListener {

        /**
         * Callback method to be invoked when an item in this AdapterView has
         * been clicked.
         *
         *
         * Implementers can call getItemAtPosition(position) if they need
         * to access the data associated with the selected item.
         *
         * @param parent The RecyclerView adapter where the click happened.
         * @param view The view within the AdapterView that was clicked (this
         * will be a view provided by the adapter)
         * @param position The position of the view in the adapter.
         * @param id The row id of the item that was clicked.
         */
        fun onItemClick(parent: RecyclerView.Adapter<*>, view: View, position: Int, id: Long)
    }

    /**
     * Interface definition for a callback to be invoked when an item in this
     * view has been clicked and held.
     */
    interface OnItemLongClickListener {
        /**
         * Callback method to be invoked when an item in this view has been
         * clicked and held.
         *
         * Implementers can call getItemAtPosition(position) if they need to access
         * the data associated with the selected item.
         *
         * @param parent The RecyclerView adapter where the click happened
         * @param view The view within the AbsListView that was clicked
         * @param position The position of the view in the list
         * @param id The row id of the item that was clicked
         *
         * @return true if the callback consumed the long click, false otherwise
         */
        fun onItemLongClick(parent: RecyclerView.Adapter<*>, view: View, position: Int, id: Long): Boolean
    }

    interface OnSelectionUpdatedListener {
        fun onSelectionUpdated(selectedUris: Collection<Uri>)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            R.layout.fileview -> GalleryViewHolder.create(parent)
//            R.layout.network_state_item -> NetworkStateItemViewHolder.create(parent, retryCallback)
            else -> throw IllegalArgumentException("unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            R.layout.fileview -> (holder as GalleryViewHolder).bind(getItem(position))
//            R.layout.network_state_item -> (holder as NetworkStateItemViewHolder).bindTo(networkState)
        }

        holder.itemView.isSelected = mSelectedPositions.contains(position)
    }

    fun getUri(position: Int): Uri? {
        return getItem(position)?.uri?.let { Uri.parse(it) }
    }

    fun addGroupSelection(position: Int) {
        if (mSelectedPositions.size > 0) {
            val first = mSelectedPositions.first()
            val last = mSelectedPositions.last()
            if (position > last) {
                addGroupSelection(last, position)
            } else if (position < first) {
                addGroupSelection(position, first)
            }
        } else {
            addGroupSelection(0, position)
        }
    }

    private fun addGroupSelection(start: Int, end: Int) {
        for (i in start..end) {
            addSelection(i)
        }
        updateSelection()
        notifyItemRangeChanged(start, end - start)
    }

    private fun addSelection(position: Int) {
        mSelectedPositions.add(position)
    }

    private fun removeSelection(position: Int) {
        mSelectedPositions.remove(position)
    }

    fun clearSelection() {
        mSelectedPositions.clear()
        updateSelection()
        // Lazy update to all, but avoid notifyDatasetChanged since there's no structural changes
        notifyItemRangeChanged(0, itemCount)
    }

    fun toggleSelection(position: Int) {
        // We could handle this internally with an extra unnoticed toggle when entering viewer,
        // but we'd still need the selection range logic, which doesn't have access to each
        // individual view, so might as well do everything the same way
        val uri = getUri(position) ?: return

        if (mSelectedItems.contains(uri)) {
            removeSelection(v, uri, position)
        } else {
            addSelection(v, uri, position)
        }
        updateSelection()
    }

    private fun updateSelection() {
        mSelectionListener?.onSelectionUpdated(selectedItems)
    }

    fun selectAll() {
        val c = getCursor()
        if (c != null && c!!.moveToFirst()) {
            do {
                mSelectedItems.add(getUri(c!!.getPosition()))
            } while (c!!.moveToNext())

            updateSelection()
            notifyItemRangeChanged(0, itemCount)
        }
    }

    fun setOnSelectionListener(listener: OnSelectionUpdatedListener) {
        mSelectionListener = listener
    }

    companion object {
        val POST_COMPARATOR = object : DiffCallback<RedditPost>() {
            override fun areContentsTheSame(oldItem: RedditPost, newItem: RedditPost): Boolean =
                oldItem == newItem

            override fun areItemsTheSame(oldItem: RedditPost, newItem: RedditPost): Boolean =
                oldItem.name == newItem.name

            override fun getChangePayload(oldItem: RedditPost, newItem: RedditPost): Any? {
                return if (sameExceptScore(oldItem, newItem)) {
                    PAYLOAD_SCORE
                } else {
                    null
                }
            }
        // TODO: This could be an entity, although I think the paging will allow full meta queries
        val REQUIRED_COLUMNS = arrayOf(BaseColumns._ID, Meta.LABEL, Meta.NAME, Meta.ORIENTATION, Meta.RATING, Meta.SUBJECT, Meta.URI, Meta.TYPE)
    }
}