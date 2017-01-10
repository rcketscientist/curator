package com.anthonymandra.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import com.anthonymandra.rawdroid.R;

import java.util.ArrayList;
import java.util.List;

public class RatingBar extends LinearLayout implements CompoundButton.OnCheckedChangeListener
{
    public interface OnRatingSelectionChangedListener
    {
        void onRatingSelectionChanged(List<Integer> checked);
    }

    private OnRatingSelectionChangedListener mListener;
    private boolean mMultiSelect = false;
    private boolean mPauseListener = false;
    Integer mRating = null;
    CompoundButton mOne, mTwo, mThree, mFour, mFive;

    public RatingBar(Context context) { this(context, null); }
    public RatingBar(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public RatingBar(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);

        inflate(context, R.layout.rating_bar, this);
        attachButtons();
    }

    private void attachButtons()
    {
        //TODO: Now that these are ToggleButtons this class can be greatly simplified
        mOne = (CompoundButton)findViewById(R.id.rating1);
        mTwo = (CompoundButton)findViewById(R.id.rating2);
        mThree = (CompoundButton)findViewById(R.id.rating3);
        mFour = (CompoundButton)findViewById(R.id.rating4);
        mFive = (CompoundButton)findViewById(R.id.rating5);

        mOne.setOnCheckedChangeListener(this);
        mTwo.setOnCheckedChangeListener(this);
        mThree.setOnCheckedChangeListener(this);
        mFour.setOnCheckedChangeListener(this);
        mFive.setOnCheckedChangeListener(this);
    }

    public void setMultiselect(boolean enable)
    {
        mMultiSelect = enable;
    }

    public void clearCheck()
    {
        mPauseListener = true;
        clear();
        mPauseListener = false;
        mListener.onRatingSelectionChanged(getCheckedRatings());
    }

    private void clear()
    {
        mRating = null;
        mOne.setChecked(false);
        mTwo.setChecked(false);
        mThree.setChecked(false);
        mFour.setChecked(false);
        mFive.setChecked(false);
    }

    public List<Integer> getCheckedRatings()
    {
        List<Integer> checked = new ArrayList<>();
        if (mFive.isChecked())
            checked.add(5);
        if (mFour.isChecked())
            checked.add(4);
        if (mThree.isChecked())
            checked.add(3);
        if (mTwo.isChecked())
            checked.add(2);
        if (mOne.isChecked())
            checked.add(1);
        if (mRating != null && mRating == 0)
            checked.add(0);
        return checked;
    }

    public Integer getRating()
    {
        return mRating;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        if (mPauseListener)
            return;

        if (!mMultiSelect)
        {
            Integer intendedRating = null;
            switch(buttonView.getId())
            {
                case R.id.rating1: intendedRating = 1; break;
                case R.id.rating2: intendedRating = 2; break;
                case R.id.rating3: intendedRating = 3; break;
                case R.id.rating4: intendedRating = 4; break;
                case R.id.rating5: intendedRating = 5; break;
            }

            // When selecting an already selected rating, clear instead
            // When selecting a lower or higher rating set the rating
            // Ex: 3 set, 2 intended, set to 2
            // 3 set, 3 intended, clear
            // 3 set, 4 intended, set to
            if (isChecked || intendedRating != null && !intendedRating.equals(mRating))
            {
                setRating(intendedRating);
                return; // setRating already handled listener updates
            }
            else
            {
                mPauseListener = true;
                clear();
                mPauseListener = false;
            }
        }

        mListener.onRatingSelectionChanged(getCheckedRatings());
    }


    public void setRating(Integer[] ratings)
    {
        if (ratings == null)
            clearCheck();
        else
        {
            for (int rating : ratings)
            {
                setRating(rating);
            }
        }
    }

    public void setRating(Integer rating)
    {
        if (!mMultiSelect)
        {
            mPauseListener = true;
            clear();
            switch (rating)
            {
                // Cascade on purpose
                case 5: mFive.setChecked(true);
                case 4: mFour.setChecked(true);
                case 3: mThree.setChecked(true);
                case 2: mTwo.setChecked(true);
                case 1: mOne.setChecked(true);
                    break;
                default: clear();
            }
            mPauseListener = false;
            mListener.onRatingSelectionChanged(getCheckedRatings());
        }
        else
        {
            switch (rating)
            {
                case 5: mFive.setChecked(true); break;
                case 4: mFour.setChecked(true); break;
                case 3: mThree.setChecked(true); break;
                case 2: mTwo.setChecked(true); break;
                case 1: mOne.setChecked(true); break;
                default: break;
            }
        }
        mRating = rating;
    }

    public void setOnRatingSelectionChangedListener(OnRatingSelectionChangedListener listener)
    {
        mListener = listener;
    }
}
