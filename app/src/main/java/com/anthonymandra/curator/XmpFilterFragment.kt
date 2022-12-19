package com.anthonymandra.curator

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.anthonymandra.curator.databinding.XmpCoreBinding
import com.anthonymandra.curator.databinding.XmpFilterLandscapeBinding
import com.anthonymandra.curator.data.FolderEntity
import com.anthonymandra.curator.data.Label
import com.anthonymandra.curator.data.SubjectEntity
import com.anthonymandra.curator.ui.FilterViewModel
import com.anthonymandra.curator.ui.FolderDialog
import com.anthonymandra.curator.ui.SearchRequestListener
import io.reactivex.disposables.CompositeDisposable
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import java.util.*


typealias FilterChangedListener = (ImageFilter) -> Unit
class XmpFilterFragment : XmpBaseFragment() {
    private var _ui: XmpFilterLandscapeBinding? = null
    private lateinit var uiCore: XmpCoreBinding

    // This property is only valid between onCreateView and onDestroyView.
    private val ui get() = _ui!!

    private var filterChangedCallback: FilterChangedListener? = null
    private var searchRequestCallback: SearchRequestListener? = null

    private var mAndTrueOrFalse: Boolean = false
    private var ascending: Boolean = false
    private var mSegregateByType: Boolean = false
    private var sortColumn: ImageFilter.SortColumns = ImageFilter.SortColumns.Name
    private var mHiddenFolders: List<FolderEntity> = Collections.emptyList()
    private var mExcludedFolders: List<FolderEntity> = Collections.emptyList()

    private val disposables = CompositeDisposable()

    private lateinit var viewModel: FilterViewModel
    private lateinit var mFolderDialog: FolderDialog
    private lateinit var preferences: SharedPreferences

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

    private val xmpFilter: XmpFilter
        get() {
            return XmpFilter(ratings, colorLabels, subject)
        }

    private val imageFilter: ImageFilter
        get() = ImageFilter(
                xmpFilter.rating,
                xmpFilter.label,
                xmpFilter.subject.map {it.id},
                mAndTrueOrFalse,
                ascending,
                mSegregateByType,
                sortColumn,
                mHiddenFolders.asSequence().map { it.id }.toSet())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _ui = XmpFilterLandscapeBinding.inflate(inflater, container, false)
        return ui.root
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isSingleSelection(false)
//        setAllowUnselected(true)

        // Pull up stored filter configuration
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        mAndTrueOrFalse = preferences.getBoolean(mPrefRelational, false)
        ascending = preferences.getBoolean(mPrefAscending, true)
        preferences.getString(mPrefColumn, sortColumn.toString())?.let {
            sortColumn = ImageFilter.SortColumns.valueOf(it)
        }
        mSegregateByType = preferences.getBoolean(mPrefSegregate, true)

        // Initial match setting
        ui.toggleAnd.isChecked = mAndTrueOrFalse

        // TODO: Pretty sure I shouldn't need all these casts
        // Initial sort setting
        if (ascending) {
            if (ImageFilter.SortColumns.Name === sortColumn)
                ui.toggleSortAfirst.isChecked = true
            else
                ui.toggleSortOldFirst.isChecked = true
        } else {
            if (ImageFilter.SortColumns.Name === sortColumn)
                ui.toggleSortZfirst.isChecked = true
            else
                ui.toggleSortYoungFirst.isChecked = true
        }

        // Initial segregate value
        ui.segregateToggleButton.isChecked = mSegregateByType

        ui.clearFilterButton.setOnClickListener { clear() }
        ui.toggleAnd.addOnCheckedChangeListener { _, checked -> andOr = checked }
        ui.sortToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked -> setSort(checkedId) }
        ui.segregateToggleButton.addOnCheckedChangeListener { _, isChecked -> segregate = isChecked }
        ui.helpButton.setOnClickListener { startTutorial() }
        ui.foldersButton.setOnClickListener { showFolderDialog() }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProvider(this).get(FilterViewModel::class.java)
        viewModel.folders.observe(viewLifecycleOwner, Observer { folders: List<FolderEntity>? ->
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
        ui.foldersButton.getLocationOnScreen(position)
        mFolderDialog = FolderDialog.newInstance(
            position[0],
            position[1])

        mFolderDialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.FolderDialog)
        mFolderDialog.setSearchRequestedListener { searchRequestCallback?.invoke() }

        mFolderDialog.show(parentFragmentManager, TAG)
    }

    private fun setSort(checkedId: Int) {
        when (checkedId) {
            // A is quantitatively lowest, ascending
            R.id.toggleSortAfirst -> {
                ascending = true
                sortColumn = ImageFilter.SortColumns.Name
            }
            R.id.toggleSortZfirst -> {
                ascending = false
                sortColumn = ImageFilter.SortColumns.Name
            }
            // Young is quantitatively highest, descending
            R.id.toggleSortYoungFirst -> {
                ascending = false
                sortColumn = ImageFilter.SortColumns.Date
            }
            R.id.toggleSortOldFirst -> {
                ascending = true
                sortColumn = ImageFilter.SortColumns.Date
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

    override fun onXmpChanged(xmp: XmpFilter) {
        onFilterUpdated()
    }

    private fun onFilterUpdated() {
        if (filterChangedCallback != null) {
            if (activity == null)
                return

            val editor = preferences.edit()

            editor.putBoolean(mPrefAscending, ascending)
            editor.putBoolean(mPrefRelational, mAndTrueOrFalse)
            editor.putString(mPrefColumn, sortColumn.toString())
            editor.putBoolean(mPrefSegregate, mSegregateByType)

            editor.apply()

            val filter = imageFilter

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
            ui.sortToggleGroup,
                R.string.sortImages,
                R.string.sortCotent
        ))

        // Segregate
        sequence.addSequenceItem(getRectangularView(
            ui.segregateToggleButton,
                R.string.sortImages,
                R.string.segregateContent
        ))

        // Folder
        sequence.addSequenceItem(getRectangularView(
            ui.foldersButton,
                R.string.filterImages,
                R.string.folderContent
        ))

        // Clear
        sequence.addSequenceItem(getRectangularView(
            ui.clearFilterButton,
                R.string.filterImages,
                R.string.clearFilterContent
        ))

        // rating
        sequence.addSequenceItem(getRectangularView(
            ui.filterLabelRating.ratingBar,
                R.string.filterImages,
                R.string.ratingLabelContent
        ))

        // subject
        sequence.addSequenceItem(getRectangularView(
            uiCore.root,
                R.string.filterImages,
                R.string.subjectContent
        ))

        // Match
        sequence.addSequenceItem(getRectangularView(
            ui.toggleAnd,
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

        private const val mPrefRelational = "relational"
        private const val mPrefAscending = "ascending"
        private const val mPrefColumn = "column"
        private const val mPrefSegregate = "segregate"
    }
}
