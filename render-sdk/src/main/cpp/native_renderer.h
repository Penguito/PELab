#pragma once

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

namespace pelab {

class NativeRenderer final {
public:
    NativeRenderer() = default;
    ~NativeRenderer();

    NativeRenderer(const NativeRenderer&) = delete;
    NativeRenderer& operator=(const NativeRenderer&) = delete;

    bool Init(ANativeWindow* output_window, int normalized_width, int normalized_height);
    GLuint GetInputTexture() const;
    void SetImageParams(float brightness, float warmth);
    void RenderFrame(const float* texture_matrix);

private:
    bool CreateInputTexture();
    bool CreateNormalizedTarget();
    bool CreateNormalizeProgram();
    bool CreatePreviewProgram();
    bool CreateVertexBuffer();
    void RenderToNormalizedTarget(const float* texture_matrix);
    void RenderToOutput();
    void Release();

    ANativeWindow* output_window_ = nullptr;
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLConfig config_ = nullptr;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface surface_ = EGL_NO_SURFACE;
    GLuint input_texture_ = 0;
    GLuint normalized_texture_ = 0;
    GLuint normalized_framebuffer_ = 0;
    GLuint normalize_program_ = 0;
    GLuint preview_program_ = 0;
    GLuint vertex_array_ = 0;
    GLuint vertex_buffer_ = 0;
    GLint normalize_texture_matrix_location_ = -1;
    GLint normalize_input_texture_location_ = -1;
    GLint preview_texture_location_ = -1;
    float brightness_ = 0.0F;
    float warmth_ = 0.0F;
    int output_width_ = 0;
    int output_height_ = 0;
    int normalized_width_ = 0;
    int normalized_height_ = 0;
};

}  // namespace pelab
