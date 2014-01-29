LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	src/actions.cpp \
	src/asfvideo.cpp\
	src/basicio.cpp\
	src/bmpimage.cpp\
	src/canonmn.cpp\
	src/convert.cpp\
	src/cr2image.cpp\
	src/crwedit.cpp\
	src/crwimage.cpp\
	src/crwparse.cpp\
	src/datasets.cpp\
	src/easyaccess.cpp\
	src/epsimage.cpp\
	src/error.cpp\
	src/exif.cpp\
	src/exiv2.cpp\
	src/fujimn.cpp\
	src/futils.cpp\
	src/gifimage.cpp\
	src/image.cpp\
	src/iptc.cpp\
	src/jp2image.cpp\
	src/jpgimage.cpp\
	src/makernote.cpp\
	src/matroskavideo.cpp\
	src/metadatum.cpp\
	src/minoltamn.cpp\
	src/mrwimage.cpp\
	src/mrwthumb.cpp\
	src/nikonmn.cpp\
	src/olympusmn.cpp\
	src/orfimage.cpp\
	src/panasonicmn.cpp\
	src/pentaxmn.cpp\
	src/pgfimage.cpp\
	src/pngchunk.cpp\
	src/pngimage.cpp\
	src/preview.cpp\
	src/properties.cpp\
	src/psdimage.cpp\
	src/quicktimevideo.cpp\
	src/rafimage.cpp\
	src/riffvideo.cpp\
	src/rw2image.cpp\
	src/samsungmn.cpp\
	src/sigmamn.cpp\
	src/sonymn.cpp\
	src/tags.cpp\
	src/tgaimage.cpp\
	src/tiff-test.cpp\
	src/tiffcomposite.cpp\
	src/tiffimage.cpp\
	src/tiffmn-test.cpp\
	src/tiffvisitor.cpp\
	src/types.cpp\
	src/utils.cpp\
	src/utiltest.cpp\
	src/value.cpp\
	src/version.cpp\
	src/xmp.cpp\
	src/xmpdump.cpp\
	src/xmpsidecar.cpp\

LOCAL_MODULE := exiv2
LOCAL_C_INCLUDES += $(LOCAL_PATH)/xmpsdk

include $(BUILD_STATIC_LIBRARY)
