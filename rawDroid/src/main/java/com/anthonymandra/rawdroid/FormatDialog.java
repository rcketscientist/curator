package com.anthonymandra.rawdroid;

import android.app.Dialog;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.ImageUtils;
import com.anthonymandra.framework.RawObject;

import java.util.ArrayList;
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

//		Button accept = (Button) findViewById(R.id.buttonAccept);
//		accept.setOnClickListener(new AcceptListener());
//
//		Button cancel = (Button) findViewById(R.id.buttonCancel);
//		cancel.setOnClickListener(new CancelListener());
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
			ArrayList<ContentProviderOperation> operations = new ArrayList<>();
			if (selected != AdapterView.INVALID_POSITION)
			{
				switch (selected)
				{
					case 0:
						for (RawObject raw : sourceFiles)
						{
							++counter;
							String baseName = customName + "-" + String.format(format, counter);
							operations.add(rename(raw, baseName));
						}
						break;
					case 1:
						for (RawObject raw : sourceFiles)
						{
							++counter;
							String baseName = customName + " (" + String.format(format, counter) + " of " + total + ")";
							operations.add(rename(raw, baseName));
						}
						break;
				}
			}
            if (listener != null)
                listener.onCompleted();


			try
			{
				// TODO: If I implement bulkInsert it's faster
				getContext().getContentResolver().applyBatch(Meta.AUTHORITY, operations);
			} catch (Exception e)
			{
				//TODO: This could automatically refresh.
				Toast.makeText(getContext(), "ERROR updating gallery.  Please manually refresh.  Please contact me.", Toast.LENGTH_LONG).show();
			}

			dismiss();
		}
	}

	private ContentProviderOperation rename(RawObject toRename, String baseName)
	{
		ContentValues c = new ContentValues();
		String priorUri = toRename.getUri().toString();
//		toRename.rename(baseName);

		c.put(Meta.Data.NAME, toRename.getName());
		c.put(Meta.Data.URI, toRename.getUri().toString());

		// Create the operation to update the contentprovider with the new name
		return ContentProviderOperation.newUpdate(Meta.Data.CONTENT_URI)
				.withSelection(Meta.URI_COLUMN + "=?", new String[]{priorUri})
				.withValues(c)
				.build();
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
