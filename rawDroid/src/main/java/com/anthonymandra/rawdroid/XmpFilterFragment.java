package com.anthonymandra.rawdroid;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.ToggleGroup;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;

import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.DocumentUtil;
import com.anthonymandra.widget.XmpLabelGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence;
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView;

public class XmpFilterFragment extends XmpBaseFragment
{
    private static final String TAG = XmpBaseFragment.class.getSimpleName();

    private MetaFilterChangedListener mFilterListener;
    interface MetaFilterChangedListener
    {
        void onMetaFilterChanged(XmpFilter xmpFilter);
    }

    private SearchRootRequestedListener mRequestListener;
    interface SearchRootRequestedListener
    {
        void onSearchRootRequested();
    }

    private boolean mAndTrueOrFalse;
    private boolean mSortAscending;
    private boolean mSegregateByType;
    private XmpFilter.SortColumns mSortColumn;
    private Set<String> mHiddenFolders;
    private Set<String> mExcludedFolders;

    private final static String mPrefName = "galleryFilter";
    private final static String mPrefRelational = "relational";
    private final static String mPrefAscending = "ascending";
    private final static String mPrefColumn = "column";
    private final static String mPrefSegregate = "segregate";
    private final static String mPrefHiddenFolders = "hiddenFolders";
    private final static String mPrefExcludedFolders = "excludedFolders";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.xmp_filter_landscape, container, false);
    }

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
		setMultiselect(true);

		// Pull up stored filter configuration
		SharedPreferences pref = getActivity().getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
		mAndTrueOrFalse = pref.getBoolean(mPrefRelational, false);
		mSortAscending = pref.getBoolean(mPrefAscending, true);
		mSortColumn = XmpFilter.SortColumns.valueOf(pref.getString(mPrefColumn, XmpFilter.SortColumns.Name.toString()));
		mSegregateByType = pref.getBoolean(mPrefSegregate, true);
		mHiddenFolders = new HashSet<>(pref.getStringSet(mPrefHiddenFolders, new HashSet<String>()));
		mExcludedFolders = new HashSet<>(pref.getStringSet(mPrefExcludedFolders, new HashSet<String>()));

		// Initial match setting
		((CompoundButton)view.findViewById(R.id.toggleAnd)).setChecked(mAndTrueOrFalse);

		// Initial sort setting
		if (mSortAscending)
		{
			if (XmpFilter.SortColumns.Name == mSortColumn)
				((CompoundButton)view.findViewById(R.id.toggleSortAfirst)).setChecked(true);
			else
				((CompoundButton)view.findViewById(R.id.toggleSortOldFirst)).setChecked(true);
		}
		else
		{
			if (XmpFilter.SortColumns.Name == mSortColumn)
				((CompoundButton)view.findViewById(R.id.toggleSortZfirst)).setChecked(true);
			else
				((CompoundButton)view.findViewById(R.id.toggleSortYoungFirst)).setChecked(true);
		}

		// Initial segregate value
		((CompoundButton)getActivity().findViewById(R.id.toggleSegregate)).setChecked(mSegregateByType);

		attachButtons(view);
	}

	private void attachButtons(View root)
    {
        root.findViewById(R.id.clearFilterButton).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                clear();
            }
        });

        final CompoundButton andOr = (CompoundButton) getActivity().findViewById(R.id.toggleAnd);
        setAndOr(andOr.isChecked());
        andOr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked)
            {
                setAndOr(andOr.isChecked());
            }
        });

        ToggleGroup sort = (ToggleGroup) getActivity().findViewById(R.id.sortGroup);
        setSort(sort.getCheckedId());
        sort.setOnCheckedChangeListener(new ToggleGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(ToggleGroup group, int[] checkedId)
            {
                setSort(group.getCheckedId());
            }
        });

        CompoundButton segregate = (CompoundButton) getActivity().findViewById(R.id.toggleSegregate);
        segregate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mSegregateByType = isChecked;
                onFilterUpdated();
            }
        });

        ImageButton clearFilter = (ImageButton) root.findViewById(R.id.clearFilterButton);
        clearFilter.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                clear();
            }
        });

        final ImageButton foldersButton = (ImageButton) getActivity().findViewById(R.id.buttonFolders);
        foldersButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                final List<String> paths = new ArrayList<>();

                try(Cursor c = getActivity().getContentResolver().query(Meta.CONTENT_URI,
                        new String[]{"DISTINCT " + Meta.PARENT}, null, null,
                        Meta.PARENT + " ASC"))
                {
                    while (c != null && c.moveToNext())
                    {
                        String path = c.getString(c.getColumnIndex(Meta.PARENT));

                        // We place the excluded folders at the end
                        if (!mExcludedFolders.contains(path))
                            paths.add(path);
                    }
                }

                // Place exclusions at the end
                for (String exclusion : mExcludedFolders)
                {
                    paths.add(exclusion);
                }

                final boolean[] visible = new boolean[paths.size()];
                int i = 0;
                for (String path : paths)
                {
                    visible[i++] = !mExcludedFolders.contains(path) && !mHiddenFolders.contains(path);
                }

                final boolean[] excluded = new boolean[paths.size()];
                i = 0;
                for (String path : paths)
                {
                    excluded[i++] = mExcludedFolders.contains(path);
                }

                int[] position = new int[2];
                foldersButton.getLocationOnScreen(position);
                FolderDialog dialog = FolderDialog.newInstance(
                        paths.toArray(new String[paths.size()]),
                        visible,
                        excluded,
                        position[0],
                        position[1]);
                dialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.FolderDialog);
                dialog.setOnVisibilityChangedListener(new FolderAdapter.OnVisibilityChangedListener()
                {
                    @Override
                    public void onVisibilityChanged(FolderVisibility visibility)
                    {
                        if (visibility.visible)
                        {
                            mHiddenFolders.remove(visibility.Path);
                        }
                        else
                        {
                            mHiddenFolders.add(visibility.Path);
                        }
                        if (visibility.excluded)
                        {
                            mExcludedFolders.add(visibility.Path);
                        }
                        else
                        {
                            mExcludedFolders.remove(visibility.Path);
                        }
                        onFilterUpdated();
                    }
                });
                dialog.setSearchRequestedListener(new SearchRootRequestedListener()
                {
                    @Override
                    public void onSearchRootRequested()
                    {
                        mRequestListener.onSearchRootRequested();
                    }
                });

                dialog.show(getFragmentManager(), TAG);
            }
        });

        final ImageButton helpButton = (ImageButton) root.findViewById(R.id.helpButton);
        helpButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startTutorial();
            }
        });
    }

    private void setAndOr(boolean and)
    {
        mAndTrueOrFalse = and;
        onFilterUpdated();
    }

    private void setSort(int checkedId)
    {
        switch (checkedId)
        {
            case R.id.toggleSortAfirst: // A is quantitatively lowest, ascending
                mSortAscending = true;
                mSortColumn = XmpFilter.SortColumns.Name;
                break;
            case R.id.toggleSortZfirst:
                mSortAscending = false;
                mSortColumn = XmpFilter.SortColumns.Name;
                break;
            case R.id.toggleSortYoungFirst: // Young is quantitatively highest, descending
                mSortAscending = false;
                mSortColumn = XmpFilter.SortColumns.Date;
                break;
            case R.id.toggleSortOldFirst:
                mSortAscending = true;
                mSortColumn = XmpFilter.SortColumns.Date;
                break;
        }
        onFilterUpdated();
    }

    @SuppressWarnings("unused")
    public boolean getAndOr()
    {
        return mAndTrueOrFalse;
    }

    @SuppressWarnings("unused")
    public boolean getSegregate()
    {
        return mSegregateByType;
    }

    @SuppressWarnings("unused")
    public XmpFilter.SortColumns getSortColumn()
    {
        return mSortColumn;
    }

    @SuppressWarnings("unused")
    public boolean getAscending()
    {
        return mSortAscending;
    }

    @SuppressWarnings("unused")
    public XmpValues getXmpValues()
    {
        XmpValues xmp = new XmpValues();
        xmp.label = getColorLabels();
        xmp.subject = getSubject();
        xmp.rating = getRatings();
        return xmp;
    }

    public Set<String> getExcludedFolders()
    {
        return mExcludedFolders;
    }

    public XmpFilter getXmpFilter()
    {
        XmpFilter filter = new XmpFilter();
        filter.andTrueOrFalse = mAndTrueOrFalse;
        filter.hiddenFolders = mHiddenFolders;
        filter.segregateByType = mSegregateByType;
        filter.sortAscending = mSortAscending;
        filter.sortColumn = mSortColumn;
        filter.xmp = getXmpValues();
        return filter;
    }

    public void registerXmpFilterChangedListener(MetaFilterChangedListener listener)
    {
        mFilterListener = listener;
    }

    @SuppressWarnings("unused")
    public void unregisterXmpFilterChangedListener()
    {
        mFilterListener = null;
    }

    public void registerSearchRootRequestedListener(SearchRootRequestedListener listener)
    {
        mRequestListener = listener;
    }

    @SuppressWarnings("unused")
    public void unregisterSearchRootRequestedListener()
    {
        mRequestListener = null;
    }

    @Override
    protected void onXmpChanged(XmpValues xmp)
    {
        onFilterUpdated();
    }

    protected void onFilterUpdated()
    {
        if (mFilterListener != null)
        {
            if (getActivity() == null)
                return;

            SharedPreferences pref = getActivity().getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();

            editor.putBoolean(mPrefAscending, mSortAscending);
            editor.putBoolean(mPrefRelational, mAndTrueOrFalse);
            editor.putString(mPrefColumn, mSortColumn.toString());
            editor.putBoolean(mPrefSegregate, mSegregateByType);
            editor.putStringSet(mPrefHiddenFolders, mHiddenFolders);
            editor.putStringSet(mPrefExcludedFolders, mExcludedFolders);

            editor.apply();

            XmpFilter filter = getXmpFilter();

            mFilterListener.onMetaFilterChanged(filter);
        }
    }

    @Override
    public void onKeywordsSelected(Collection<String> selectedKeywords)
    {
        onFilterUpdated();
    }

    @Override
    public void onLabelSelectionChanged(List<XmpLabelGroup.Labels> checked)
    {
        onFilterUpdated();
    }

    @Override
    public void onRatingSelectionChanged(List<Integer> checked)
    {
        onFilterUpdated();
    }

    public static class FolderDialog extends DialogFragment
    {
        public static final String ARG_PATHS = "paths";
        public static final String ARG_VISIBLE = "visible";
        public static final String ARG_EXCLUDED = "excluded";
        public static final String ARG_X = "x";
        public static final String ARG_Y = "y";

        private final List<FolderVisibility> items = new ArrayList<>();

        private FolderAdapter.OnVisibilityChangedListener mVisibilityListener;
        public void setOnVisibilityChangedListener(FolderAdapter.OnVisibilityChangedListener listener)
        {
            mVisibilityListener = listener;
        }
        private SearchRootRequestedListener mSearchListener;
        public void setSearchRequestedListener(SearchRootRequestedListener listener)
        {
            mSearchListener = listener;
        }

        static FolderDialog newInstance(@NonNull String[] paths, @NonNull boolean[] visible, @NonNull boolean[] excluded, int x, int y)
        {
            FolderDialog f = new FolderDialog();
            Bundle args = new Bundle();
            args.putStringArray(ARG_PATHS, paths);
            args.putBooleanArray(ARG_VISIBLE, visible);
            args.putBooleanArray(ARG_EXCLUDED, excluded);
            args.putInt(ARG_X, x);
            args.putInt(ARG_Y, y);

            f.setArguments(args);
            return f;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
        {
            View v = inflater.inflate(R.layout.folder_visibility, container, false);
            String[] paths = getArguments().getStringArray(ARG_PATHS);
            boolean[] visible = getArguments().getBooleanArray(ARG_VISIBLE);
            boolean[] excluded = getArguments().getBooleanArray(ARG_EXCLUDED);
            int x = getArguments().getInt(ARG_X);
            int y = getArguments().getInt(ARG_Y);

            // Set the position of the dialog
            Window window = getDialog().getWindow();
	        //noinspection ConstantConditions
	        window.setGravity(Gravity.TOP|Gravity.START);
            WindowManager.LayoutParams params = window.getAttributes();
            params.x = x;
            params.y = y;
            window.setAttributes(params);

            //noinspection ConstantConditions
            for (int i = 0; i < paths.length; i++)
            {
                FolderVisibility fv = new FolderVisibility();
                fv.Path = paths[i];
                //noinspection NullableProblems,ConstantConditions
                fv.visible = visible[i];
                //noinspection NullableProblems,ConstantConditions
                fv.excluded = excluded[i];
                items.add(fv);
            }

            FolderAdapter adapter = new FolderAdapter(v.getContext(), items);
            adapter.setOnVisibilityChangedListener(mVisibilityListener);
            ListView listView = (ListView) v.findViewById(R.id.listViewVisibility);
            listView.setAdapter(adapter);

            final Button addRootButton = (Button) v.findViewById(R.id.buttonAddSearchRoot);
            addRootButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (mSearchListener != null)
                        mSearchListener.onSearchRootRequested();
                }
            });

            return v;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Dialog dialog = super.onCreateDialog(savedInstanceState);

            // request a window without the title
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            return dialog;
        }
    }

    private static class FolderVisibility
    {
        String Path;
        boolean visible;
        boolean excluded;
    }

    private static class FolderAdapter extends ArrayAdapter<FolderVisibility>
    {
        interface OnVisibilityChangedListener
        {
            void onVisibilityChanged(FolderVisibility visibility);
        }

        static class ViewHolder
        {
            private CheckBox path;
            private ImageButton exclude;
        }

        private OnVisibilityChangedListener mListener;
        void setOnVisibilityChangedListener(OnVisibilityChangedListener listener)
        {
            mListener = listener;
        }

        FolderAdapter(Context context, List<FolderVisibility> objects)
        {
            super(context, R.layout.folder_list_item, objects);
        }

        @NonNull
        @Override
        public View getView(final int position, View convertView, @NonNull ViewGroup parent)
        {
            final ViewHolder viewHolder;
            final FolderVisibility item = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(this.getContext())
                        .inflate(R.layout.folder_list_item, parent, false);

                viewHolder = new ViewHolder();
                viewHolder.path = (CheckBox) convertView.findViewById(R.id.checkBoxFolderPath);
                viewHolder.exclude = (ImageButton) convertView.findViewById(R.id.excludeButton);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
                viewHolder.path.setOnCheckedChangeListener(null);
                viewHolder.exclude.setOnClickListener(null);
            }

            String path = item.Path != null ? DocumentUtil.getNicePath(Uri.parse(item.Path)) : null;
            viewHolder.path.setText(path);
            viewHolder.path.setChecked(item.visible);

            // If excluded disable visibility switch and strike-through
            if (item.excluded)
            {
                viewHolder.path.setPaintFlags(viewHolder.path.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                viewHolder.path.setEnabled(false);
            }
            else
            {
                viewHolder.path.setPaintFlags(viewHolder.path.getPaintFlags() & (~ Paint.STRIKE_THRU_TEXT_FLAG));
                viewHolder.path.setEnabled(true);
            }

            viewHolder.exclude.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    FolderVisibility item = getItem(position);
                    item.excluded = !item.excluded;
                    viewHolder.path.setChecked(!item.excluded);

                    if (mListener != null)
                        mListener.onVisibilityChanged(item);
                    notifyDataSetChanged();
                }
            });
            viewHolder.path.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    item.visible = isChecked;

                    if (mListener != null)
                        mListener.onVisibilityChanged(item);
                }
            });

            return convertView;
        }
    }

    private void startTutorial()
    {
	    MaterialShowcaseSequence sequence = new MaterialShowcaseSequence(getActivity());

	    View root = getView();
	    if (root == null)
	    	return;

	    // Sort group
	    sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.sortGroup),
			    R.string.sortImages,
			    R.string.sortCotent,
			    R.string.ok));

	    // Segregate
	    sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.toggleSegregate),
			    R.string.sortImages,
			    R.string.segregateContent,
			    R.string.ok));

	    // Folder
	    sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.buttonFolders),
			    R.string.filterImages,
			    R.string.folderContent,
			    R.string.ok));

	    // Clear
	    sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.clearFilterButton),
			    R.string.filterImages,
			    R.string.clearFilterContent,
			    R.string.ok));

	    // Rating
	    sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.filterLabelRating),
			    R.string.filterImages,
			    R.string.ratingLabelContent,
			    R.string.ok));

	    // Subject
	    sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.keywordFragment),
			    R.string.filterImages,
			    R.string.subjectContent,
			    R.string.ok));

	    // Match
	    sequence.addSequenceItem(getRectangularView(
                root.findViewById(R.id.toggleAnd),
			    R.string.filterImages,
			    R.string.matchContent,
			    R.string.ok));

	    sequence.start();
    }

	private MaterialShowcaseView getRectangularView(View target, @StringRes int titleId, @StringRes int contentId, @StringRes int dismissId)
	{
		return getRectangularView(target,
				getString(titleId),
				getString(contentId),
				getString(dismissId));
	}

	private MaterialShowcaseView getRectangularView(View target, String title, String content, String dismiss)
	{
		return new MaterialShowcaseView.Builder(getActivity())
				.setTarget(target)
				.setTitleText(title)
				.setContentText(content)
				.setDismissOnTouch(true)
				.setDismissText(dismiss)
				.withRectangleShape()
				.build();
	}
}
