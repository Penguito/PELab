#include "filter_pass.h"

#include "gl_utils.h"

#include <android/log.h>

namespace pelab {
namespace {

constexpr char kLogTag[] = "PELabEGL";

constexpr char kFilterVertexShaderSource[] = R"(#version 300 es

layout(location = 0) in vec2 position;
layout(location = 1) in vec2 textureCoordinate;

out vec2 imageTextureCoordinate;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    imageTextureCoordinate = textureCoordinate;
}
)";

constexpr char kFilterFragmentShaderSource[] = R"(#version 300 es

precision highp float;

uniform sampler2D inputTexture;
uniform sampler2D lutTexture;
uniform vec2 lutTextureSize;

in vec2 imageTextureCoordinate;
out vec4 outputColor;

void main() {
    vec4 color = texture(inputTexture, imageTextureCoordinate);
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

}  // namespace

bool FilterPass::Init(int width, int height) {
    width_ = width;
    height_ = height;

    if (!CreateTarget()) {
        return false;
    }
    return CreateFilterProgram();
}

bool FilterPass::SetLutTexture(
        const void* pixels,
        int width,
        int height,
        int row_stride) {

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
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA8,
            width,
            height,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            pixels);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    if (lut_texture_ == 0) {
        return false;
    }

    lut_width_ = width;
    lut_height_ = height;
    return true;
}

bool FilterPass::IsEnabled() const {
    return lut_texture_ != 0;
}

void FilterPass::Render(GLuint input_texture, GLuint vertex_array) const {

    // bind filter target
    glBindFramebuffer(GL_FRAMEBUFFER, filter_framebuffer_);
    glViewport(0, 0, width_, height_);

    // use filter program
    glUseProgram(filter_program_);
    glBindVertexArray(vertex_array);

    // bind input texture and LUT texture
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, input_texture);
    glUniform1i(input_texture_location_, 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, lut_texture_);
    glUniform1i(lut_texture_location_, 1);
    glUniform2f(
            lut_size_location_,
            static_cast<GLfloat>(lut_width_),
            static_cast<GLfloat>(lut_height_));

    // render input texture to filter target
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}

GLuint FilterPass::GetOutputTexture() const {
    return filter_texture_;
}

bool FilterPass::CreateTarget() {

    // create RGBA texture
    glGenTextures(1, &filter_texture_);
    glBindTexture(GL_TEXTURE_2D, filter_texture_);

    // allocate texture storage
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA8,
            width_,
            height_,
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
    const GLenum framebuffer_status =
            glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    if (framebuffer_status != GL_FRAMEBUFFER_COMPLETE) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "Filter framebuffer creation failed: 0x%x",
                framebuffer_status);
        return false;
    }
    return true;
}

bool FilterPass::CreateFilterProgram() {
    filter_program_ = CreateProgram(kFilterVertexShaderSource,kFilterFragmentShaderSource);
    if (filter_program_ == 0) {
        return false;
    }

    input_texture_location_ =
            glGetUniformLocation(filter_program_, "inputTexture");
    lut_texture_location_ =
            glGetUniformLocation(filter_program_, "lutTexture");
    lut_size_location_ =
            glGetUniformLocation(filter_program_, "lutTextureSize");
    return true;
}

void FilterPass::Release() {
    if (lut_texture_ != 0) {
        glDeleteTextures(1, &lut_texture_);
    }
    if (filter_framebuffer_ != 0) {
        glDeleteFramebuffers(1, &filter_framebuffer_);
    }
    if (filter_texture_ != 0) {
        glDeleteTextures(1, &filter_texture_);
    }
    if (filter_program_ != 0) {
        glDeleteProgram(filter_program_);
    }

    filter_texture_ = 0;
    filter_framebuffer_ = 0;
    lut_texture_ = 0;
    filter_program_ = 0;
    input_texture_location_ = -1;
    lut_texture_location_ = -1;
    lut_size_location_ = -1;
    width_ = 0;
    height_ = 0;
    lut_width_ = 0;
    lut_height_ = 0;
}

}  // namespace pelab
