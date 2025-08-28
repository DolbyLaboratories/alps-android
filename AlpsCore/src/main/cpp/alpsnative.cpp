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

static JavaVM *globalJavaVM = nullptr;
static std::mutex mtx;
static alps_ctx *ctx;
static void *allocatedMemory = nullptr;
static jobject presChangedCallbackKotlin = nullptr;

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
Java_com_dolby_android_alps_alpsnative_AlpsNativeImpl_getVersion(JNIEnv *env, jobject thiz) {
    jstring version = env->NewStringUTF(alps_version());
    return version;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_dolby_android_alps_alpsnative_AlpsNativeImpl_queryMem(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(mtx);

    size_t memorySize;
    alps_ret ret = alps_query_mem(&memorySize);
    if (ret == ALPS_RET_OK) {
        ALOGI("alps_query_mem successful, size: %zu", memorySize);
        if (allocatedMemory != nullptr) {
            free(allocatedMemory);
        }
        allocatedMemory = malloc(memorySize);
        if (allocatedMemory == nullptr) {
            ALOGE("Failed to allocate memory");
            throwJniException(env, "Failed to allocate memory");
        }
        return memorySize;
    } else {
        ALOGE("alps_query_mem failed, error: %d", ret);
        handleNativeError(env, ret);
    }

    return -1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dolby_android_alps_alpsnative_AlpsNativeImpl_initialize(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(mtx);

    if (allocatedMemory == nullptr) {
        ALOGE("alps_init failed, memory not allocated");
        throwJniException(env, "Memory not allocated when initializing");
    }

    alps_ret ret = alps_init(&ctx, allocatedMemory);
    if (ret == ALPS_RET_OK) {
        ALOGI("alps_init successful");
    } else {
        ALOGE("alps_init failed, error: %d", ret);
        handleNativeError(env, ret);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dolby_android_alps_alpsnative_AlpsNativeImpl_destroy(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(mtx);
    alps_destroy(ctx);
    if (allocatedMemory != nullptr) {
        free(allocatedMemory);
        allocatedMemory = nullptr;
    }
    ALOGI("Alps destroyed");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dolby_android_alps_alpsnative_AlpsNativeImpl_processIsobmffSegment(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jobject buffer) {
    std::lock_guard<std::mutex> lock(mtx);

    auto bufferPtr = reinterpret_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    auto bufferSize = (int32_t)env->GetDirectBufferCapacity(buffer);

    alps_ret ret = alps_process_isobmff_segment(ctx, bufferPtr, bufferSize);

    if (ret == ALPS_RET_OK) {
        ALOGI("alps_process_isobmff_segment successful");
    } else {
        ALOGE("alps_process_isobmff_segment failed, error: %d", ret);
        handleNativeError(env, ret);
    }
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_dolby_android_alps_alpsnative_AlpsNativeImpl_getPresentations(JNIEnv *env, jobject thiz) {
    alps_presentation *nativePresentationsList = nullptr;
    size_t presentationsCount;

    alps_ret ret = alps_get_presentations(ctx, &nativePresentationsList, &presentationsCount);
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
Java_com_dolby_android_alps_alpsnative_AlpsNativeImpl_getActivePresentationId(JNIEnv *env, jobject thiz) {
    jint activeIndex;

    alps_ret ret = alps_get_active_presentation_id(ctx, &activeIndex);
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
Java_com_dolby_android_alps_alpsnative_AlpsNativeImpl_setActivePresentationId(JNIEnv *env, jobject thiz,
                                                                         jint id) {
    alps_ret ret = alps_set_active_presentation_id(ctx, id);
    if (ret == ALPS_RET_OK) {
        ALOGI("alps_set_active_presentation_id successful");
    } else {
        ALOGE("alps_set_active_presentation_id failed, error: %d", ret);
        handleNativeError(env, ret);
    }
}

void presentationChangedCallback(void *callbackCtx) {
    JNIEnv *env;
    bool attached = false;

    if (globalJavaVM->GetEnv((void**)&env, JNI_VERSION_1_6)) {
        globalJavaVM->AttachCurrentThread(&env, nullptr);
        attached = true;
    }

    if (presChangedCallbackKotlin != nullptr) {
        jclass clazz = env->GetObjectClass(presChangedCallbackKotlin);
        jmethodID methodId = env->GetMethodID(clazz, "onPresentationsChanged", "()V");

        env->CallVoidMethod(presChangedCallbackKotlin, methodId);
    }

    if (attached) {
        globalJavaVM->DetachCurrentThread();
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dolby_android_alps_alpsnative_AlpsNativeImpl_setPresentationsChangedCallback(JNIEnv *env,
                                                                                     jobject thiz,
                                                                                     jobject callback) {
    std::lock_guard<std::mutex> lock(mtx);
    env->GetJavaVM(&globalJavaVM);

    if (presChangedCallbackKotlin != nullptr) {
        env->DeleteGlobalRef(presChangedCallbackKotlin);
    }
    presChangedCallbackKotlin = env->NewGlobalRef(callback);

    alps_set_presentations_changed_callback(
            ctx,
            presentationChangedCallback,
            nullptr
    );
}