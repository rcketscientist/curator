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
import com.jakewharton.rxbinding2.widget.itemClicks
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import java.util.*

typealias KeywordSelectedListener = (Collection<SubjectEntity>) -> Unit
abstract class KeywordBaseFragment : Fragment() {

    // Kotlin interfaces don't support SAM, so just store a callback
    private var onKeywordsSelected: KeywordSelectedListener? = null
    val selectedKeywords = HashSet<SubjectEntity>()
    protected var mCascadeSelection = false
    abstract val keywordGridLayout: Int
    private lateinit var keywordGrid: GridView
    private lateinit var viewModel: KeywordViewModel

    private val disposables = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(keywordGridLayout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = SelectArrayAdapter(activity!!)
        keywordGrid = view.findViewById(R.id.keywordGridView)
        keywordGrid.adapter = adapter
        val keywordListener = keywordGrid
            .itemClicks()
            .subscribe({ onKeywordClicked(adapter.getItem(it)) })
        disposables.add(keywordListener)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(KeywordViewModel::class.java)
        viewModel.keywords.observe(this, Observer {
            keywords: List<SubjectEntity>? -> updateKeywordGrid(keywords)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    fun setOnKeywordsSelectedListener(callback: KeywordSelectedListener) {
        onKeywordsSelected = callback
    }

    open fun onKeywordClicked(keyword: SubjectEntity) {
        val selected = isSelected(keyword)

        when {
            mCascadeSelection -> cascadeKeywordSelection(keyword, selected)
            selected -> selectedKeywords.remove(keyword)
            else -> selectedKeywords.add(keyword)
        }

        onKeywordsSelected?.invoke(selectedKeywords)

        (keywordGrid.adapter as SelectArrayAdapter).notifyDataSetChanged()
    }

    fun setSelectedKeywords(selected: Collection<SubjectEntity>) {
        selectedKeywords.clear()
        selectedKeywords.addAll(selected)
    }

    fun clearSelectedKeywords() {
        selectedKeywords.clear()
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
    private fun cascadeKeywordSelection(keyword: SubjectEntity, selected: Boolean) {
        if (selected) { // Un-select all descendants
            val stream = viewModel.getDescendants(keyword.path)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { selectedTree ->
                        selectedKeywords.removeAll { selected -> containsId(selectedTree, selected) }
                        (keywordGrid.adapter as SelectArrayAdapter).notifyDataSetChanged()
                    }
                )
            disposables.add(stream)
        } else {    // Select all ancestors and set recent time
            val time = System.currentTimeMillis()
            val stream = viewModel.getAncestors(keyword.path)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { selectedTree ->
                        selectedTree.forEach { selectedElement ->
                            selectedElement.recent = time
                            selectedKeywords.add(selectedElement)
                        }
                        viewModel.update(selectedTree)
                    }
                )
            disposables.add(stream)
        }
    }

    private fun containsId(list: Collection<SubjectEntity>, keyword: SubjectEntity) : Boolean =
            list.map{it.id}.contains(keyword.id)
    private fun isSelected(keyword: SubjectEntity): Boolean = containsId(selectedKeywords, keyword)

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
