/* libtiffdecoder A tiff decoder run on android system. Copyright (C) 2009 figofuture
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public 
 * License as published by the Free Software Foundation; either version 2.1 of the License, or (at your option) any later 
 * version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free 
 * Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 * 
 * */

#include <jni.h>
#include <stdio.h>
#include <tiffio.h>
#include <android/log.h>
#include <stdlib.h>

TIFF *_image = NULL;
unsigned int *_buffer = NULL;
tsize_t _stripSize = 0;
unsigned long _bufferSize = 0;
int _stripMax = 0;
unsigned int _width = 0;
unsigned int _height = 0;
unsigned int _samplesperpixel = 0;
unsigned int _bitspersample = 0;
unsigned int _totalFrame = 0;

jintArray decode( JNIEnv* env, jobject thiz, TIFF* image, jstring path, jintArray dimensions);

jintArray
Java_com_anthonymandra_rawprocessor_TiffDecoder_getImageFd( JNIEnv* env, jobject thiz, jstring name, int fd, jintArray dimensions)
{
	// Open the TIFF image
	const char *nameString = NULL;
	nameString = (*env)->GetStringUTFChars(env, name, NULL );
	TIFF *image = NULL;
	if((image = TIFFFdOpen(fd, nameString, "r")) == NULL){
		__android_log_print(ANDROID_LOG_INFO, "nativeTiffOpen", "Could not open incoming image", nameString);
		return -1;
	}
	(*env)->ReleaseStringUTFChars(env, name, nameString);
	return decode(env, thiz, image, name, dimensions);
}

jintArray
Java_com_anthonymandra_rawprocessor_TiffDecoder_getImage( JNIEnv* env, jobject thiz, jstring path, jintArray dimensions)
{
	// Open the TIFF image
	const char *strPath = NULL;
	strPath = (*env)->GetStringUTFChars(env, path, NULL );
	TIFF *image = NULL;
	if((image = TIFFOpen(strPath, "r")) == NULL){
		__android_log_print(ANDROID_LOG_INFO, "nativeTiffOpen", "Could not open incoming image", strPath);
		return -1;
	}
	(*env)->ReleaseStringUTFChars(env, path, strPath);
	return decode(env, thiz, image, path, dimensions);
}

/**
 * For now this method is managing 'image' if the method is successful
 */
jintArray decode( JNIEnv* env, jobject thiz, TIFF* image, jstring path, jintArray dimensions)
{
	unsigned int width = 0;
	unsigned int height = 0;

	TIFFGetField(image, TIFFTAG_IMAGEWIDTH, &width);
	TIFFGetField(image, TIFFTAG_IMAGELENGTH, &height);

	jint *dim = (*env)->GetIntArrayElements(env, dimensions, 0);
	dim[0] = width;
	dim[1] = height;
	(*env)->ReleaseIntArrayElements(env, dimensions, dim, 0);

	unsigned long bufferSize = width * height;
	unsigned int *buffer = NULL;

	// Allocate the memory
	if((buffer = (unsigned int *) _TIFFmalloc(bufferSize * sizeof (unsigned int))) == NULL){
		__android_log_print(ANDROID_LOG_INFO, "nativeTiffOpen", "Could not allocate enough memory for the uncompressed image");
		return -1;
	}

	int stripCount = 0;
	unsigned long imageOffset = 0;
	unsigned long result = 0;
	uint16 photo = 0;
	uint16 fillorder = 0;
	char tempbyte = 0;
	unsigned long count = 0;

	// Get the RGBA Image
	//TIFFReadRGBAImage(image, width, height, buffer, 0);
	TIFFReadRGBAImageOriented(image, width, height, buffer, ORIENTATION_TOPLEFT, 0);

	// Convert ABGR to ARGB
	int i = 0;
	int j = 0;
	int tmp = 0;
	for( i = 0; i < height; i++ )
	{
		for( j=0; j< width; j++ )
		{
			tmp = buffer[ j + width * i ];
			buffer[ j + width * i ] =
					(tmp & 0xff000000) |
					((tmp & 0x00ff0000)>>16) |
					(tmp & 0x0000ff00 ) |
					((tmp & 0xff)<<16);
		}
	}

	// Deal with photometric interpretations
	if(TIFFGetField(image, TIFFTAG_PHOTOMETRIC, &photo) == 0)
	{
		__android_log_print(ANDROID_LOG_INFO, "nativeTiffGetBytes", "Image has an undefined photometric interpretation");
	}

	// Deal with fillorder
	if(TIFFGetField(image, TIFFTAG_FILLORDER, &fillorder) == 0)
	{
		__android_log_print(ANDROID_LOG_INFO, "nativeTiffGetBytes", "Image has an undefined fillorder");
		//exit(42);
	}

	jintArray array = (*env)->NewIntArray( env, bufferSize );
	if (!array)
	{
		__android_log_print(ANDROID_LOG_INFO, "nativeTiffGetBytes", "OutOfMemoryError is thrown.");
	}
	else
	{
		// Original
		jint* bytes = (*env)->GetIntArrayElements( env, array, NULL );
		if (bytes != NULL)
		{
			memcpy(bytes, buffer, bufferSize * sizeof (unsigned int));
			(*env)->ReleaseIntArrayElements( env, array, bytes, 0 );
		}
	}

	if(image)
	{
		TIFFClose(image);
		image = NULL;
	}
	if(buffer)
	{
		_TIFFfree(buffer);
		buffer = NULL;
	}

	return array;
}