APP_OPTIM := release
#APP_STL := c++_shared
#APP_STL := gnustl_shared
APP_STL := gnustl_static
#APP_STL := stlport_static
#APP_STL := stlport_shared
APP_CPPFLAGS += -fexceptions -frtti
APP_PLATFORM := android-10
LOCAL_ARM_MODE := arm
APP_ABI := all
#APP_ABI := armeabi-v7a
NDK_TOOLCHAIN_VERSION=4.9