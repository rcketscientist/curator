LOCAL_PATH := $(call my-dir)
STARTUP_DIR := $(LOCAL_PATH)
TAG := CORE

include $(CLEAR_VARS)

LOCAL_MODULE    := libraw
  
LOCAL_LDLIBS    += -L$(SYSROOT)/usr/lib -llog
LOCAL_SRC_FILES := libraw.cpp

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/libjpeg-turbo \
	$(LOCAL_PATH)/LibRaw \
	
LOCAL_STATIC_LIBRARIES := libjpeg_static
LOCAL_SHARED_LIBRARIES := libraw_r

include $(BUILD_SHARED_LIBRARY)

#include $(STARTUP_DIR)/libjpeg-turbo/Android.mk
include $(STARTUP_DIR)/libjpeg/Android.mk
include $(STARTUP_DIR)/libxml2/Android.mk
include $(STARTUP_DIR)/LibRaw/Android.mk