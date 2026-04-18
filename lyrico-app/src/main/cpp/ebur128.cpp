#include <jni.h>
#include <cmath>
#include <algorithm>
#include "ebur128/ebur128.h"

extern "C" {

// 初始化状态
JNIEXPORT jlong JNICALL
Java_com_lonx_lyrico_utils_LibEbuR128_initNative(JNIEnv *env, jobject thiz, jint channels, jint sampleRate) {
    // 开启 响度计算(MODE_I) 和 真实峰值计算(MODE_TRUE_PEAK)
    ebur128_state* state = ebur128_init((size_t)channels, (size_t)sampleRate,
                                        EBUR128_MODE_I | EBUR128_MODE_TRUE_PEAK);
    return reinterpret_cast<jlong>(state);
}

// 释放内存
JNIEXPORT void JNICALL
Java_com_lonx_lyrico_utils_LibEbuR128_destroyNative(JNIEnv *env, jobject thiz, jlong statePtr) {
    auto* state = reinterpret_cast<ebur128_state*>(statePtr);
    if (state != nullptr) {
        ebur128_destroy(&state);
    }
}

// 处理音频帧 (直接内存，零拷贝)
JNIEXPORT void JNICALL
Java_com_lonx_lyrico_utils_LibEbuR128_processDirectNative(JNIEnv *env, jobject thiz, jlong statePtr,
                                                          jobject directBuffer, jint format, jint frames) {
    auto* state = reinterpret_cast<ebur128_state*>(statePtr);
    if (!state || !directBuffer || frames <= 0) return;

    void* bufferAddress = env->GetDirectBufferAddress(directBuffer);
    if (!bufferAddress) return;

    if (format == 1) { // 1 = Short PCM
        ebur128_add_frames_short(state, static_cast<const short*>(bufferAddress), (size_t)frames);
    } else if (format == 2) { // 2 = Float (32-bit Float PCM)
        ebur128_add_frames_float(state, static_cast<const float*>(bufferAddress), (size_t)frames);
    }
}

// 获取最终的 LUFS 响度
JNIEXPORT jdouble JNICALL
Java_com_lonx_lyrico_utils_LibEbuR128_getLoudnessNative(JNIEnv *env, jobject thiz, jlong statePtr) {
    auto* state = reinterpret_cast<ebur128_state*>(statePtr);
    if (!state) return -70.0;

    double loudness = -70.0; // 默认极静门限
    // 增加错误码校验，仅在 SUCCESS 时采纳结果
    if (ebur128_loudness_global(state, &loudness) != EBUR128_SUCCESS) {
        return -70.0;
    }
    return loudness;
}

// 获取 True Peak (真实峰值)
JNIEXPORT jdouble JNICALL
Java_com_lonx_lyrico_utils_LibEbuR128_getTruePeakNative(JNIEnv *env, jobject thiz, jlong statePtr, jint channels) {
    auto* state = reinterpret_cast<ebur128_state*>(statePtr);
    if (!state || channels <= 0) return 0.0;
    double maxPeak = 0.0;
    for (int c = 0; c < channels; ++c) {
        double channelPeak = 0.0;
        // 增加错误码校验，防止读取未计算完成的脏数据
        if (ebur128_true_peak(state, (unsigned int)c, &channelPeak) == EBUR128_SUCCESS) {
            maxPeak = std::max(maxPeak, channelPeak);
        }
    }
    return maxPeak;
}

}