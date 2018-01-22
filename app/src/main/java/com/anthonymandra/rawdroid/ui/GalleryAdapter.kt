package com.anthonymandra.rawdroid.ui

import android.arch.paging.PagedListAdapter
import android.net.Uri
import android.provider.BaseColumns
import android.support.v7.recyclerview.extensions.DiffCallback
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataResult
import java.util.*
import kotlin.collections.HashSet

class GalleryAdapter : PagedListAdapter<MetadataResult, RecyclerView.ViewHolder>(POST_COMPARATOR)
{
    init { setHasStableIds(true) }

    private val mSelectedItems = HashSet<Uri>()
    private val mSelectedPositions = TreeSet<Int>()
    var multiSelectMode = false

    var mSelectionListener: OnSelectionUpdatedListener? = null

    /**
     * Callback to be invoked when an item in this AdapterView has been clicked.
     */
    var onItemClickListener: OnItemClickListener? = null
    /**
     * @return The callback to be invoked with an item in this AdapterView has
     * been clicked and held, or null id no callback as been set.
     */

    /**
     * Callback to be invoked when an item in this AdapterView has been clicked and held
     */
    var onItemLongClickListener: OnItemLongClickListener? = null

    private val selectedItems: Collection<Uri>
        get() = mSelectedItems

    val selectedItemCount: Int
        get() = mSelectedItems.size

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

    override fun getItemId(position: Int): Long {
        return getItem(position)?.id ?: RecyclerView.NO_ID
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
        val item = getItem(position)
        when (getItemViewType(position)) {
            R.layout.fileview -> (holder as GalleryViewHolder).bind(item)
//            R.layout.network_state_item -> (holder as NetworkStateItemViewHolder).bindTo(networkState)
        }

        holder.itemView.setOnClickListener {
            val clickPosition = holder.adapterPosition
            if (clickPosition == RecyclerView.NO_POSITION) return@setOnClickListener
            toggleSelection(clickPosition)
            onItemClickListener?.onItemClick(this, it, clickPosition, getItemId(clickPosition))
        }

        holder.itemView.setOnLongClickListener {
            val clickPosition = holder.adapterPosition
            if (clickPosition == RecyclerView.NO_POSITION) return@setOnLongClickListener false
            toggleSelection(clickPosition)
            return@setOnLongClickListener onItemLongClickListener?.onItemLongClick(
                this, it, clickPosition, getItemId(clickPosition)) ?: false
        }

        // TODO: was: vh.mBaseView.setChecked(mSelectedItems.contains(galleryItem.uri));
        holder.itemView.isSelected = mSelectedItems.contains(Uri.parse(item?.uri))
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
            addSelection(getUri(i), i)
        }
        updateSelection()
        notifyItemRangeChanged(start, end - start)
    }

    private fun addSelection(uri: Uri?, position: Int) {
        if (uri == null)
            return

        mSelectedItems.add(uri)
        mSelectedPositions.add(position)
    }

    private fun removeSelection(uri: Uri, position: Int) {
        mSelectedItems.remove(uri)
        mSelectedPositions.remove(position)
    }

    @SuppressWarnings("unused")
    fun clearSelection() {
        mSelectedItems.clear()
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
            removeSelection(uri, position)
        } else {
            addSelection(uri, position)
        }
        updateSelection()
        notifyItemChanged(position)
    }

    private fun updateSelection() {
        mSelectionListener?.onSelectionUpdated(selectedItems)
    }

    fun selectAll() {
        val list = currentList ?: return
        mSelectedItems.addAll( list.mapNotNull { Uri.parse(it.uri) } )
        updateSelection()
        notifyItemRangeChanged(0, itemCount)
    }

    companion object {
        val POST_COMPARATOR = object : DiffCallback<MetadataResult>() {
            override fun areContentsTheSame(oldItem: MetadataResult, newItem: MetadataResult): Boolean =
                oldItem == newItem

            override fun areItemsTheSame(oldItem: MetadataResult, newItem: MetadataResult): Boolean =
                oldItem.uri == newItem.uri
        }
        // TODO: This could be an entity, although I think the paging will allow full meta queries
        val REQUIRED_COLUMNS = arrayOf(BaseColumns._ID, Meta.LABEL, Meta.NAME, Meta.ORIENTATION, Meta.RATING, Meta.SUBJECT, Meta.URI, Meta.TYPE)
    }
}