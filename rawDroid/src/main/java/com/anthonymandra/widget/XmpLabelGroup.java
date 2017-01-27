package com.anthonymandra.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.IdRes;
import android.support.v7.widget.ToggleGroup;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import com.anthonymandra.rawdroid.R;

import java.util.ArrayList;
import java.util.List;

public class XmpLabelGroup extends ToggleGroup
{
    public interface OnLabelSelectionChangedListener
    {
        void onLabelSelectionChanged(List<Labels> checked);
    }

    public enum Labels
    {
        blue,
        red,
        green,
        yellow,
        purple
    }

    OnLabelSelectionChangedListener mListener;
    CompoundButton mBlue, mRed, mGreen, mYellow, mPurple;

    public XmpLabelGroup(Context context) { this(context, null); }
    public XmpLabelGroup(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public XmpLabelGroup(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
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

        setOnCheckedChangeListener(new OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(ToggleGroup group, @IdRes int[] checkedId)
            {
                if (mListener != null)
                    mListener.onLabelSelectionChanged(getCheckedLabels());
            }
        });
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

    public void setOnLabelSelectionChangedListener(OnLabelSelectionChangedListener listener)
    {
        mListener = listener;
    }
}
