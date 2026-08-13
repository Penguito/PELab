#include "native_renderer.h"

#include <android/bitmap.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include <memory>

namespace {

pelab::NativeRenderer* FromHandle(jlong handle) {
    return reinterpret_cast<pelab::NativeRenderer*>(handle);
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_penguito_effectlab_render_sdk_RenderEngine_nativeGetBridgeInfo(
        JNIEnv* env,
        jclass) {
    __android_log_print(
            ANDROID_LOG_INFO,
            "PELabNative",
            "Java -> JNI -> C++ bridge call success");
    return env->NewStringUTF("PELab Native Bridge ready");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_penguito_effectlab_render_sdk_RenderEngine_nativeInitRenderer(
        JNIEnv* env,
        jclass,
        jobject output_surface,
        jint normalized_width,
        jint normalized_height) {
    if (output_surface == nullptr) {
        return 0;
    }

    ANativeWindow* output_window =
            ANativeWindow_fromSurface(env, output_surface);
    auto renderer = std::make_unique<pelab::NativeRenderer>();
    if (!renderer->Init(
                output_window,
                normalized_width,
                normalized_height)) {
        return 0;
    }
    return reinterpret_cast<jlong>(renderer.release());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_penguito_effectlab_render_sdk_RenderEngine_nativeGetInputTexture(
        JNIEnv*,
        jclass,
        jlong handle) {
    return static_cast<jint>(FromHandle(handle)->GetInputTexture());
}

extern "C" JNIEXPORT void JNICALL
Java_com_penguito_effectlab_render_sdk_RenderEngine_nativeRenderFrame(
        JNIEnv* env,
        jclass,
        jlong handle,
        jfloatArray texture_matrix) {
    jfloat matrix[16];
    env->GetFloatArrayRegion(texture_matrix, 0, 16, matrix);
    FromHandle(handle)->RenderFrame(matrix);
}

extern "C" JNIEXPORT void JNICALL
Java_com_penguito_effectlab_render_sdk_RenderEngine_nativeSetImageParams(
        JNIEnv*,
        jclass,
        jlong handle,
        jfloat brightness,
        jfloat warmth) {
    FromHandle(handle)->SetImageParams(brightness, warmth);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_penguito_effectlab_render_sdk_RenderEngine_nativeSetLutTexture(
        JNIEnv* env,
        jclass,
        jlong handle,
        jobject lut_bitmap) {

    if (lut_bitmap == nullptr) {
        return FromHandle(handle)->SetLutTexture(nullptr, 0, 0, 0) ? JNI_TRUE : JNI_FALSE;
    }

    AndroidBitmapInfo bitmap_info{};
    if (AndroidBitmap_getInfo(env, lut_bitmap, &bitmap_info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return JNI_FALSE;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, lut_bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || pixels == nullptr) {
        return JNI_FALSE;
    }
    const bool uploaded = FromHandle(handle)->SetLutTexture(pixels,
                                                            static_cast<int>(bitmap_info.width),
                                                            static_cast<int>(bitmap_info.height),
                                                            static_cast<int>(bitmap_info.stride));
    AndroidBitmap_unlockPixels(env, lut_bitmap);
    return uploaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_penguito_effectlab_render_sdk_RenderEngine_nativeDestroyRenderer(
        JNIEnv*,
        jclass,
        jlong handle) {
    delete FromHandle(handle);
}
