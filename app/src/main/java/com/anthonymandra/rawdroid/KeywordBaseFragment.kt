package com.anthonymandra.rawdroid

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.GridView
import com.anthonymandra.rawdroid.data.SubjectEntity
import com.anthonymandra.rawdroid.ui.KeywordViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import java.util.*

abstract class KeywordBaseFragment : Fragment() {

    lateinit var mListener: OnKeywordsSelectedListener
    private val mSelectedKeywords = HashSet<SubjectEntity>()
    protected var mCascadeSelection = false
    abstract val keywordGridLayout: Int
    private lateinit var keywordGrid: GridView
    private lateinit var viewModel: KeywordViewModel

    interface OnKeywordsSelectedListener {
        fun onKeywordsSelected(selectedKeywords: Collection<SubjectEntity>)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(keywordGridLayout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = SelectArrayAdapter(activity!!)
        keywordGrid = view.findViewById(R.id.keywordGridView)
        keywordGrid.adapter = adapter
        keywordGrid.setOnItemClickListener { _, _, position, _ ->
            onKeywordClicked(adapter.getItem(position))
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(KeywordViewModel::class.java)
        subscribeUi(viewModel)
    }

    private fun subscribeUi(viewModel: KeywordViewModel) {
        // Update the list when the data changes
        viewModel.keywords.observe(this, Observer {
            keywords: List<SubjectEntity>? -> updateKeywordGrid(keywords)
        })
    }

    fun setOnKeywordsSelectedListener(listener: OnKeywordsSelectedListener) {
        mListener = listener
    }

    fun onKeywordClicked(keyword: SubjectEntity) {
        setSelectedKeyword(keyword)
    }

    fun getSelectedKeywords(): Collection<SubjectEntity> {
        return mSelectedKeywords
    }

    fun setSelectedKeyword(keyword: SubjectEntity) {
        val selected = isSelected(keyword)

        if (mCascadeSelection) {
            cascadeKeywordSelection(keyword, selected)
        }
        else if (selected) {
            mSelectedKeywords.remove(keyword)
        }
        else {
            mSelectedKeywords.add(keyword)
        }

        if (mListener != null)
            mListener.onKeywordsSelected(mSelectedKeywords)

        (keywordGrid.adapter as SelectArrayAdapter).notifyDataSetChanged()
    }

    fun setSelectedKeywords(selected: Collection<SubjectEntity>) {
        mSelectedKeywords.clear()
        mSelectedKeywords.addAll(selected)
    }

    fun clearSelectedKeywords() {
        mSelectedKeywords.clear()
        (keywordGrid.adapter as SelectArrayAdapter).notifyDataSetChanged()
    }

    private fun updateKeywordGrid(keywords: List<SubjectEntity>?) {
        keywords?.let {
            val adapter = (keywordGrid.adapter as SelectArrayAdapter)
            adapter.clear()
            adapter.addAll(keywords)
        }
    }

    /**
     * This will use the Path aspects of Keywords to cascade selection up ancestors or down descendants
     */
    protected fun cascadeKeywordSelection(keyword: SubjectEntity, selected: Boolean) {
        if (selected) { // Un-select all descendants
            viewModel.getDescendants(keyword.path)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { selectedTree ->
                        mSelectedKeywords.removeAll { selected -> containsId(selectedTree, selected) }
                        (keywordGrid.adapter as SelectArrayAdapter).notifyDataSetChanged()
                    }
                )
        } else {    // Select all ancestors and set recent time
            val time = System.currentTimeMillis()
            viewModel.getAncestors(keyword.path)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { selectedTree ->
                        selectedTree.forEach { selectedElement ->
                            selectedElement.recent = time
                            mSelectedKeywords.add(selectedElement)
                        }
                        viewModel.update(selectedTree)
                    }
                )
        }
    }

    private fun containsId(list: Collection<SubjectEntity>, keyword: SubjectEntity) : Boolean =
            list.map{it.id}.contains(keyword.id)
    private fun isSelected(keyword: SubjectEntity): Boolean = containsId(mSelectedKeywords, keyword)

    private inner class SelectArrayAdapter internal constructor(context: Context) : ArrayAdapter<SubjectEntity>(context, R.layout.keyword_entry) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = if (convertView == null) {
                LayoutInflater.from(context).
                        inflate(R.layout.keyword_entry, parent, false) as CheckedTextView
            } else {
                convertView as CheckedTextView
            }

            val entity = getItem(position)

            if (position % 2 == 0)
                view.background.alpha = 255
            else
                view.background.alpha = 230

            view.text = entity.name
            view.isChecked = isSelected(entity)
            return view
        }
    }
}
