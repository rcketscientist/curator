LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	lib/xmlparse.c \
	lib/xmlrole.c \
	lib/xmltok.c \
	lib/xmltok_impl.c \
	lib/xmltok_ns.c \

LOCAL_MODULE := expat

include $(BUILD_STATIC_LIBRARY)