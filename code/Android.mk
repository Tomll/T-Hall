LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_CERTIFICATE := platform
# LOCAL_PRIVILEGED_MODULE := true
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_MODULE_TAGS := optional
#LOCAL_SDK_VERSION := current

LOCAL_PACKAGE_NAME := Hall

LOCAL_SRC_FILES := $(call all-java-files-under, src gen)

LOCAL_JAVA_LIBRARIES :=  telephony-common

LOCAL_STATIC_JAVA_LIBRARIES := \
		android-support-v4 \

LOCAL_AAPT_FLAGS := --auto-add-overlay

#LOCAL_PROGUARD_FLAG_FILES := ../../../frameworks/support/design/proguard-rules.pro

LOCAL_DEX_PREOPT:= false

include $(BUILD_PACKAGE)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := android-support-v4:./libs/android-support-v4.jar\

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))