package com.anthonymandra.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.anthonymandra.rawdroid.R;

//TODO: Move these to a library
public class SeekBarPreference extends DialogPreference
{
    private static final String androidns="http://schemas.android.com/apk/res/android";

    private SeekBar mSeekBar;
    private final Context mContext;

    private final String mSuffix;
	private int mProgress;  // Master value when the seekbar would be null when dialog isn't visible
    private int mMax;
	private boolean mValueSet;

    public SeekBarPreference(Context context, AttributeSet attrs) {

        super(context,attrs);
        mContext = context;

        // Get string value for suffix (text attribute in xml file) :
        int mSuffixId = attrs.getAttributeResourceValue(androidns, "text", 0);
        if(mSuffixId == 0) mSuffix = attrs.getAttributeValue(androidns, "text");
        else mSuffix = mContext.getString(mSuffixId);

        mMax = attrs.getAttributeIntValue(androidns, "max", 100);
	    setDialogLayoutResource(R.layout.seekbar_preference);
    }

	@Override
	protected void onBindDialogView(View view)
	{
		super.onBindDialogView(view);
		final TextView valueText = (TextView) view.findViewById(R.id.textView);

		mSeekBar = (SeekBar) view.findViewById(R.id.seekBar);
		mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
		{
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
			{
				String t = String.valueOf(progress);
				valueText.setText(mSuffix == null ? t : t.concat(" " + mSuffix));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});
		mSeekBar.setMax(mMax);
		mSeekBar.setProgress(mProgress);
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getInt(index, 0);
	}

	@Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
    {
	    setProgress(restoreValue ? getPersistedInt(mProgress) : (int) defaultValue);
    }

    @SuppressWarnings("unused")
    public void setMax(int max) { mMax = max; }
    @SuppressWarnings("unused")
    public int getMax() { return mMax; }

    @SuppressWarnings("unused")
    public void setProgress(int progress) {
	    // Always persist/notify the first time.
	    final boolean changed = mProgress != progress;
	    if (changed || !mValueSet) {
		    mProgress = progress;
		    mValueSet = true;
		    persistInt(progress);
		    if (changed) {
			    notifyChanged();
		    }
	    }
    }

    @SuppressWarnings("unused")
    public int getProgress() { return mProgress; }

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);

		if (positiveResult) {
			if (callChangeListener(getProgress())) {
				setProgress(mSeekBar.getProgress());
			}
		}
	}

    // According to ListPreference implementation
    @Override
    public CharSequence getSummary() {
        String text = Integer.toString(getProgress());
        CharSequence summary = super.getSummary();
        if (summary != null) {
            return String.format(summary.toString(), text);
        } else {
            return null;
        }
    }

	@Override
	protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();
		if (isPersistent()) {
			// No need to save instance state since it's persistent
			return superState;
		}

		final SavedState myState = new SavedState(superState);
		myState.value = getProgress();
		return myState;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if (state == null || !state.getClass().equals(SavedState.class)) {
			// Didn't save state for us in onSaveInstanceState
			super.onRestoreInstanceState(state);
			return;
		}

		SavedState myState = (SavedState) state;
		super.onRestoreInstanceState(myState.getSuperState());
		setProgress(myState.value);
	}

	private static class SavedState extends BaseSavedState {
		int value;

		public SavedState(Parcel source) {
			super(source);
			value = source.readInt();
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeInt(value);
		}

		public SavedState(Parcelable superState) {
			super(superState);
		}

		public static final Parcelable.Creator<SavedState> CREATOR =
				new Parcelable.Creator<SavedState>() {
					public SavedState createFromParcel(Parcel in) {
						return new SavedState(in);
					}

					public SavedState[] newArray(int size) {
						return new SavedState[size];
					}
				};
	}
}