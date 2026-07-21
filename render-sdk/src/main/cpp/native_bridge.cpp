#include <android/log.h>
#include <jni.h>


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
