package com.anthonymandra.rawdroid;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.anthonymandra.content.Meta;

import java.util.ArrayList;
import java.util.List;

public class XmpFilterFragment extends XmpBaseFragment
{
    private MetaFilterChangedListener mListener;
    public interface MetaFilterChangedListener
    {
        void onMetaFilterChanged(XmpValues xmp, boolean andTrueOrFalse, boolean sortAscending, boolean segregateByType, SortColumns sortColumn);
    }

    public enum SortColumns
    {
        Name,
        Date
    }

    /**
     * Used to control updating of the listener to avoid updates during creation, or
     * multiple updates during bulk changes.
     */
    private boolean mPauseListener;
    private boolean mAndTrueOrFalse;
    private boolean mSortAscending;
    private boolean mSegregateByType;
    private SortColumns mSortColumn;
    private XmpValues mXmpValues;

    private final String mPrefName = "galleryFilter";
    private final String mPrefRelational = "relational";
    private final String mPrefAscending = "ascending";
    private final String mPrefColumn = "column";
    private final String mPrefSegregate = "segregate";

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
        mSortColumn = SortColumns.valueOf(pref.getString(mPrefColumn, SortColumns.Name.toString()));
        mSegregateByType = pref.getBoolean(mPrefSegregate, true);

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
                        mSortColumn = SortColumns.Name;
                        break;
                    case 1:
                        mSortAscending = false;
                        mSortColumn = SortColumns.Name;
                        break;
                    case 2:
                        mSortAscending = true;
                        mSortColumn = SortColumns.Date;
                        break;
                    case 3:
                        mSortAscending = false;
                        mSortColumn = SortColumns.Date;
                        break;
                    default:
                        mSortAscending = true;
                        mSortColumn = SortColumns.Name;
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

        Button folders = (Button) getActivity().findViewById(R.id.buttonFolders);
        folders.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                List<String> folders = new ArrayList<>();
                Cursor c = getActivity().getContentResolver().query(Meta.Data.CONTENT_URI,
                        new String[] {"DISTINCT" + Meta.Data.PARENT}, null, null, null);
                while (c.moveToNext())
                {
                    folders.add(c.getString(Meta.PARENT_COLUMN));
                }
                c.close();

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1);
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.
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

    public SortColumns getSortCoumn()
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

    public void registerXmpFilterChangedListener(MetaFilterChangedListener listener)
    {
        mListener = listener;
    }

    public void unregisterXmpFilterChangedListener()
    {
        mListener = null;
    }

    @Override
    protected void onXmpChanged(XmpValues xmp)
    {
        super.onXmpChanged(xmp);
        mXmpValues = xmp;
        dispatchChange();
    }

    private void dispatchChange()
    {
        if (mListener != null && !mPauseListener)
        {
            SharedPreferences pref = getActivity().getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();

            editor.putBoolean(mPrefAscending, mSortAscending);
            editor.putBoolean(mPrefRelational, mAndTrueOrFalse);
            editor.putString(mPrefColumn, mSortColumn.toString());
            editor.putBoolean(mPrefSegregate, mSegregateByType);

            editor.apply();

            mListener.onMetaFilterChanged(mXmpValues, mAndTrueOrFalse, mSortAscending, mSegregateByType, mSortColumn);
        }
    }
}
