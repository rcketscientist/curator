package com.anthonymandra.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import com.anthonymandra.rawdroid.FullSettingsActivity;
import com.anthonymandra.rawdroid.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MultiSpinner extends Spinner implements OnMultiChoiceClickListener// , OnCancelListener
{
	private List<String> items;
	private List<String> selected = new ArrayList<>();
	private static final String delimiter = ",";
	AlertDialog dialogSelectKeyword;

	public MultiSpinner(Context context)
	{
		super(context);
		initialize();
	}

	public MultiSpinner(Context arg0, AttributeSet arg1)
	{
		super(arg0, arg1);
		initialize();
	}

	public MultiSpinner(Context arg0, AttributeSet arg1, int arg2)
	{
		super(arg0, arg1, arg2);
		initialize();
	}

	private void initialize()
	{
		ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, new String[]
		{ (String) getPrompt() });
		setAdapter(adapter);
		parsePreference();
	}

	@Override
	public void onClick(DialogInterface dialog, int which, boolean isChecked)
	{
		if (isChecked)
			addSelection(items.get(which));
		else
			selected.remove(items.get(which));
	}

	private void savePreference()
	{
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
		sp.edit().putString(FullSettingsActivity.KEY_CustomKeywords, serializeItems()).apply();
	}

	private String serializeItems()
	{
		String serial = "";
		for (int i = 0; i < items.size(); i++)
		{
			serial += items.get(i);
			if (i < items.size() - 1)
				serial += delimiter;
		}
		return serial;
	}

	private void parsePreference()
	{
		if (isInEditMode())
			return;

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
		String pref = sp.getString(FullSettingsActivity.KEY_CustomKeywords, "");
		items = new ArrayList<>();
		Collections.addAll(items, pref.split(delimiter));
	}

	private boolean[] getSelectedArray()
	{
		boolean[] selection = new boolean[items.size()];
		Arrays.fill(selection, false);
		for (String select : selected)
		{
			int index = items.indexOf(select);
			if (index >= 0)
				selection[index] = true;
		}
		return selection;
	}

	@Override
	public boolean performClick()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		List<String> display = new ArrayList<>(items);
		builder.setMultiChoiceItems(display.toArray(new CharSequence[display.size()]), getSelectedArray(), this);
		builder.setPositiveButton(R.string.addKeyword, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				AlertDialog.Builder alert = new AlertDialog.Builder(getContext());

				alert.setTitle(R.string.addKeyword);

				// Set an EditText view to get user input
				final EditText input = new EditText(getContext());
				alert.setView(input);

				alert.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						String value = input.getText().toString();
						addKeyword(value);
						Collections.sort(items);
						addSelection(value);
						savePreference();
						dialogSelectKeyword.dismiss();
					}
				});

				alert.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						// Canceled.
					}
				});

				alert.show();
			}

		});
		builder.setNeutralButton(R.string.delete, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				for (String item : selected)
				{
					items.remove(item);
				}

				savePreference();
				dialog.dismiss();
			}
		});
		dialogSelectKeyword = builder.create();
		dialogSelectKeyword.show();
		return true;
	}

	public List<String> getSelected()
	{
		return selected;
	}

	public void setSelected(List<String> keywords)
	{
		for (String keyword : keywords)
		{
			if (items.contains(keyword))
				addSelection(keyword);
		}
	}

	/**
	 * Ensures keywords are unique
	 * 
	 * @param keyword
	 */
	private void addKeyword(String keyword)
	{
		if (!items.contains(keyword))
			items.add(keyword);
	}

	/**
	 * Ensures selections are unique
	 * 
	 * @param keyword
	 */
	private void addSelection(String keyword)
	{
		if (!selected.contains(keyword))
			selected.add(keyword);
	}

	public void clearSelected()
	{
		selected.clear();
	}
}