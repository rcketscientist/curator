LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

#LOCAL_CLFAGS := \
#	-Wno-multichar \
#	-D_ANDROID \
#	-DLIBDIR=\"c" \
#	-DBUILDING_LIBICONV \
#	-DIN_LIBRARY \
	
LOCAL_CFLAGS := \
	-DLIBDIR=\"c\" \
	-DBUILDING_LIBICONV \
	-DIN_LIBRARY \
	-D_ANDROID \
	
LOCAL_SRC_FILES := \
	lib/iconv.c \
	lib/relocatable.c \
	libcharset/lib/langinfo.c \
	libcharset/lib/localcharset.c \
	
LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/include/ \
	$(LOCAL_PATH)/lib/ \
	$(LOCAL_PATH)/libcharset \
	$(LOCAL_PATH)/libcharset/include/ \
      
LOCAL_MODULE    := libiconv 

include $(BUILD_STATIC_LIBRARY) 