#include "native_renderer.h"

#include <EGL/eglext.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl3.h>
#include <android/log.h>

namespace pelab {
namespace {

constexpr char kLogTag[] = "PELabEGL";

constexpr char kVertexShaderSource[] = R"(#version 300 es

layout(location = 0) in vec2 position;
layout(location = 1) in vec2 textureCoordinate;

uniform mat4 textureMatrix;

out vec2 previewTextureCoordinate;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    previewTextureCoordinate =
            (textureMatrix * vec4(textureCoordinate, 0.0, 1.0)).xy;
}
)";

constexpr char kFragmentShaderSource[] = R"(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

uniform samplerExternalOES inputTexture;

in vec2 previewTextureCoordinate;
out vec4 outputColor;

void main() {
    outputColor = texture(inputTexture, previewTextureCoordinate);
}
)";

constexpr GLfloat kPreviewVertices[] = {
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

    if (!CreateInputTexture() || !CreatePreviewProgram()) {
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
            "EGL environment ready: surface=%dx%d, GL=%s",
            output_width_,
            output_height_,
            gl_version == nullptr ? "unknown" : gl_version);
    return true;
}

GLuint NativeRenderer::GetInputTexture() const {
    return input_texture_;
}

void NativeRenderer::RenderFrame(const float* texture_matrix) {
    glViewport(0, 0, output_width_, output_height_);
    glUseProgram(preview_program_);
    glBindVertexArray(vertex_array_);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, input_texture_);
    glUniform1i(input_texture_location_, 0);
    glUniformMatrix4fv(
            texture_matrix_location_,
            1,
            GL_FALSE,
            texture_matrix);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    glBindVertexArray(0);
    glUseProgram(0);

    if (eglSwapBuffers(display_, surface_) != EGL_TRUE) {
        LogEglError("eglSwapBuffers");
    }
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

bool NativeRenderer::CreatePreviewProgram() {
    const GLuint vertex_shader =
            CompileShader(GL_VERTEX_SHADER, kVertexShaderSource);
    if (vertex_shader == 0) {
        return false;
    }

    const GLuint fragment_shader =
            CompileShader(GL_FRAGMENT_SHADER, kFragmentShaderSource);
    if (fragment_shader == 0) {
        glDeleteShader(vertex_shader);
        return false;
    }

    preview_program_ = glCreateProgram();
    glAttachShader(preview_program_, vertex_shader);
    glAttachShader(preview_program_, fragment_shader);
    glLinkProgram(preview_program_);
    glDeleteShader(vertex_shader);
    glDeleteShader(fragment_shader);

    GLint link_status = GL_FALSE;
    glGetProgramiv(preview_program_, GL_LINK_STATUS, &link_status);
    if (link_status != GL_TRUE) {
        char error_message[512] = {};
        glGetProgramInfoLog(
                preview_program_,
                sizeof(error_message),
                nullptr,
                error_message);
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "Program link failed: %s",
                error_message);
        return false;
    }

    texture_matrix_location_ =
            glGetUniformLocation(preview_program_, "textureMatrix");
    input_texture_location_ =
            glGetUniformLocation(preview_program_, "inputTexture");

    glGenVertexArrays(1, &vertex_array_);
    glBindVertexArray(vertex_array_);

    glGenBuffers(1, &vertex_buffer_);
    glBindBuffer(GL_ARRAY_BUFFER, vertex_buffer_);
    glBufferData(
            GL_ARRAY_BUFFER,
            sizeof(kPreviewVertices),
            kPreviewVertices,
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
    if (vertex_buffer_ != 0) {
        glDeleteBuffers(1, &vertex_buffer_);
    }
    if (vertex_array_ != 0) {
        glDeleteVertexArrays(1, &vertex_array_);
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
    preview_program_ = 0;
    vertex_array_ = 0;
    vertex_buffer_ = 0;
    texture_matrix_location_ = -1;
    input_texture_location_ = -1;
    output_width_ = 0;
    output_height_ = 0;
}

}  // namespace pelab
