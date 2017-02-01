package com.anthonymandra.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Checkable;
import android.widget.RelativeLayout;

import com.anthonymandra.rawdroid.R;

public class CheckableRelativeLayout extends RelativeLayout implements Checkable
{
    private static final String TAG = CheckableRelativeLayout.class.getCanonicalName();
    /**
     * Interface definition for a callback to be invoked when the checked state
     * of a compound button changed.
     */
    @SuppressWarnings("WeakerAccess")
    public interface OnCheckedChangeListener {
        /**
         * Called when the checked state of a compound button has changed.
         *
         * @param view The view whose state has changed.
         * @param isChecked  The new checked state of buttonView.
         */
        void onCheckedChanged(CheckableRelativeLayout view, boolean isChecked);
    }

    private boolean mChecked = false;
    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };
    private OnCheckedChangeListener mCheckedChangedListener;
    private Drawable mForeground;

    public CheckableRelativeLayout(Context context) { this(context, null); }
    public CheckableRelativeLayout(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public CheckableRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {this(context, attrs, defStyleAttr, 0);}
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    public CheckableRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CheckableRelativeLayout);

        final Drawable d = a.getDrawable(R.styleable.CheckableRelativeLayout_foreground);
        if (d != null) {
            setForeground(d);
        }
        a.recycle();
    }

    public void setForeground(Drawable d) {
        this.mForeground = d;
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked())
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        return drawableState;
    }

    @Override
    protected void drawableStateChanged()
    {
        super.drawableStateChanged();
        final Drawable drawable = getBackground();
        boolean needRedraw = false;
        final int[] myDrawableState = getDrawableState();
        if (drawable != null) {
            drawable.setState(myDrawableState);
            needRedraw = true;
        }
        if (mForeground != null) {
            mForeground.setState(myDrawableState);
            needRedraw = true;
        }
        if (needRedraw)
            invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mForeground != null)
            mForeground.setBounds(0, 0, w, h);
    }

    @Override
    protected void dispatchDraw(Canvas canvas)
    {
        super.dispatchDraw(canvas);
        if (mForeground != null)
            mForeground.draw(canvas);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || (who == mForeground);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mForeground != null) mForeground.jumpToCurrentState();
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        mChecked = checked;
        if (mCheckedChangedListener != null)
            mCheckedChangedListener.onCheckedChanged(this, checked);
        refreshDrawableState();
    }

    @Override
    public void toggle() {
        setChecked(!mChecked);
    }

    @SuppressWarnings("unused")
    public void setCheckedChangedListener(OnCheckedChangeListener listener)
    {
        mCheckedChangedListener = listener;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        // Force our ancestor class to save its state
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState savedState = new SavedState(superState);
        savedState.checked = isChecked();
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(final Parcelable state) {
        final SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        setChecked(savedState.checked);
        requestLayout();
    }

    @SuppressLint("ClickableViewAccessibility")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onTouchEvent(final MotionEvent e) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && //
                e.getActionMasked() == MotionEvent.ACTION_DOWN && //
                mForeground != null)
            mForeground.setHotspot(e.getX(), e.getY());
        return super.onTouchEvent(e);
    }

    // /////////////
    // SavedState //
    // /////////////

    private static class SavedState extends BaseSavedState {
        boolean checked;

        SavedState(final Parcelable superState) {
            super(superState);
        }

        private SavedState(final Parcel in) {
            super(in);
            checked = (Boolean) in.readValue(getClass().getClassLoader());
        }

        @Override
        public void writeToParcel(final Parcel out, final int flags) {
            super.writeToParcel(out, flags);
            out.writeValue(checked);
        }

        @Override
        public String toString() {
            return TAG + ".SavedState{" + Integer.toHexString(System.identityHashCode(this)) + " checked=" + checked
                    + "}";
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(final Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(final int size) {
                return new SavedState[size];
            }
        };
    }
}
