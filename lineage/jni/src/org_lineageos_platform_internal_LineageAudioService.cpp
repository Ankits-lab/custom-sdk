/*
**
** Copyright 2016, The CyanogenMod Project
**           2017,2020 The LineageOS Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

//#define LOG_NDEBUG 0

#define LOG_TAG "LineageAudioService-JNI"

#include <utils/Log.h>

#include <jni.h>
#include "core_jni_helpers.h"
#include "android_media_AudioErrors.h"

#include <media/AudioSystem.h>
#include <media/AudioSession.h>

#include <system/audio.h>
#include <utils/misc.h>
#include <utils/threads.h>

// ----------------------------------------------------------------------------

namespace android {

static const char* const kClassPathName = "org/lineageos/platform/internal/LineageAudioService";

static jclass gArrayListClass;
static struct {
    jmethodID    add;
    jmethodID    toArray;
} gArrayListMethods;

static struct {
    jmethodID postAudioSessionEventFromNative;
} gAudioSessionEventHandlerMethods;

static jclass gAudioSessionInfoClass;
static jmethodID gAudioSessionInfoCstor;

static jobject gThiz;

static Mutex gCallbackLock;

// ----------------------------------------------------------------------------

static void
org_lineageos_platform_internal_LineageAudioService_session_info_callback(int event,
        sp<AudioSessionInfo>& info, bool added)
{
    AutoMutex _l(gCallbackLock);

    JNIEnv *env = AndroidRuntime::getJNIEnv();
    if (env == NULL) {
        return;
    }

    jobject jSession = env->NewObject(gAudioSessionInfoClass, gAudioSessionInfoCstor,
            info->mSessionId, info->mStream, info->mFlags, info->mChannelMask, info->mUid);

    env->CallVoidMethod(gThiz,
            gAudioSessionEventHandlerMethods.postAudioSessionEventFromNative,
            event, jSession, added);

    env->DeleteLocalRef(jSession);
}

static void
org_lineageos_platform_internal_LineageAudioService_registerAudioSessionCallback(
        JNIEnv *env, jobject thiz, jboolean enabled)
{
    if (gThiz == NULL) {
        gThiz = env->NewGlobalRef(thiz);
    }

    AudioSystem::setAudioSessionCallback( enabled ?
            org_lineageos_platform_internal_LineageAudioService_session_info_callback : NULL);
}

static jint
org_lineageos_platform_internal_LineageAudioService_listAudioSessions(JNIEnv *env, jobject thiz,
        jint streams, jobject jSessions)
{
    ALOGV("listAudioSessions");

    if (jSessions == NULL) {
        ALOGE("listAudioSessions NULL arraylist");
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }
    if (!env->IsInstanceOf(jSessions, gArrayListClass)) {
        ALOGE("listAudioSessions not an arraylist");
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }

    status_t status;
    Vector< sp<AudioSessionInfo>> sessions;

    status = AudioSystem::listAudioSessions((audio_stream_type_t)streams, sessions);
    if (status != NO_ERROR) {
        ALOGE("AudioSystem::listAudioSessions error %d", status);
    } else {
        ALOGV("AudioSystem::listAudioSessions count=%zu", sessions.size());
    }

    jint jStatus = nativeToJavaStatus(status);
    if (jStatus != AUDIO_JAVA_SUCCESS) {
        goto exit;
    }

    for (size_t i = 0; i < sessions.size(); i++) {
        const sp<AudioSessionInfo>& s = sessions.itemAt(i);

        jobject jSession = env->NewObject(gAudioSessionInfoClass, gAudioSessionInfoCstor,
                s->mSessionId, s->mStream, s->mFlags, s->mChannelMask, s->mUid);

        if (jSession == NULL) {
            jStatus = (jint)AUDIO_JAVA_ERROR;
            goto exit;
        }

        env->CallBooleanMethod(jSessions, gArrayListMethods.add, jSession);
        env->DeleteLocalRef(jSession);
    }

exit:
    return jStatus;
}


// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
     {"native_listAudioSessions", "(ILjava/util/ArrayList;)I",
            (void *)org_lineageos_platform_internal_LineageAudioService_listAudioSessions},
     {"native_registerAudioSessionCallback", "(Z)V",
            (void *)org_lineageos_platform_internal_LineageAudioService_registerAudioSessionCallback},
};

int register_org_lineageos_platform_internal_LineageAudioService(JNIEnv *env)
{
    jclass arrayListClass = FindClassOrDie(env, "java/util/ArrayList");
    gArrayListClass = MakeGlobalRefOrDie(env, arrayListClass);
    gArrayListMethods.add = GetMethodIDOrDie(env, arrayListClass, "add", "(Ljava/lang/Object;)Z");
    gArrayListMethods.toArray = GetMethodIDOrDie(env, arrayListClass, "toArray", "()[Ljava/lang/Object;");

    jclass audioSessionInfoClass = FindClassOrDie(env, "lineageos/media/AudioSessionInfo");
    gAudioSessionInfoClass = MakeGlobalRefOrDie(env, audioSessionInfoClass);
    gAudioSessionInfoCstor = GetMethodIDOrDie(env, audioSessionInfoClass, "<init>", "(IIIII)V");

    gAudioSessionEventHandlerMethods.postAudioSessionEventFromNative =
            GetMethodIDOrDie(env, env->FindClass(kClassPathName),
            "audioSessionCallbackFromNative", "(ILlineageos/media/AudioSessionInfo;Z)V");

    return RegisterMethodsOrDie(env, kClassPathName, gMethods, NELEM(gMethods));
}

} /* namespace android */
