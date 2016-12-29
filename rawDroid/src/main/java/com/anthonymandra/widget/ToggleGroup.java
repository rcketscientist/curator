package com.anthonymandra.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import com.anthonymandra.rawdroid.R;

import java.util.ArrayList;
import java.util.List;

public class ToggleGroup extends LinearLayout
{
    // holds the checked id in the case of exclusive mode; the selection is empty by default
    private int mCheckedId = View.NO_ID;

    // holds all checked values in the case of nonexclusive mode; the selection is empty by default
    private List<Integer> mCheckedIds = new ArrayList<>();

    private boolean mExclusive;
    private boolean mUnselected;
    // tracks children radio buttons checked state
    private CompoundButton.OnCheckedChangeListener mChildOnCheckedChangeListener;
    // when true, mOnCheckedChangeListener discards events
    private boolean mProtectFromCheckedChange = false;
    private ToggleGroup.OnCheckedChangeListener mOnCheckedChangeListener;
    private PassThroughHierarchyChangeListener mPassThroughListener;

    /**
     * {@inheritDoc}
     */
    public ToggleGroup(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        init();
    }

    /**
     * {@inheritDoc}
     */
    public ToggleGroup(Context context, AttributeSet attrs) {
        super(context, attrs);

        // retrieve selected radio button as requested by the user in the
        // XML layout file
        TypedArray attributes = context.obtainStyledAttributes(
                attrs, R.styleable.ToggleGroup, R.attr.radioButtonStyle, 0);

        mExclusive = attributes.getBoolean(R.styleable.ToggleGroup_exclusive, false);
        mUnselected = attributes.getBoolean(R.styleable.ToggleGroup_allowUnselected, false);

        int value = attributes.getResourceId(R.styleable.ToggleGroup_checkedButton, View.NO_ID);
        if (value != View.NO_ID) {
            mCheckedId = value; // We set this regardless to help with designer

            if (mExclusive)
                mCheckedIds.add(value);
        }

        attributes.recycle();
        init();
    }

    private void init() {
        mChildOnCheckedChangeListener = new CheckedStateTracker();
        mPassThroughListener = new PassThroughHierarchyChangeListener();
        super.setOnHierarchyChangeListener(mPassThroughListener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
        // the user listener is delegated to our pass-through listener
        mPassThroughListener.mOnHierarchyChangeListener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // checks the appropriate radio button as requested in the XML file
        if (mCheckedId != View.NO_ID) {
            mProtectFromCheckedChange = true;
            setCheckedStateForView(mCheckedId, true);
            mProtectFromCheckedChange = false;
            addCheckedId(mCheckedId);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child instanceof CompoundButton) {
            final CompoundButton button = (CompoundButton) child;
            if (button.isChecked()) {
                mProtectFromCheckedChange = true;
                if (mExclusive && mCheckedId != View.NO_ID) {
                    setCheckedStateForView(mCheckedId, false);
                }
                mProtectFromCheckedChange = false;
                addCheckedId(button.getId());
            }
        }

        super.addView(child, index, params);
    }

    /**
     * <p>Sets the selection to the radio button whose identifier is passed in
     * parameter. Using -1 as the selection identifier clears the selection;
     * such an operation is equivalent to invoking {@link #clearChecked()}.</p>
     *
     * @param id the unique id of the radio button to select in this group
     *
     * @see #getCheckedId()
     * @see #clearChecked()
     */
    public void check(@IdRes int id) {
        if (mExclusive)
            checkExclusive(id);
        else
            checkMulti(id);
    }

    private void checkMulti(@IdRes int id)
    {
        if (id == -1)
            removeAllChecked();
        else if (mCheckedIds.contains(id))
            removeCheckedId(id);
        else
            addCheckedId(id);
    }

    private void checkExclusive(@IdRes int id) {
        // don't even bother
        if (id != -1 && (id == mCheckedId)) {
            return;
        }

        if (mCheckedId != -1) {
            setCheckedStateForView(mCheckedId, false);
        }

        if (id != -1) {
            setCheckedStateForView(id, true);
        }

        addCheckedId(id);
    }

    private void removeAllChecked() {
        mCheckedIds.clear();
        fireCheckedChanged();
    }

    private void removeCheckedId(@IdRes int id) {
        mCheckedIds.remove(id);
        fireCheckedChanged();
    }

    private void fireCheckedChanged()
    {
        if (mOnCheckedChangeListener != null) {
            int[] checked = new int[mCheckedIds.size()];
            for (int i = 0; i < mCheckedIds.size(); i++) {
                checked[i] = mCheckedIds.get(i);
            }
            mOnCheckedChangeListener.onCheckedChanged(this, checked);
        }
    }

    private void addCheckedId(@IdRes int id) {
        mCheckedId = id;
        mCheckedIds.add(id);
        fireCheckedChanged();
    }

    private void setCheckedStateForView(int viewId, boolean checked) {
        View checkedView = findViewById(viewId);
        if (checkedView != null && checkedView instanceof CompoundButton) {
            ((CompoundButton) checkedView).setChecked(checked);
        }
    }

    /**
     * <p>Returns the identifier of the selected radio button in this group.
     * Upon empty selection, the returned value is -1.</p>
     *
     * @return the unique id of the selected radio button in this group
     *
     * @see #check(int)
     * @see #clearChecked()
     *
     * @attr ref android.R.styleable#ToggleGroup_checkedButton
     */
    @IdRes
    public int getCheckedId() {
        if (!mExclusive)
            throw new UnsupportedOperationException("This method only returns a value in exclusive mode.");
        return mCheckedId;
    }

    /**
     * <p>Returns the identifiers of the selected toggles in this group.
     * Upon empty selection, the returned value is null.</p>
     *
     * @return the unique id of the selected radio button in this group
     *
     * @see #check(int)
     * @see #clearChecked()
     *
     * @attr ref android.R.styleable#ToggleGroup_checkedButton
     */
    public @Nullable
    int[] getCheckedIds() {
        if (mCheckedIds. size() == 0)
            return null;

        int[] checked = new int[mCheckedIds.size()];
        for (int i = 0; i < mCheckedIds.size(); i++) {
            checked[i] = mCheckedIds.get(i);
        }
        return checked;
    }

    /**
     * <p>Clears the selection. When the selection is cleared, no radio button
     * in this group is selected and {@link #getCheckedId()} returns
     * null.</p>
     *
     * @see #check(int)
     * @see #getCheckedId()
     */
    public void clearChecked() {
        check(View.NO_ID);
    }

    /**
     * <p>Register a callback to be invoked when the checked radio button
     * changes in this group.</p>
     *
     * @param listener the callback to call on checked state change
     */
    public void setOnCheckedChangeListener(ToggleGroup.OnCheckedChangeListener listener) {
        mOnCheckedChangeListener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ToggleGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new ToggleGroup.LayoutParams(getContext(), attrs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof ToggleGroup.LayoutParams;
    }

    @Override
    protected LinearLayout.LayoutParams generateDefaultLayoutParams() {
        return new ToggleGroup.LayoutParams(ToggleGroup.LayoutParams.WRAP_CONTENT, ToggleGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return ToggleGroup.class.getName();
    }

    /**
     * <p>This set of layout parameters defaults the width and the height of
     * the children to {@link #WRAP_CONTENT} when they are not specified in the
     * XML file. Otherwise, this class uses the value read from the XML file.</p>
     *
     */
    public static class LayoutParams extends LinearLayout.LayoutParams {
        /**
         * {@inheritDoc}
         */
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(int w, int h) {
            super(w, h);
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(int w, int h, float initWeight) {
            super(w, h, initWeight);
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(ViewGroup.LayoutParams p) {
            super(p);
        }

        /**
         * {@inheritDoc}
         */
        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        /**
         * <p>Fixes the child's width to
         * {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT} and the child's
         * height to  {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}
         * when not specified in the XML file.</p>
         *
         * @param a the styled attributes set
         * @param widthAttr the width attribute to fetch
         * @param heightAttr the height attribute to fetch
         */
        @Override
        protected void setBaseAttributes(TypedArray a,
                                         int widthAttr, int heightAttr) {

            if (a.hasValue(widthAttr)) {
                width = a.getLayoutDimension(widthAttr, "layout_width");
            } else {
                width = WRAP_CONTENT;
            }

            if (a.hasValue(heightAttr)) {
                height = a.getLayoutDimension(heightAttr, "layout_height");
            } else {
                height = WRAP_CONTENT;
            }
        }
    }

    /**
     * <p>Interface definition for a callback to be invoked when the checked
     * toggle changed in this group.</p>
     */
    public interface OnCheckedChangeListener {
        /**
         * <p>Called when the checked toggle has changed. When the
         * selection is cleared, checkedId is null.</p>
         *
         * @param group the group in which the checked radio button has changed
         * @param checkedId the unique identifier of the newly checked radio button
         */
        public void onCheckedChanged(ToggleGroup group, @IdRes int[] checkedId);
    }

    private class CheckedStateTracker implements CompoundButton.OnCheckedChangeListener {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            // prevents from infinite recursion
            if (mProtectFromCheckedChange) {
                return;
            }

            mProtectFromCheckedChange = true;
            check(buttonView.getId());
            mProtectFromCheckedChange = false;
        }
    }

    /**
     * <p>A pass-through listener acts upon the events and dispatches them
     * to another listener. This allows the table layout to set its own internal
     * hierarchy change listener without preventing the user to setup his.</p>
     */
    private class PassThroughHierarchyChangeListener implements
            ViewGroup.OnHierarchyChangeListener {
        private ViewGroup.OnHierarchyChangeListener mOnHierarchyChangeListener;

        /**
         * {@inheritDoc}
         */
        public void onChildViewAdded(View parent, View child) {
            if (parent == ToggleGroup.this && child instanceof CompoundButton) {
                int id = child.getId();
                // generates an id if it's missing
                if (id == View.NO_ID) {
                    id = View.generateViewId();
                    child.setId(id);
                }
                ((CompoundButton) child).setOnCheckedChangeListener(mChildOnCheckedChangeListener);
            }

            if (mOnHierarchyChangeListener != null) {
                mOnHierarchyChangeListener.onChildViewAdded(parent, child);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void onChildViewRemoved(View parent, View child) {
            if (parent == ToggleGroup.this && child instanceof CompoundButton) {
                ((CompoundButton) child).setOnCheckedChangeListener(null);
            }

            if (mOnHierarchyChangeListener != null) {
                mOnHierarchyChangeListener.onChildViewRemoved(parent, child);
            }
        }
    }
}
