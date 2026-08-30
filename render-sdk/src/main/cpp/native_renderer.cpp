#include "native_renderer.h"

#include "gl_utils.h"

#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <android/log.h>

namespace pelab {
namespace {

constexpr char kLogTag[] = "PELabEGL";

constexpr char kImageVertexShaderSource[] = R"(#version 300 es

layout(location = 0) in vec2 position;
layout(location = 1) in vec2 textureCoordinate;

out vec2 imageTextureCoordinate;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    imageTextureCoordinate = textureCoordinate;
}
)";

constexpr char kPreviewFragmentShaderSource[] = R"(#version 300 es

precision mediump float;

uniform sampler2D previewTexture;

in vec2 imageTextureCoordinate;
out vec4 outputColor;

void main() {
    outputColor = texture(previewTexture, imageTextureCoordinate);
}
)";

constexpr GLfloat kRenderVertices[] = {
        -1.0F, -1.0F, 0.0F, 0.0F,
        1.0F, -1.0F, 1.0F, 0.0F,
        -1.0F, 1.0F, 0.0F, 1.0F,
        1.0F, 1.0F, 1.0F, 1.0F,
};

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

bool NativeRenderer::Init(
        ANativeWindow* output_window,
        int normalized_width,
        int normalized_height) {

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

    normalized_width_ = normalized_width;
    normalized_height_ = normalized_height;

    // 1. create shared normalized target
    if (!CreateNormalizedTarget()) {
        return false;
    }

    // 2. init OES input pass
    if (!oes_input_pass_.Init()) {
        return false;
    }

    // 3. init bitmap input pass
    if (!bitmap_input_pass_.Init()) {
        return false;
    }

    // 4. init image pass
    if (!image_pass_.Init(normalized_width_, normalized_height_)) {
        return false;
    }

    // 5. init filter pass
    if (!filter_pass_.Init(normalized_width_, normalized_height_)) {
        return false;
    }

    // 6. create preview program
    if (!CreatePreviewProgram()) {
        return false;
    }

    // 7. create vertex buffer
    if (!CreateVertexBuffer()) {
        return false;
    }

    // set viewport to the window size
    output_width_ = ANativeWindow_getWidth(output_window_);
    output_height_ = ANativeWindow_getHeight(output_window_);
    glViewport(0, 0, output_width_, output_height_);

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
            "EGL environment ready: surface=%dx%d, normalized=%dx%d, GL=%s",
            output_width_,
            output_height_,
            normalized_width_,
            normalized_height_,
            gl_version == nullptr ? "unknown" : gl_version);
    return true;
}

GLuint NativeRenderer::GetCameraInputTexture() const {
    return oes_input_pass_.GetInputTexture();
}

bool NativeRenderer::SetBitmap(
        const void* pixels,
        int width,
        int height,
        int row_stride) {
    return bitmap_input_pass_.SetBitmap(pixels, width, height, row_stride);
}

void NativeRenderer::SetImageParams(float brightness, float warmth) {
    image_pass_.SetParams(brightness, warmth);
}

bool NativeRenderer::SetLutTexture(const void* pixels, int width, int height, int row_stride) {
    return filter_pass_.SetLutTexture(pixels, width, height, row_stride);
}

void NativeRenderer::RenderCameraFrame(const float* texture_matrix) {

    // Pass OES Input: OES texture -> normalized target
    oes_input_pass_.Render(
            normalized_framebuffer_,
            normalized_width_,
            normalized_height_,
            vertex_array_,
            texture_matrix);
    RenderPostProcessing();
}

void NativeRenderer::RenderBitmap() {

    // Pass Bitmap Input: bitmap texture -> normalized target
    bitmap_input_pass_.Render(
            normalized_framebuffer_,
            normalized_width_,
            normalized_height_,
            vertex_array_);
    RenderPostProcessing();
}

void NativeRenderer::RenderPostProcessing() {

    // Pass Image Adjustment: normalized buffer -> image buffer
    image_pass_.Render(normalized_texture_, vertex_array_);
    GLuint output_texture = image_pass_.GetOutputTexture();

    // Pass Filter: image buffer -> filter buffer
    if (filter_pass_.IsEnabled()) {
        filter_pass_.Render(output_texture, vertex_array_);
        output_texture = filter_pass_.GetOutputTexture();
    }
    final_texture_ = output_texture;

    // Pass Output: last buffer -> surfaceView
    RenderToOutput(final_texture_);

    if (eglSwapBuffers(display_, surface_) != EGL_TRUE) {
        LogEglError("eglSwapBuffers");
    }
}

bool NativeRenderer::CaptureFrame(void* pixels, int row_stride) const {

    if (final_texture_ == 0) {
        return false;
    }

    // attach the final texture for reading
    GLuint capture_framebuffer = 0;
    glGenFramebuffers(1, &capture_framebuffer);
    glBindFramebuffer(GL_FRAMEBUFFER, capture_framebuffer);
    glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D,
            final_texture_,
            0);

    // read the processed RGBA pixels
    glPixelStorei(GL_PACK_ROW_LENGTH, row_stride / 4);
    glReadPixels(0, 0, normalized_width_, normalized_height_, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glPixelStorei(GL_PACK_ROW_LENGTH, 0);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &capture_framebuffer);
    return true;
}

void NativeRenderer::RenderToOutput(GLuint preview_texture) const {

    // bind framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, output_width_, output_height_);

    // use program
    glUseProgram(preview_program_);
    glBindVertexArray(vertex_array_);

    // bind final texture
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, preview_texture);
    glUniform1i(preview_texture_location_, 0);

    // render preview texture to surfaceView
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_2D, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}

bool NativeRenderer::CreateNormalizedTarget() {

    // create RGBA texture
    glGenTextures(1, &normalized_texture_);
    glBindTexture(GL_TEXTURE_2D, normalized_texture_);

    // allocate texture storage
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA8,
            normalized_width_,
            normalized_height_,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    // create framebuffer and attach texture
    glGenFramebuffers(1, &normalized_framebuffer_);
    glBindFramebuffer(GL_FRAMEBUFFER, normalized_framebuffer_);
    glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D,
            normalized_texture_,
            0);

    // verify framebuffer
    const GLenum framebuffer_status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);

    if (framebuffer_status != GL_FRAMEBUFFER_COMPLETE) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "Framebuffer creation failed: 0x%x",
                framebuffer_status);
        return false;
    }

    return true;
}

bool NativeRenderer::CreatePreviewProgram() {
    preview_program_ = CreateProgram(kImageVertexShaderSource, kPreviewFragmentShaderSource);
    if (preview_program_ == 0) {
        return false;
    }

    preview_texture_location_ =
            glGetUniformLocation(preview_program_, "previewTexture");
    return true;
}

bool NativeRenderer::CreateVertexBuffer() {
    glGenVertexArrays(1, &vertex_array_);
    glBindVertexArray(vertex_array_);

    glGenBuffers(1, &vertex_buffer_);
    glBindBuffer(GL_ARRAY_BUFFER, vertex_buffer_);
    glBufferData(
            GL_ARRAY_BUFFER,
            sizeof(kRenderVertices),
            kRenderVertices,
            GL_STATIC_DRAW);

    glEnableVertexAttribArray(0);
    glVertexAttribPointer(
            0,
            2,
            GL_FLOAT,
            GL_FALSE,
            4 * sizeof(GLfloat),
            nullptr);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(
            1,
            2,
            GL_FLOAT,
            GL_FALSE,
            4 * sizeof(GLfloat),
            reinterpret_cast<void*>(2 * sizeof(GLfloat)));

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
    return true;
}

void NativeRenderer::Release() {
    filter_pass_.Release();
    image_pass_.Release();
    bitmap_input_pass_.Release();
    oes_input_pass_.Release();
    if (normalized_framebuffer_ != 0) {
        glDeleteFramebuffers(1, &normalized_framebuffer_);
    }
    if (normalized_texture_ != 0) {
        glDeleteTextures(1, &normalized_texture_);
    }
    if (vertex_buffer_ != 0) {
        glDeleteBuffers(1, &vertex_buffer_);
    }
    if (vertex_array_ != 0) {
        glDeleteVertexArrays(1, &vertex_array_);
    }
    if (preview_program_ != 0) {
        glDeleteProgram(preview_program_);
    }

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
    normalized_texture_ = 0;
    normalized_framebuffer_ = 0;
    final_texture_ = 0;
    preview_program_ = 0;
    vertex_array_ = 0;
    vertex_buffer_ = 0;
    preview_texture_location_ = -1;
    output_width_ = 0;
    output_height_ = 0;
    normalized_width_ = 0;
    normalized_height_ = 0;
}

}  // namespace pelab
