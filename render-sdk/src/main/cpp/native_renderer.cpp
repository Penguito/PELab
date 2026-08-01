#include "native_renderer.h"

#include <EGL/eglext.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl3.h>
#include <android/log.h>

namespace pelab {
namespace {

constexpr char kLogTag[] = "PELabEGL";

constexpr char kNormalizeVertexShaderSource[] = R"(#version 300 es

layout(location = 0) in vec2 position;
layout(location = 1) in vec2 textureCoordinate;

uniform mat4 textureMatrix;

out vec2 normalizedTextureCoordinate;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    normalizedTextureCoordinate =
            (textureMatrix * vec4(textureCoordinate, 0.0, 1.0)).xy;
}
)";

constexpr char kNormalizeFragmentShaderSource[] = R"(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

uniform samplerExternalOES inputTexture;

in vec2 normalizedTextureCoordinate;
out vec4 outputColor;

void main() {
    outputColor = texture(inputTexture, normalizedTextureCoordinate);
}
)";

constexpr char kPreviewVertexShaderSource[] = R"(#version 300 es

layout(location = 0) in vec2 position;
layout(location = 1) in vec2 textureCoordinate;

out vec2 previewTextureCoordinate;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    previewTextureCoordinate = textureCoordinate;
}
)";

constexpr char kPreviewFragmentShaderSource[] = R"(#version 300 es

precision mediump float;

uniform sampler2D normalizedTexture;

in vec2 previewTextureCoordinate;
out vec4 outputColor;

void main() {
    outputColor = texture(normalizedTexture, previewTextureCoordinate);
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

GLuint CompileShader(GLenum type, const char* source) {
    const GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint compile_status = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compile_status);
    if (compile_status == GL_TRUE) {
        return shader;
    }

    char error_message[512] = {};
    glGetShaderInfoLog(
            shader,
            sizeof(error_message),
            nullptr,
            error_message);
    __android_log_print(
            ANDROID_LOG_ERROR,
            kLogTag,
            "Shader compilation failed: %s",
            error_message);
    glDeleteShader(shader);
    return 0;
}

GLuint CreateProgram(
        const char* vertex_shader_source,
        const char* fragment_shader_source) {

    const GLuint vertex_shader =
            CompileShader(GL_VERTEX_SHADER, vertex_shader_source);
    if (vertex_shader == 0) {
        return 0;
    }

    const GLuint fragment_shader =
            CompileShader(GL_FRAGMENT_SHADER, fragment_shader_source);
    if (fragment_shader == 0) {
        glDeleteShader(vertex_shader);
        return 0;
    }

    const GLuint program = glCreateProgram();
    glAttachShader(program, vertex_shader);
    glAttachShader(program, fragment_shader);
    glLinkProgram(program);
    glDeleteShader(vertex_shader);
    glDeleteShader(fragment_shader);

    GLint link_status = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &link_status);
    if (link_status == GL_TRUE) {
        return program;
    }

    char error_message[512] = {};
    glGetProgramInfoLog(
            program,
            sizeof(error_message),
            nullptr,
            error_message);
    __android_log_print(
            ANDROID_LOG_ERROR,
            kLogTag,
            "Program link failed: %s",
            error_message);
    glDeleteProgram(program);
    return 0;
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

    // 1. create input texture
    if (!CreateInputTexture()) {
        return false;
    }
    // 2. create render buffer
    if (!CreateNormalizedTarget()) {
        return false;
    }

    // 3. create normalize program
    if (!CreateNormalizeProgram()) {
        return false;
    }

    // 4. create preview program
    if (!CreatePreviewProgram()) {
        return false;
    }

    // 5. create vertex buffer
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

GLuint NativeRenderer::GetInputTexture() const {
    return input_texture_;
}

void NativeRenderer::RenderFrame(const float* texture_matrix) {

    // Pass 1: OES -> RGBA buffer
    RenderToNormalizedTarget(texture_matrix);

    // Pass 2: RGBA buffer -> surfaceView
    RenderToOutput();

    if (eglSwapBuffers(display_, surface_) != EGL_TRUE) {
        LogEglError("eglSwapBuffers");
    }
}

void NativeRenderer::RenderToNormalizedTarget(
        const float* texture_matrix) {

    // bind framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, normalized_framebuffer_);
    glViewport(0, 0, normalized_width_, normalized_height_);

    // use program
    glUseProgram(normalize_program_);
    glBindVertexArray(vertex_array_);

    // bind OES texture and apply texture matrix
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, input_texture_);
    glUniform1i(normalize_input_texture_location_, 0);
    glUniformMatrix4fv(
            normalize_texture_matrix_location_,
            1,
            GL_FALSE,
            texture_matrix);

    // render OES to RGBA buffer
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}

void NativeRenderer::RenderToOutput() {

    // bind framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, output_width_, output_height_);

    // use program
    glUseProgram(preview_program_);
    glBindVertexArray(vertex_array_);

    // bind RGBA buffer
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, normalized_texture_);
    glUniform1i(preview_texture_location_, 0);

    // render RGBA buffer to surfaceView
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_2D, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}

bool NativeRenderer::CreateInputTexture() {
    glGenTextures(1, &input_texture_);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, input_texture_);
    glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GL_TEXTURE_MIN_FILTER,
            GL_LINEAR);
    glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GL_TEXTURE_MAG_FILTER,
            GL_LINEAR);
    glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GL_TEXTURE_WRAP_S,
            GL_CLAMP_TO_EDGE);
    glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GL_TEXTURE_WRAP_T,
            GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    return input_texture_ != 0;
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
    const GLenum framebuffer_status =
            glCheckFramebufferStatus(GL_FRAMEBUFFER);
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

bool NativeRenderer::CreateNormalizeProgram() {
    normalize_program_ = CreateProgram(
            kNormalizeVertexShaderSource,
            kNormalizeFragmentShaderSource);
    if (normalize_program_ == 0) {
        return false;
    }

    normalize_texture_matrix_location_ =
            glGetUniformLocation(normalize_program_, "textureMatrix");
    normalize_input_texture_location_ =
            glGetUniformLocation(normalize_program_, "inputTexture");
    return true;
}

bool NativeRenderer::CreatePreviewProgram() {
    preview_program_ = CreateProgram(
            kPreviewVertexShaderSource,
            kPreviewFragmentShaderSource);
    if (preview_program_ == 0) {
        return false;
    }

    preview_texture_location_ =
            glGetUniformLocation(preview_program_, "normalizedTexture");
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
    if (normalize_program_ != 0) {
        glDeleteProgram(normalize_program_);
    }
    if (preview_program_ != 0) {
        glDeleteProgram(preview_program_);
    }
    if (input_texture_ != 0) {
        glDeleteTextures(1, &input_texture_);
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
    input_texture_ = 0;
    normalized_texture_ = 0;
    normalized_framebuffer_ = 0;
    normalize_program_ = 0;
    preview_program_ = 0;
    vertex_array_ = 0;
    vertex_buffer_ = 0;
    normalize_texture_matrix_location_ = -1;
    normalize_input_texture_location_ = -1;
    preview_texture_location_ = -1;
    output_width_ = 0;
    output_height_ = 0;
    normalized_width_ = 0;
    normalized_height_ = 0;
}

}  // namespace pelab
