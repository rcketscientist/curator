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

jintArray
Java_com_anthonymandra_dcraw_TiffDecoder_getImage( JNIEnv* env, jobject thiz, jstring path, jintArray dimensions)
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
		// Reference from libraw.cpp
//		jbyteArray thumb = env->NewByteArray(jpegSize);
//	 	env->SetByteArrayRegion(thumb, 0, jpegSize, (jbyte *) jpeg);
//		if (jpeg)
//			free(jpeg);

//		jbyteArray thumb = env->NewByteArray(jpegSize);
//		env->SetByteArrayRegion(thumb, 0, jpegSize, (jbyte *) jpeg);

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

jint
Java_com_anthonymandra_dcraw_TiffDecoder_nativeTiffOpen( JNIEnv* env, jobject thiz, jstring path )
{
	// Open the TIFF image
	const char *strPath = NULL;
	strPath = (*env)->GetStringUTFChars(env, path, NULL );
	if((_image = TIFFOpen(strPath, "r")) == NULL){
		__android_log_print(ANDROID_LOG_INFO, "nativeTiffOpen", "Could not open incoming image", strPath);
    	return -1;
	}
	(*env)->ReleaseStringUTFChars(env, path, strPath);

	// Read in the possibly multiple strips
	_stripSize = TIFFStripSize (_image);
	_stripMax = TIFFNumberOfStrips (_image);

	//_bufferSize = stripMax * stripSize;

	_totalFrame = TIFFNumberOfDirectories(_image);
	TIFFGetField(_image, TIFFTAG_IMAGEWIDTH, &_width);
	TIFFGetField(_image, TIFFTAG_IMAGELENGTH, &_height);
	TIFFGetField(_image, TIFFTAG_SAMPLESPERPIXEL, &_samplesperpixel);
	TIFFGetField(_image, TIFFTAG_BITSPERSAMPLE, &_bitspersample);

	_bufferSize = _width * _height;
	// Allocate the memory
	if((_buffer = (unsigned int *) _TIFFmalloc(_bufferSize * sizeof (unsigned int))) == NULL){
		__android_log_print(ANDROID_LOG_INFO, "nativeTiffOpen", "Could not allocate enough memory for the uncompressed image");
		return -1;
	}

	return 0;
}

jintArray
Java_com_anthonymandra_dcraw_TiffDecoder_nativeTiffGetBytes( JNIEnv* env )
{
	int stripCount = 0;
	unsigned long imageOffset = 0;
	unsigned long result = 0;
	uint16 photo = 0;
	uint16 fillorder = 0;
	char tempbyte = 0;
	unsigned long count = 0;

	// Get the RGBA Image
	//TIFFReadRGBAImage(image, width, height, buffer, 0);
	TIFFReadRGBAImageOriented(_image, _width, _height, _buffer, ORIENTATION_TOPLEFT, 0);

	// Convert ABGR to ARGB
	int i = 0;
	int j = 0;
	int tmp = 0;
	for( i = 0; i < _height; i++ )
	    for( j=0; j< _width; j++ )
	    {
		tmp = _buffer[ j + _width * i ];
		_buffer[ j + _width * i ] = (tmp & 0xff000000) | ((tmp & 0x00ff0000)>>16) | (tmp & 0x0000ff00 ) | ((tmp & 0xff)<<16);
	    }
	// Deal with photometric interpretations
	if(TIFFGetField(_image, TIFFTAG_PHOTOMETRIC, &photo) == 0){
			__android_log_print(ANDROID_LOG_INFO, "nativeTiffGetBytes", "Image has an undefined photometric interpretation");
		//;
	}
 
	/*
	if(photo != PHOTOMETRIC_MINISWHITE){
    // Flip bits
    	__android_log_print(ANDROID_LOG_INFO, "nativeTiffGetBytes", "Fixing the photometric interpretation");

    for(count = 0; count < bufferSize; count++)
      buffer[count] = ~buffer[count];
	}
	*/

	// Deal with fillorder
	if(TIFFGetField(_image, TIFFTAG_FILLORDER, &fillorder) == 0){
    __android_log_print(ANDROID_LOG_INFO, "nativeTiffGetBytes", "Image has an undefined fillorder");
    //exit(42);
	}
	
	/*
	if(fillorder != FILLORDER_MSB2LSB){
    // We need to swap bits -- ABCDEFGH becomes HGFEDCBA
    __android_log_print(ANDROID_LOG_INFO, "nativeTiffGetBytes", "Fixing the fillorder");

	for(count = 0; count < bufferSize; count++){
		tempbyte = 0;
		if(buffer[count] & 128) tempbyte += 1;
		if(buffer[count] & 64) tempbyte += 2;
		if(buffer[count] & 32) tempbyte += 4;
		if(buffer[count] & 16) tempbyte += 8;
		if(buffer[count] & 8) tempbyte += 16;
		if(buffer[count] & 4) tempbyte += 32;
		if(buffer[count] & 2) tempbyte += 64;
		if(buffer[count] & 1) tempbyte += 128;
		buffer[count] = tempbyte;
	}
	}
	*/

	jintArray array = (*env)->NewIntArray( env, _bufferSize );
	if (!array) {
			__android_log_print(ANDROID_LOG_INFO, "nativeTiffGetBytes", "OutOfMemoryError is thrown.");
	}else{
			jint* bytes = (*env)->GetIntArrayElements( env, array, NULL );
			if (bytes != NULL) {
				memcpy(bytes, _buffer, _bufferSize * sizeof (unsigned int));
				(*env)->ReleaseIntArrayElements( env, array, bytes, 0 );
			}
	}
	return array;
}

jint
Java_com_anthonymandra_dcraw_TiffDecoder_nativeTiffGetLength( JNIEnv* env )
{
    return _bufferSize;
}

jint
Java_com_anthonymandra_dcraw_TiffDecoder_nativeTiffGetWidth( JNIEnv* env )
{
    return _width;
}

jint
Java_com_anthonymandra_dcraw_TiffDecoder_nativeTiffGetHeight( JNIEnv* env )
{
    return _height;
}

void
Java_com_anthonymandra_dcraw_TiffDecoder_nativeTiffClose( JNIEnv* env )
{
    if(_image)
    {
	TIFFClose(_image);
	_image = NULL;
    }
    if(_buffer)
    {
	 _TIFFfree(_buffer);
	_buffer = NULL;
    }
}
