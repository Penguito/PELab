#pragma once

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

#include "bitmap_input_pass.h"
#include "filter_pass.h"
#include "image_pass.h"
#include "oes_input_pass.h"

namespace pelab {

class NativeRenderer final {
public:
    NativeRenderer() = default;
    ~NativeRenderer();

    NativeRenderer(const NativeRenderer&) = delete;
    NativeRenderer& operator=(const NativeRenderer&) = delete;

    bool Init(ANativeWindow* output_window, int normalized_width, int normalized_height);
    GLuint GetCameraInputTexture() const;
    bool SetBitmap(const void* pixels, int width, int height, int row_stride);
    void SetImageParams(float brightness, float warmth);
    bool SetLutTexture(const void* pixels, int width, int height, int row_stride);
    void RenderCameraFrame(const float* texture_matrix);
    void RenderBitmap();
    bool CaptureFrame(void* pixels, int row_stride) const;

private:
    bool CreateNormalizedTarget();
    bool CreatePreviewProgram();
    bool CreateVertexBuffer();
    void RenderPostProcessing();
    void RenderToOutput(GLuint preview_texture) const;
    void Release();

    ANativeWindow* output_window_ = nullptr;
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLConfig config_ = nullptr;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface surface_ = EGL_NO_SURFACE;
    GLuint normalized_texture_ = 0;
    GLuint normalized_framebuffer_ = 0;
    GLuint final_texture_ = 0;
    GLuint preview_program_ = 0;
    GLuint vertex_array_ = 0;
    GLuint vertex_buffer_ = 0;
    GLint preview_texture_location_ = -1;
    OesInputPass oes_input_pass_;
    BitmapInputPass bitmap_input_pass_;
    ImagePass image_pass_;
    FilterPass filter_pass_;
    int output_width_ = 0;
    int output_height_ = 0;
    int normalized_width_ = 0;
    int normalized_height_ = 0;
};

}  // namespace pelab
