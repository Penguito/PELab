#include "native_renderer.h"

#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <android/log.h>

namespace pelab {
namespace {

constexpr char kLogTag[] = "PELabEGL";

void LogEglError(const char* operation) {
    __android_log_print(
            ANDROID_LOG_ERROR,
            kLogTag,
            "%s failed with EGL error 0x%x",
            operation,
            eglGetError());
}

}  // namespace

NativeRenderer::~NativeRenderer() {
    Release();
}

bool NativeRenderer::Init(ANativeWindow* output_window) {

    // create native window
    output_window_ = output_window;
    if (output_window_ == nullptr) {
        return false;
    }

    // get EGL display
    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY) {
        LogEglError("eglGetDisplay");
        return false;
    }

    // init EGL
    if (eglInitialize(display_, nullptr, nullptr) != EGL_TRUE) {
        LogEglError("eglInitialize");
        return false;
    }

    // bind GLES API.
    if (eglBindAPI(EGL_OPENGL_ES_API) != EGL_TRUE) {
        LogEglError("eglBindAPI");
        return false;
    }

    // create window config for GL3
    const EGLint config_attributes[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_NONE,
    };
    EGLint config_count = 0;
    if (eglChooseConfig(
                display_,
                config_attributes,
                &config_,
                1,
                &config_count) != EGL_TRUE
            || config_count == 0) {
        LogEglError("eglChooseConfig");
        return false;
    }

    // create GL3 context
    const EGLint context_attributes[] = {
            EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL_NONE,
    };
    context_ = eglCreateContext(
            display_,
            config_,
            EGL_NO_CONTEXT,
            context_attributes);
    if (context_ == EGL_NO_CONTEXT) {
        LogEglError("eglCreateContext");
        return false;
    }

    // create window surface
    surface_ = eglCreateWindowSurface(
            display_,
            config_,
            output_window_,
            nullptr);
    if (surface_ == EGL_NO_SURFACE) {
        LogEglError("eglCreateWindowSurface");
        return false;
    }

    // bind context and surface to render thread
    if (eglMakeCurrent(display_, surface_, surface_, context_) != EGL_TRUE) {
        LogEglError("eglMakeCurrent");
        return false;
    }

    // set viewport to the window size
    const int width = ANativeWindow_getWidth(output_window_);
    const int height = ANativeWindow_getHeight(output_window_);
    glViewport(0, 0, width, height);

    // clear and display first frame
    glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
    glClear(GL_COLOR_BUFFER_BIT);
    if (eglSwapBuffers(display_, surface_) != EGL_TRUE) {
        LogEglError("eglSwapBuffers");
        return false;
    }

    // Log current GL version
    const auto* gl_version =
            reinterpret_cast<const char*>(glGetString(GL_VERSION));
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "EGL environment ready: surface=%dx%d, GL=%s",
            width,
            height,
            gl_version == nullptr ? "unknown" : gl_version);
    return true;
}

void NativeRenderer::Release() {
    if (display_ != EGL_NO_DISPLAY) {

        // unbind current EGL context
        eglMakeCurrent(
                display_,
                EGL_NO_SURFACE,
                EGL_NO_SURFACE,
                EGL_NO_CONTEXT);

        // destroy EGL surface
        if (surface_ != EGL_NO_SURFACE) {
            eglDestroySurface(display_, surface_);
        }

        // destroy EGL context
        if (context_ != EGL_NO_CONTEXT) {
            eglDestroyContext(display_, context_);
        }

        // terminate EGL display
        eglTerminate(display_);

        // release EGL thread state
        eglReleaseThread();
    }

    // release native window
    if (output_window_ != nullptr) {
        ANativeWindow_release(output_window_);
    }

    output_window_ = nullptr;
    display_ = EGL_NO_DISPLAY;
    config_ = nullptr;
    context_ = EGL_NO_CONTEXT;
    surface_ = EGL_NO_SURFACE;
}

}  // namespace pelab
