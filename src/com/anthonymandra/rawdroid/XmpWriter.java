package com.anthonymandra.rawdroid;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.UUID;

import android.util.Log;

import com.anthonymandra.framework.MediaObject;

public class XmpWriter
{
	private static final String TAG = XmpWriter.class.getSimpleName();
	private static final String xmlns_rdf = "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"";
	private static final String xmlns_x = "xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.3-c007 1.136881, 2010/06/10-18:11:35        \"";
	private static final String xmlns_xmp = "xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\"";
	private static final String xmlns_xmpMM = "xmlns:xmpMM=\"http://ns.adobe.com/xap/1.0/mm/\"";
	private static final String xmlns_dc = "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"";

	private static final String x = "x:xmpmeta ";
	private static final String rdf = "rdf:RDF ";
	private static final String rdfDescription = "rdf:Description ";
	private static final String rdfAbout = "rdf:about=\"\"";
	private static final String xmpLabel = "xmp:Label=\"";
	private static final String xmpRating = "xmp:Rating=\"";
	private static final String xmpOriginalDoc = "xmpMM:OriginalDocumentID=\"xmp.did:";
	private static final String dcSubject = "dc:subject";
	private static final String rdfBag = "rdf:Bag";
	private static final String rdfLi = "rdf:li";
	
	public enum ColorKeyLabel
	{
		Blue,
		Red,
		Green,
		Yellow,
		Purple
	}

	public static File getXmpFile(File image)
	{
		String name = image.getName();
		int pos = name.lastIndexOf(".");
		if (pos > 0)
		{
			name = name.substring(0, pos);
		}
		name += ".xmp";

		return new File(image.getParent(), name);
	}

	public static void write(MediaObject media)
	{
		// This is ghetto as hell...
		Writer w = media.getXmpWriter();
		if (w == null)
		{
			return;
		}
		
		BufferedWriter writer = new BufferedWriter(w);
		try
		{
			XmpMeta xmp = media.getXmp();
			writer.write("<" + x + xmlns_x + ">");
			writer.newLine();
			writer.write("<" + rdf + xmlns_rdf + ">");
			writer.newLine();
			writer.write("<" + rdfDescription + rdfAbout);
			writer.newLine();
			writer.write("\t" + xmlns_xmp);
			writer.newLine();
			writer.write("\t" + xmlns_xmpMM);
			writer.newLine();
			writer.write("\t" + xmlns_dc);
			writer.newLine();

			if (xmp.getLabel() != null)
			{
				writer.write("\t" + xmpLabel + xmp.getLabel() + "\"");
				writer.newLine();
			}
			if (xmp.getRating() != null)
			{
				writer.write("\t" + xmpRating + xmp.getRating() + "\"");
				writer.newLine();
			}

			writer.write("\t" + xmpOriginalDoc + UUID.randomUUID().toString() + "\">");
			writer.newLine();

			List<String> keywords = xmp.getKeywords();
			if (keywords != null && keywords.size() > 0)
			{
				writer.write("<" + dcSubject + ">");
				writer.newLine();
				writer.write("\t<" + rdfBag + ">");
				writer.newLine();
				for (String keyword : keywords)
				{
					writer.write("\t\t<" + rdfLi + ">" + keyword + "</" + rdfLi + ">");
					writer.newLine();
				}
				writer.write("\t</" + rdfBag + ">");
				writer.newLine();
				writer.write("</" + dcSubject + ">");
				writer.newLine();
			}

			writer.write("</" + rdfDescription + ">");
			writer.newLine();
			writer.write("</" + rdf + ">");
			writer.newLine();
			writer.write("</" + x + ">");
		}
		catch (IOException e)
		{
			Log.e(TAG, "Failed to write xmp fields.", e);
		}
		finally
		{
			try
			{
				if (writer != null)
					writer.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "Failed to close xmp writer.", e);
			}
		}
	}
}
