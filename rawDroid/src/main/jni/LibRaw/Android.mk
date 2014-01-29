LOCAL_PATH := $(call my-dir)
TAG := LIBRAW

#Most make data pulled from Makefile.dist
#include $(call all-subdir-makefiles)
include $(CLEAR_VARS)

#LOCAL_CFLAGS=-O4  -I. -w

# OpenMP support
#CFLAGS+=-fopenmp

# RawSpeed Support
#LOCAL_CFLAGS+=-pthread -DUSE_RAWSPEED -I../RawSpeed -I/usr/local/include/libxml2
#LDADD+=-L../RawSpeed/RawSpeed -lrawspeed -L/usr/local/include -ljpeg -lxml2
#LOCAL_CFLAGS += -pthread -DUSE_RAWSPEED
#LOCAL_C_INCLUDES += $(LOCAL_PATH)/../RawSpeed/
#LOCAL_C_INCLUDES += $(LOCAL_PATH)/../libxml2/
#LOCAL_C_INCLUDES += $(LOCAL_PATH)/../libxml2/include
#LOCAL_C_INCLUDES += $(LOCAL_PATH)/../libiconv/include
#	
#LOCAL_STATIC_LIBRARIES += \
#	libxml2 \
#	librawspeed \
#	libjpeg \

#I don't think libiconv is needed

#RAWSPEED_DATA=$(LOCAL_PATH)/../RawSpeed/data/cameras.xml

# Jasper support for RedCine
#CFLAGS+=-DUSE_JASPER -I/usr/local/include
#LDADD+=-L/usr/local/lib -ljasper

# JPEG support for DNG
LOCAL_CFLAGS+=-DUSE_JPEG
LOCAL_STATIC_LIBRARIES+=libjpeg
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../libjpeg

# LIBJPEG8:
#CFLAGS+=-DUSE_JPEG8

# LCMS support
#CFLAGS+=-DUSE_LCMS -I/usr/local/include
#LDADD+=-L/usr/local/lib -llcms

# LCMS2.x support
#CFLAGS+=-DUSE_LCMS2 -I/usr/local/include
#LDADD+=-L/usr/local/lib -llcms2

# Demosaic Pack GPL2:
LOCAL_CFLAGS+=-DLIBRAW_DEMOSAIC_PACK_GPL2
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../LibRaw-demosaic-pack-GPL2

# Demosaic Pack GPL3:
LOCAL_CFLAGS+=-DLIBRAW_DEMOSAIC_PACK_GPL3
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../LibRaw-demosaic-pack-GPL3

#used for compiling libraw
LOCAL_CFLAGS += -pthread -w 
LOCAL_CXXFLAGS += -I$(SYSROOT)/usr/lib/include/libraw -pthread -w
LOCAL_MODULE     := libraw_r                                    				# name of your module
             
#core libraw
LOCAL_SRC_FILES := \
	internal/dcraw_common.cpp \
	internal/dcraw_fileio.cpp \
	internal/demosaic_packs.cpp \
	src/libraw_cxx.cpp \
	src/libraw_c_api.cpp \
	src/libraw_datastream.cpp \

include $(BUILD_SHARED_LIBRARY)