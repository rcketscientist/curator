package com.anthonymandra.rawdroid.ui

import android.arch.paging.PagedListAdapter
import android.net.Uri
import android.provider.BaseColumns
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.data.MetadataTest
import java.util.*
import kotlin.collections.HashSet

class GalleryAdapter : PagedListAdapter<MetadataTest, GalleryViewHolder>(POST_COMPARATOR)
{
    init { setHasStableIds(true) }

    private val mSelectedItems = HashSet<Uri>()
    private val mSelectedPositions = TreeSet<Int>()
    var multiSelectMode = false
        set(value)  {
            clearSelection()
            field = value
        }


    var onSelectionChangedListener: OnSelectionUpdatedListener? = null
    /**
     * Callback to be invoked when an item in this AdapterView has been clicked.
     */
    var onItemClickListener: OnItemClickListener? = null
    /**
     * Callback to be invoked when an item in this AdapterView has been clicked and held
     */
    var onItemLongClickListener: OnItemLongClickListener? = null

    val selectedItems: Collection<Uri>
        get() = mSelectedItems

    val selectedItemCount: Int
        get() = mSelectedItems.size

    fun getUri(position: Int): Uri? = getItem(position)?.uri?.let { Uri.parse(it) }
    override fun getItemId(position: Int): Long = getItem(position)?.id ?: RecyclerView.NO_ID

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        return GalleryViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)

        holder.itemView.setOnClickListener {
            val clickPosition = holder.adapterPosition
            if (clickPosition == RecyclerView.NO_POSITION) return@setOnClickListener
            toggleSelection(clickPosition)
            onItemClickListener?.onItemClick(this, it, clickPosition, getItemId(clickPosition))
        }

        holder.itemView.setOnLongClickListener {
            val clickPosition = holder.adapterPosition
            if (clickPosition == RecyclerView.NO_POSITION) return@setOnLongClickListener false

            if (multiSelectMode) {
                addGroupSelection(clickPosition)
            } else {
                multiSelectMode = true
                toggleSelection(clickPosition)
            }

            return@setOnLongClickListener onItemLongClickListener?.onItemLongClick(
                this, it, clickPosition, getItemId(clickPosition)) ?: false
        }

        holder.itemView.isActivated = mSelectedItems.contains(Uri.parse(item?.uri))
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

    private fun updateSelection() = onSelectionChangedListener?.onSelectionUpdated(selectedItems)

    fun selectAll() {
        multiSelectMode = true
        val list = currentList ?: return
        mSelectedItems.addAll( list.mapNotNull { Uri.parse(it.uri) } )
        updateSelection()
        notifyItemRangeChanged(0, itemCount)
    }

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

    companion object {
        val POST_COMPARATOR = object : DiffUtil.ItemCallback<MetadataTest>() {
            override fun areContentsTheSame(oldItem: MetadataTest, newItem: MetadataTest): Boolean =
                oldItem == newItem

            override fun areItemsTheSame(oldItem: MetadataTest, newItem: MetadataTest): Boolean =
                oldItem.uri == newItem.uri
        }
        // TODO: This could be an entity, although I think the paging will allow full meta queries
        val REQUIRED_COLUMNS = arrayOf(BaseColumns._ID, Meta.LABEL, Meta.NAME, Meta.ORIENTATION, Meta.RATING, Meta.SUBJECT, Meta.URI, Meta.TYPE)
    }
}