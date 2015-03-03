package com.anthonymandra.rawdroid;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.anthonymandra.framework.RawObject;

import java.util.List;

public class FormatDialog extends Dialog
{
	private List<? extends RawObject> sourceFiles;
	private Spinner spinner;

	private Context mContext;
    private DialogListener listener;

    interface DialogListener
    {
        void onCompleted();
        void onCanceled();
    }

    public void setDialogListener(DialogListener listener)
    {
        this.listener = listener;
    }

	public FormatDialog(Context context, List<? extends RawObject> sourceFiles)
	{
		super(context);
		this.mContext = context;
		this.sourceFiles = sourceFiles;
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
						for (RawObject raw : sourceFiles)
						{
							++counter;
							String baseName = customName + "-" + String.format(format, counter);
							raw.rename(baseName);
						}
						break;
					case 1:
						for (RawObject raw : sourceFiles)
						{
							++counter;
							String baseName = customName + " (" + String.format(format, counter) + " of " + total + ")";
							raw.rename(baseName);
						}
						break;
				}
			}
            if (listener != null)
                listener.onCompleted();

			dismiss();
		}
	}

	private class CancelListener implements android.view.View.OnClickListener
	{
		@Override
		public void onClick(View v)
		{
            if (listener != null)
                listener.onCanceled();

            dismiss();
		}
	}
}
