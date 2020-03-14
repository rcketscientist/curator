//package com.anthonymandra.widget;
//
//import android.content.Context;
//import androidx.preference.EditTextPreference;
//import android.text.TextUtils;
//import android.util.AttributeSet;
//
///**
// * Extends {@link EditTextPreference} to automatically populate summary (%s) like ListPreference
// */
//public class FriendlyEditTextPreference extends EditTextPreference {
//
//	public FriendlyEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
//		super(context, attrs, defStyle);
//	}
//
//	public FriendlyEditTextPreference(Context context, AttributeSet attrs) {
//		super(context, attrs);
//	}
//
//	public FriendlyEditTextPreference(Context context) {
//		super(context);
//	}
//
//	// According to ListPreference implementation
//	@Override
//	public CharSequence getSummary() {
//		String text = getText();
//		if (TextUtils.isEmpty(text)) {
//			return getEditText().getHint();
//		} else {
//			CharSequence summary = super.getSummary();
//			if (summary != null) {
//				return String.format(summary.toString(), text);
//			} else {
//				return null;
//			}
//		}
//	}
//}
