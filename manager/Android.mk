
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

#LOCAL_STATIC_JAVA_LIBRARIES := \
#			android-support-v4 \

LOCAL_NMANAGER_SRC_DIRS += \
	AntitheftManager/src \


LOCAL_NMANAGER_AIDLS += \
	AntitheftManager/src/com/ndroid/atmanager/IAntiTheftService.aidl \
	

LOCAL_SRC_FILES		:=  $(call all-java-files-under, $(LOCAL_NMANAGER_SRC_DIRS)) \
			    $(LOCAL_NMANAGER_AIDLS)

LOCAL_AIDL_INCLUDES += vendor/manager/AntitheftManager/src/com/ndroid/atmanager/IAntiTheftService.aidl \

LOCAL_MODULE		:= ndroid-manager
LOCAL_PROGUARD_ENABLED	:= disabled
LOCAL_CERTIFICATE	:= platform

include $(BUILD_JAVA_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
