package com.anthonymandra.rawdroid;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;

public class XmpFilterFragment extends XmpBaseFragment
{
    private MetaFilterChangedListener mListener;
    public interface MetaFilterChangedListener
    {
        void onMetaFilterChanged(XmpValues xmp, boolean andTrueOrFalse, boolean sortAscending, SortColumns sortColumn);
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
    private boolean mSortAscending = true;
    private SortColumns mSortColumn = SortColumns.Name;
    private XmpValues mXmpValues;

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
                switch(position)
                {
                    case 0: mSortAscending = true; mSortColumn = SortColumns.Name; break;
                    case 1: mSortAscending = false; mSortColumn = SortColumns.Name; break;
                    case 2: mSortAscending = true; mSortColumn = SortColumns.Date; break;
                    case 3: mSortAscending = false; mSortColumn = SortColumns.Date; break;
                    default: mSortAscending = true; mSortColumn = SortColumns.Name; break;
                }
                dispatchChange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {
                //Do nothing
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
            mListener.onMetaFilterChanged(mXmpValues, mAndTrueOrFalse, mSortAscending, mSortColumn);
        }
    }
}
