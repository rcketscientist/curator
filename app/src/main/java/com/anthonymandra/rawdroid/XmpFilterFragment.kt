package com.anthonymandra.rawdroid

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.anthonymandra.rawdroid.data.FolderEntity
import com.anthonymandra.rawdroid.data.Label
import com.anthonymandra.rawdroid.data.SubjectEntity
import com.anthonymandra.rawdroid.ui.FilterViewModel
import com.anthonymandra.rawdroid.ui.FolderDialog
import com.anthonymandra.rawdroid.ui.SearchRequestListener
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.xmp_filter_landscape.*
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import java.util.*


typealias FilterChangedListener = (XmpFilter) -> Unit
class XmpFilterFragment : XmpBaseFragment() {

    private var filterChangedCallback: FilterChangedListener? = null
    private var searchRequestCallback: SearchRequestListener? = null

    private var mAndTrueOrFalse: Boolean = false
    private var ascending: Boolean = false
    private var mSegregateByType: Boolean = false
    private var sortColumn: XmpFilter.SortColumns = XmpFilter.SortColumns.Name
    private var mHiddenFolders: List<FolderEntity> = Collections.emptyList()
    private var mExcludedFolders: List<FolderEntity> = Collections.emptyList()

    private val disposables = CompositeDisposable()

    private lateinit var viewModel: FilterViewModel
    private lateinit var mFolderDialog: FolderDialog

    private var andOr: Boolean
        get() = mAndTrueOrFalse
        private set(and) {
            mAndTrueOrFalse = and
            onFilterUpdated()
        }

    private var segregate: Boolean
        get() = mSegregateByType
        private set(segregate) {
            mSegregateByType = segregate
            onFilterUpdated()
        }

    private val xmpValues: XmpValues
        get() {
            return XmpValues(ratings, colorLabels, subject)
        }

    val excludedFolders: List<String>?
        get() = mExcludedFolders.map { it.documentUri }

    private val xmpFilter: XmpFilter
        get() = XmpFilter(
                xmpValues,
                mAndTrueOrFalse,
                ascending,
                mSegregateByType,
                sortColumn,
                mHiddenFolders.map { it.id }.toSet())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.xmp_filter_landscape, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setExclusive(false)
        setAllowUnselected(true)

        // Pull up stored filter configuration
        val pref = activity!!.getSharedPreferences(mPrefName, Context.MODE_PRIVATE)
        mAndTrueOrFalse = pref.getBoolean(mPrefRelational, false)
        ascending = pref.getBoolean(mPrefAscending, true)
        sortColumn = XmpFilter.SortColumns.valueOf(pref.getString(mPrefColumn, XmpFilter.SortColumns.Name.toString()))
        mSegregateByType = pref.getBoolean(mPrefSegregate, true)

        // Initial match setting
        toggleAnd.isChecked = mAndTrueOrFalse

        // Initial sort setting
        if (ascending) {
            if (XmpFilter.SortColumns.Name === sortColumn)
                toggleSortAfirst.isChecked = true
            else
                toggleSortOldFirst.isChecked = true
        } else {
            if (XmpFilter.SortColumns.Name === sortColumn)
                toggleSortZfirst.isChecked = true
            else
                toggleSortYoungFirst.isChecked = true
        }

        // Initial segregate value
        segregateToggleButton.isChecked = mSegregateByType

        clearFilterButton.setOnClickListener { clear() }
        toggleAnd.setOnCheckedChangeListener { _, checked -> andOr = checked }
        sortToggleGroup.setOnCheckedChangeListener { group, _ -> setSort(group.checkedId) }
        segregateToggleButton.setOnCheckedChangeListener { _, isChecked -> segregate = isChecked }
        helpButton.setOnClickListener { startTutorial() }
        foldersButton.setOnClickListener { showFolderDialog() }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(FilterViewModel::class.java)
        viewModel.folders.observe(this, Observer { folders: List<FolderEntity>? ->
            mExcludedFolders = folders?.filter { it.excluded } ?: Collections.emptyList()
            mHiddenFolders = folders?.filter { !it.visible } ?: Collections.emptyList()
            onFilterUpdated()
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    private fun showFolderDialog() {
        val position = IntArray(2)
        foldersButton.getLocationOnScreen(position)
        mFolderDialog = FolderDialog.newInstance(
            position[0],
            position[1])

        mFolderDialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.FolderDialog)
        mFolderDialog.setSearchRequestedListener { searchRequestCallback?.invoke() }

        mFolderDialog.show(fragmentManager!!, TAG)
    }

    private fun setSort(checkedId: Int) {
        when (checkedId) {
            // A is quantitatively lowest, ascending
            R.id.toggleSortAfirst -> {
                ascending = true
                sortColumn = XmpFilter.SortColumns.Name
            }
            R.id.toggleSortZfirst -> {
                ascending = false
                sortColumn = XmpFilter.SortColumns.Name
            }
            // Young is quantitatively highest, descending
            R.id.toggleSortYoungFirst -> {
                ascending = false
                sortColumn = XmpFilter.SortColumns.Date
            }
            R.id.toggleSortOldFirst -> {
                ascending = true
                sortColumn = XmpFilter.SortColumns.Date
            }
        }
        onFilterUpdated()
    }

    fun registerXmpFilterChangedListener(listener: FilterChangedListener) {
        filterChangedCallback = listener
    }

    fun registerSearchRootRequestedListener(listener: SearchRequestListener) {
        searchRequestCallback = listener
    }

    override fun onXmpChanged(xmp: XmpValues) {
        onFilterUpdated()
    }

    private fun onFilterUpdated() {
        if (filterChangedCallback != null) {
            if (activity == null)
                return

            val pref = activity!!.getSharedPreferences(mPrefName, Context.MODE_PRIVATE)
            val editor = pref.edit()

            editor.putBoolean(mPrefAscending, ascending)
            editor.putBoolean(mPrefRelational, mAndTrueOrFalse)
            editor.putString(mPrefColumn, sortColumn.toString())
            editor.putBoolean(mPrefSegregate, mSegregateByType)

            editor.apply()

            val filter = xmpFilter

            filterChangedCallback?.invoke(filter)
        }
    }

    override fun onKeywordsSelected(selectedKeywords: Collection<SubjectEntity>) {
        onFilterUpdated()
    }

    override fun onLabelSelectionChanged(checked: List<Label>) {
        onFilterUpdated()
    }

    override fun onRatingSelectionChanged(checked: List<Int>) {
        onFilterUpdated()
    }

    private fun startTutorial() {
        val sequence = MaterialShowcaseSequence(activity)

        val root = view ?: return

        // Sort group
        sequence.addSequenceItem(getRectangularView(
                sortToggleGroup,
                R.string.sortImages,
                R.string.sortCotent
        ))

        // Segregate
        sequence.addSequenceItem(getRectangularView(
                segregateToggleButton,
                R.string.sortImages,
                R.string.segregateContent
        ))

        // Folder
        sequence.addSequenceItem(getRectangularView(
                foldersButton,
                R.string.filterImages,
                R.string.folderContent
        ))

        // Clear
        sequence.addSequenceItem(getRectangularView(
                clearFilterButton,
                R.string.filterImages,
                R.string.clearFilterContent
        ))

        // rating
        sequence.addSequenceItem(getRectangularView(
                filterLabelRating,
                R.string.filterImages,
                R.string.ratingLabelContent
        ))

        // subject
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.keywordFragment),
                R.string.filterImages,
                R.string.subjectContent
        ))

        // Match
        sequence.addSequenceItem(getRectangularView(
                toggleAnd,
                R.string.filterImages,
                R.string.matchContent
        ))

        sequence.start()
    }

    private fun getRectangularView(target: View, @StringRes titleId: Int, @StringRes contentId: Int): MaterialShowcaseView {
        return getRectangularView(target,
                getString(titleId),
                getString(contentId),
                getString(R.string.ok))
    }

    private fun getRectangularView(target: View, title: String, content: String, dismiss: String): MaterialShowcaseView {
        return MaterialShowcaseView.Builder(activity)
                .setTarget(target)
                .setTitleText(title)
                .setContentText(content)
                .setDismissOnTouch(true)
                .setDismissText(dismiss)
                .withRectangleShape()
                .build()
    }

    companion object {
        private val TAG = XmpBaseFragment::class.java.simpleName

        private const val mPrefName = "galleryFilter"
        private const val mPrefRelational = "relational"
        private const val mPrefAscending = "ascending"
        private const val mPrefColumn = "column"
        private const val mPrefSegregate = "segregate"
    }
}
