package com.anthonymandra.widget;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.anthonymandra.rawdroid.R;

import java.util.HashSet;
import java.util.Set;


public class RemovableListPreference extends DialogPreference implements OnClickListener
{
    private static final String androidns="http://schemas.android.com/apk/res/android";

    private ListView mListView;
	private ArrayAdapter<String> mAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1);
    private TextView mMessage;
	private String mPreferenceKey;
    private Context mContext;

    private String mDialogMessage;

    public RemovableListPreference(Context context, AttributeSet attrs) {

        super(context,attrs);
        mContext = context;

	    // Key attribute for the view, used to automatically update the preferences
	    int preferenceId = attrs.getAttributeResourceValue(androidns, "key", -1);
	    if (preferenceId == -1)
		    throw new RuntimeException(RemovableListPreference.class.getSimpleName() + " requires a preference key (android:key)");
	    else
		    mPreferenceKey = mContext.getString(preferenceId);
    }

    @Override
    protected View onCreateDialogView() {

	    int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, mContext.getResources().getDisplayMetrics());

        LinearLayout.LayoutParams params;
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);

        mMessage = new TextView(mContext);
	    mMessage.setId(android.R.id.message);
	    mMessage.setPadding(4 * padding, padding, padding, 2 * padding);
        layout.addView(mMessage);

	    View divider = new View(mContext);
	    divider.setLayoutParams(new ActionBar.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
	    divider.setBackgroundColor(mContext.getResources().getColor(android.R.color.white));
	    layout.addView(divider);

        mListView = new ListView(mContext);
	    mListView.setDivider(getDivider());

	    mListView.setAdapter(mAdapter);
	    mListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
	    {
		    @Override
		    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		    {
			    mAdapter.remove(mAdapter.getItem(position));
		    }
	    });
	    layout.addView(mListView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

	    return layout;
    }

	public void setEntries(Set<String> entries)
	{
		mAdapter.clear();
		mAdapter.addAll(entries);
	}

    @Override
    public void showDialog(Bundle state) {

        super.showDialog(state);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        Set<String> excludedFolders = pref.getStringSet(mContext.getString(R.string.KEY_EXCLUDED_FOLDERS), new HashSet<String>());
	    mAdapter.clear();
        mAdapter.addAll(excludedFolders);

        Button positiveButton = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
	    if (mPreferenceKey != null)
	    {
		    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
		    Set<String> rows = new HashSet<>();
		    for (int i = 0; i < mAdapter.getCount(); ++i)
		    {
			    rows.add(mAdapter.getItem(i));
		    }
		    editor.putStringSet(mPreferenceKey, rows);
            editor.apply();
	    }
        ((AlertDialog) getDialog()).dismiss();
    }

	private Drawable getDivider() {
		int[] attrs = { android.R.attr.listDivider };
		TypedArray a = mContext.obtainStyledAttributes(attrs);
		Drawable divider = a.getDrawable(0);
		a.recycle();

		return divider;
	}
}