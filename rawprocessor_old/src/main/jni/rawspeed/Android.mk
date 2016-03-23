LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

OCAL_CFLAGS += -pthread

LOCAL_SRC_FILES := \
	RawSpeed/ArwDecoder.cpp \
	RawSpeed/BitPumpJPEG.cpp \
	RawSpeed/BitPumpMSB.cpp \
	RawSpeed/BitPumpMSB32.cpp \
	RawSpeed/BitPumpPlain.cpp \
	RawSpeed/BlackArea.cpp \
	RawSpeed/ByteStream.cpp \
	RawSpeed/ByteStreamSwap.cpp \
	RawSpeed/Camera.cpp \
	RawSpeed/CameraMetaData.cpp \
	RawSpeed/CameraMetadataException.cpp \
	RawSpeed/CameraSensorInfo.cpp \
	RawSpeed/ColorFilterArray.cpp \
	RawSpeed/Common.cpp \
	RawSpeed/Cr2Decoder.cpp \
	RawSpeed/DngDecoder.cpp \
	RawSpeed/DngDecoderSlices.cpp \
	RawSpeed/DngOpcodes.cpp \
	RawSpeed/FileIOException.cpp \
	RawSpeed/FileMap.cpp \
	RawSpeed/FileReader.cpp \
	RawSpeed/IOException.cpp \
	RawSpeed/LJpegDecompressor.cpp \
	RawSpeed/LJpegPlain.cpp \
	RawSpeed/NefDecoder.cpp \
	RawSpeed/NikonDecompressor.cpp \
	RawSpeed/OrfDecoder.cpp \
	RawSpeed/PefDecoder.cpp \
	RawSpeed/PentaxDecompressor.cpp \
	RawSpeed/RawDecoder.cpp \
	RawSpeed/RawDecoderException.cpp \
	RawSpeed/RawImage.cpp \
	RawSpeed/RawImageDataFloat.cpp \
	RawSpeed/RawImageDataU16.cpp \
	RawSpeed/RawParser.cpp \
	RawSpeed/Rw2Decoder.cpp \
	RawSpeed/SrwDecoder.cpp \
	RawSpeed/StdAfx.cpp \
	RawSpeed/TiffEntry.cpp \
	RawSpeed/TiffEntryBE.cpp \
	RawSpeed/TiffIFD.cpp \
	RawSpeed/TiffIFDBE.cpp \
	RawSpeed/TiffParser.cpp \
	RawSpeed/TiffParserException.cpp \
	RawSpeed/TiffParserHeaderless.cpp \
	RawSpeed/TiffParserOlympus.cpp \

# This is a test app I believe	
#	RawSpeed/RawSpeed.cpp \
	
LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../libjpeg \
	$(LOCAL_PATH)/../libxml2/include \
	$(LOCAL_PATH)/../libiconv/include \
      
LOCAL_MODULE    := rawspeed 

include $(BUILD_STATIC_LIBRARY) 