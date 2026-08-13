#include "image_pass.h"

#include "gl_utils.h"

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

constexpr char kImageFragmentShaderSource[] = R"(#version 300 es

precision mediump float;

uniform sampler2D inputTexture;
uniform float brightness;
uniform float warmth;

in vec2 imageTextureCoordinate;
out vec4 outputColor;

void main() {
    vec4 color = texture(inputTexture, imageTextureCoordinate);
    color.rgb += brightness;
    color.r += warmth * 0.15;
    color.b -= warmth * 0.15;
    color.rgb = clamp(color.rgb, 0.0, 1.0);
    outputColor = color;
}
)";

}  // namespace

bool ImagePass::Init(int width, int height) {
    width_ = width;
    height_ = height;

    if (!CreateTarget()) {
        return false;
    }
    return CreateImageProgram();
}

void ImagePass::SetParams(float brightness, float warmth) {
    brightness_ = brightness;
    warmth_ = warmth;
}

void ImagePass::Render(GLuint input_texture, GLuint vertex_array) const {

    // bind image target
    glBindFramebuffer(GL_FRAMEBUFFER, image_framebuffer_);
    glViewport(0, 0, width_, height_);

    // use image program
    glUseProgram(image_program_);
    glBindVertexArray(vertex_array);

    // bind input texture and image params
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, input_texture);
    glUniform1i(input_texture_location_, 0);
    glUniform1f(brightness_location_, brightness_);
    glUniform1f(warmth_location_, warmth_);

    // render input texture to image target
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_2D, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}

GLuint ImagePass::GetOutputTexture() const {
    return image_texture_;
}

bool ImagePass::CreateTarget() {

    // create RGBA texture
    glGenTextures(1, &image_texture_);
    glBindTexture(GL_TEXTURE_2D, image_texture_);

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
    glGenFramebuffers(1, &image_framebuffer_);
    glBindFramebuffer(GL_FRAMEBUFFER, image_framebuffer_);
    glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D,
            image_texture_,
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
                "Image framebuffer creation failed: 0x%x",
                framebuffer_status);
        return false;
    }
    return true;
}

bool ImagePass::CreateImageProgram() {
    image_program_ = CreateProgram(kImageVertexShaderSource, kImageFragmentShaderSource);
    if (image_program_ == 0) {
        return false;
    }

    input_texture_location_ =
            glGetUniformLocation(image_program_, "inputTexture");
    brightness_location_ =
            glGetUniformLocation(image_program_, "brightness");
    warmth_location_ =
            glGetUniformLocation(image_program_, "warmth");
    return true;
}

void ImagePass::Release() {
    if (image_framebuffer_ != 0) {
        glDeleteFramebuffers(1, &image_framebuffer_);
    }
    if (image_texture_ != 0) {
        glDeleteTextures(1, &image_texture_);
    }
    if (image_program_ != 0) {
        glDeleteProgram(image_program_);
    }

    image_texture_ = 0;
    image_framebuffer_ = 0;
    image_program_ = 0;
    input_texture_location_ = -1;
    brightness_location_ = -1;
    warmth_location_ = -1;
    width_ = 0;
    height_ = 0;
}

}  // namespace pelab
