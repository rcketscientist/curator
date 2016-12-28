package com.anthonymandra.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import com.anthonymandra.rawdroid.R;

import java.util.ArrayList;
import java.util.List;

public class ToggleGroup extends RadioGroup implements CompoundButton.OnCheckedChangeListener
{
    public interface OnCheckedChangedListener
    {
        void onCheckedChanged(int[] checkedIds);
    }

    boolean mPauseListener;
    boolean mMultiSelect;
    OnCheckedChangedListener mListener;
    public ToggleGroup(Context context) { this(context, null); }
    public ToggleGroup(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public ToggleGroup(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.XmpLabelGroup);
        mMultiSelect = a.getBoolean(R.styleable.XmpLabelGroup_multiselect, false);
        a.recycle();

        inflate(context, R.layout.material_color_key, this);
        attachButtons();
    }

    private void attachButtons()
    {
        mBlue = (CompoundButton)findViewById(R.id.blueLabel);
        mRed = (CompoundButton)findViewById(R.id.redLabel);
        mGreen = (CompoundButton)findViewById(R.id.greenLabel);
        mYellow = (CompoundButton)findViewById(R.id.yellowLabel);
        mPurple = (CompoundButton)findViewById(R.id.purpleLabel);

        mBlue.setOnCheckedChangeListener(this);
        mRed.setOnCheckedChangeListener(this);
        mGreen.setOnCheckedChangeListener(this);
        mYellow.setOnCheckedChangeListener(this);
        mPurple.setOnCheckedChangeListener(this);
    }

    public void setMultiselect(boolean enable)
    {
        mMultiSelect = enable;
    }

    public void setChecked(Labels toCheck, boolean checked)
    {
        switch(toCheck)
        {
            case blue: mBlue.setChecked(checked); break;
            case red: mRed.setChecked(checked); break;
            case green: mGreen.setChecked(checked); break;
            case yellow: mYellow.setChecked(checked); break;
            case purple: mPurple.setChecked(checked); break;
        }
    }

    public void clearCheck()
    {
        mPauseListener = true;
        mBlue.setChecked(false);
        mRed.setChecked(false);
        mGreen.setChecked(false);
        mYellow.setChecked(false);
        mPurple.setChecked(false);
        mPauseListener = false;
        mListener.onLabelSelectionChanged(getCheckedLabels());
    }

    public List<Labels> getCheckedLabels()
    {
        List<Labels> checked = new ArrayList<>();
        if (mBlue.isChecked())
            checked.add(Labels.blue);
        if (mRed.isChecked())
            checked.add(Labels.red);
        if (mGreen.isChecked())
            checked.add(Labels.green);
        if (mYellow.isChecked())
            checked.add(Labels.yellow);
        if (mPurple.isChecked())
            checked.add(Labels.purple);
        return checked;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        if (mPauseListener)
            return;

        if (!mMultiSelect)
        {
            mPauseListener = true;
            if (mBlue != buttonView && mBlue.isChecked())
                mBlue.setChecked(false);
            if (mRed != buttonView && mRed.isChecked())
                mRed.setChecked(false);
            if (mGreen != buttonView && mGreen.isChecked())
                mGreen.setChecked(false);
            if (mYellow != buttonView && mYellow.isChecked())
                mYellow.setChecked(false);
            if (mPurple != buttonView && mPurple.isChecked())
                mPurple.setChecked(false);
            mPauseListener = false;
        }

        mListener.onLabelSelectionChanged(getCheckedLabels());
    }

    public void setOnLabelSelectionChangedListener(OnLabelSelectionChangedListener listener)
    {
        mListener = listener;
    }
}
