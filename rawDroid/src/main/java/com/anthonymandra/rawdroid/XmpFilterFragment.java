package com.anthonymandra.rawdroid;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.DocumentUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XmpFilterFragment extends XmpBaseFragment
{
    private MetaFilterChangedListener mFilterListener;
    public interface MetaFilterChangedListener
    {
        void onMetaFilterChanged(XmpFilter xmpFilter);
    }

    private SearchRootRequestedListener mRequestListener;
    public interface SearchRootRequestedListener
    {
        void onSearchRootRequested();
    }

    /**
     * Used to control updating of the listener to avoid updates during creation, or
     * multiple updates during bulk changes.
     */
    private boolean mPauseListener;
    private boolean mAndTrueOrFalse;
    private boolean mSortAscending;
    private boolean mSegregateByType;
    private XmpFilter.SortColumns mSortColumn;
    private XmpValues mXmpValues;
    private Set<String> mHiddenFolders;
    private Set<String> mExcludedFolders;

    private final String mPrefName = "galleryFilter";
    private final String mPrefRelational = "relational";
    private final String mPrefAscending = "ascending";
    private final String mPrefColumn = "column";
    private final String mPrefSegregate = "segregate";
    private final String mPrefHiddenFolders = "hiddenFolders";
    private final String mPrefExcludedFolders = "excludedFolders";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mPauseListener = true;
        return inflater.inflate(R.layout.xmp_filter_landscape, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        setMultiselect(true);

        SharedPreferences pref = getActivity().getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        mAndTrueOrFalse = pref.getBoolean(mPrefRelational, false);
        mSortAscending = pref.getBoolean(mPrefAscending, true);
        mSortColumn = XmpFilter.SortColumns.valueOf(pref.getString(mPrefColumn, XmpFilter.SortColumns.Name.toString()));
        mSegregateByType = pref.getBoolean(mPrefSegregate, true);
        mHiddenFolders = pref.getStringSet(mPrefHiddenFolders, new HashSet<String>());
        mExcludedFolders = pref.getStringSet(mPrefExcludedFolders, new HashSet<String>());

        attachButtons();
    }

    @Override
    public void onStart()
    {
        super.onStart();
        mPauseListener = false;
    }

    private void attachButtons()
    {
        getActivity().findViewById(R.id.clearButton).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                mPauseListener = true;
                clear();
                mPauseListener = false;
                mXmpValues = getXmp();
                dispatchChange();
            }
        });

        RadioGroup andOr = (RadioGroup) getActivity().findViewById(R.id.andOrRadioGroup);
        setAndOr(andOr.getCheckedRadioButtonId());
        andOr.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId)
            {
                setAndOr(checkedId);
                dispatchChange();
            }
        });

        Spinner sort = (Spinner) getActivity().findViewById(R.id.sortSpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), R.layout.right_align_dropdown_item, getResources().getStringArray(R.array.sortValues));
        adapter.setDropDownViewResource(R.layout.right_align_dropdown_item);
        sort.setAdapter(adapter);
        sort.setSelection(0, false);
        sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                //TODO: This is really vulnerable to array index changes!
                switch (position)
                {
                    case 0:
                        mSortAscending = true;
                        mSortColumn = XmpFilter.SortColumns.Name;
                        break;
                    case 1:
                        mSortAscending = false;
                        mSortColumn = XmpFilter.SortColumns.Name;
                        break;
                    case 2:
                        mSortAscending = true;
                        mSortColumn = XmpFilter.SortColumns.Date;
                        break;
                    case 3:
                        mSortAscending = false;
                        mSortColumn = XmpFilter.SortColumns.Date;
                        break;
                    default:
                        mSortAscending = true;
                        mSortColumn = XmpFilter.SortColumns.Name;
                        break;
                }
                dispatchChange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {
                //Do nothing
            }
        });

        CheckBox segregate = (CheckBox) getActivity().findViewById(R.id.segregateCheckBox);
        segregate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mSegregateByType = isChecked;
                dispatchChange();
            }
        });

        final Button foldersButton = (Button) getActivity().findViewById(R.id.buttonFolders);
        foldersButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                final List<String> paths = new ArrayList<>();
                final List<String> sortedPaths = new ArrayList<>();

                Cursor c = getActivity().getContentResolver().query(Meta.Data.CONTENT_URI,
                        new String[]{"DISTINCT " + Meta.Data.PARENT}, null, null,
                        Meta.Data.PARENT + " ASC");
                while (c.moveToNext())
                {
                    String path = c.getString(c.getColumnIndex(Meta.Data.PARENT));

                    // We place the excluded folders at the end
                    if (!mExcludedFolders.contains(path))
                        paths.add(path);
                }
                c.close();

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

                FolderDialog dialog = FolderDialog.newInstance(
                        paths.toArray(new String[paths.size()]),
                        visible,
                        excluded,
                        foldersButton.getLeft(),
                        foldersButton.getTop());
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
                        dispatchChange();
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

                dialog.show(getFragmentManager(), "diag");
            }
        });
    }

    private void setAndOr(int checkedId)
    {
        switch (checkedId)
        {
            case R.id.andRadioButton:
                mAndTrueOrFalse = true;
                break;
            case R.id.orRadioButton:
                mAndTrueOrFalse = false;
                break;
        }
    }

    public boolean getAndOr()
    {
        return mAndTrueOrFalse;
    }

    public boolean getSegregate()
    {
        return mSegregateByType;
    }

    public XmpFilter.SortColumns getSortCoumn()
    {
        return mSortColumn;
    }

    public boolean getAscending()
    {
        return mSortAscending;
    }

    public XmpValues getXmpValues()
    {
        return mXmpValues;
    }

    public Set<String> getExcludedFolders()
    {
        return mExcludedFolders;
    }

    public void addExcludedFolders(Set<String> exclusions)
    {
        mExcludedFolders.addAll(exclusions);
        mHiddenFolders.addAll(exclusions);
        dispatchChange();
    }

    public XmpFilter getXmpFilter()
    {
        XmpFilter filter = new XmpFilter();
        filter.andTrueOrFalse = mAndTrueOrFalse;
        filter.hiddenFolders = mHiddenFolders;
        filter.segregateByType = mSegregateByType;
        filter.sortAscending = mSortAscending;
        filter.sortColumn = mSortColumn;
        filter.xmp = mXmpValues;
        return filter;
    }

    public void registerXmpFilterChangedListener(MetaFilterChangedListener listener)
    {
        mFilterListener = listener;
    }

    public void unregisterXmpFilterChangedListener()
    {
        mFilterListener = null;
    }

    public void registerSearchRootRequestedListener(SearchRootRequestedListener listener)
    {
        mRequestListener = listener;
    }

    public void unregisterSearchRootRequestedListener()
    {
        mRequestListener = null;
    }

    @Override
    protected void onXmpChanged(XmpValues xmp)
    {
        super.onXmpChanged(xmp);
        mXmpValues = xmp;
        dispatchChange();
    }

    protected void dispatchChange()
    {
        if (mFilterListener != null && !mPauseListener)
        {
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

        static FolderDialog newInstance(String[] paths, boolean[] visible, boolean[] excluded, int x, int y)
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
            window.setGravity(Gravity.TOP|Gravity.START);
            WindowManager.LayoutParams params = window.getAttributes();
            params.x = x;
            params.y = y;
            window.setAttributes(params);

//            List<FolderVisibility> items = new ArrayList<>();
            for (int i = 0; i < paths.length; i++)
            {
                FolderVisibility fv = new FolderVisibility();
                fv.Path = paths[i];
                fv.visible = visible[i];
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
        public interface OnVisibilityChangedListener
        {
            void onVisibilityChanged(FolderVisibility visibility);
        }

        static class ViewHolder
        {
            private CheckBox path;
            private ImageButton exclude;
        }

        private OnVisibilityChangedListener mListener;
        public void setOnVisibilityChangedListener(OnVisibilityChangedListener listener)
        {
            mListener = listener;
        }

        public FolderAdapter(Context context, List<FolderVisibility> objects)
        {
            super(context, R.layout.folder_list_item, objects);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent)
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
                    mListener.onVisibilityChanged(item);
                }
            });

            return convertView;
        }
    }
}
