#include <jni.h>
#include <string>

#include "whisper.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_com_daboua_captions_WhisperNative_init(
        JNIEnv* env,
        jobject,
        jstring modelPath) {

    if (modelPath == nullptr) {
        return 0;
    }

    const char* path =
        env->GetStringUTFChars(
            modelPath,
            nullptr
        );

    if (path == nullptr) {
        return 0;
    }

    struct whisper_context_params ctxParams =
        whisper_context_default_params();

    ctxParams.use_gpu = false;

    struct whisper_context* ctx =
        whisper_init_from_file_with_params(
            path,
            ctxParams
        );

    env->ReleaseStringUTFChars(
        modelPath,
        path
    );

    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_daboua_captions_WhisperNative_free(
        JNIEnv*,
        jobject,
        jlong contextPtr) {

    auto* ctx =
        reinterpret_cast<whisper_context*>(
            contextPtr
        );

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
        reinterpret_cast<whisper_context*>(
            contextPtr
        );

    if (ctx == nullptr || audioArray == nullptr) {
        return -1;
    }

    const jsize audioLength =
        env->GetArrayLength(audioArray);

    if (audioLength <= 0) {
        return -2;
    }

    jfloat* audio =
        env->GetFloatArrayElements(
            audioArray,
            nullptr
        );

    if (audio == nullptr) {
        return -3;
    }

    whisper_full_params params =
        whisper_full_default_params(
            WHISPER_SAMPLING_GREEDY
        );

    /*
     * خروجی را برای Auto Caption تنظیم می‌کنیم.
     */

    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = true;
    params.print_special = false;

    params.translate = false;

    /*
     * فارسی.
     */
    params.language = "fa";

    /*
     * اجازه می‌دهیم Whisper از متن قبلی
     * برای پیوستگی بهتر استفاده کند.
     */
    params.no_context = false;

    /*
     * حتماً چند Segment تولید شود.
     */
    params.single_segment = false;

    /*
     * Timestamp در سطح Token فعال می‌شود
     * تا segmentation دقیق‌تر شود.
     */
    params.token_timestamps = true;

    /*
     * کپشن‌ها بیش از حد طولانی نشوند.
     *
     * whisper.cpp وقتی token timestamps
     * فعال باشد، می‌تواند Segmentهای طولانی
     * را به قطعات کوتاه‌تر تقسیم کند.
     */
    params.max_len = 42;
    params.split_on_word = true;

    /*
     * تعداد Tokenهای هر Segment را هم
     * محدود می‌کنیم تا جمله‌های خیلی بزرگ
     * تولید نشوند.
     */
    params.max_tokens = 64;

    /*
     * تعداد Thread.
     */
    params.n_threads =
        numThreads > 0
            ? numThreads
            : 4;

    if (params.n_threads > 8) {
        params.n_threads = 8;
    }

    /*
     * دقت پایدارتر.
     */
    params.temperature = 0.0f;
    params.temperature_inc = 0.2f;

    /*
     * آستانه تشخیص عدم وجود گفتار.
     */
    params.no_speech_thold = 0.60f;

    /*
     * اجرای کامل فایل صوتی.
     */
    params.offset_ms = 0;
    params.duration_ms = 0;

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
        reinterpret_cast<whisper_context*>(
            contextPtr
        );

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
        reinterpret_cast<whisper_context*>(
            contextPtr
        );

    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    const int count =
        whisper_full_n_segments(ctx);

    if (index < 0 || index >= count) {
        return env->NewStringUTF("");
    }

    const char* text =
        whisper_full_get_segment_text(
            ctx,
            index
        );

    if (text == nullptr) {
        return env->NewStringUTF("");
    }

    return env->NewStringUTF(text);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_daboua_captions_WhisperNative_getSegmentStart(
        JNIEnv*,
        jobject,
        jlong contextPtr,
        jint index) {

    auto* ctx =
        reinterpret_cast<whisper_context*>(
            contextPtr
        );

    if (ctx == nullptr) {
        return 0;
    }

    const int count =
        whisper_full_n_segments(ctx);

    if (index < 0 || index >= count) {
        return 0;
    }

    /*
     * whisper.cpp timestampها را در واحد
     * 10 میلی‌ثانیه برمی‌گرداند.
     */
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
        reinterpret_cast<whisper_context*>(
            contextPtr
        );

    if (ctx == nullptr) {
        return 0;
    }

    const int count =
        whisper_full_n_segments(ctx);

    if (index < 0 || index >= count) {
        return 0;
    }

    return whisper_full_get_segment_t1(
        ctx,
        index
    );
}
