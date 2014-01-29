#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <vector>
#include <dirent.h>

#include <libraw/libraw.h>

extern "C" 
{
	#include <jpeglib.h>
	// Can decode
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_canDecodeFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_canDecodeFromFile(JNIEnv* env, jclass clazz, jstring filePath);
	JNIEXPORT jobjectArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_canDecodeDirectory(JNIEnv* env, jclass clazz, jstring directory);
	
	// Return thumb bitmap
    JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jobjectArray exif, int quality, jobject config, jobject compressFormat);
    JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat);
	JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbWithWatermark(JNIEnv* env, jclass clazz, jstring filePath, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight, int quality);

	// Write thumb (native format)
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination, int quality);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination, int quality);
	JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbWatermark(JNIEnv* env, jclass clazz, jstring filePath, jstring destination, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight, int quality);

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
void createJpeg(JNIEnv* env, unsigned char** outJpeg, unsigned long* outJpegSize, unsigned char* data, int width, int height, int quality);
unsigned char* readJpeg(JNIEnv* env, libraw_processed_image_t *raw, int width, int height);
unsigned char clamp(unsigned char a, unsigned char b);

jboolean getThumb(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat);
jboolean getThumbWithWatermark(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight, int quality);

jbyteArray getRawImage(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat);
jbyteArray getHalfRawImage(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat);

jobject getHalfRawDecoder(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat);
jobject getRawDecoder(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat);

jboolean writeRaw(JNIEnv* env, LibRaw* rawProcessor, const char* destination);
jboolean writeThumb(JNIEnv* env, LibRaw* rawProcessor, const char* destination, int quality);
jboolean writeThumbWatermark(JNIEnv* env, LibRaw* rawProcessor, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight, int quality, const char* destination);

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
	int result = RawProcessor.open_file(str, 0);
	env->ReleaseStringUTFChars(filePath, str);
	RawProcessor.recycle();

	if (result == 0)
		return JNI_TRUE;
	
	return JNI_FALSE;
}

using namespace std;
// This processes a whole folder but didn't prove to be a significant improvement.  1000 files (~15s individual vs. 14.4 here)
JNIEXPORT jobjectArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_canDecodeDirectory(JNIEnv* env, jclass clazz, jstring directory)
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
			result = RawProcessor.open_file(p.c_str(), 0);
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


JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();
	int result;

	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	result = rawProcessor->open_buffer(buffer, len);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	unsigned char* jpeg;
	unsigned long jpegSize;
	jboolean success = getThumb(env, &jpeg, &jpegSize, rawProcessor, exif, quality, config, compressFormat);
	jbyteArray thumb = env->NewByteArray(jpegSize);
	env->SetByteArrayRegion(thumb, 0, jpegSize, (jbyte *) jpeg);
	if (jpeg)
		free(jpeg);

	rawProcessor->recycle();
	free(rawProcessor);
	return thumb;
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(str, 0);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	unsigned char* jpeg = NULL;
	unsigned long jpegSize = 0;
	jboolean success = getThumb(env, &jpeg, &jpegSize, rawProcessor, exif, quality, config, compressFormat);
	if (!success)
		return NULL;

//	FILE *pass = fopen("/sdcard/passed.jpg","wb");
//	fwrite(jpeg, sizeof(char), jpegSize, pass);
//	fclose(pass);

	jbyteArray thumb = env->NewByteArray(jpegSize);
 	env->SetByteArrayRegion(thumb, 0, jpegSize, (jbyte *) jpeg);
	if (jpeg)
		free(jpeg);

	rawProcessor->recycle();
	free(rawProcessor);
	return thumb;
}

JNIEXPORT jbyteArray JNICALL Java_com_anthonymandra_dcraw_LibRaw_getThumbWithWatermark(JNIEnv* env, jclass clazz, jstring filePath, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight, int quality)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(str, 0);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	unsigned char* jpeg = NULL;
	unsigned long jpegSize = 0;
	jboolean success = getThumbWithWatermark(env, &jpeg, &jpegSize, rawProcessor, watermark, margins, waterWidth, waterHeight, quality);
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
	int result = rawProcessor->open_file(str, 0);
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
	int result = rawProcessor->open_file(str, 0);
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

	rawProcessor->imgdata.params.use_rawspeed = 1;
	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(str, 0);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jbyteArray bmp = getRawImage(env, rawProcessor, quality, config, compressFormat);
	rawProcessor->recycle();
	free(rawProcessor);
	return bmp;
}

JNIEXPORT jobject JNICALL Java_com_anthonymandra_dcraw_LibRaw_getHalfDecoderFromFile(JNIEnv* env, jclass clazz, jstring filePath, int quality, jobject config, jobject compressFormat)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *str= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(str, 0);
	env->ReleaseStringUTFChars(filePath, str);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	jobject regionDecoder = getHalfRawDecoder(env, rawProcessor, quality, config, compressFormat);
	rawProcessor->recycle();
	free(rawProcessor);
	return regionDecoder;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination, int quality)
{
	LibRaw* rawProcessor = new LibRaw();
	const char *output = env->GetStringUTFChars(destination, 0);
	
	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	int result = rawProcessor->open_buffer(buffer, len);
	if (LIBRAW_FATAL_ERROR(result))
		return JNI_FALSE;

	jboolean success = writeThumb(env, rawProcessor, output, quality);
	env->ReleaseStringUTFChars(destination, output);
	
	env->ReleaseByteArrayElements(bufferBytes, buffer, 0);

	rawProcessor->recycle();
	return success;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination, int quality)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *input = env->GetStringUTFChars(filePath,0);
	const char *output = env->GetStringUTFChars(destination, 0);

	int result = rawProcessor->open_file(input, 0);
	env->ReleaseStringUTFChars(filePath, input);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeThumb(env, rawProcessor, output, quality);
	env->ReleaseStringUTFChars(destination, output);

	rawProcessor->recycle();
	return success;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeThumbWatermark(JNIEnv* env, jclass clazz, jstring filePath, jstring destination, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight, int quality)
{
	LibRaw* rawProcessor = new LibRaw();

	const char *input = env->GetStringUTFChars(filePath,0);
	const char *output = env->GetStringUTFChars(destination, 0);

	int result = rawProcessor->open_file(input, 0);
	env->ReleaseStringUTFChars(filePath, input);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeThumbWatermark(env, rawProcessor, watermark, margins, waterWidth, waterHeight, quality, output);
	env->ReleaseStringUTFChars(destination, output);

	rawProcessor->recycle();
	return success;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeRawFromFile(JNIEnv* env, jclass clazz, jstring filePath, jstring destination)
{
	LibRaw* rawProcessor = new LibRaw();
	const char *output = env->GetStringUTFChars(destination, 0);

	const char *input= env->GetStringUTFChars(filePath,0);
	int result = rawProcessor->open_file(input, 0);
	env->ReleaseStringUTFChars(filePath, input);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeRaw(env, rawProcessor, output);
	env->ReleaseStringUTFChars(destination, output);

	rawProcessor->recycle();
	return success;
}

JNIEXPORT jboolean JNICALL Java_com_anthonymandra_dcraw_LibRaw_writeRawFromBuffer(JNIEnv* env, jclass clazz, jbyteArray bufferBytes, jstring destination)
{
	LibRaw* rawProcessor = new LibRaw();
	const char *output = env->GetStringUTFChars(destination, 0);

	jsize len = env->GetArrayLength(bufferBytes);
	jbyte* buffer = env->GetByteArrayElements(bufferBytes, 0);

	int result = rawProcessor->open_buffer(buffer, len);
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	jboolean success = writeRaw(env, rawProcessor, output);
	env->ReleaseStringUTFChars(destination, output);

	env->ReleaseByteArrayElements(bufferBytes, buffer, 0);
	rawProcessor->recycle();
	return success;
}

jboolean getThumb(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jobjectArray exif, int quality, jobject config, jobject compressFormat)
{
	int result;
	jbyteArray thumb;

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

	if (image->type == LIBRAW_IMAGE_BITMAP)
	{
		createJpeg(env, outJpeg, outJpegSize, image->data, image->width, image->height, quality);
	}
	else
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
}

jboolean getThumbWithWatermark(JNIEnv* env, unsigned char** outJpeg, unsigned long int* outJpegSize, LibRaw* rawProcessor, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight, int quality)
{
	int* margin = env->GetIntArrayElements(margins, 0);
	int top = margin[0];
	int left = margin[1];
	int bottom = margin[2];
	int right = margin[3];
	int startX, startY;
	int result;

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

	int width = rawProcessor->imgdata.thumbnail.twidth;
	int height = rawProcessor->imgdata.thumbnail.theight;

	if (waterWidth > width || waterHeight > height)
	{
		__android_log_print(ANDROID_LOG_INFO, "JNI", "Watermark Out of Bounds x = %d -> %d , y = %d -> %d",
				waterWidth, width, waterHeight, height);
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
		startX = width - right - waterWidth;
	}
	else
	{
		startX = width / 2 - waterWidth / 2;	//center
	}
	if (startX < 0 || startX + waterWidth > width)
	{
		startX = 0;
	}

	if (top >= 0)
	{
		startY = top;
	}
	else if (bottom >= 0)
	{
		startY = height - bottom - waterHeight;
	}
	else
	{
		startY = height / 2 - waterHeight / 2;	//center
	}
	if (startY < 0 || startY + waterHeight > height)
	{
		startY = 0;
	}

	unsigned char* pixels;
	bool jpeg = image->type == LIBRAW_IMAGE_JPEG;
	if (jpeg)
	{
		pixels = readJpeg(env, image, width, height);
	}
	else
	{
		pixels = image->data;
	}

	jsize len = env->GetArrayLength(watermark);
	unsigned char* watermarkPixels = (unsigned char*)env->GetByteArrayElements(watermark, 0);

    int delta = (startY * width + startX) * 3;
    int rowStride = width * 3;
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

	createJpeg(env, outJpeg, outJpegSize, pixels, width, height, quality);

	if (jpeg && pixels)
		free(pixels);

	rawProcessor->dcraw_clear_mem(image);

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

jbyteArray getRawImage(JNIEnv* env, LibRaw* rawProcessor, int quality, jobject config, jobject compressFormat)
{
	int result = rawProcessor->unpack();
	if (result != LIBRAW_SUCCESS)
		return NULL;

	//TODO: What is the cost of process?
	result = rawProcessor->dcraw_process();
	if (result != LIBRAW_SUCCESS)
		return NULL;

	if (rawProcessor->imgdata.process_warnings & LIBRAW_WARN_RAWSPEED_PROCESSED)
		__android_log_print(ANDROID_LOG_INFO, "JNI", "process = %d", result);
	libraw_processed_image_t *image = rawProcessor->dcraw_make_mem_image(&result);
	if (result != LIBRAW_SUCCESS)
		return NULL;

	// image.width is only populated for rgb, but full decodes are always rgb
	unsigned char* jpeg;
	unsigned long jpegSize;
	createJpeg(env, &jpeg, &jpegSize, image->data, image->width, image->height, quality);
	jbyteArray raw = env->NewByteArray(jpegSize);
	env->SetByteArrayRegion(raw, 0, jpegSize, (jbyte *) jpeg);
	if (jpeg)
		free(jpeg);

	rawProcessor->dcraw_clear_mem(image);
	return raw;
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

jboolean writeThumb(JNIEnv* env, LibRaw* rawProcessor, const char* destination, int quality)
{
	int result = rawProcessor->unpack_thumb();
	if (result != LIBRAW_SUCCESS)
		return JNI_FALSE;

	unsigned char* jpeg;
	unsigned long jpegSize;
	jboolean success = getThumb(env, &jpeg, &jpegSize, rawProcessor, NULL, quality, NULL, NULL);

	if(!destination)
		return JNI_FALSE;

	FILE *tfp = fopen(destination,"wb");

	if(!tfp)
		return JNI_FALSE;

	if(!jpeg)
	{
		fclose(tfp);
		return JNI_FALSE;
	}

	try
	{
		fwrite(jpeg, 0, jpegSize, tfp);
		fclose(tfp);
	}
	catch(...)
	{
		fclose(tfp);
	}

	return JNI_TRUE;
}

jboolean writeThumbWatermark(JNIEnv* env, LibRaw* rawProcessor, jbyteArray watermark, jintArray margins, int waterWidth, int waterHeight, int quality, const char* destination)
{
	unsigned char* jpeg = NULL;
	unsigned long jpegSize = 0;
	jboolean success = getThumbWithWatermark(env, &jpeg, &jpegSize, rawProcessor, watermark, margins, waterWidth, waterHeight, quality);

	if(!destination)
		return JNI_FALSE;

	FILE *tfp = fopen(destination,"wb");

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

	//	FILE *orig = fopen("/sdcard/original.jpg","wb");
	//	FILE *copy = fopen("/sdcard/copy.jpg","wb");
	//	fwrite(image->data, sizeof(char), image->data_size, orig);
	//	fwrite(*outJpeg, sizeof(char), *outJpegSize, copy);
	//	fclose(orig);
	//	fclose(copy);

	return JNI_TRUE;
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
