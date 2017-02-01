package com.anthonymandra.widget;

import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.support.v7.widget.ToggleGroup;
import android.util.AttributeSet;
import android.widget.CompoundButton;

import com.anthonymandra.rawdroid.R;

import java.util.ArrayList;
import java.util.List;

public class RatingBar extends ToggleGroup
{
    public interface OnRatingSelectionChangedListener
    {
        void onRatingSelectionChanged(List<Integer> checked);
    }

    private OnRatingSelectionChangedListener mListener;
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
        mOne = (CompoundButton)findViewById(R.id.rating1);
        mTwo = (CompoundButton)findViewById(R.id.rating2);
        mThree = (CompoundButton)findViewById(R.id.rating3);
        mFour = (CompoundButton)findViewById(R.id.rating4);
        mFive = (CompoundButton)findViewById(R.id.rating5);

        setOnCheckedChangeListener(new OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(ToggleGroup group, @IdRes int[] checkedId)
            {
                // In exclusive mode this will behave like a factory rating bar
                if (isExclusive())
                {
                    resetIcons();

                     if(checkedId.length > 0)
                     {
                         switch (checkedId[0])
                         {
                             // Cascade selected stars down like a typical ratingbar
                             case R.id.rating5:
                                 mFive.setButtonDrawable(R.drawable.ic_star);
                             case R.id.rating4:
                                 mFour.setButtonDrawable(R.drawable.ic_star);
                             case R.id.rating3:
                                 mThree.setButtonDrawable(R.drawable.ic_star);
                             case R.id.rating2:
                                 mTwo.setButtonDrawable(R.drawable.ic_star);
                             case R.id.rating1:
                                 mOne.setButtonDrawable(R.drawable.ic_star);
                         }
                     }
                }

                if (mListener != null)
                    mListener.onRatingSelectionChanged(getCheckedRatings());
            }
        });
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
        return checked;
    }

    @Nullable
    public Integer getRating()
    {
        List<Integer> ratings = getCheckedRatings();
        return ratings.size() > 0 ? ratings.get(0) : null ;
    }

    private void resetIcons()
    {
        mFive.setButtonDrawable(R.drawable.ic_star_border);
        mFour.setButtonDrawable(R.drawable.ic_star_border);
        mThree.setButtonDrawable(R.drawable.ic_star_border);
        mTwo.setButtonDrawable(R.drawable.ic_star_border);
        mOne.setButtonDrawable(R.drawable.ic_star_border);
    }

    /**
     * Sets the given ratings, if null is passed all ratings will be cleared.
     * @param ratings ratings to check
     */
    public void setRating(Integer[] ratings)
    {
        if (ratings == null)
            clearChecked();
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

    public void setOnRatingSelectionChangedListener(OnRatingSelectionChangedListener listener)
    {
        mListener = listener;
    }
}
