/***************************************************************************************************
 *                Copyright (C) 2024 by Dolby International AB.
 *                All rights reserved.

 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 *    and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 *    endorse or promote products derived from this software without specific prior written
 *    permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **************************************************************************************************/

#include <jni.h>
#include <string>
#include <mutex>

#include "log.h"

extern "C"
{
    #include "dlb_alps_native.h"
}

static std::mutex mtx;
static JavaVM *globalJavaVM = nullptr;

jint JNI_OnLoad(JavaVM* vm, void*) {
    globalJavaVM = vm;
    return JNI_VERSION_1_6;
}

static void throwJniException(JNIEnv* env, const std::string& message) {
    jclass exceptionClass = env->FindClass("com/dolby/android/alps/utils/AlpsException$JNI");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
    }
}

static void handleNativeError(JNIEnv* env, alps_ret error) {
    if (error == ALPS_RET_OK) return;

    std::string exceptionClassPath = "com/dolby/android/alps/utils/AlpsException$Native$";
    std::string exceptionClassName;

    switch(error) {
        case ALPS_RET_E_UNDEFINED:
            exceptionClassName = "Undefined";
            break;
        case ALPS_RET_E_INVALID_ARG:
            exceptionClassName = "InvalidArg";
            break;
        case ALPS_RET_E_BUFF_TOO_SMALL:
            exceptionClassName = "BuffTooSmall";
            break;
        case ALPS_RET_E_PARSE:
            exceptionClassName = "ParseFailed";
            break;
        case ALPS_RET_E_NEXT_SEGMENT:
            exceptionClassName = "NextSegment";
            break;
        case ALPS_RET_E_NO_MOVIE_INFO:
            exceptionClassName = "NoMovieInfo";
            break;
        case ALPS_RET_E_PRES_ID_NOT_FOUND:
            exceptionClassName = "PresIdNotFound";
            break;
        default:
            return;
    }

    exceptionClassPath += exceptionClassName;

    jclass exceptionClass = env->FindClass(exceptionClassPath.c_str());
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, nullptr);
    } else {
        throwJniException(env, "AlpsNative returned unknown error");
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_dolby_android_alps_alpsnative_AlpsNativeInfo_getVersion(JNIEnv *env, jobject thiz) {
    jstring version = env->NewStringUTF(alps_version());
    return version;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_dolby_android_alps_alpsnative_DefaultAlpsNative_create(JNIEnv *env, jobject thiz) {
    void *memory = nullptr;
    alps_ctx *alps;
    size_t memorySize;
    alps_ret ret = alps_query_mem(&memorySize);
    if (ret == ALPS_RET_OK) {
        ALOGI("alps_query_mem successful, size: %zu", memorySize);
        memory = malloc(memorySize);
        if (memory == nullptr) {
            ALOGE("Failed to allocate memory");
            throwJniException(env, "Failed to allocate memory");
        }

        ret = alps_init(&alps, memory);
        if (ret == ALPS_RET_OK) {
            ALOGI("alps_init successful");
            return (jlong)(uintptr_t)alps;
        } else {
            ALOGE("alps_init failed, error: %d", ret);
            handleNativeError(env, ret);
        }
    } else {
        ALOGE("alps_query_mem failed, error: %d", ret);
        handleNativeError(env, ret);
    }

    return -1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dolby_android_alps_alpsnative_DefaultAlpsNative_destroy(JNIEnv *env,
                                                                 jobject thiz,
                                                                 jlong alpsHandle) {
    auto *alps = (alps_ctx*)(uintptr_t)alpsHandle;

    auto callback = (jobject)alps_get_presentations_changed_callback_context(alps);
    if (callback != nullptr) {
        env->DeleteGlobalRef(callback);
    }

    alps_destroy(alps);
    if (alps != nullptr) {
        free(alps);
        alps = nullptr;
    }
    ALOGI("Alps destroyed");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dolby_android_alps_alpsnative_DefaultAlpsNative_processIsobmffSegment(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jlong alpsHandle,
                                                                               jobject buffer) {
    auto *alps = (alps_ctx*)(uintptr_t)alpsHandle;
    auto bufferPtr = reinterpret_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    auto bufferSize = (int32_t)env->GetDirectBufferCapacity(buffer);

    alps_ret ret = alps_process_isobmff_segment(alps, bufferPtr, bufferSize);

    if (ret == ALPS_RET_OK) {
        ALOGI("alps_process_isobmff_segment successful");
    } else {
        ALOGE("alps_process_isobmff_segment failed, error: %d", ret);
        handleNativeError(env, ret);
    }
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_dolby_android_alps_alpsnative_DefaultAlpsNative_getPresentations(JNIEnv *env,
                                                                          jobject thiz,
                                                                          jlong alpsHandle) {
    auto *alps = (alps_ctx*)(uintptr_t)alpsHandle;

    alps_presentation *nativePresentationsList = nullptr;
    size_t presentationsCount;

    alps_ret ret = alps_get_presentations(alps, &nativePresentationsList, &presentationsCount);
    if (ret == ALPS_RET_OK) {
        ALOGI("alps_get_presentations successful. Presentations count: %zu", presentationsCount);
        jclass arrayListClass = env->FindClass("java/util/ArrayList");
        jmethodID arrayListConstructor = env->GetMethodID(arrayListClass, "<init>", "()V");
        jobject presentationsList = env->NewObject(arrayListClass, arrayListConstructor);

        jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

        jclass presentationClz = env->FindClass("com/dolby/android/alps/models/Presentation");
        jmethodID presentationMid = env->GetMethodID(
                presentationClz,
                "<init>", "(ILjava/lang/String;Ljava/lang/String;)V"
        );
        for (int i = 0; i < presentationsCount; i++) {
            alps_presentation nativePresentation = nativePresentationsList[i];
            jstring label = env->NewStringUTF(nativePresentation.label);
            jstring extendedLanguage = env->NewStringUTF(nativePresentation.language);
            jobject presentation = env->NewObject(presentationClz, presentationMid,
                                                  nativePresentation.presentation_id,
                                                  label,
                                                  extendedLanguage);

            env->CallBooleanMethod(presentationsList, arrayListAdd, presentation);
            env->DeleteLocalRef(presentation);
        }
        return presentationsList;
    } else {
        ALOGE("alps_get_presentations failed, error: %d", ret);
        handleNativeError(env, ret);
        return nullptr;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_dolby_android_alps_alpsnative_DefaultAlpsNative_getActivePresentationId(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jlong alpsHandle) {
    auto *alps = (alps_ctx*)(uintptr_t)alpsHandle;
    jint activeIndex;

    alps_ret ret = alps_get_active_presentation_id(alps, &activeIndex);
    if (ret == ALPS_RET_OK) {
        ALOGI("alps_get_active_presentation_id successful");
        return activeIndex;
    } else {
        ALOGE("alps_get_active_presentation_id failed, error: %d", ret);
        handleNativeError(env, ret);
        return -1;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dolby_android_alps_alpsnative_DefaultAlpsNative_setActivePresentationId(JNIEnv *env, jobject thiz,
                                                                                 jlong alpsHandle,
                                                                                 jint id) {
    auto *alps = (alps_ctx*)(uintptr_t)alpsHandle;
    alps_ret ret = alps_set_active_presentation_id(alps, id);
    if (ret == ALPS_RET_OK) {
        ALOGI("alps_set_active_presentation_id successful");
    } else {
        ALOGE("alps_set_active_presentation_id failed, error: %d", ret);
        handleNativeError(env, ret);
    }
}

JNIEnv* getJNIEnv() {
    JNIEnv* env = nullptr;
    jint result = globalJavaVM->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (result == JNI_OK) {
        return env;
    } else if (result == JNI_EDETACHED) {
        if (globalJavaVM->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return nullptr;
        }
        return env;
    } else {
        return nullptr;
    }
}


void presentationChangedCallback(void *callbackCtx) {
    std::lock_guard<std::mutex> lock(mtx);

    auto callback = (jobject)callbackCtx;
    JNIEnv *env = getJNIEnv();

    if (env == nullptr) {
        ALOGE("presentationChangedCallback failed. Couldn't get JNIEnv.");
        return;
    }

    if (callback != nullptr) {
        jclass clazz = env->GetObjectClass(callback);
        jmethodID methodId = env->GetMethodID(clazz, "onPresentationsChanged", "()V");

        env->CallVoidMethod(callback, methodId);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dolby_android_alps_alpsnative_DefaultAlpsNative_setPresentationsChangedCallback(JNIEnv *env,
                                                                                         jobject thiz,
                                                                                         jlong alpsHandle,
                                                                                         jobject callback) {
    std::lock_guard<std::mutex> lock(mtx);
    auto *alps = (alps_ctx*)(uintptr_t)alpsHandle;

    alps_set_presentations_changed_callback(
            alps,
            presentationChangedCallback,
            env->NewGlobalRef(callback)
    );
}