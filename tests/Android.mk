#
# Copyright (C) 2015 The CyanogenMod Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_STATIC_JAVA_LIBRARIES := \
    org.lineageos.platform.sdk \
    android-support-test \
    mockito-target

LOCAL_DEX_PREOPT := false

LOCAL_SRC_FILES := $(call all-subdir-java-files, src/)

LOCAL_PACKAGE_NAME := LineagePlatformTests
LOCAL_CERTIFICATE := platform
LOCAL_JAVA_LIBRARIES := android.test.runner
LOCAL_PROGUARD_ENABLED := optimization
LOCAL_PROGUARD_FLAG_FILES := proguard.flags
LOCAL_PRIVATE_PLATFORM_APIS := true

include $(BUILD_PACKAGE)

# Register as LineageTS
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := tests

LOCAL_STATIC_JAVA_LIBRARIES := \
    org.lineageos.platform.sdk \
    android-support-test \
    mockito-target

LOCAL_DEX_PREOPT := false

LOCAL_SRC_FILES := $(call all-subdir-java-files, src/)

LOCAL_PACKAGE_NAME := CmtsPlatformSDKTests
LOCAL_CERTIFICATE := platform
LOCAL_JAVA_LIBRARIES := android.test.runner
LOCAL_PROGUARD_ENABLED := optimization
LOCAL_PROGUARD_FLAG_FILES := proguard.flags
include $(BUILD_LineageTS_PACKAGE)
