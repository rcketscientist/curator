LOCAL_PATH := $(call my-dir)
STARTUP_DIR := $(LOCAL_PATH)

#used to skip re-compiling libraw
#include $(CLEAR_VARS)
#LOCAL_MODULE    := libraw_r
#LOCAL_SRC_FILES := ../obj/local/armeabi/libraw_r.so
#LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/libraw
#include $(PREBUILT_SHARED_LIBRARY)

# Create libjpeg (doesn't seem to help with failed decodes, disable for now)
#include $(call all-subdir-makefiles)
include $(CLEAR_VARS)
# The following two lines link libjpeg
#LOCAL_CFLAGS += -DUSE_JPEG# -I$(SYSROOT)/usr/local/include
#LOCAL_SHARED_LIBRARIES := libjpeg

#Reset the working directory
LOCAL_PATH := $(STARTUP_DIR)

#used for compiling libraw
include $(CLEAR_VARS)
LOCAL_CFLAGS += -I$(SYSROOT)/usr/lib/include/libraw 
LOCAL_CFLAGS += -pthread -w 
LOCAL_CFLAGS += -DLIBRAW_DEMOSAIC_PACK_GPL2 
LOCAL_CFLAGS += -DLIBRAW_DEMOSAIC_PACK_GPL3
#LOCAL_CFLAGS += -DP2=/LibRaw-demosaic-pack-GPL2
#LOCAL_CFLAGS += -DP3=/LibRaw-demosaic-pack-GPL3

LOCAL_CXXFLAGS += -I$(SYSROOT)/usr/lib/include/libraw -pthread -w
LOCAL_MODULE     := libraw_r                                    				# name of your module
# libraries to link against
LOCAL_LDLIBS     += -L$(SYSROOT)/usr/lib -lstdc++								# lstdc++ is auto-linked
#LOCAL_LDLIBS     += -L$(MYDROID)/out/target/product/generic/system/lib/ -ljpeg	# ljpeg is for DNG jpg

#core libraw
LOCAL_SRC_FILES := internal/dcraw_common.cpp 
LOCAL_SRC_FILES += internal/dcraw_fileio.cpp
LOCAL_SRC_FILES += internal/demosaic_packs.cpp
LOCAL_SRC_FILES += src/libraw_cxx.cpp
LOCAL_SRC_FILES += src/libraw_c_api.cpp
LOCAL_SRC_FILES += src/libraw_datastream.cpp

#demosaic pack GPL2
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL2/afd_interpolate_pl.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL2/ahd_interpolate_mod.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL2/ahd_partial_interpolate.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL2/dcraw_foveon.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL2/es_median_filter.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL2/median_filter_new.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL2/lmmse_interpolate.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL2/media_filter_new.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL2/refinement.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL2/vcd_interpolate.c

#demosaic pack GPL3
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL3/amaze_demosaic_RT.cc
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL3/CA_correct_RT.cc
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL3/cfa_impulse_gauss.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL3/cfa_linedn_new.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL3/green_equi.c
#LOCAL_SRC_FILES += LibRaw-demosaic-pack-GPL3/shrtdct_float.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/libraw
LOCAL_MODULE    := libraw
LOCAL_LDLIBS     += -L$(SYSROOT)/usr/lib -llog
LOCAL_SRC_FILES := libraw.cpp
LOCAL_SHARED_LIBRARIES := libraw_r
include $(BUILD_SHARED_LIBRARY)