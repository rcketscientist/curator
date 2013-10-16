#include <string.h>
#include <jni.h>

#include "libraw/libraw.h"

#include <android/log.h>

extern "C" 
{
	#include "jpeglib.h";
	// Can decode
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_canDecodeFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_canDecodeFromFile(JNIEnv* env, jclass clazz, jstring filePath);
	
	// Return thumb bitmap
    JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jobjectArray exif, int quality, jobject config, jobject compressFormat);
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat);

	// Write thumb (native format)
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination);

	// Return raw bitmap
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getHalfImageFromFile(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat);
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getImageFromFile(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat);
	JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getHalfDecoderFromFile(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat);
	JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, int quality, jobject config, jobject compressFormat);
	JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getDecoderFromFile(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat);

	// Write raw tiff
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeRawFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination);
};

void setExif(JNIEnv* env, libraw_data_t imgdata, jobjectArray exif);
jintArray getColors(JNIEnv* env, libraw_processed_image_t *raw);
jobject createBitmapRegionDecoder(JNIEnv* env, libraw_processed_image_t *raw, int quality, jobject config, jobject compressFormat);
jbyteArray createBitmap(JNIEnv* env, libraw_processed_image_t *raw, int quality, jobject config, jobject compressFormat);

jbyteArray getThumb(JNIEnv* env, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat);

jbyteArray getRawImage(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat);
jbyteArray getHalfRawImage(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat);

jobject getHalfRawDecoder(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat);
jobject getRawDecoder(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat);

jboolean writeRaw(JNIEnv* env, LibRaw* rawProcessor, jstring destination);
jboolean writeThumb(JNIEnv* env, LibRaw* rawProcessor, jstring destination);

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

//JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
//{
//    JNIEnv* env;
//    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
//        return -1;
//    }
//
//    // Get jclass with env->FindClass.
//    // Register methods with env->RegisterNatives.
//
//
////    jclass bitmapConfig = env->FindClass("android/graphics/Bitmap$Config");
////    jfieldID bitmapConfig_ARGB8888 = env->GetStaticFieldID(bitmapConfig, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
////    jmethodID bmpCfgValueOf = env->GetStaticMethodID(bitmapConfig, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
////    JNI_IDS.argb8888 = env->GetStaticObjectField(bitmapConfig, bitmapConfig_ARGB8888);
//
////    jclass bitmapCompress = env->FindClass("android/graphics/Bitmap$CompressFormat");
////    jmethodID midValueOf = env->GetStaticMethodID(bitmapConfig, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
////    jfieldID bitmapCompress_PNG = env->GetStaticFieldID(bitmapCompress, "PNG", "Landroid/graphics/Bitmap$CompressFormat;");
////    JNI_IDS.PNG = env->GetStaticObjectField(bitmapCompress, bitmapCompress_PNG);
//
////    jclass bitmapCompress = env->FindClass("android/graphics/Bitmap$CompressFormat");
////    jmethodID bmpCmpValueOf = env->GetStaticMethodID(bitmapCompress, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$CompressFormat;");
////    JNI_IDS.PNG = env->CallStaticObjectMethod(bitmapConfig, bmpCfgValueOf, env->NewStringUTF("PNG"));
//
////    JNI_IDS.RawContainer = env->FindClass("com/anthonymandra/dcraw/RawContainer");
////    JNI_IDS.raw_init = env->GetMethodID(JNI_IDS.RawContainer, "<init>", "([BIIZ)V");
////
////    JNI_IDS.bitmap = env->FindClass("android/graphics/Bitmap");
////    JNI_IDS.bitmap_createBitmap = env->GetStaticMethodID(JNI_IDS.bitmap, "createBitmap", "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
////    JNI_IDS.bitmap_createBitmap2 = env->GetStaticMethodID(JNI_IDS.bitmap, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
////    JNI_IDS.bitmap_compress = env->GetMethodID(JNI_IDS.bitmap, "compress", "(Landroid/graphics/Bitmap$CompressFormat;ILjava/io/OutputStream;)Z");
////
////    JNI_IDS.bitmapRegionDecoder = env->FindClass("android/graphics/BitmapRegionDecoder");
////    JNI_IDS.bitmapRegionDecoder_newInstance = env->GetStaticMethodID(JNI_IDS.bitmapRegionDecoder, "newInstance", "([BIIZ)Landroid/graphics/BitmapRegionDecoder;");
////
////	jclass t = env->FindClass("java/io/ByteArrayOutputStream");
////	jclass test = env->FindClass("java/io/ByteArrayInputStream");
////	JNI_IDS.byteArrayInputStream = env->FindClass("java/io/ByteArrayInputStream");
////	JNI_IDS.byteArrayInputStream_init = env->GetMethodID(JNI_IDS.byteArrayInputStream, "<init>", "([B)V");
////
////	JNI_IDS.byteArrayOutputStream = env->FindClass("java/io/ByteArrayOutputStream");
////	JNI_IDS.byteArrayOutputStream_init = env->GetMethodID(JNI_IDS.byteArrayOutputStream, "<init>", "()V");
////	JNI_IDS.byteArrayOutputStream_toByteArray = env->GetMethodID(JNI_IDS.byteArrayOutputStream, "toByteArray", "()[B");
//
//    return JNI_VERSION_1_6;
//}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_canDecodeFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes)
{
	LibRaw RawProcessor;

	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);
	
	int result = RawProcessor.open_buffer(buffer, len);
	env->ReleaseByteArrayElements(bufferBytes, buffer, 0);
	RawProcessor.recycle();

	if (result == 0)
		return JNI_TRUE;
	
	return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_canDecodeFromFile(JNIEnv* env, jclass clazz, jstring filePath)
{
	LibRaw RawProcessor;
	
	const char *str= env->GetStringUTFChars(filePath,0);
	int result = RawProcessor.open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	RawProcessor.recycle();

	if (result == 0)
		return JNI_TRUE;
	
	return JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();
	int result;

	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	result = rawProcessor->open_buffer(buffer, len);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jbyteArray thumb = getThumb(env, rawProcessor, exif, quality, config, compressFormat);
	rawProcessor->recycle();
	free(rawProcessor);
	return thumb;
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jbyteArray thumb = getThumb(env, rawProcessor, exif, quality, config, compressFormat);
	rawProcessor->recycle();
	free(rawProcessor);
	return thumb;
}

JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();

	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	int result = rawProcessor->open_buffer(buffer, len);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jobject regionDecoder = getRawDecoder(env, rawProcessor, quality, config, compressFormat);
	rawProcessor->recycle();

	return regionDecoder;
}

JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getDecoderFromFile(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat)
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

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getHalfImageFromFile(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jbyteArray bmp = getHalfRawImage(env, rawProcessor, quality, config, compressFormat);
	rawProcessor->recycle();

	return bmp;
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getImageFromFile(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jbyteArray bmp = getRawImage(env, rawProcessor, quality, config, compressFormat);
	rawProcessor->recycle();

	return bmp;
}

JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getHalfDecoderFromFile(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jobject regionDecoder = getHalfRawDecoder(env, rawProcessor, quality, config, compressFormat);
	rawProcessor->recycle();

	return regionDecoder;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination)
{
	LibRaw* rawProcessor = new LibRaw();
	
	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	int result = rawProcessor->open_buffer(buffer, len);
	if (LIBRAW_FATAL_ERROR(result))
		return JNI_FALSE;

	jboolean success = writeThumb(env, rawProcessor, destination);
	
	env->ReleaseByteArrayElements(bufferBytes, buffer, 0);
	rawProcessor->recycle();
	return success;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *input= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(input);
	env->ReleaseStringUTFChars(filePath, input);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeThumb(env, rawProcessor, destination);

	rawProcessor->recycle();
	return success;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeRawFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *input= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(input);
	env->ReleaseStringUTFChars(filePath, input);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeRaw(env, rawProcessor, destination);

	rawProcessor->recycle();
	return success;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination)
{
	LibRaw* rawProcessor = new LibRaw();

	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	int result = rawProcessor->open_buffer(buffer, len);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeRaw(env, rawProcessor, destination);

	env->ReleaseByteArrayElements(bufferBytes, buffer, 0);
	rawProcessor->recycle();
	return success;
}

jbyteArray getThumb(JNIEnv* env, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
	int result;
	jbyteArray thumb;

	setExif(env, rawProcessor->imgdata, exif);

	result = rawProcessor->unpack_thumb();
	if (result != LIBRAW_SUCCESS)
	{
		__android_log_write(ANDROID_LOG_INFO, "JNI", libraw_strerror(result));
		return NULL;
	}
	libraw_processed_image_t *image = rawProcessor->dcraw_make_mem_thumb(&result);
	if (result != LIBRAW_SUCCESS)
	{
		__android_log_write(ANDROID_LOG_INFO, "JNI", libraw_strerror(result));
		return NULL;
	}

	if (image->type == LIBRAW_IMAGE_BITMAP)
	{
		thumb = createBitmap(env, image, quality, config, compressFormat);
	}
	else
	{
		thumb = env->NewByteArray(image->data_size);
		env->SetByteArrayRegion(thumb, 0, image->data_size, (jbyte *) image->data);
	}
	rawProcessor->dcraw_clear_mem(image);
	return thumb;
}

jobject getRawDecoder(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat)
{
	int result = rawProcessor->unpack();
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "unpack = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	//TODO: What is the cost of process?
	result = rawProcessor->dcraw_process();
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "process = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	libraw_processed_image_t *image = rawProcessor->dcraw_make_mem_image(&result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jobject regionDecoder = createBitmapRegionDecoder(env, image, quality, config, compressFormat);

	rawProcessor->dcraw_clear_mem(image);
	return regionDecoder;
}

jbyteArray getRawImage(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat)
{
	int result = rawProcessor->unpack();
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "unpack = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	//TODO: What is the cost of process?
	result = rawProcessor->dcraw_process();
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "process = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	libraw_processed_image_t *image = rawProcessor->dcraw_make_mem_image(&result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jbyteArray bmp = createBitmap(env, image, quality, config, compressFormat);

	rawProcessor->dcraw_clear_mem(image);
	return bmp;
}

jbyteArray getHalfRawImage(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat)
{
	rawProcessor->imgdata.params.half_size = 1;

	return getRawImage(env, rawProcessor, quality, config, compressFormat);
}

jobject getHalfRawDecoder(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat)
{
	rawProcessor->imgdata.params.half_size = 1;

	return getRawDecoder(env, rawProcessor, quality, config, compressFormat);
}

jboolean writeThumb(JNIEnv* env, LibRaw* rawProcessor, jstring destination)
{
	int result = rawProcessor->unpack_thumb();
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	const char *output = env->GetStringUTFChars(destination, 0);
	result = rawProcessor->dcraw_thumb_writer(output);
	env->ReleaseStringUTFChars(destination, output);
	return result == LIBRAW_SUCCESS;
}

jboolean writeRaw(JNIEnv* env, LibRaw* rawProcessor, jstring destination)
{
	// Set the image to be processed as tiff
	rawProcessor->imgdata.params.output_tiff = 0;
	rawProcessor->imgdata.params.use_camera_wb = 1;
	int result = rawProcessor->dcraw_process();
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	const char *output = env->GetStringUTFChars(destination, 0);
	result = rawProcessor->dcraw_ppm_tiff_writer(output);
	env->ReleaseStringUTFChars(destination, output);
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

jobject createBitmapRegionDecoder(JNIEnv* env, libraw_processed_image_t *raw, int quality, jobject config, jobject compressFormat)
{
	if (!JNI_IDS.cached) cacheJniIds(env);

	jbyteArray image = createBitmap(env, raw, quality, config, compressFormat);
	jsize len = env->GetArrayLength(image);

	jobject regionDecoder = env->CallObjectMethod(JNI_IDS.bitmapRegionDecoder, JNI_IDS.bitmapRegionDecoder_newInstance, image, 0, len, JNI_FALSE);
	return regionDecoder;
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
