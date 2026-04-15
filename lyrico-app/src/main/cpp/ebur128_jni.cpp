#include <jni.h>
#include <cmath>
#include <algorithm>
#include "ebur128/ebur128_jni.h"

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
    // GetDirectBufferAddress 极其高效，直接拿到底层指针
    void* bufferAddress = env->GetDirectBufferAddress(directBuffer);

    if (bufferAddress == nullptr) return;

    if (format == 1) { // 1 = Short (16-bit PCM)
        ebur128_add_frames_short(state, static_cast<const short*>(bufferAddress), (size_t)frames);
    } else if (format == 2) { // 2 = Float (32-bit Float PCM)
        ebur128_add_frames_float(state, static_cast<const float*>(bufferAddress), (size_t)frames);
    }
}

// 获取最终的 LUFS 响度
JNIEXPORT jdouble JNICALL
Java_com_lonx_lyrico_utils_LibEbuR128_getLoudnessNative(JNIEnv *env, jobject thiz, jlong statePtr) {
    auto* state = reinterpret_cast<ebur128_state*>(statePtr);
    double loudness = 0.0;
    ebur128_loudness_global(state, &loudness);
    return loudness;
}

// 获取 True Peak (真实峰值)
JNIEXPORT jdouble JNICALL
Java_com_lonx_lyrico_utils_LibEbuR128_getTruePeakNative(JNIEnv *env, jobject thiz, jlong statePtr) {
    auto* state = reinterpret_cast<ebur128_state*>(statePtr);
    double maxPeak = 0.0;
    // 遍历所有声道，找出最大的 True Peak
    for (size_t c = 0; c < state->channels; ++c) {
        double channelPeak = 0.0;
        ebur128_true_peak(state, c, &channelPeak);
        maxPeak = std::max(maxPeak, channelPeak);
    }
    return maxPeak;
}

}