package com.anthonymandra.rawdroid.ui

import android.app.Dialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.anthonymandra.framework.DocumentUtil
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.FolderEntity
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.folder_list_item.*
import kotlinx.android.synthetic.main.folder_visibility.*

typealias SearchRequestListener = () -> Unit
class FolderDialog : DialogFragment() {

    private val sortedFolders: MutableList<FolderEntity> = arrayListOf()
    internal lateinit var viewModel: FilterViewModel
    private lateinit var mAdapter: FolderAdapter

    private var searchRequestCallback: SearchRequestListener? = null

    fun setSearchRequestedListener(listener: SearchRequestListener) {
        searchRequestCallback = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        // request a window without the title
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.folder_visibility, container, false)
        val x = arguments!!.getInt(ARG_X)
        val y = arguments!!.getInt(ARG_Y)

        // Set the position of the dialog
        val window = dialog.window
        window!!.setGravity(Gravity.TOP or Gravity.START)
        val params = window.attributes
        params.x = x
        params.y = y
        window.attributes = params

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAdapter = FolderAdapter()

        folderVisibilityListView.layoutManager = LinearLayoutManager(context)
        folderVisibilityListView.adapter = mAdapter
        buttonAddSearchRoot.setOnClickListener {
            searchRequestCallback?.invoke()
            dismiss()
        }

        viewModel = ViewModelProviders.of(this).get(FilterViewModel::class.java)
        viewModel.folders.observe(this, Observer { folders: List<FolderEntity>? ->
            if (folders == null) return@Observer

            val allParents = folders.toMutableList()

            //TODO: This is locking
            // Now we want to check for shell parents as well
//            for (path in folders) {
//                val folder = UsefulDocumentFile.fromUri(context!!, Uri.parse(path.documentUri))
//                var parent = folder.parentFile
//                while (parent != null) {
//                    allParents.add(FolderEntity(parent.uri.toString()))
//                    parent = parent.parentFile
//                }
//            }

            // Add any discovered folders
            viewModel.insertFolders(*allParents.filter { !folders.contains(it) }.toTypedArray())

            sortedFolders.clear()

            // We place the excluded folders at the end
            allParents.filterNotTo(sortedFolders) { it.excluded }
            allParents.filterTo(sortedFolders) { it.excluded }

            mAdapter.submitList(sortedFolders)
        })
    }

    inner class FolderFilterViewHolder(override val containerView: View)
        : RecyclerView.ViewHolder(containerView), LayoutContainer {
        private var folder: FolderEntity? = null

        fun bind(folder: FolderEntity?) {
            this.folder = folder
            if (folder == null) {
                checkBoxFolderPath.text = ""
                checkBoxFolderPath.isChecked = false
            }
            else {
                updateView()
            }

            checkBoxFolderPath.setOnCheckedChangeListener { _, isChecked ->
                if (folder == null) return@setOnCheckedChangeListener

                folder.visible = isChecked

                viewModel.updateFolders(folder)
            }

            excludeButton.setOnClickListener {
                if (folder == null) return@setOnClickListener

                folder.excluded = !folder.excluded
                folder.visible = !folder.excluded

                updateView()
                viewModel.updateFolders(folder)
            }
        }

        private fun updateView() {
            folder?.let {
                checkBoxFolderPath.text = DocumentUtil.getNicePath(Uri.parse(it.documentUri))
                checkBoxFolderPath.isChecked = it.visible && !it.excluded
                // If excluded disable visibility switch and strike-through
                if (it.excluded) {
                    checkBoxFolderPath.paintFlags = checkBoxFolderPath.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    checkBoxFolderPath.isEnabled = false
                } else {
                    checkBoxFolderPath.paintFlags = checkBoxFolderPath.paintFlags xor Paint.STRIKE_THRU_TEXT_FLAG
                    checkBoxFolderPath.isEnabled = true
                }
            }
        }
    }

    inner class FolderAdapter
        : ListAdapter<FolderEntity, FolderFilterViewHolder>(DIFF_CALLBACK) {

        // TODO: This is poor coupling, return to this later
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderFilterViewHolder {
            return FolderFilterViewHolder(LayoutInflater.from(parent.context)
                            .inflate(R.layout.folder_list_item, parent, false))
        }

        override fun onBindViewHolder(holder: FolderFilterViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    companion object {
        const val ARG_X = "x"
        const val ARG_Y = "y"

        internal fun newInstance(x: Int, y: Int): FolderDialog {
            val f = FolderDialog()
            val args = Bundle()
            args.putInt(ARG_X, x)
            args.putInt(ARG_Y, y)

            f.arguments = args
            return f
        }

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FolderEntity>() {
            override fun areContentsTheSame(oldItem: FolderEntity, newItem: FolderEntity): Boolean =
                    oldItem == newItem

            override fun areItemsTheSame(oldItem: FolderEntity, newItem: FolderEntity): Boolean =
                    oldItem.id == newItem.id
        }
    }
}