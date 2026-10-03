#include <jni.h>
#include <string>
#include <vector>

#include "whisper.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_com_daboua_captions_WhisperNative_init(
        JNIEnv* env,
        jobject,
        jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    struct whisper_context_params params =
        whisper_context_default_params();

    params.use_gpu = false;

    struct whisper_context* ctx =
        whisper_init_from_file_with_params(path, params);

    env->ReleaseStringUTFChars(modelPath, path);

    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_daboua_captions_WhisperNative_free(
        JNIEnv*,
        jobject,
        jlong contextPtr) {

    auto* ctx =
        reinterpret_cast<whisper_context*>(contextPtr);

    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_daboua_captions_WhisperNative_transcribe(
        JNIEnv* env,
        jobject,
        jlong contextPtr,
        jfloatArray audioArray,
        jint numThreads) {

    auto* ctx =
        reinterpret_cast<whisper_context*>(contextPtr);

    if (ctx == nullptr) {
        return -1;
    }

    jfloat* audio =
        env->GetFloatArrayElements(
            audioArray,
            nullptr
        );

    const jsize audioLength =
        env->GetArrayLength(audioArray);

    whisper_full_params params =
        whisper_full_default_params(
            WHISPER_SAMPLING_GREEDY
        );

    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = true;
    params.print_special = false;

    params.translate = false;
    params.language = "fa";

    params.n_threads = numThreads;
    params.no_context = true;
    params.single_segment = false;

    params.temperature = 0.0f;

    const int result =
        whisper_full(
            ctx,
            params,
            audio,
            audioLength
        );

    env->ReleaseFloatArrayElements(
        audioArray,
        audio,
        JNI_ABORT
    );

    return result;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_daboua_captions_WhisperNative_getSegmentCount(
        JNIEnv*,
        jobject,
        jlong contextPtr) {

    auto* ctx =
        reinterpret_cast<whisper_context*>(contextPtr);

    if (ctx == nullptr) {
        return 0;
    }

    return whisper_full_n_segments(ctx);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_daboua_captions_WhisperNative_getSegmentText(
        JNIEnv* env,
        jobject,
        jlong contextPtr,
        jint index) {

    auto* ctx =
        reinterpret_cast<whisper_context*>(contextPtr);

    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    const char* text =
        whisper_full_get_segment_text(
            ctx,
            index
        );

    return env->NewStringUTF(
        text != nullptr ? text : ""
    );
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_daboua_captions_WhisperNative_getSegmentStart(
        JNIEnv*,
        jobject,
        jlong contextPtr,
        jint index) {

    auto* ctx =
        reinterpret_cast<whisper_context*>(contextPtr);

    if (ctx == nullptr) {
        return 0;
    }

    return whisper_full_get_segment_t0(
        ctx,
        index
    );
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_daboua_captions_WhisperNative_getSegmentEnd(
        JNIEnv*,
        jobject,
        jlong contextPtr,
        jint index) {

    auto* ctx =
        reinterpret_cast<whisper_context*>(contextPtr);

    if (ctx == nullptr) {
        return 0;
    }

    return whisper_full_get_segment_t1(
        ctx,
        index
    );
}
