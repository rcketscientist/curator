package com.anthonymandra.rawdroid;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Color;

public class PPMtoBMP
{
	public static byte[] readBitmapFromPPM(byte[] data) throws IOException
	{
		// FileInputStream fs = new FileInputStream(file);
		// DataInputStream reader = new DataInputStream(new
		// FileInputStream(file));
		BufferedInputStream reader = new BufferedInputStream(new ByteArrayInputStream(data));// FileInputStream(file));
		// BufferedReader reader = new BufferedReader(new FileReader(file));
		if (reader.read() != 'P' || reader.read() != '6')
			return data;
		reader.read(); // Eat newline
		String widths = "", heights = "";
		char temp;
		while ((temp = (char) reader.read()) != ' ')
			widths += temp;
		while ((temp = (char) reader.read()) >= '0' && temp <= '9')
			heights += temp;
		if (reader.read() != '2' || reader.read() != '5' || reader.read() != '5')
			return null;
		reader.read(); // Eat the last newline
		int width = Integer.parseInt(widths);
		int height = Integer.parseInt(heights);
		int[] colors = new int[width * height];

		// Read in the pixels
		for (int y = 0; y < height; y++)
		{
			// byte[] line = reader.readLine().getBytes();
			for (int x = 0; x < width; x++)
			{
				// char[] pixel = new char[3];
				// reader.read(pixel);
				/*
				 * int red = reader.read(); int green = reader.read(); int blue = reader.read(); byte r = (byte)red; byte g = (byte)green; byte b =
				 * (byte)blue;
				 */
				int r = reader.read();// line[x];
				// ++x;
				int g = reader.read();// line[x];
				// ++x;
				int b = reader.read();// line[x];
				colors[y * width + x] = Color.rgb(r, g, b);
				/*
				 * byte r = gamma709(reader.readByte()); byte g = gamma709(reader.readByte()); byte b = gamma709(reader.readByte()); colors[y * width
				 * + x] = Color.rgb(r, g, b);
				 */
				/* (255 << 24) | //A (r << 16) | //R (g << 8) | //G (b); //B */
			}
		}

		Bitmap bmp = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
		// FileOutputStream fs = new FileOutputStream(destination + "/" + file.getName().replaceFirst("[.][^.]+$", "") + ".jpg");
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		bmp.compress(CompressFormat.JPEG, 100, byteStream);
		byte[] resultBytes = byteStream.toByteArray();
		byteStream.close();
		reader.close();
		return resultBytes;
		/* bitmap.SetPixel(x, y, new Bitmap.Color() { Red = reader.ReadByte(), Green = reader.ReadByte(), Blue = reader.ReadByte() }); */
		// return bitmap;
	}

	/*
	 * private static byte gamma709(int intensity) { final float gamma = 2.2f; final float oneOverGamma = 1.0f / gamma; final float linearCutoff =
	 * 0.018f; final float linearExpansion = (float)(1.099 * Math.pow(linearCutoff, oneOverGamma) - 0.099) / linearCutoff; float brightness; if
	 * (intensity < linearCutoff) brightness = intensity * linearExpansion; else brightness = (float)(1.099 * Math.pow(intensity, oneOverGamma) -
	 * 0.099); return (byte)brightness; }
	 */
	/*
	 * static void doPgmPpm(String inputFILE * const ifP, Header h, String output) unsigned int const cols, unsigned int const rows, pixval const
	 * maxval, int final ppmFormat, int const class, FILE * const ofP) { PGM and PPM. The input image is read into a PPM array, scanned for color
	 * analysis and converted to a BMP raster. Logic works for PBM. int minimumBpp; int bitsPerPixel;//unsigned int bitsPerPixel; colortype colortype;
	 * int row;//unsigned int row; pixel ** pixels; colorMap colorMap; pixels = ppm_allocarray(cols, rows); for (row = 0; row < rows; ++row)
	 * ppm_readppmrow(ifP, pixels[row], cols, maxval, ppmFormat); analyze_colors((const pixel**)pixels, cols, rows, maxval, &minimumBpp, &colorMap);
	 * choose_colortype_bpp(cmdline, colorMap.count, minimumBpp, &colortype, &bitsPerPixel); BMPEncode(stdout, class, colortype, bitsPerPixel, cols,
	 * rows, (const pixel**)pixels, maxval, &colorMap); freeColorMap(&colorMap); } privat void ppm_readppmrow(FILE* const fileP, pixel* const
	 * pixelrow, int const cols, pixval const maxval, int const format) String file, Header h, something holding pixel row) { switch (format) { case
	 * PpmFormat.P3: { unsigned int col; for (col = 0; col < cols; ++col) { pixval const r = pm_getuint(fileP); pixval const g = pm_getuint(fileP);
	 * pixval const b = pm_getuint(fileP); if (r > maxval) pm_error("Red sample value %u is greater than maxval (%u)", r, maxval); if (g > maxval)
	 * pm_error("Green sample value %u is greater than maxval (%u)", g, maxval); if (b > maxval)
	 * pm_error("Blue sample value %u is greater than maxval (%u)", b, maxval); PPM_ASSIGN(pixelrow[col], r, g, b); } } break; For PAM, we require a
	 * depth of 3, which means the raster format is identical to Raw PPM! How convenient. case PpmFormat.P7: case PpmFormat.P6: { unsigned int const
	 * bytesPerSample = maxval < 256 ? 1 : 2; unsigned int const bytesPerRow = cols * 3 * bytesPerSample; unsigned int bufferCursor; unsigned char *
	 * rowBuffer; ssize_t rc; MALLOCARRAY(rowBuffer, bytesPerRow); if (rowBuffer == NULL) pm_error("Unable to allocate memory for row buffer "
	 * "for %u columns", cols); rc = fread(rowBuffer, 1, bytesPerRow, fileP); if (feof(fileP)) pm_error("Unexpected EOF reading row of PPM image.");
	 * else if (ferror(fileP)) pm_error("Error reading row.  fread() errno=%d (%s)", errno, strerror(errno)); else if (rc != bytesPerRow)
	 * pm_error("Error reading row.  Short read of %u bytes " "instead of %u", rc, bytesPerRow); bufferCursor = 0; start at beginning of rowBuffer[]
	 * if (bytesPerSample == 1) { unsigned int col; for (col = 0; col < cols; ++col) { pixval const r = rowBuffer[bufferCursor++]; pixval const g =
	 * rowBuffer[bufferCursor++]; pixval const b = rowBuffer[bufferCursor++]; PPM_ASSIGN(pixelrow[col], r, g, b); } } else { two byte samples unsigned
	 * int col; for (col = 0; col < cols; ++col) { pixval r, g, b; r = rowBuffer[bufferCursor++] << 8; r |= rowBuffer[bufferCursor++]; g =
	 * rowBuffer[bufferCursor++] << 8; g |= rowBuffer[bufferCursor++]; b = rowBuffer[bufferCursor++] << 8; b |= rowBuffer[bufferCursor++];
	 * PPM_ASSIGN(pixelrow[col], r, g, b); } } free(rowBuffer); } break; case PpmFormat.P2: case PpmFormat.P5: { gray * const grayrow =
	 * pgm_allocrow(cols); unsigned int col; pgm_readpgmrow(fileP, grayrow, cols, maxval, format); for (col = 0; col < cols; ++col) { pixval const g =
	 * grayrow[col]; PPM_ASSIGN(pixelrow[col], g, g, g); } pgm_freerow(grayrow); } break; case PBM_FORMAT: case RPBM_FORMAT: { bit * const bitrow =
	 * pbm_allocrow(cols); unsigned int col; pbm_readpbmrow(fileP, bitrow, cols, format); for (col = 0; col < cols; ++col) { pixval const g =
	 * (bitrow[col] == PBM_WHITE) ? maxval : 0; PPM_ASSIGN(pixelrow[col], g, g, g); } pbm_freerow(bitrow); } break; default:
	 * pm_error("Invalid format code"); } } void ppm_readppminit(FILE * const fileP, int * const colsP, int * const rowsP, pixval * const maxvalP, int
	 * * const formatP) { int realFormat; Check magic number. realFormat = pm_readmagicnumber(fileP); switch (PAM_FORMAT_TYPE(realFormat)) { case
	 * PPM_TYPE:formatP = realFormat; ppm_readppminitrest(fileP, colsP, rowsP, maxvalP); break; case PGM_TYPE:formatP = realFormat;
	 * pgm_readpgminitrest(fileP, colsP, rowsP, maxvalP); break; case PBM_TYPE:formatP = realFormat;maxvalP = 1; pbm_readpbminitrest(fileP, colsP,
	 * rowsP); break; case PAM_TYPE: pnm_readpaminitrestaspnm(fileP, colsP, rowsP, maxvalP, formatP); break; default:
	 * pm_error("bad magic number %d - not a PPM, PGM, PBM, or PAM file", PAM_FORMAT_TYPE(*formatP)); } validateComputableSize(*colsP, *rowsP); }
	 * private enum colortype { TRUECOLOR, PALETTE } private class Header { public int rows; public int columns; public PpmFormat format; public int
	 * maxPixel; } private enum PpmFormat { P2, P3, P5, P6, P7 }
	 */
}
