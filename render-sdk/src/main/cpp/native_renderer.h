#pragma once

#include <EGL/egl.h>
#include <android/native_window.h>

namespace pelab {

class NativeRenderer final {
public:
    NativeRenderer() = default;
    ~NativeRenderer();

    NativeRenderer(const NativeRenderer&) = delete;
    NativeRenderer& operator=(const NativeRenderer&) = delete;

    bool Init(ANativeWindow* output_window);

private:
    void Release();

    ANativeWindow* output_window_ = nullptr;
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLConfig config_ = nullptr;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface surface_ = EGL_NO_SURFACE;
};

}  // namespace pelab
