/***************************************************************************************************
 *                Copyright (C) 2024-2026 by Dolby International AB.
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
        case ALPS_RET_E_NO_AC4_TRACK:
            exceptionClassName = "NoAc4Track";
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
        jclass labelClz = env->FindClass("com/dolby/android/alps/models/Label");
        jclass kindClz = env->FindClass("com/dolby/android/alps/models/Kind");
        jclass floatClz = env->FindClass("java/lang/Float");

        jmethodID presentationMid = env->GetMethodID(
                presentationClz,
                "<init>",
                "(ILjava/lang/String;Ljava/util/List;Ljava/util/List;IILjava/lang/Float;)V"
        );


jmethodID labelMid = env->GetMethodID(
                labelClz,
                "<init>", "(ILjava/lang/String;Ljava/lang/String;Z)V"
        );

        jmethodID kindMid = env->GetMethodID(
                kindClz,
                "<init>", "(Ljava/lang/String;Ljava/lang/String;)V"
        );
        jmethodID floatMid = env->GetMethodID(
                floatClz,
                "<init>",
                "(F)V"
        );

        for (int i = 0; i < presentationsCount; i++) {
            alps_presentation nativePresentation = nativePresentationsList[i];

            // Labels
            jint labelsCount = nativePresentation.labels_count;
            jobject labelsList = env->NewObject(arrayListClass, arrayListConstructor);
            for (int label_index = 0; label_index < labelsCount; label_index++) {
                alps_presentation_label current_label = nativePresentation.labels[label_index];
                jstring label = env->NewStringUTF(current_label.label);
                jstring language = env->NewStringUTF(current_label.language);
                jint label_id = current_label.label_id;
                jboolean is_group_label = current_label.is_group_label != 0;

                jobject label_obj = env->NewObject(labelClz, labelMid,
                        label_id,
                        language,
                        label,
                        is_group_label);

                env->CallBooleanMethod(labelsList, arrayListAdd, label_obj);
                env->DeleteLocalRef(language);
                env->DeleteLocalRef(label);
                env->DeleteLocalRef(label_obj);

            }

            // Kinds
            jint kindsCount = nativePresentation.kinds_count;
            jobject kindsList = env->NewObject(arrayListClass, arrayListConstructor);
            for (int kind_index = 0; kind_index < kindsCount; kind_index++) {
                alps_presentation_kind current_kind = nativePresentation.kinds[kind_index];
                jstring schemeUri = env->NewStringUTF(current_kind.scheme_uri);
                jstring value = env->NewStringUTF(current_kind.value);

                jobject kind_obj = env->NewObject(kindClz, kindMid,
                        schemeUri,
                        value);

                env->CallBooleanMethod(kindsList, arrayListAdd, kind_obj);
                env->DeleteLocalRef(schemeUri);
                env->DeleteLocalRef(value);
                env->DeleteLocalRef(kind_obj);
            }



            jstring extendedLanguage = env->NewStringUTF(nativePresentation.extended_language);
            jint selectionPriority = nativePresentation.selection_priority;
            jint audioRenderingIndication = nativePresentation.audio_rendering_indication;
            jobject dialogGain = nullptr;
            if (nativePresentation.dialog_gain_present){
                dialogGain = env->NewObject( floatClz, floatMid, nativePresentation.dialog_gain);
            }

            jobject presentation = env->NewObject(presentationClz, presentationMid,
                                                  nativePresentation.id,
                                                  extendedLanguage,
                                                  kindsList,
                                                  labelsList,
                                                  selectionPriority,
                                                  audioRenderingIndication,
                                                  dialogGain);

            env->CallBooleanMethod(presentationsList, arrayListAdd, presentation);
            env->DeleteLocalRef(presentation);
            if (dialogGain != nullptr) {
                env-> DeleteLocalRef(dialogGain);
            }
            env->DeleteLocalRef(kindsList);
            env->DeleteLocalRef(labelsList);
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
