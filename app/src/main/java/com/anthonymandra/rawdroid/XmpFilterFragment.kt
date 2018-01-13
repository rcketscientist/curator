package com.anthonymandra.rawdroid

import android.app.Dialog
import android.content.Context
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.app.DialogFragment
import android.support.v7.widget.ToggleGroup
import android.view.*
import android.widget.*
import com.anthonymandra.content.Meta
import com.anthonymandra.framework.DocumentUtil
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.data.SubjectEntity
import com.anthonymandra.widget.XmpLabelGroup
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import java.util.*
import kotlin.collections.LinkedHashSet


typealias FilterChangedListener = (XmpFilter) -> Unit
typealias SearchRequestListener = () -> Unit
class XmpFilterFragment : XmpBaseFragment() {

    private var filterChangedCallback: FilterChangedListener? = null
    private var searchRequestCallback: SearchRequestListener? = null

    private var mAndTrueOrFalse: Boolean = false
    private var ascending: Boolean = false
    private var mSegregateByType: Boolean = false
    private var sortColumn: XmpFilter.SortColumns = XmpFilter.SortColumns.Name
    private var mHiddenFolders: MutableSet<String> = Collections.emptySet()
    private var mExcludedFolders: MutableSet<String> = Collections.emptySet()
    private val mPaths = LinkedHashSet<String>()

    private val disposables = CompositeDisposable()

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

    val excludedFolders: Set<String>?
        get() = mExcludedFolders

    val xmpFilter: XmpFilter
        get() = XmpFilter(
                xmpValues,
                mAndTrueOrFalse,
                ascending,
                mSegregateByType,
                sortColumn,
                mHiddenFolders)

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
        mHiddenFolders = HashSet(pref.getStringSet(mPrefHiddenFolders, HashSet())!!)
        mExcludedFolders = HashSet(pref.getStringSet(mPrefExcludedFolders, HashSet())!!)

        // Initial match setting
        (view.findViewById<View>(R.id.toggleAnd) as CompoundButton).isChecked = mAndTrueOrFalse

        // Initial sort setting
        if (ascending) {
            if (XmpFilter.SortColumns.Name === sortColumn)
                (view.findViewById<View>(R.id.toggleSortAfirst) as CompoundButton).isChecked = true
            else
                (view.findViewById<View>(R.id.toggleSortOldFirst) as CompoundButton).isChecked = true
        } else {
            if (XmpFilter.SortColumns.Name === sortColumn)
                (view.findViewById<View>(R.id.toggleSortZfirst) as CompoundButton).isChecked = true
            else
                (view.findViewById<View>(R.id.toggleSortYoungFirst) as CompoundButton).isChecked = true
        }

        // Initial segregate value
        (view.findViewById<View>(R.id.toggleSegregate) as CompoundButton).isChecked = mSegregateByType

        attachButtons(view)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    private fun attachButtons(root: View) {
        root.findViewById<View>(R.id.clearFilterButton).setOnClickListener { clear() }

        val andOrButton = root.findViewById<CompoundButton>(R.id.toggleAnd)
        andOr = andOrButton.isChecked
        andOrButton.setOnCheckedChangeListener { _, checked -> andOr = checked }

        val sort = root.findViewById<ToggleGroup>(R.id.sortGroup)
        setSort(sort.checkedId)
        sort.setOnCheckedChangeListener { group, _ -> setSort(group.checkedId) }

        val segregateButton = root.findViewById<CompoundButton>(R.id.toggleSegregate)
        segregateButton.setOnCheckedChangeListener { _, isChecked -> segregate = isChecked }

        val clearFilter = root.findViewById<ImageButton>(R.id.clearFilterButton)
        clearFilter.setOnClickListener { clear() }

        val foldersButton = root.findViewById<ImageButton>(R.id.buttonFolders)
        foldersButton.setOnClickListener {
            updatePaths()

            val position = IntArray(2)
            foldersButton.getLocationOnScreen(position)
            mFolderDialog = FolderDialog.newInstance(
                    ArrayList(mPaths),
                    ArrayList(mHiddenFolders),
                    ArrayList(mExcludedFolders),
                    position[0],
                    position[1])
            mFolderDialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.FolderDialog)
            mFolderDialog.setOnVisibilityChangedListener({ visibility ->
                if (visibility.visible) {
                    mHiddenFolders.remove(visibility.Path)
                    mPaths
                        .filter { it.startsWith(visibility.Path) }
                        .forEach { mHiddenFolders.remove(it) }
                } else {
                    mHiddenFolders.add(visibility.Path)
                    mPaths
                        .filter { it.startsWith(visibility.Path) }
                        .forEach { mHiddenFolders.add(it) }
                }
                if (visibility.excluded) {
                    mExcludedFolders.add(visibility.Path)
                    mPaths
                        .filter { it.startsWith(visibility.Path) }
                        .forEach { mExcludedFolders.add(it) }
                } else {
                    mExcludedFolders.remove(visibility.Path)
                    mPaths
                        .filter { it.startsWith(visibility.Path) }
                        .forEach { mExcludedFolders.remove(it) }
                }
                onFilterUpdated()
            })
            mFolderDialog.setSearchRequestedListener({ searchRequestCallback?.invoke() })

            mFolderDialog.show(fragmentManager!!, TAG)
        }

        val helpButton = root.findViewById<ImageButton>(R.id.helpButton)
        helpButton.setOnClickListener { startTutorial() }
    }

    private fun updatePaths() {
        val updateTask = Completable.fromAction {
            val directParents = TreeSet<String>()

            context!!.contentResolver.query(Meta.CONTENT_URI,
                    arrayOf("DISTINCT " + Meta.PARENT), null, null,
                    Meta.PARENT + " ASC")!!.use { c ->
                while (c.moveToNext()) {
                    val parent = c.getString(c.getColumnIndex(Meta.PARENT))
                    if (parent != null)
                        directParents.add(parent)
                }
            }

            val allParents = TreeSet(directParents)
            // Now we want to check for shell parents as well
            for (path in directParents) {
                val folder = UsefulDocumentFile.fromUri(context!!, Uri.parse(path))
                var parent = folder.parentFile
                while (parent != null) {
                    allParents.add(parent.uri.toString())
                    parent = parent.parentFile
                }
            }

            mPaths.clear()
            allParents.filterNotTo(mPaths) {
                // We place the excluded folders at the end
                mExcludedFolders.contains(it)
            }

            // Place exclusions at the end
            mPaths += mExcludedFolders
        }
        .subscribeOn(Schedulers.newThread())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy (
            onComplete = {
                mFolderDialog.updateListEntries(
                        ArrayList(mPaths),
                        ArrayList(mHiddenFolders),
                        ArrayList(mExcludedFolders))
            }
        )
        disposables.add(updateTask)
    }

    private fun setSort(checkedId: Int) {
        when (checkedId) {
            R.id.toggleSortAfirst // A is quantitatively lowest, ascending
            -> {
                ascending = true
                sortColumn = XmpFilter.SortColumns.Name
            }
            R.id.toggleSortZfirst -> {
                ascending = false
                sortColumn = XmpFilter.SortColumns.Name
            }
            R.id.toggleSortYoungFirst // Young is quantitatively highest, descending
            -> {
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
            editor.putStringSet(mPrefHiddenFolders, mHiddenFolders)
            editor.putStringSet(mPrefExcludedFolders, mExcludedFolders)

            editor.apply()

            val filter = xmpFilter

            filterChangedCallback?.invoke(filter)
        }
    }

    override fun onKeywordsSelected(selectedKeywords: Collection<SubjectEntity>) {
        onFilterUpdated()
    }

    override fun onLabelSelectionChanged(checked: List<XmpLabelGroup.Labels>) {
        onFilterUpdated()
    }

    override fun onRatingSelectionChanged(checked: List<Int>) {
        onFilterUpdated()
    }

    internal class FolderDialog : DialogFragment() {

        private val items = ArrayList<FolderVisibility>()
        private lateinit var mAdapter: FolderAdapter

        private var visibilityChangedCallback: ((FolderVisibility) -> Unit)? = null
        private var searchRequestCallback: SearchRequestListener? = null

        fun setOnVisibilityChangedListener(listener: (FolderVisibility) -> Unit) {
            visibilityChangedCallback = listener
        }

        fun setSearchRequestedListener(listener: SearchRequestListener) {
            searchRequestCallback = listener
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val v = inflater.inflate(R.layout.folder_visibility, container, false)
            val paths = arguments!!.getStringArrayList(ARG_PATHS)
            val visible = arguments!!.getStringArrayList(ARG_VISIBLE)
            val excluded = arguments!!.getStringArrayList(ARG_EXCLUDED)
            val x = arguments!!.getInt(ARG_X)
            val y = arguments!!.getInt(ARG_Y)

            // Set the position of the dialog
            val window = dialog.window

            window!!.setGravity(Gravity.TOP or Gravity.START)
            val params = window.attributes
            params.x = x
            params.y = y
            window.attributes = params

            mAdapter = FolderAdapter(v.context, items)
            visibilityChangedCallback?.let { mAdapter.setOnVisibilityChangedListener(it) }
            val listView = v.findViewById<View>(R.id.listViewVisibility) as ListView
            listView.adapter = mAdapter
            updateListEntries(paths!!, visible!!, excluded!!)

            v.findViewById<View>(R.id.buttonAddSearchRoot).setOnClickListener {
                searchRequestCallback?.invoke()
                dismiss()
            }

            return v
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = super.onCreateDialog(savedInstanceState)

            // request a window without the title
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            return dialog
        }

        fun updateListEntries(paths: ArrayList<String>, hiddenFolders: ArrayList<String>, excludedFolders: ArrayList<String>) {
            // Not sure we can change this after creation
            arguments!!.putStringArrayList(ARG_PATHS, paths)
            arguments!!.putStringArrayList(ARG_VISIBLE, hiddenFolders)
            arguments!!.putStringArrayList(ARG_EXCLUDED, excludedFolders)

            items.clear()
            paths.mapTo(items) {
                FolderVisibility(
                    it,
                    !excludedFolders.contains(it) && !hiddenFolders.contains(it),
                    excludedFolders.contains(it))
            }

            mAdapter.notifyDataSetChanged()
        }

        companion object {
            val ARG_PATHS = "paths"
            val ARG_VISIBLE = "visible"
            val ARG_EXCLUDED = "excluded"
            val ARG_X = "x"
            val ARG_Y = "y"

            internal fun newInstance(paths: ArrayList<String>, visible: ArrayList<String>, excluded: ArrayList<String>, x: Int, y: Int): FolderDialog {
                val f = FolderDialog()
                val args = Bundle()
                args.putStringArrayList(ARG_PATHS, paths)
                args.putStringArrayList(ARG_VISIBLE, visible)
                args.putStringArrayList(ARG_EXCLUDED, excluded)
                args.putInt(ARG_X, x)
                args.putInt(ARG_Y, y)

                f.arguments = args
                return f
            }
        }
    }

    internal data class FolderVisibility(val Path: String, var visible: Boolean, var excluded: Boolean)

    internal class FolderAdapter internal constructor(context: Context, objects: List<FolderVisibility>) : ArrayAdapter<FolderVisibility>(context, R.layout.folder_list_item, objects) {

        private var visibilityChangedCallback: ((FolderVisibility) -> Unit)? = null

        data class ViewHolder (val path: CheckBox, val exclude: ImageButton)

        internal fun setOnVisibilityChangedListener(listener: (FolderVisibility) -> Unit) {
            visibilityChangedCallback = listener
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View
            val viewHolder: ViewHolder
            val item = getItem(position)

            if (convertView == null) {
                view = LayoutInflater.from(this.context)
                        .inflate(R.layout.folder_list_item, parent, false)

                viewHolder = ViewHolder(
                    view.findViewById(R.id.checkBoxFolderPath),
                    view.findViewById(R.id.excludeButton))
                view.tag = viewHolder
            } else {
                view = convertView
                viewHolder = view.tag as ViewHolder
                viewHolder.path.setOnCheckedChangeListener(null)
                viewHolder.exclude.setOnClickListener(null)
            }

            val path = DocumentUtil.getNicePath(Uri.parse(item.Path))
            viewHolder.path.text = path
            viewHolder.path.isChecked = item.visible

            // If excluded disable visibility switch and strike-through
            if (item.excluded) {
                viewHolder.path.paintFlags = viewHolder.path.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                viewHolder.path.isEnabled = false
            } else {
                viewHolder.path.paintFlags = viewHolder.path.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                viewHolder.path.isEnabled = true
            }

            viewHolder.exclude.setOnClickListener {
                item.excluded = !item.excluded
                viewHolder.path.isChecked = !item.excluded

                visibilityChangedCallback?.invoke(item)
                notifyDataSetChanged()
            }
            viewHolder.path.setOnCheckedChangeListener { _, isChecked ->
                item.visible = isChecked

                visibilityChangedCallback?.invoke(item)
            }

            return view
        }
    }

    private fun startTutorial() {
        val sequence = MaterialShowcaseSequence(activity)

        val root = view ?: return

        // Sort group
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.sortGroup),
                R.string.sortImages,
                R.string.sortCotent
        ))

        // Segregate
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.toggleSegregate),
                R.string.sortImages,
                R.string.segregateContent
        ))

        // Folder
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.buttonFolders),
                R.string.filterImages,
                R.string.folderContent
        ))

        // Clear
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.clearFilterButton),
                R.string.filterImages,
                R.string.clearFilterContent
        ))

        // Rating
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.filterLabelRating),
                R.string.filterImages,
                R.string.ratingLabelContent
        ))

        // Subject
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.keywordFragment),
                R.string.filterImages,
                R.string.subjectContent
        ))

        // Match
        sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.toggleAnd),
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

        private val mPrefName = "galleryFilter"
        private val mPrefRelational = "relational"
        private val mPrefAscending = "ascending"
        private val mPrefColumn = "column"
        private val mPrefSegregate = "segregate"
        private val mPrefHiddenFolders = "hiddenFolders"
        private val mPrefExcludedFolders = "excludedFolders"
    }
}
