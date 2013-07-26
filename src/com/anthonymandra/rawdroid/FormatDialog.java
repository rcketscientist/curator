package com.anthonymandra.rawdroid;

import java.util.List;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.anthonymandra.framework.MediaObject;

public class FormatDialog extends Dialog
{
	private List<? extends MediaObject> sourceFiles;
	private Spinner spinner;

	private Context mContext;

	private ResponseListener responseListener;

	public FormatDialog(Context context, List<? extends MediaObject> sourceFiles)
	{
		super(context);
		this.mContext = context;
		this.sourceFiles = sourceFiles;
	}

	public FormatDialog(Context context, String title, List<MediaObject> sourceFiles, ResponseListener listener)
	{
		super(context);
		setTitle(title);
		this.mContext = context;
		this.sourceFiles = sourceFiles;
		this.responseListener = listener;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.format_name);
		// setTitle("Rename");

		spinner = (Spinner) findViewById(R.id.spinner1);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mContext, R.array.format_array, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);

		Button accept = (Button) findViewById(R.id.buttonAccept);
		accept.setOnClickListener(new AcceptListener());

		Button cancel = (Button) findViewById(R.id.buttonCancel);
		cancel.setOnClickListener(new CancelListener());
	}

	int numDigits(int x)
	{
		return (x < 10 ? 1 : (x < 100 ? 2 : (x < 1000 ? 3 : (x < 10000 ? 4 : (x < 100000 ? 5 : (x < 1000000 ? 6 : (x < 10000000 ? 7 : (x < 100000000 ? 8
				: (x < 1000000000 ? 9 : 10)))))))));
	}
	
	private class AcceptListener implements android.view.View.OnClickListener
	{
		@Override
		public void onClick(View v)
		{
			String customName = ((EditText) findViewById(R.id.editTextFormat)).getText().toString();
			int selected = spinner.getSelectedItemPosition();
			int counter = 0;
			int total = sourceFiles.size();
			String format = "%0" + numDigits(total) + "d";
			if (selected != AdapterView.INVALID_POSITION)
			{
				switch (selected)
				{
				case 0:
					for (MediaObject raw : sourceFiles)
					{
						++counter;
						String baseName = customName + "-" + String.format(format, counter);
						raw.rename(baseName);
					}
					break;
				case 1:
					for (MediaObject raw : sourceFiles)
					{
						++counter;
						String baseName = customName + " (" + String.format(format, counter) + " of " + total + ")";
						raw.rename(baseName);
					}
					break;
				}
			}
			responseListener.Response(true);
			FormatDialog.this.dismiss();
		}
	}

	private class CancelListener implements android.view.View.OnClickListener
	{
		@Override
		public void onClick(View v)
		{
			responseListener.Response(false);
			FormatDialog.this.dismiss();
		}
	}

	public interface ResponseListener
	{
		public void Response(Boolean accept);
	}
}
