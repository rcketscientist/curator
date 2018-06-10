package com.anthonymandra.rawdroid.ui

import android.arch.paging.PagedListAdapter
import android.net.Uri
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.anthonymandra.rawdroid.data.MetadataTest
import java.util.*
import kotlin.collections.HashSet

class GalleryAdapter : PagedListAdapter<MetadataTest, GalleryViewHolder>(POST_COMPARATOR)
{
    init { setHasStableIds(true) }

    private val mSelectedItems = HashSet<MetadataTest>()
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

    val selectedItems: Collection<MetadataTest>
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

            onItemClickListener?.onItemClick(this, it, clickPosition, getItemId(clickPosition))

            toggleSelection(clickPosition)
        }

        holder.itemView.setOnLongClickListener {
            val clickPosition = holder.adapterPosition
            if (clickPosition == RecyclerView.NO_POSITION) return@setOnLongClickListener false

            onItemLongClickListener?.onItemLongClick(this, it, clickPosition, getItemId(clickPosition))

            if (multiSelectMode) {
                addGroupSelection(clickPosition)
            } else {
                multiSelectMode = true
                toggleSelection(clickPosition)
            }
            true
        }

        holder.itemView.isActivated = mSelectedItems.contains(item)
    }

    private fun addGroupSelection(position: Int) {
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
            addSelection(getItem(i), i)
        }
        updateSelection()
        notifyItemRangeChanged(start, end - start + 1)  // inclusive
    }

    private fun addSelection(image: MetadataTest?, position: Int) {
        if (image == null)
            return

        mSelectedItems.add(image)
        mSelectedPositions.add(position)
    }

    private fun removeSelection(image: MetadataTest, position: Int) {
        mSelectedItems.remove(image)
        mSelectedPositions.remove(position)
    }

    @SuppressWarnings("unused")
    private fun clearSelection() {
        mSelectedItems.clear()
        mSelectedPositions.clear()
        updateSelection()
        // Lazy update to all, but avoid notifyDatasetChanged since there's no structural changes
        notifyItemRangeChanged(0, itemCount)
    }

    private fun toggleSelection(position: Int) {
        // We could handle this internally with an extra unnoticed toggle when entering viewer,
        // but we'd still need the selection range logic, which doesn't have access to each
        // individual view, so might as well do everything the same way
        val image = getItem(position) ?: return

        if (mSelectedItems.contains(image)) {
            removeSelection(image, position)
        } else {
            addSelection(image, position)
        }
        updateSelection()
        notifyItemChanged(position)
    }

    private fun updateSelection() = onSelectionChangedListener?.onSelectionUpdated(selectedItems)

    fun selectAll() {
        multiSelectMode = true
        val list = currentList ?: return    // TODO: Need to spin through and gather all ids
        // Actually best solution is prolly to accept ids for selection externally
        mSelectedItems.addAll( list.mapNotNull { it } )
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
        fun onItemLongClick(parent: RecyclerView.Adapter<*>, view: View, position: Int, id: Long)
    }

    interface OnSelectionUpdatedListener {
        fun onSelectionUpdated(selectedUris: Collection<MetadataTest>)
    }

    companion object {
        val POST_COMPARATOR = object : DiffUtil.ItemCallback<MetadataTest>() {
            override fun areContentsTheSame(oldItem: MetadataTest, newItem: MetadataTest): Boolean =
                oldItem == newItem

            override fun areItemsTheSame(oldItem: MetadataTest, newItem: MetadataTest): Boolean =
                oldItem.uri == newItem.uri
        }
    }
}