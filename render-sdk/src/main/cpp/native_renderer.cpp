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

constexpr char kImageVertexShaderSource[] = R"(#version 300 es

layout(location = 0) in vec2 position;
layout(location = 1) in vec2 textureCoordinate;

out vec2 imageTextureCoordinate;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    imageTextureCoordinate = textureCoordinate;
}
)";

constexpr char kAdjustmentFragmentShaderSource[] = R"(#version 300 es

precision mediump float;

uniform sampler2D normalizedTexture;
uniform float brightness;
uniform float warmth;

in vec2 imageTextureCoordinate;
out vec4 outputColor;

void main() {
    vec4 color = texture(normalizedTexture, imageTextureCoordinate);
    color.rgb += brightness;
    color.r += warmth * 0.15;
    color.b -= warmth * 0.15;
    color.rgb = clamp(color.rgb, 0.0, 1.0);
    outputColor = color;
}
)";

constexpr char kFilterFragmentShaderSource[] = R"(#version 300 es

precision highp float;

uniform sampler2D imageTexture;
uniform sampler2D lutTexture;
uniform vec2 lutTextureSize;

in vec2 imageTextureCoordinate;
out vec4 outputColor;

void main() {
    vec4 color = texture(imageTexture, imageTextureCoordinate);
    float blueIndex = color.b * 63.0;
    float lowerIndex = floor(blueIndex);
    float upperIndex = ceil(blueIndex);

    vec2 lowerTile = vec2(
            mod(lowerIndex, 8.0),
            floor(lowerIndex / 8.0));
    vec2 upperTile = vec2(
            mod(upperIndex, 8.0),
            floor(upperIndex / 8.0));

    vec2 texelSize = 1.0 / lutTextureSize;
    vec2 tileSize = vec2(0.125);
    vec2 tileRange = tileSize - texelSize;
    vec2 texelOffset = texelSize * 0.5;
    vec2 lowerCoordinate =
            lowerTile * tileSize + texelOffset + color.rg * tileRange;
    vec2 upperCoordinate =
            upperTile * tileSize + texelOffset + color.rg * tileRange;

    vec3 lowerColor = texture(lutTexture, lowerCoordinate).rgb;
    vec3 upperColor = texture(lutTexture, upperCoordinate).rgb;
    outputColor = vec4(
            mix(lowerColor, upperColor, fract(blueIndex)),
            color.a);
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
    // 2. create normalized buffer
    if (!CreateNormalizedTarget()) {
        return false;
    }

    // 3. create image buffer
    if (!CreateImageTarget()) {
        return false;
    }

    // 4. create filter buffer
    if (!CreateFilterTarget()) {
        return false;
    }

    // 5. create normalize program
    if (!CreateNormalizeProgram()) {
        return false;
    }

    // 6. create adjustment program
    if (!CreateAdjustmentProgram()) {
        return false;
    }

    // 7. create filter program
    if (!CreateFilterProgram()) {
        return false;
    }

    // 8. create preview program
    if (!CreatePreviewProgram()) {
        return false;
    }

    // 9. create vertex buffer
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

void NativeRenderer::SetImageParams(float brightness, float warmth) {
    brightness_ = brightness;
    warmth_ = warmth;
}

bool NativeRenderer::SetLutTexture(const void* pixels, int width, int height, int row_stride) {

    if (lut_texture_ != 0) {
        glDeleteTextures(1, &lut_texture_);
        lut_texture_ = 0;
    }
    lut_width_ = 0;
    lut_height_ = 0;
    if (pixels == nullptr) {
        return true;
    }
    if (width <= 0 || height <= 0 || row_stride < width * 4) {
        return false;
    }

    glGenTextures(1, &lut_texture_);
    glBindTexture(GL_TEXTURE_2D, lut_texture_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, row_stride / 4);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    if (lut_texture_ == 0) {
        return false;
    }

    lut_width_ = width;
    lut_height_ = height;
    return true;
}

void NativeRenderer::RenderFrame(const float* texture_matrix) {

    // Pass 1: OES -> normalized buffer
    RenderToNormalizedTarget(texture_matrix);

    // Pass 2: normalized buffer -> image buffer
    RenderToImageTarget();

    // Pass Filter: image buffer -> filter buffer
    if (lut_texture_ != 0) {
        RenderToFilterTarget();
    }

    // Pass Final: last buffer -> surfaceView
    RenderToOutput();

    if (eglSwapBuffers(display_, surface_) != EGL_TRUE) {
        LogEglError("eglSwapBuffers");
    }
}

void NativeRenderer::RenderToNormalizedTarget(const float* texture_matrix) const {

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

void NativeRenderer::RenderToImageTarget() const {

    // bind framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, image_framebuffer_);
    glViewport(0, 0, normalized_width_, normalized_height_);

    // use program
    glUseProgram(adjustment_program_);
    glBindVertexArray(vertex_array_);

    // bind normalized buffer
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, normalized_texture_);
    glUniform1i(adjustment_texture_location_, 0);
    glUniform1f(adjustment_brightness_location_, brightness_);
    glUniform1f(adjustment_warmth_location_, warmth_);

    // render normalized buffer to image buffer
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_2D, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}

void NativeRenderer::RenderToFilterTarget() const {

    // bind framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, filter_framebuffer_);
    glViewport(0, 0, normalized_width_, normalized_height_);

    // use program
    glUseProgram(filter_program_);
    glBindVertexArray(vertex_array_);

    // bind image buffer and LUT texture
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, image_texture_);
    glUniform1i(filter_input_texture_location_, 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, lut_texture_);
    glUniform1i(filter_lut_texture_location_, 1);
    glUniform2f(filter_lut_size_location_, static_cast<GLfloat>(lut_width_), static_cast<GLfloat>(lut_height_));

    // render image buffer to filter buffer
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}

void NativeRenderer::RenderToOutput() const {

    // bind framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, output_width_, output_height_);

    // use program
    glUseProgram(preview_program_);
    glBindVertexArray(vertex_array_);

    // bind image or filter buffer
    const GLuint preview_texture =
            lut_texture_ == 0 ? image_texture_ : filter_texture_;
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, preview_texture);
    glUniform1i(preview_texture_location_, 0);

    // render preview texture to surfaceView
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_2D, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}

bool NativeRenderer::CreateInputTexture() {
    glGenTextures(1, &input_texture_);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, input_texture_);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
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

bool NativeRenderer::CreateImageTarget() {

    // create RGBA texture
    glGenTextures(1, &image_texture_);
    glBindTexture(GL_TEXTURE_2D, image_texture_);

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
    glGenFramebuffers(1, &image_framebuffer_);
    glBindFramebuffer(GL_FRAMEBUFFER, image_framebuffer_);
    glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D,
            image_texture_,
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

bool NativeRenderer::CreateFilterTarget() {

    // create RGBA texture
    glGenTextures(1, &filter_texture_);
    glBindTexture(GL_TEXTURE_2D, filter_texture_);

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
    glGenFramebuffers(1, &filter_framebuffer_);
    glBindFramebuffer(GL_FRAMEBUFFER, filter_framebuffer_);
    glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D,
            filter_texture_,
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

bool NativeRenderer::CreateAdjustmentProgram() {
    adjustment_program_ = CreateProgram(
            kImageVertexShaderSource,
            kAdjustmentFragmentShaderSource);
    if (adjustment_program_ == 0) {
        return false;
    }

    adjustment_texture_location_ =
            glGetUniformLocation(adjustment_program_, "normalizedTexture");
    adjustment_brightness_location_ =
            glGetUniformLocation(adjustment_program_, "brightness");
    adjustment_warmth_location_ =
            glGetUniformLocation(adjustment_program_, "warmth");
    return true;
}

bool NativeRenderer::CreateFilterProgram() {
    filter_program_ = CreateProgram(
            kImageVertexShaderSource,
            kFilterFragmentShaderSource);
    if (filter_program_ == 0) {
        return false;
    }

    filter_input_texture_location_ =
            glGetUniformLocation(filter_program_, "imageTexture");
    filter_lut_texture_location_ =
            glGetUniformLocation(filter_program_, "lutTexture");
    filter_lut_size_location_ =
            glGetUniformLocation(filter_program_, "lutTextureSize");
    return true;
}

bool NativeRenderer::CreatePreviewProgram() {
    preview_program_ = CreateProgram(
            kImageVertexShaderSource,
            kPreviewFragmentShaderSource);
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
    if (lut_texture_ != 0) {
        glDeleteTextures(1, &lut_texture_);
    }
    if (image_framebuffer_ != 0) {
        glDeleteFramebuffers(1, &image_framebuffer_);
    }
    if (image_texture_ != 0) {
        glDeleteTextures(1, &image_texture_);
    }
    if (filter_framebuffer_ != 0) {
        glDeleteFramebuffers(1, &filter_framebuffer_);
    }
    if (filter_texture_ != 0) {
        glDeleteTextures(1, &filter_texture_);
    }
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
    if (adjustment_program_ != 0) {
        glDeleteProgram(adjustment_program_);
    }
    if (filter_program_ != 0) {
        glDeleteProgram(filter_program_);
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
    image_texture_ = 0;
    image_framebuffer_ = 0;
    lut_texture_ = 0;
    filter_texture_ = 0;
    filter_framebuffer_ = 0;
    normalize_program_ = 0;
    adjustment_program_ = 0;
    filter_program_ = 0;
    preview_program_ = 0;
    vertex_array_ = 0;
    vertex_buffer_ = 0;
    normalize_texture_matrix_location_ = -1;
    normalize_input_texture_location_ = -1;
    adjustment_texture_location_ = -1;
    adjustment_brightness_location_ = -1;
    adjustment_warmth_location_ = -1;
    filter_input_texture_location_ = -1;
    filter_lut_texture_location_ = -1;
    filter_lut_size_location_ = -1;
    preview_texture_location_ = -1;
    output_width_ = 0;
    output_height_ = 0;
    normalized_width_ = 0;
    normalized_height_ = 0;
    lut_width_ = 0;
    lut_height_ = 0;
}

}  // namespace pelab
