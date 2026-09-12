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
uniform float contrast;
uniform float exposure;
uniform float highlights;
uniform float shadows;
uniform float warmth;

in vec2 imageTextureCoordinate;
out vec4 outputColor;

vec3 adjustTone(vec3 color, float amount, float mask) {
    vec3 availableRange = amount >= 0.0 ? vec3(1.0) - color : color;
    return color + amount * mask * availableRange * 0.5;
}

void main() {
    vec4 color = texture(inputTexture, imageTextureCoordinate);
    float sourceLuminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    float highlightMask = smoothstep(0.5, 1.0, sourceLuminance);
    float shadowMask = 1.0 - smoothstep(0.0, 0.5, sourceLuminance);

    vec3 adjustedColor = color.rgb * exp2(exposure);
    adjustedColor += brightness;
    adjustedColor = (adjustedColor - vec3(0.5)) * (1.0 + contrast) + vec3(0.5);
    adjustedColor = clamp(adjustedColor, 0.0, 1.0);
    adjustedColor = adjustTone(adjustedColor, highlights, highlightMask);
    adjustedColor = adjustTone(adjustedColor, shadows, shadowMask);
    adjustedColor.r += warmth * 0.15;
    adjustedColor.b -= warmth * 0.15;
    outputColor = vec4(clamp(adjustedColor, 0.0, 1.0), color.a);
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

void ImagePass::SetParams(
        float brightness,
        float contrast,
        float exposure,
        float highlights,
        float shadows,
        float warmth,
        float tint,
        float saturation,
        float vibrance,
        float grain,
        float vignette) {
    brightness_ = brightness;
    contrast_ = contrast;
    exposure_ = exposure;
    highlights_ = highlights;
    shadows_ = shadows;
    warmth_ = warmth;
    tint_ = tint;
    saturation_ = saturation;
    vibrance_ = vibrance;
    grain_ = grain;
    vignette_ = vignette;
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
    glUniform1f(contrast_location_, contrast_);
    glUniform1f(exposure_location_, exposure_);
    glUniform1f(highlights_location_, highlights_);
    glUniform1f(shadows_location_, shadows_);
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
    contrast_location_ =
            glGetUniformLocation(image_program_, "contrast");
    exposure_location_ =
            glGetUniformLocation(image_program_, "exposure");
    highlights_location_ =
            glGetUniformLocation(image_program_, "highlights");
    shadows_location_ =
            glGetUniformLocation(image_program_, "shadows");
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
    contrast_location_ = -1;
    exposure_location_ = -1;
    highlights_location_ = -1;
    shadows_location_ = -1;
    warmth_location_ = -1;
    width_ = 0;
    height_ = 0;
}

}  // namespace pelab
