#include <string.h>
#include <jni.h>

#include "libraw/libraw.h"

#include <android/log.h>

extern "C" 
{
	// Can decode
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_canDecodeFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_canDecodeFromFile(JNIEnv* env, jclass clazz, jstring filePath);
	
	// Return thumb bitmap
    JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes);
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath);
	JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile2(JNIEnv* env, jclass clazz, jstring filePath);
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile3(JNIEnv* env, jclass clazz, jstring filePath);
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile4(JNIEnv* env, jclass clazz, jstring filePath, jintArray results);
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile5(JNIEnv* env, jclass clazz, jstring filePath, jintArray results, jobjectArray exif);
	
	// Write thumb (native format)
	JNIEXPORT int JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination);

	// Return raw bitmap
	JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes);
	JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getRawFromFile(JNIEnv* env, jclass clazz, jstring filePath);

	// Write raw tiff
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeRawFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination);
};

void getColors(int *colors, unsigned char *rgb, int width, int height);
jobject createBitmapRegionDecoder(JNIEnv* env, unsigned char *rgb, int width, int height);
jbyteArray createBitmap(JNIEnv* env, unsigned char* rgb, int width, int height);

//static JNINativeMethod methods[] = {
//    {"hashCode",    "()I",                    (void *)&JVM_IHashCode},
//    {"wait",        "(J)V",                   (void *)&JVM_MonitorWait},
//    {"notify",      "()V",                    (void *)&JVM_MonitorNotify},
//    {"notifyAll",   "()V",                    (void *)&JVM_MonitorNotifyAll},
//    {"clone",       "()Ljava/lang/Object;",   (void *)&JVM_Clone},
//};

typedef struct ID_CACHE
{
	// Prolly don't need to store the config class/field
    int cached;
    jclass bitmap;
    //jclass bitmapConfig;
    //jclass bitmapCompress;
    jclass bitmapRegionDecoder;
    jclass byteArrayOutputStream;
    jclass byteArrayInputStream;

    //jfieldID bitmapConfig_ARGB8888;
    //jfieldID bitmapCompress_PNG;
    jobject argb8888;
    jobject PNG;

    jmethodID raw_init;
    jmethodID bitmap_createBitmap;
    jmethodID bitmap_createBitmap2;
    jmethodID bitmap_compress;
    jmethodID bitmapRegionDecoder_newInstance;
    jmethodID byteArrayOutputStream_init;
    jmethodID byteArrayInputStream_init;
    jmethodID byteArrayOutputStream_toByteArray;
} ID_CACHE;

ID_CACHE JNI_IDS;

//void cacheJniIds(JNIEnv *env)
//{
//    if (JNI_IDS.cached) return;
//
//    jclass bitmapConfig = env->FindClass("android/graphics/Bitmap$Config");
//    //JNI_IDS.bitmapConfig_ARGB8888 = env->GetStaticFieldID(JNI_IDS.bitmapConfig, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
//    jmethodID bmpCfgValueOf = env->GetStaticMethodID(bitmapConfig, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
//    JNI_IDS.argb8888 = env->CallStaticObjectMethod(bitmapConfig, bmpCfgValueOf, env->NewStringUTF("ARGB_8888"));
//
////    JNI_IDS.bitmapCompress = env->FindClass("android/graphics/Bitmap$CompressFormat");
////    jmethodID midValueOf = env->GetStaticMethodID(bitmapConfig, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
////    JNI_IDS.bitmapCompress_PNG = env->GetStaticFieldID(JNI_IDS.bitmapCompress, "PNG", "Landroid/graphics/Bitmap$CompressFormat;");
////    JNI_IDS.PNG = env->GetStaticObjectField(JNI_IDS.bitmapCompress, JNI_IDS.bitmapCompress_PNG);
//
//    jclass bitmapCompress = env->FindClass("android/graphics/Bitmap$CompressFormat");
//    jmethodID bmpCmpValueOf = env->GetStaticMethodID(bitmapCompress, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$CompressFormat;");
//    JNI_IDS.PNG = env->CallStaticObjectMethod(bitmapConfig, bmpCfgValueOf, env->NewStringUTF("PNG"));
//
//    jclass rawDecode = env->FindClass("com/anthonymandra/dcraw/RawContainer");
//
//    JNI_IDS.bitmap = env->FindClass("android/graphics/Bitmap");
//    JNI_IDS.bitmap_createBitmap = env->GetStaticMethodID(JNI_IDS.bitmap, "createBitmap", "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
//    JNI_IDS.bitmap_createBitmap2 = env->GetStaticMethodID(JNI_IDS.bitmap, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
//    JNI_IDS.bitmap_compress = env->GetMethodID(JNI_IDS.bitmap, "compress", "(Landroid/graphics/Bitmap$CompressFormat;ILjava/io/OutputStream;)Z");
//
////    JNI_IDS.bitmapRegionDecoder = env->FindClass("android/graphics/BitmapRegionDecoder");
////    JNI_IDS.bitmapRegionDecoder_newInstance = env->GetStaticMethodID(JNI_IDS.bitmapRegionDecoder, "newInstance", "([BIIZ)Landroid/graphics/BitmapRegionDecoder;");
//
////    jclass t = env->FindClass("java/io/ByteArrayOutputStream");
////    jclass test = env->FindClass("java/io/ByteArrayInputStream");
////    JNI_IDS.byteArrayInputStream = env->FindClass("java/io/ByteArrayInputStream");
////    JNI_IDS.byteArrayInputStream_init = env->GetMethodID(JNI_IDS.byteArrayInputStream, "<init>", "([B)V");
////
////    JNI_IDS.byteArrayOutputStream = env->FindClass("java/io/ByteArrayOutputStream");
////    JNI_IDS.byteArrayOutputStream_init = env->GetMethodID(JNI_IDS.byteArrayOutputStream, "<init>", "()V");
////    JNI_IDS.byteArrayOutputStream_toByteArray = env->GetMethodID(JNI_IDS.byteArrayOutputStream, "toByteArray", "()[B");
//
//    JNI_IDS.cached = 1;
//}

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
	//jbyte buffer[len];
	//buffer = new jbyte[len];
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);
	
	//__android_log_write(ANDROID_LOG_INFO, "JNI", "libraw");
	//env->GetByteArrayRegion(bufferBytes, 0, len, buffer);
	///__android_log_write(ANDROID_LOG_INFO, "JNI", "open_buffer");
	int result = RawProcessor.open_buffer(buffer, len);
	//__android_log_print(ANDROID_LOG_INFO, "JNI", "result = %d", result);
	env->ReleaseByteArrayElements(bufferBytes, buffer, 0);
	//__android_log_write(ANDROID_LOG_INFO, "JNI", "release");
	//if (result == 0)
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

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes) 
{
	LibRaw RawProcessor;
	int result;
	
	jsize len = env->GetArrayLength(bufferBytes);
	//jbyte buffer[len];
	//env->GetByteArrayRegion(bufferBytes, 0, len, buffer);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	result = RawProcessor.open_buffer(buffer, len);
	//__android_log_print(ANDROID_LOG_INFO, "JNI", "open = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;
	result = RawProcessor.unpack_thumb();
	//__android_log_print(ANDROID_LOG_INFO, "JNI", "unpack = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;
	libraw_processed_image_t *image = RawProcessor.dcraw_make_mem_thumb(&result);
	//__android_log_print(ANDROID_LOG_INFO, "JNI", "mem = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;
	jbyteArray thumb = env->NewByteArray(image->data_size);
	env->SetByteArrayRegion(thumb, 0, image->data_size, (jbyte *) image->data);
	env->ReleaseByteArrayElements(bufferBytes, buffer, 0);
	RawProcessor.dcraw_clear_mem(image);
	RawProcessor.recycle();
	return thumb;
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath) 
{
	LibRaw RawProcessor;
	
	const char *str= env->GetStringUTFChars(filePath,0);
	int result = RawProcessor.open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;
	result = RawProcessor.unpack_thumb();
	if (result != LIBRAW_SUCCESS)
		return NULL;
	libraw_processed_image_t *image = RawProcessor.dcraw_make_mem_thumb(&result);
	if (result != LIBRAW_SUCCESS)
		return NULL;
	jbyteArray thumb = env->NewByteArray(image->data_size);
	env->SetByteArrayRegion(thumb, 0, image->data_size, (jbyte *) image->data);
	RawProcessor.dcraw_clear_mem(image);
	RawProcessor.recycle();
	return thumb;
}

//JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile2(JNIEnv* env, jclass clazz, jstring filePath)
//{
//	LibRaw RawProcessor;
//	jbyteArray thumb;
//
//	if (!JNI_IDS.cached)
//		cacheJniIds(env);
//
//	const char *str= env->GetStringUTFChars(filePath,0);
//	int result = RawProcessor.open_file(str);
//	env->ReleaseStringUTFChars(filePath, str);
//	if (result != LIBRAW_SUCCESS)
//		return NULL;
//	result = RawProcessor.unpack_thumb();
//	if (result != LIBRAW_SUCCESS)
//		return NULL;
//	libraw_processed_image_t *image = RawProcessor.dcraw_make_mem_thumb(&result);
//	if (result != LIBRAW_SUCCESS)
//		return NULL;
//	jboolean convertColors = image->type == LIBRAW_IMAGE_BITMAP;
//
//	thumb = env->NewByteArray(image->data_size);
//	env->SetByteArrayRegion(thumb, 0, image->data_size, (jbyte *) image->data);
//
//    jobject package = env->NewObject(JNI_IDS.RawContainer, JNI_IDS.raw_init, thumb, image->width, image->height, convertColors);
//
//	RawProcessor.dcraw_clear_mem(image);
//	RawProcessor.recycle();
//	return package;
//}

void setExif(JNIEnv* env, libraw_data_t imgdata, jobjectArray exif)
{
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "exif: length");
	jsize exifLength = env->GetArrayLength(exif);
	if (exifLength < 8)
		return;
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "exif: make");
	jstring make = env->NewStringUTF(imgdata.idata.make);
	env->SetObjectArrayElement(exif, 0, make);
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "exif: model");
	jstring model = env->NewStringUTF(imgdata.idata.model);
	env->SetObjectArrayElement(exif, 1, model);
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "exif: aperture");
	char value[20] = "";
	sprintf(value, "%.5f", imgdata.other.aperture);
	jstring apertureString = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 2, apertureString);
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "exif: focal");
	sprintf(value, "%.5f", imgdata.other.focal_len);
	jstring focalString = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 3, focalString);
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "exif: iso");
	sprintf(value, "%.0f", imgdata.other.iso_speed);
	jstring isoString = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 4, isoString);
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "exif: shutter");
	sprintf(value, "%.5f", imgdata.other.shutter);
	jstring shutterString = env->NewStringUTF(value);
	env->SetObjectArrayElement(exif, 5, shutterString);
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "exif: timestamp");
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
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "exif: done");
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile4(JNIEnv* env, jclass clazz, jstring filePath, jintArray results)
{
	LibRaw RawProcessor;
	jbyteArray thumb;

//	if (!JNI_IDS.cached)
//		cacheJniIds(env);
	const char *str= env->GetStringUTFChars(filePath,0);
	//__android_log_print(ANDROID_LOG_INFO, "JNI", "file = %s", str);
	int result = RawProcessor.open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	result = RawProcessor.unpack_thumb();
	//__android_log_print(ANDROID_LOG_INFO, "JNI", "unpack = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;
	libraw_processed_image_t *image = RawProcessor.dcraw_make_mem_thumb(&result);
	//__android_log_print(ANDROID_LOG_INFO, "JNI", "make thumb = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jint* info = new jint[3];
	if (image->type == LIBRAW_IMAGE_BITMAP)
	{
		info[0] = 0;
		info[1] = image->width;
		info[2] = image->height;

		env->SetIntArrayRegion(results, 0, 3, info);
	}
	else
	{
		info[0] = 1;
		env->SetIntArrayRegion(results, 0, 3, info);
	}

	thumb = env->NewByteArray(image->data_size);
	env->SetByteArrayRegion(thumb, 0, image->data_size, (jbyte *) image->data);

	RawProcessor.dcraw_clear_mem(image);
	RawProcessor.recycle();
	return thumb;
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile5(JNIEnv* env, jclass clazz, jstring filePath, jintArray results, jobjectArray exif)
{
	LibRaw RawProcessor;
	jbyteArray thumb;

//	if (!JNI_IDS.cached)
//		cacheJniIds(env);
	const char *str= env->GetStringUTFChars(filePath,0);
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "file = %s", str);
	int result = RawProcessor.open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	setExif(env, RawProcessor.imgdata, exif);
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "exif: return");

	result = RawProcessor.unpack_thumb();
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "unpack = %d", result);
	if (result != LIBRAW_SUCCESS)
	{
		__android_log_print(ANDROID_LOG_INFO, "JNI", libraw_strerror(result));
		return NULL;
	}
	libraw_processed_image_t *image = RawProcessor.dcraw_make_mem_thumb(&result);
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "make thumb = %d", result);
	if (result != LIBRAW_SUCCESS)
	{
		__android_log_print(ANDROID_LOG_INFO, "JNI", libraw_strerror(result));
		return NULL;
	}

	jint* info = new jint[3];
	if (image->type == LIBRAW_IMAGE_BITMAP)
	{
		info[0] = 0;
		info[1] = image->width;
		info[2] = image->height;

		env->SetIntArrayRegion(results, 0, 3, info);
	}
	else
	{
		info[0] = 1;
		env->SetIntArrayRegion(results, 0, 3, info);
	}

	thumb = env->NewByteArray(image->data_size);
	env->SetByteArrayRegion(thumb, 0, image->data_size, (jbyte *) image->data);

	RawProcessor.dcraw_clear_mem(image);
	RawProcessor.recycle();
	return thumb;
}

//JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile3(JNIEnv* env, jclass clazz, jstring filePath)
//{
//	LibRaw RawProcessor;
//	jbyteArray thumb;
//
////	if (!JNI_IDS.cached)
////		cacheJniIds(env);
//
//	const char *str= env->GetStringUTFChars(filePath,0);
//	int result = RawProcessor.open_file(str);
//	env->ReleaseStringUTFChars(filePath, str);
//	if (result != LIBRAW_SUCCESS)
//		return NULL;
//	result = RawProcessor.unpack_thumb();
//	if (result != LIBRAW_SUCCESS)
//		return NULL;
//	libraw_processed_image_t *image = RawProcessor.dcraw_make_mem_thumb(&result);
//	if (result != LIBRAW_SUCCESS)
//		return NULL;
//	jboolean convertColors = image->type == LIBRAW_IMAGE_BITMAP;
//
//	thumb = createBitmap(env, image->data, image->width, image->height);
//
////	thumb = env->NewByteArray(image->data_size);
////	env->SetByteArrayRegion(thumb, 0, image->data_size, (jbyte *) image->data);
////
////    jclass rawDecode = env->FindClass("com/anthonymandra/dcraw/RawContainer");
////    jmethodID cnstr = env->GetMethodID(JNI_IDS.byteArrayInputStream, "<init>", "([BIIZ)V");
////    jobject package = env->NewObject(rawDecode, cnstr, thumb, image->width, image->height, convertColors);
//
//	RawProcessor.dcraw_clear_mem(image);
//	RawProcessor.recycle();
//	return thumb;
//}

JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes)
{
	LibRaw rawProcessor;
	int result;

	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	result = rawProcessor.open_buffer(buffer, len);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	result = rawProcessor.dcraw_process();
	if (result != LIBRAW_SUCCESS)
		return NULL;

	libraw_processed_image_t *image = rawProcessor.dcraw_make_mem_image(&result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jobject regionDecoder = createBitmapRegionDecoder(env, image->data, image->width, image->height);
	rawProcessor.dcraw_clear_mem(image);
	if (regionDecoder == NULL)
		return NULL;

	rawProcessor.recycle();
	return regionDecoder;
}

JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getRawFromFile(JNIEnv* env, jclass clazz, jstring filePath)
{
	LibRaw rawProcessor;

	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor.open_file(str);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	result = rawProcessor.unpack();
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "unpack = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	result = rawProcessor.dcraw_process();
//	__android_log_print(ANDROID_LOG_INFO, "JNI", "process = %d", result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	libraw_processed_image_t *image = rawProcessor.dcraw_make_mem_image(&result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jobject regionDecoder = createBitmapRegionDecoder(env, image->data, image->width, image->height);
	rawProcessor.dcraw_clear_mem(image);
	if (regionDecoder == NULL)
		return NULL;

	rawProcessor.recycle();
	return regionDecoder;
}

JNIEXPORT int JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination)
{
	LibRaw RawProcessor;
	int result;
	
	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	result = RawProcessor.open_buffer(buffer, len);
	if (LIBRAW_FATAL_ERROR(result))
		return 1;
	result = RawProcessor.unpack_thumb();
	if (LIBRAW_FATAL_ERROR(result))
		return 1;
	const char *nativeString = env->GetStringUTFChars(destination, 0);
	result = RawProcessor.dcraw_thumb_writer(nativeString);
	env->ReleaseStringUTFChars(destination, nativeString);
	if (result != LIBRAW_SUCCESS)
		return 1;
	
	env->ReleaseByteArrayElements(bufferBytes, buffer, 0);
	RawProcessor.recycle();
	return 0;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination)
{
	LibRaw RawProcessor;

	const char *input= env->GetStringUTFChars(filePath,0);
	int result = RawProcessor.open_file(input);
	env->ReleaseStringUTFChars(filePath, input);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	result = RawProcessor.unpack_thumb();
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	const char *output = env->GetStringUTFChars(destination, 0);
	result = RawProcessor.dcraw_thumb_writer(output);
	env->ReleaseStringUTFChars(destination, output);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	RawProcessor.recycle();
	return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeRawFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination)
{
	LibRaw RawProcessor;

	const char *input= env->GetStringUTFChars(filePath,0);
	int result = RawProcessor.open_file(input);
	env->ReleaseStringUTFChars(filePath, input);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	// Set the image to be processed as tiff
	RawProcessor.imgdata.params.output_tiff = 0;
	RawProcessor.imgdata.params.use_camera_wb = 1;
	RawProcessor.dcraw_process();
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	const char *output = env->GetStringUTFChars(destination, 0);
	result = RawProcessor.dcraw_ppm_tiff_writer(output);
	env->ReleaseStringUTFChars(destination, output);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	RawProcessor.recycle();
	return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination)
{
	LibRaw RawProcessor;

	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	int result = RawProcessor.open_buffer(buffer, len);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	// Set the image to be processed as tiff
	RawProcessor.imgdata.params.output_tiff = 0;
	RawProcessor.imgdata.params.use_camera_wb = 1;
	RawProcessor.dcraw_process();
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	const char *output = env->GetStringUTFChars(destination, 0);
	result = RawProcessor.dcraw_ppm_tiff_writer(output);
	env->ReleaseStringUTFChars(destination, output);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	env->ReleaseByteArrayElements(bufferBytes, buffer, 0);
	RawProcessor.recycle();
	return JNI_TRUE;
}

// Converts an rgb bitmap of width and height to an int array of colors
void getColors(int *colors, unsigned char *rgb, int width, int height)
{
	int color = 0;
	for (int y = 0; y < height; y++)
	{
		for (int x = 0; x < width; x++)
		{
			int r = rgb[color++];
			int g = rgb[color++];
			int b = rgb[color++];
			colors[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
		}
	}
}

// Converts an rgb bitmap of width and height to an int array of colors
jintArray getColors2(JNIEnv* env, unsigned char *rgb, int width, int height)
{
	int color = 0;
	int *colors = new int[height * width];


	for (int y = 0; y < height; y++)
	{
		for (int x = 0; x < width; x++)
		{
			int r = rgb[color++];
			int g = rgb[color++];
			int b = rgb[color++];
			colors[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
		}
	}

	jintArray colorArray = env->NewIntArray(width * height);
	env->SetIntArrayRegion(colorArray, 0, width * height, colors);
	return colorArray;
}

//jbyteArray createBitmap(JNIEnv* env, unsigned char* rgb, int width, int height)
//{
//	if (!JNI_IDS.cached)
//		cacheJniIds(env);
//
//	//int *colors = new int[height * width];
//	//getColors(colors, rgb, width, height);
//	jintArray colors = getColors2(env, rgb, width, height);
//
////	jclass java_bitmap_class = (jclass)env->FindClass("android/graphics/Bitmap");
////	jmethodID mid = env->GetStaticMethodID(java_bitmap_class, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
////
////	const wchar_t config_name[] = L"ARGB_8888";
////	jstring j_config_name = env->NewString((const jchar*)config_name, wcslen(config_name));
////	jclass bcfg_class = env->FindClass("android/graphics/Bitmap$Config");
////	//jobject java_bitmap_config = env->CallStaticObjectMethod(bcfg_class, env->GetStaticMethodID(bcfg_class, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;"), j_config_name);
////	jmethodID midValueOf = env->GetStaticMethodID(bcfg_class, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
////	jobject java_bitmap_config = env->CallStaticObjectMethod(bcfg_class, midValueOf, env->NewStringUTF("ARGB_8888"));
//
//	//jobject test = env->CallStaticObjectMethod(java_bitmap_class, mid, width, height, java_bitmap_config);
//	//jobject test = env->CallStaticObjectMethod(JNI_IDS.bitmap, JNI_IDS.bitmap_createBitmap2, width, height, JNI_IDS.argb8888);
//	jobject bitmap = env->CallStaticObjectMethod(JNI_IDS.bitmap, JNI_IDS.bitmap_createBitmap, colors, width, height, JNI_IDS.argb8888);
//	jobject byteArrayOutputStream = env->NewObject(JNI_IDS.byteArrayOutputStream, JNI_IDS.byteArrayOutputStream_init);
//	jboolean worked = env->CallBooleanMethod(JNI_IDS.bitmap, JNI_IDS.bitmap_compress, JNI_IDS.PNG, 100, byteArrayOutputStream); //This is currently failing, prolly png
//	return (jbyteArray) env->CallObjectMethod(JNI_IDS.byteArrayOutputStream, JNI_IDS.byteArrayOutputStream_toByteArray);
//}

//BROKEN!!!
jobject createBitmapRegionDecoder(JNIEnv* env, unsigned char* rgb, int width, int height)
{
//	if (!JNI_IDS.cached)
//		cacheJniIds(env);

	int *colors = new int[height * width];

	getColors(colors, rgb, width, height);

	jobject bitmap = env->CallStaticObjectMethod(JNI_IDS.bitmap, JNI_IDS.bitmap_createBitmap, colors, width, height, JNI_IDS.argb8888);
	free(colors);
	jobject byteArrayOutputStream = env->NewObject(JNI_IDS.byteArrayOutputStream, JNI_IDS.byteArrayOutputStream_init);
	jboolean worked = env->CallBooleanMethod(JNI_IDS.bitmap, JNI_IDS.bitmap_compress, JNI_IDS.PNG, 100, byteArrayOutputStream);
	jobject byteArray = env->CallObjectMethod(JNI_IDS.byteArrayOutputStream, JNI_IDS.byteArrayOutputStream_toByteArray);
	jobject byteArrayInputStream = env->NewObject(JNI_IDS.byteArrayInputStream, JNI_IDS.byteArrayInputStream_init, byteArray);
	jobject regionDecoder = env->CallObjectMethod(JNI_IDS.bitmapRegionDecoder, JNI_IDS.bitmapRegionDecoder_newInstance, byteArrayInputStream, JNI_FALSE);
	return regionDecoder;
}

	
