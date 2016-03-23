#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <vector>
#include <dirent.h>

#include <libraw/libraw.h>
#include "libraw_descriptor_stream.h"

extern "C"
{
	#include <jpeglib.h>
	// Can decode
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_canDecodeFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_canDecodeFromFile(JNIEnv* env, jclass clazz, jstring filePath);
	JNIEXPORT jobjectArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_canDecodeDirectory(JNIEnv* env, jclass clazz, jstring directory);

	// Return thumb bitmap
    JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getThumbBuffer
        (JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jobjectArray exif, int quality, jobject config, jobject compressFormat);
    JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getThumbFile
        (JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat);
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getThumbFd
		(JNIEnv* env, jclass clazz, int source, jobjectArray exif, int quality, jobject config, jobject compressFormat);
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getThumbFileWatermark
	    (JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);

	// Write thumb (native format)
    JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeThumbBuffer
        (JNIEnv* env, jclass clazz, jbyteArray bufferBytes, int quality, jobject config, jobject compressFormat, int destination);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeThumbFile
	    (JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat, int destination);
    JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeThumbFd
        (JNIEnv* env, jclass clazz, int source, int quality, jobject config, jobject compressFormat, int destination);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeThumbFileWatermark
	    (JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat, int destination, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeThumbFdWatermark
	    (JNIEnv* env, jclass clazz, int source, int quality, jobject config, jobject compressFormat, int destination, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);

	// Return raw bitmap
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getImageFile
	    (JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat);
    JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getImageFileWatermark
        (JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getHalfImageFile
	    (JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat);

	JNIEXPORT jobject JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getHalfDecoder
	    (JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat);
	JNIEXPORT jobject JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getRawFromBuffer
	    (JNIEnv* env, jclass clazz, jbyteArray bufferBytes, int quality, jobject config, jobject compressFormat);
	JNIEXPORT jobject JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getDecoderFromFile
	    (JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat);

	// Write raw tiff
	//TODO: These must use the Document API, perhaps use a fileDescriptor, convert to mem, then copy write logic to the FILE*
//	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination);
//	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeRawFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination);
};

void setExif(JNIEnv* env, libraw_data_t imgdata, jobjectArray exif);
jintArray getColors(JNIEnv* env, libraw_processed_image_t *raw);
jobject createBitmapRegionDecoder(JNIEnv* env, libraw_processed_image_t *raw, int quality, jobject config, jobject compressFormat);
jbyteArray createBitmap(JNIEnv* env, libraw_processed_image_t *raw, int quality, jobject config, jobject compressFormat);
void createJpeg(JNIEnv* env, unsigned char** outJpeg, unsigned long* outJpegSize, unsigned char* data, int width, int height, int quality);
unsigned char* readJpeg(JNIEnv* env, libraw_processed_image_t *raw, int width, int height);
unsigned char clamp(unsigned char a, unsigned char b);

jboolean getEmbedded(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);
jboolean getThumbnail(JNIEnv* env, unsigned char** jpeg, unsigned long int* jpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);
jbyteArray getThumbnail(JNIEnv* env, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat);
jbyteArray getThumbnail(JNIEnv* env, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);

jboolean insertWatermark(JNIEnv* env, unsigned char* pixels, int imageWidth, int imageHeight, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);

jbyteArray getRaw(JNIEnv* env, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat);
jbyteArray getRaw(JNIEnv* env, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);
jboolean getRawImage(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);
jboolean getHalfRawImage(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat);
jboolean getHalfRawImage(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight);

jobject getHalfRawDecoder(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat);
jobject getRawDecoder(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat);

jboolean writeRaw(JNIEnv* env, LibRaw* rawProcessor, const char* destination);
jboolean writeThumb(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat, int destination);
jboolean writeThumb(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight, int destination);

jboolean openFile(JNIEnv* env, jstring filePath, LibRaw* rawProcessor);
jboolean openBuffer(JNIEnv* env, jbyteArray bufferBytes, LibRaw* rawProcessor);

typedef struct ID_CACHE
{
    int cached;

	// Classes are local references
	// http://android-developers.blogspot.kr/2011/11/jni-local-reference-changes-in-ics.html
    jclass bitmap;
    jclass bitmapRegionDecoder;
    jclass byteArrayOutputStream;
    jclass byteArrayInputStream;

    jmethodID bitmap_createBitmap;
    jmethodID bitmap_compress;
    jmethodID bitmapRegionDecoder_newInstance;
    jmethodID byteArrayOutputStream_init;
    jmethodID byteArrayInputStream_init;
    jmethodID byteArrayOutputStream_toByteArray;
} ID_CACHE;

ID_CACHE JNI_IDS;

void cacheJniIds(JNIEnv *env)
{
    if (JNI_IDS.cached) return;

    // Class declarations are local, must be made global
    JNI_IDS.bitmap = (jclass)env->NewGlobalRef(env->FindClass("android/graphics/Bitmap"));
    JNI_IDS.bitmapRegionDecoder = (jclass)env->NewGlobalRef(env->FindClass("android/graphics/BitmapRegionDecoder"));
    JNI_IDS.byteArrayInputStream = (jclass)env->NewGlobalRef(env->FindClass("java/io/ByteArrayInputStream"));
    JNI_IDS.byteArrayOutputStream = (jclass)env->NewGlobalRef(env->FindClass("java/io/ByteArrayOutputStream"));

    JNI_IDS.bitmap_createBitmap = env->GetStaticMethodID(JNI_IDS.bitmap, "createBitmap", "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    JNI_IDS.bitmap_compress = env->GetMethodID(JNI_IDS.bitmap, "compress", "(Landroid/graphics/Bitmap$CompressFormat;ILjava/io/OutputStream;)Z");

    JNI_IDS.bitmapRegionDecoder_newInstance = env->GetStaticMethodID(JNI_IDS.bitmapRegionDecoder, "newInstance", "([BIIZ)Landroid/graphics/BitmapRegionDecoder;");

    JNI_IDS.byteArrayInputStream = env->FindClass("java/io/ByteArrayInputStream");
    JNI_IDS.byteArrayInputStream_init = env->GetMethodID(JNI_IDS.byteArrayInputStream, "<init>", "([B)V");

    JNI_IDS.byteArrayOutputStream_init = env->GetMethodID(JNI_IDS.byteArrayOutputStream, "<init>", "()V");
    JNI_IDS.byteArrayOutputStream_toByteArray = env->GetMethodID(JNI_IDS.byteArrayOutputStream, "toByteArray", "()[B");

    JNI_IDS.cached = 1;
}



JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_canDecodeFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes)
{
	LibRaw RawProcessor;

	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);
	
	int result = RawProcessor.open_buffer(buffer, len);
	env->ReleaseByteArrayElements(bufferBytes, buffer, JNI_ABORT);
	RawProcessor.recycle();

	if (result == 0)
		return JNI_TRUE;
	
	return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_canDecodeFromFile(JNIEnv* env, jclass clazz, jstring filePath)
{
	LibRaw RawProcessor;
	
	const char *str= env->GetStringUTFChars(filePath,0);
	int result = RawProcessor.open_file(str, 0);
	env->ReleaseStringUTFChars(filePath, str);
	RawProcessor.recycle();

	if (result == 0)
		return JNI_TRUE;
	
	return JNI_FALSE;
}

using namespace std;
// This processes a whole folder but didn't prove to be a significant improvement.  1000 files (~15s individual vs. 14.4 here)
JNIEXPORT jobjectArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_canDecodeDirectory(JNIEnv* env, jclass clazz, jstring directory)
{
	LibRaw RawProcessor;
	DIR *dir;
	struct dirent *ent;
	const char *str = env->GetStringUTFChars(directory,0);
	string path = string(str);
	env->ReleaseStringUTFChars(directory, str);

    int numRaw = 0;
    vector<string> rawFiles = vector<string>();
    int result;
	if ((dir = opendir (str)) != NULL)
	{
		while ((ent = readdir (dir)) != NULL)
		{
			string p = path + "/" + ent->d_name;
//			__android_log_print(ANDROID_LOG_INFO, "JNI", "file = %s", p.c_str());
			result = RawProcessor.open_file(p.c_str());
			if (result == 0)
				rawFiles.push_back(p);
			RawProcessor.recycle();
		}
		closedir (dir);
	}
	else
	{
		return NULL;
	}

	jobjectArray raw = (jobjectArray)env->NewObjectArray(rawFiles.size(), env->FindClass("java/lang/String"), env->NewStringUTF(""));
	for(int i = 0; i < rawFiles.size(); i++)
	{
//		__android_log_print(ANDROID_LOG_INFO, "JNI", "raw = %s", rawFiles[i].c_str());
		jstring r = env->NewStringUTF(rawFiles[i].c_str());
		env->SetObjectArrayElement(raw, i, r);
		env->DeleteLocalRef(r);
	}
	return raw;
}

/***************************************************************************************************************************************************************************************************************
/*      START THUMB GET
/**************************************************************************************************************************************************************************************************************/

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getThumbBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();
	if(!openBuffer(env, bufferBytes, rawProcessor))
	    return NULL;

	return getThumbnail(env, rawProcessor, exif, quality, config, compressFormat);
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getThumbFd(JNIEnv* env, jclass clazz, int source, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();
	LibRaw_descriptor_datastream *stream = new LibRaw_descriptor_datastream(source);
	int result = rawProcessor->open_datastream(stream);

	if (result != LIBRAW_SUCCESS)
		return NULL;

	jbyteArray image = getThumbnail(env, rawProcessor, exif, quality, config, compressFormat);
	free(stream);
	return image;
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getThumbFile(JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
    LibRaw* rawProcessor = new LibRaw();
    if (!openFile(env, filePath, rawProcessor))
        return NULL;

    return getThumbnail(env, rawProcessor, exif, quality, config, compressFormat);
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getThumbFileWatermark(JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
    LibRaw* rawProcessor = new LibRaw();
    if (!openFile(env, filePath, rawProcessor))
        return NULL;

    return getThumbnail(env, rawProcessor, exif, quality, config, compressFormat, watermark, margins, waterWidth, waterHeight);
}

jbyteArray getThumbnail(JNIEnv* env, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
    return getThumbnail(env, rawProcessor, exif, quality, config, compressFormat, NULL, NULL, 0, 0);
}

jbyteArray getThumbnail(JNIEnv* env, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
    unsigned char* jpeg = NULL;
    unsigned long jpegSize = 0;

    jboolean success = getThumbnail(env, &jpeg, &jpegSize, rawProcessor, exif, quality, config, compressFormat, watermark, margins, waterWidth, waterHeight);

    if (!success)
        return NULL;

    jbyteArray thumb = env->NewByteArray(jpegSize);
    env->SetByteArrayRegion(thumb, 0, jpegSize, (jbyte *) jpeg);
    if (jpeg)
        free(jpeg);

    rawProcessor->recycle();
    free(rawProcessor);
    return thumb;
}

jboolean getThumbnail(JNIEnv* env, unsigned char** jpeg, unsigned long int* jpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
    if (rawProcessor->imgdata.thumbnail.tlength)    // If there's an embedded thumbnail
        return getEmbedded(env, jpeg, jpegSize, rawProcessor, exif, quality, config, compressFormat, watermark, margins, waterWidth, waterHeight);
    else                                            // otherwise do a half-res decode
        return getHalfRawImage(env, jpeg, jpegSize, rawProcessor, exif, quality, config, compressFormat, watermark, margins, waterWidth, waterHeight);
}

jboolean getEmbedded(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
	int result;

	if (exif)
		setExif(env, rawProcessor->imgdata, exif);

	result = rawProcessor->unpack_thumb();
	if (result != LIBRAW_SUCCESS)
	{
		__android_log_write(ANDROID_LOG_INFO, "JNI", libraw_strerror(result));
		return JNI_FALSE;
	}

	libraw_processed_image_t *image = rawProcessor->dcraw_make_mem_thumb(&result);
	if (result != LIBRAW_SUCCESS)
	{
		__android_log_write(ANDROID_LOG_INFO, "JNI", libraw_strerror(result));
		return JNI_FALSE;
	}

    if (watermark)
    {
        unsigned char* pixels;
        int width = rawProcessor->imgdata.thumbnail.twidth;
        int height = rawProcessor->imgdata.thumbnail.theight;

        bool isJpeg = image->type == LIBRAW_IMAGE_JPEG;
        if (isJpeg) //jpeg must convert to bitmap
        {
            pixels = readJpeg(env, image, width, height);
        }
        else        //already bitmap
        {
            pixels = image->data;
        }
        bool watermarkSuccess = insertWatermark(env, pixels, width, height, watermark, margins, waterWidth, waterHeight);
        createJpeg(env, outJpeg, outJpegSize, pixels, width, height, quality);

        if (isJpeg && pixels)   // free the pixels if WE created them (libraw handles internal)
            free(pixels);
    }
    else if (image->type == LIBRAW_IMAGE_BITMAP)    // no watermark, bitmap thumb, must convert to jpeg
    {
        createJpeg(env, outJpeg, outJpegSize, image->data, image->width, image->height, quality);
    }
    else                                            // thumb is already a bitmap, just copy
    {
        *outJpeg = new unsigned char[image->data_size];//(unsigned char *) malloc(image->data_size);
        *outJpegSize = image->data_size;
        memcpy(*outJpeg, image->data, *outJpegSize);
    }

//	FILE *orig = fopen("/sdcard/original.jpg","wb");
//	FILE *copy = fopen("/sdcard/copy.jpg","wb");
//	fwrite(image->data, sizeof(char), image->data_size, orig);
//	fwrite(*outJpeg, sizeof(char), *outJpegSize, copy);
//	fclose(orig);
//	fclose(copy);

	rawProcessor->dcraw_clear_mem(image);
	return JNI_TRUE;
}

/***************************************************************************************************************************************************************************************************************
/*      END THUMB GET
/**************************************************************************************************************************************************************************************************************/


/***************************************************************************************************************************************************************************************************************
/*      START FULL RAW GET
/**************************************************************************************************************************************************************************************************************/

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getHalfImageFile(JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();
    if (!openFile(env, filePath, rawProcessor))
        return NULL;

    unsigned char* jpeg = NULL;
    unsigned long jpegSize = 0;

	if(!getHalfRawImage(env, &jpeg, &jpegSize, rawProcessor, exif, quality, config, compressFormat))
	    return NULL;

    jbyteArray image = env->NewByteArray(jpegSize);
    env->SetByteArrayRegion(image, 0, jpegSize, (jbyte *) jpeg);
    if (jpeg)
        free(jpeg);

	rawProcessor->recycle();
	free(rawProcessor);

	return image;
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getImageFile(JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
    getRaw(env, filePath, exif, quality, config, compressFormat);
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getImageFileWatermark(JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
    getRaw(env, filePath, exif, quality, config, compressFormat, watermark, margins, waterWidth, waterHeight);
}

jbyteArray getRaw(JNIEnv* env, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
    return getRaw(env, filePath, exif, quality, config, compressFormat, NULL, NULL, 0, 0);
}

jbyteArray getRaw(JNIEnv* env, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
    LibRaw* rawProcessor = new LibRaw();
    unsigned char* jpeg = NULL;
    unsigned long jpegSize = 0;

    rawProcessor->imgdata.params.use_rawspeed = 1;
    const char *str= env->GetStringUTFChars(filePath,0);
    int result = rawProcessor->open_file(str);
    env->ReleaseStringUTFChars(filePath, str);
    if (result != LIBRAW_SUCCESS)
        return NULL;

    jboolean success = getRawImage(env, &jpeg, &jpegSize, rawProcessor, exif, quality, config, compressFormat, watermark, margins, waterWidth, waterHeight);
    if (!success)
        return NULL;

    jbyteArray image = env->NewByteArray(jpegSize);
    env->SetByteArrayRegion(image, 0, jpegSize, (jbyte *) jpeg);
    if (jpeg)
        free(jpeg);

    rawProcessor->recycle();
    free(rawProcessor);

    return image;
}

jboolean getRawImage(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
	if (exif)
		setExif(env, rawProcessor->imgdata, exif);

	int result = rawProcessor->unpack();
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	//TODO: What is the cost of process?
	result = rawProcessor->dcraw_process();
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	if (rawProcessor->imgdata.process_warnings & LIBRAW_WARN_RAWSPEED_PROCESSED)
		__android_log_print(ANDROID_LOG_INFO, "JNI", "process = %d", result);
	libraw_processed_image_t *image = rawProcessor->dcraw_make_mem_image(&result);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

    if (watermark)
    {
        // Always a bitmap, to save memory we edit the libraw copy directly, this could be dangerous, but doesn't affect current code.
        bool watermarkSuccess = insertWatermark(env, image->data, image->width, image->height, watermark, margins, waterWidth, waterHeight);
    }

	createJpeg(env, outJpeg, outJpegSize, image->data, image->width, image->height, quality);

	rawProcessor->dcraw_clear_mem(image);
    return JNI_TRUE;
}

jboolean getHalfRawImage(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
	rawProcessor->imgdata.params.half_size = 1;

	return getRawImage(env, outJpeg, outJpegSize, rawProcessor, exif, quality, config, compressFormat, NULL, NULL, 0, 0);
}

jboolean getHalfRawImage(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
	rawProcessor->imgdata.params.half_size = 1;

	return getRawImage(env, outJpeg, outJpegSize, rawProcessor, exif, quality, config, compressFormat, watermark, margins, waterWidth, waterHeight);
}

/***************************************************************************************************************************************************************************************************************
/*      END FULL RAW GET
/**************************************************************************************************************************************************************************************************************/


JNIEXPORT jobject JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getHalfDecoder(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jobject regionDecoder = getHalfRawDecoder(env, rawProcessor, quality, config, compressFormat);
	rawProcessor->recycle();
	free(rawProcessor);
	return regionDecoder;
}

JNIEXPORT jobject JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getDecoder(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jobject regionDecoder = getRawDecoder(env, rawProcessor, quality, config, compressFormat);
	rawProcessor->recycle();

	return regionDecoder;
}

JNIEXPORT jobject JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_getRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();
	if (!openBuffer(env, bufferBytes, rawProcessor))
	    return NULL;

	jobject regionDecoder = getRawDecoder(env, rawProcessor, quality, config, compressFormat);
	rawProcessor->recycle();

	return regionDecoder;
}

/***************************************************************************************************************************************************************************************************************
/*      START THUMB WRITE
/**************************************************************************************************************************************************************************************************************/

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeThumbFile
    (JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat, int destination)
{
    LibRaw* rawProcessor = new LibRaw();
    if (!openFile(env, filePath, rawProcessor))
        return JNI_FALSE;

    return writeThumb(env, rawProcessor, quality, config, compressFormat, destination);
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeThumbFd
    (JNIEnv* env, jclass clazz, int source, int quality, jobject config, jobject compressFormat, int destination)
{
	LibRaw* rawProcessor = new LibRaw();
	LibRaw_descriptor_datastream *stream = new LibRaw_descriptor_datastream(source);
	int result = rawProcessor->open_datastream(stream);

	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeThumb(env, rawProcessor, quality, config, compressFormat, destination);
	free(stream);
	return success;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeThumbFileWatermark
    (JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat, int destination, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
    LibRaw* rawProcessor = new LibRaw();
    if (!openFile(env, filePath, rawProcessor))
        return JNI_FALSE;

    return writeThumb(env, rawProcessor, quality, config, compressFormat, watermark, margins, waterWidth, waterHeight, destination);
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeThumbFdWatermark
    (JNIEnv* env, jclass clazz, int source, int quality, jobject config, jobject compressFormat, int destination, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
	LibRaw* rawProcessor = new LibRaw();
	LibRaw_descriptor_datastream *stream = new LibRaw_descriptor_datastream(source);
	int result = rawProcessor->open_datastream(stream);

	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeThumb(env, rawProcessor, quality, config, compressFormat, watermark, margins, waterWidth, waterHeight, destination);
	free(stream);
	return success;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeThumbBuffer
    (JNIEnv* env, jclass clazz, jbyteArray bufferBytes, int quality, jobject config, jobject compressFormat, int destination)
{
	LibRaw* rawProcessor = new LibRaw();
	if (!openBuffer(env, bufferBytes, rawProcessor))
	    return JNI_FALSE;

	return writeThumb(env, rawProcessor, quality, config, compressFormat, destination);
}

jboolean writeThumb(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat, int destination)
{
	return writeThumb(env, rawProcessor, quality, config, compressFormat, NULL, NULL, 0, 0, destination);
}

jboolean writeThumb(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight, int destination)
{
  	unsigned char* jpeg = NULL;
	unsigned long jpegSize = 0;

	// TODO: Add exif to jpeg (libexif) http://stackoverflow.com/questions/17019476/libexif-writing-new-exif-into-image-with-iptc-xmp
	jboolean success = getThumbnail(env, &jpeg, &jpegSize, rawProcessor, NULL, quality, config, compressFormat, watermark, margins, waterWidth, waterHeight);

	if(!destination)
		return JNI_FALSE;

	FILE* tfp = fdopen(destination, "wb");
//    const char *output = env->GetStringUTFChars(destination, 0);
//	FILE* tfp = fopen(output,"wb");
//    env->ReleaseStringUTFChars(destination, output);

	if(!tfp)
		return JNI_FALSE;

	if(!jpeg)
	{
		fclose(tfp);
		return JNI_FALSE;
	}

	try
	{
		fwrite(jpeg, sizeof(char), jpegSize, tfp);
		fclose(tfp);
	}
	catch(...)
	{
		fclose(tfp);
	}

    if (jpeg)
        free(jpeg);

	rawProcessor->recycle();
	free(rawProcessor);

	return JNI_TRUE;
}

/***************************************************************************************************************************************************************************************************************
/*      END THUMB WRITE
/**************************************************************************************************************************************************************************************************************/

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeRaw(JNIEnv* env, jclass clazz, jstring filePath, jstring destination)
{
	LibRaw* rawProcessor = new LibRaw();
	const char *output = env->GetStringUTFChars(destination, 0);

	const char *input= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(input);
	env->ReleaseStringUTFChars(filePath, input);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeRaw(env, rawProcessor, output);
	env->ReleaseStringUTFChars(destination, output);

	rawProcessor->recycle();
	return success;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_rawprocessor_LibRaw_writeRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination)
{
	LibRaw* rawProcessor = new LibRaw();
	const char *output = env->GetStringUTFChars(destination, 0);

	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);
	int result = rawProcessor->open_buffer(buffer, len);
	env->ReleaseByteArrayElements(bufferBytes, buffer, JNI_ABORT);

	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeRaw(env, rawProcessor, output);
	env->ReleaseStringUTFChars(destination, output);

	rawProcessor->recycle();
	return success;
}

//Ensure the incoming pixels are a bitmap!
jboolean insertWatermark(JNIEnv* env, unsigned char* pixels, int imageWidth, int imageHeight, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight)
{
	int* margin = env->GetIntArrayElements(margins, 0);
	int top = margin[0];
	int left = margin[1];
	int bottom = margin[2];
	int right = margin[3];
	env->ReleaseIntArrayElements(margins, margin, JNI_ABORT);

	int startX, startY;
	int result;

    if (waterWidth > imageWidth || waterHeight > imageHeight)
    {
        __android_log_print(ANDROID_LOG_INFO, "JNI", "Watermark Out of Bounds x = %d -> %d , y = %d -> %d",
                waterWidth, imageWidth, waterHeight, imageHeight);
        return JNI_FALSE;
    }
    if ((top > 0 && bottom > 0))
        return JNI_FALSE;
    if ((right > 0 && left > 0))
        return JNI_FALSE;

    if (left >= 0)
    {
        startX = left;
    }
    else if (right >= 0)
    {
        startX = imageWidth - right - waterWidth;
    }
    else
    {
        startX = imageWidth / 2 - waterWidth / 2;	//center
    }
    if (startX < 0 || startX + waterWidth > imageWidth)
    {
        startX = 0;
    }

    if (top >= 0)
    {
        startY = top;
    }
    else if (bottom >= 0)
    {
        startY = imageHeight - bottom - waterHeight;
    }
    else
    {
        startY = imageHeight / 2 - waterHeight / 2;	//center
    }
    if (startY < 0 || startY + waterHeight > imageHeight)
    {
        startY = 0;
    }

    jsize len = env->GetArrayLength(watermark);
    unsigned char* watermarkPixels = (unsigned char*)env->GetByteArrayElements(watermark, 0);

    int delta = (startY * imageWidth + startX) * 3;
    int rowStride = imageWidth * 3;
    int waterStride = waterWidth * 3;
    int i = 0;
    for (int y = 0; y < waterHeight; y++)
    {
        int x = 0;
        while(x < waterStride)
        {
            pixels[delta + x] = clamp(pixels[delta + x], watermarkPixels[i++] >> 1); ++x; //r
            pixels[delta + x] = clamp(pixels[delta + x], watermarkPixels[i++] >> 1); ++x; //g
            pixels[delta + x] = clamp(pixels[delta + x], watermarkPixels[i++] >> 1); ++x; //b
            i++; //a
        }
        delta += rowStride;
    }

    env->ReleaseByteArrayElements(watermark, (jbyte *)watermarkPixels, JNI_ABORT);

    return JNI_TRUE;
}

unsigned char clamp(unsigned char a, unsigned char b)
{
	int result = a + b;
	return result < 255 ? result : 255;
}

jobject getRawDecoder(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat)
{
	int result = rawProcessor->unpack();
	if (result != LIBRAW_SUCCESS)
		return NULL;

	//TODO: What is the cost of process?
	result = rawProcessor->dcraw_process();
	if (result != LIBRAW_SUCCESS)
		return NULL;

	libraw_processed_image_t *image = rawProcessor->dcraw_make_mem_image(&result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jobject regionDecoder = createBitmapRegionDecoder(env, image, quality, config, compressFormat);

	rawProcessor->dcraw_clear_mem(image);
	return regionDecoder;
}

jobject getHalfRawDecoder(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat)
{
	rawProcessor->imgdata.params.half_size = 1;

	return getRawDecoder(env, rawProcessor, quality, config, compressFormat);
}

jboolean writeRaw(JNIEnv* env, LibRaw* rawProcessor, const char* destination)
{
	// Set the image to be processed as tiff
	rawProcessor->imgdata.params.output_tiff = 0;
	rawProcessor->imgdata.params.use_camera_wb = 1;
	int result = rawProcessor->dcraw_process();
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	result = rawProcessor->dcraw_ppm_tiff_writer(destination);

	return result == LIBRAW_SUCCESS;
}

// Converts an rgb bitmap of width and height to an int array of colors
jintArray getColors(JNIEnv* env, libraw_processed_image_t *raw)
{
	int color = 0;
	int dimensions = raw->height * raw->width;
	int *colors = new int[dimensions];

	for (int y = 0; y < raw->height; y++)
	{
		for (int x = 0; x < raw->width; x++)
		{
			int r = raw->data[color++];
			int g = raw->data[color++];
			int b = raw->data[color++];
			colors[y * raw->width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
		}
	}

	jintArray colorArray = env->NewIntArray(dimensions);
	env->SetIntArrayRegion(colorArray, 0, dimensions, colors);
	delete[] colors;
	return colorArray;
}

jbyteArray createBitmap(JNIEnv* env, libraw_processed_image_t *raw, int quality, jobject config, jobject compressFormat)
{
	if (!JNI_IDS.cached) cacheJniIds(env);

	jintArray colors = getColors(env, raw);

	jobject bitmap = env->CallStaticObjectMethod(JNI_IDS.bitmap, JNI_IDS.bitmap_createBitmap, colors, raw->width, raw->height, config);
	jobject byteArrayOutputStream = env->NewObject(JNI_IDS.byteArrayOutputStream, JNI_IDS.byteArrayOutputStream_init);
	jboolean worked = env->CallBooleanMethod(bitmap, JNI_IDS.bitmap_compress, compressFormat, quality, byteArrayOutputStream);
	return (jbyteArray) env->CallObjectMethod(byteArrayOutputStream, JNI_IDS.byteArrayOutputStream_toByteArray);
}

unsigned char* readJpeg(JNIEnv* env, libraw_processed_image_t *raw, int width, int height)
{
	// http://sourceforge.net/p/libjpeg-turbo/code/HEAD/tree/trunk/example.c#l109
	// http://stackoverflow.com/questions/5616216/need-help-in-reading-jpeg-file-using-libjpeg

	struct jpeg_decompress_struct cinfo;
	struct jpeg_error_mgr jerr;
	int row_stride;		/* physical row width in output buffer */

	cinfo.err = jpeg_std_error(&jerr);
	/* Now we can initialize the JPEG decompression object. */
	jpeg_create_decompress(&cinfo);

	/* Step 2: specify data source (eg, a file) */
	jpeg_mem_src(&cinfo, raw->data, raw->data_size);
	/* Step 3: read file parameters with jpeg_read_header() */
	(void) jpeg_read_header(&cinfo, TRUE);

	/* Step 4: set parameters for decompression */

	/* In this example, we don't need to change any of the defaults set by
	* jpeg_read_header(), so we do nothing here.
	*/

	/* Step 5: Start decompressor */
	(void) jpeg_start_decompress(&cinfo);
	/* We can ignore the return value since suspension is not possible
	* with the stdio data source.
	*/

	/* We may need to do some setup of our own at this point before reading
	* the data.  After jpeg_start_decompress() we have the correct scaled
	* output image dimensions available, as well as the output colormap
	* if we asked for color quantization.
	* In this example, we need to make an output work buffer of the right size.
	*/
	/* JSAMPLEs per row in output buffer */
	//AJM: cinfo has incorrect width/height information
//	row_stride = cinfo.output_width * cinfo.output_components;
	row_stride = width * cinfo.output_components;
	JSAMPROW rowData;
	unsigned char* imageData = new unsigned char[height * row_stride];
	/* Step 6: while (scan lines remain to be read) */
	/*           jpeg_read_scanlines(...); */

	/* Here we use the library's state variable cinfo.output_scanline as the
	* loop counter, so that we don't have to keep track ourselves.
	*/
	int row = 0;
	while (cinfo.output_scanline < cinfo.output_height)
	{
		rowData = imageData + (row * row_stride);
		jpeg_read_scanlines(&cinfo, &rowData, 1);
		++row;
	}

	/* Step 7: Finish decompression */
	(void) jpeg_finish_decompress(&cinfo);
	/* We can ignore the return value since suspension is not possible
	* with the stdio data source.
	*/

	/* Step 8: Release JPEG decompression object */
	/* This is an important step since it will release a good deal of memory. */
	jpeg_destroy_decompress(&cinfo);
	/* At this point you may want to check to see whether any corrupt-data
	* warnings occurred (test whether jerr.pub.num_warnings is nonzero).
	*/

	/* And we're done! */
	return imageData;
}

//TODO: return a value
void createJpeg(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, unsigned char data[], int width, int height, int quality)
{
	struct jpeg_compress_struct cinfo;
	struct jpeg_error_mgr jerr;

	JSAMPROW row_pointer[1];	/* pointer to JSAMPLE row[s] */
	int row_stride;				/* physical row width in image buffer */

	cinfo.err = jpeg_std_error(&jerr);
	jpeg_create_compress(&cinfo);

//	unsigned char* mem = NULL;
//	unsigned long mem_size = 0;
	jpeg_mem_dest(&cinfo, outJpeg, outJpegSize);

	cinfo.image_width = width; 	/* image width and height, in pixels */
	cinfo.image_height = height;
	cinfo.input_components = 3;			/* # of color components per pixel */
	cinfo.in_color_space = JCS_RGB; 	/* colorspace of input image */

	/* Now use the library's routine to set default compression parameters.
	* (You must set at least cinfo.in_color_space before calling this,
	* since the defaults depend on the source color space.)
	*/
	jpeg_set_defaults(&cinfo);
	/* Now you can set any non-default parameters you wish to.
	* Here we just illustrate the use of quality (quantization table) scaling:
	*/
	jpeg_set_quality(&cinfo, quality, TRUE /* limit to baseline-JPEG values */);

	/* Step 4: Start compressor */

	/* TRUE ensures that we will write a complete interchange-JPEG file.
	* Pass TRUE unless you are very sure of what you're doing.
	*/
	jpeg_start_compress(&cinfo, TRUE);

	/* Step 5: while (scan lines remain to be written) */
	/*           jpeg_write_scanlines(...); */

	/* Here we use the library's state variable cinfo.next_scanline as the
	* loop counter, so that we don't have to keep track ourselves.
	* To keep things simple, we pass one scanline per call; you can pass
	* more if you wish, though.
	*/
//	row_stride = cinfo.image_width * cinfo.in_color_space;	/* JSAMPLEs per row in image_buffer */
	row_stride = cinfo.image_width * 3;
	while (cinfo.next_scanline < cinfo.image_height)
	{
		/* jpeg_write_scanlines expects an array of pointers to scanlines.
		 * Here the array is only one element long, but you could pass
		 * more than one scanline at a time if that's more convenient.
		 */
		row_pointer[0] = & data[cinfo.next_scanline * row_stride];
		(void) jpeg_write_scanlines(&cinfo, row_pointer, 1);
	}

	/* Step 6: Finish compression */

	jpeg_finish_compress(&cinfo);

	/* Step 7: release JPEG compression object */

	/* This is an important step since it will release a good deal of memory. */
	jpeg_destroy_compress(&cinfo);

//	return mem;
}

jobject createBitmapRegionDecoder(JNIEnv* env, libraw_processed_image_t *raw, int quality, jobject config, jobject compressFormat)
{
	if (!JNI_IDS.cached) cacheJniIds(env);

	unsigned char* jpeg;
	unsigned long jpegSize;
	createJpeg(env, &jpeg, &jpegSize, raw->data, raw->width, raw->height, quality);
	jbyteArray image = env->NewByteArray(jpegSize);
	env->SetByteArrayRegion(image, 0, jpegSize, (jbyte *) jpeg);
	if (jpeg)
		free(jpeg);

	jobject regionDecoder = env->CallObjectMethod(JNI_IDS.bitmapRegionDecoder, JNI_IDS.bitmapRegionDecoder_newInstance, image, 0, jpegSize, JNI_FALSE);
	return regionDecoder;
}

jboolean openFile(JNIEnv* env, jstring filePath, LibRaw* rawProcessor)
{
    const char *str= env->GetStringUTFChars(filePath,0);
    int result = rawProcessor->open_file(str);
    env->ReleaseStringUTFChars(filePath, str);
//    if (LIBRAW_FATAL_ERROR(result))
    if (result != LIBRAW_SUCCESS)
        return JNI_FALSE;

    return JNI_TRUE;
}

jboolean openStream(JNIEnv* env, int source, LibRaw* rawProcessor)
{
	LibRaw_descriptor_datastream *stream = new LibRaw_descriptor_datastream(source);
	int result = rawProcessor->open_datastream(stream);

	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	return JNI_TRUE;
}

jboolean openBuffer(JNIEnv* env, jbyteArray bufferBytes, LibRaw* rawProcessor)
{
	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);
	int result = rawProcessor->open_buffer(buffer, len);
	env->ReleaseByteArrayElements(bufferBytes, buffer, JNI_ABORT);
    //if (LIBRAW_FATAL_ERROR(result))
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

    return JNI_TRUE;
}

void setExif(JNIEnv* env, libraw_data_t imgdata, jobjectArray exif)
{
	jsize exifLength = env->GetArrayLength(exif);
	if (exifLength < 8)
		return;
	jstring make = env->NewStringUTF(imgdata.idata.make);
	env->SetObjectArrayElement(exif, 0, make);
	jstring model = env->NewStringUTF(imgdata.idata.model);
	env->SetObjectArrayElement(exif, 1, model);
	char value[20] = "";
	sprintf(value, "%.5f", imgdata.other.aperture);
	jstring apertureString = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 2, apertureString);
	sprintf(value, "%.5f", imgdata.other.focal_len);
	jstring focalString = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 3, focalString);
	sprintf(value, "%.0f", imgdata.other.iso_speed);
	jstring isoString = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 4, isoString);
	sprintf(value, "%.5f", imgdata.other.shutter);
	jstring shutterString = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 5, shutterString);
	jstring timestamp = env->NewStringUTF(ctime(&imgdata.other.timestamp));
	env->SetObjectArrayElement(exif, 6, timestamp);

	// Libraw 	0: no rotation; 3: 180 degrees; 6: 90 degrees; 5: 270 degrees;
	// XMP 		0: no rotation; 3: 180 degrees; 6: 90 degrees; 8: 270 degrees;
	jstring orientation;
	int flip = imgdata.sizes.flip;
	if (flip == 3)
		orientation = env->NewStringUTF("3");
	else if (flip == 5)
		orientation = env->NewStringUTF("8");
	else if (flip == 6)
		orientation = env->NewStringUTF("6");
	else
		orientation = env->NewStringUTF("0");
	env->SetObjectArrayElement(exif, 7, orientation);
	sprintf(value, "%d", imgdata.thumbnail.theight);
	jstring thumbHeight = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 8, thumbHeight);
	sprintf(value, "%d", imgdata.thumbnail.twidth);
	jstring thumbWidth = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 9, thumbWidth);
	sprintf(value, "%d", imgdata.sizes.height);
	jstring height = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 10, height);
	sprintf(value, "%d", imgdata.sizes.width);
	jstring width = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 11, width);
}
