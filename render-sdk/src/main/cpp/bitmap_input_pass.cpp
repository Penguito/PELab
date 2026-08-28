#include "bitmap_input_pass.h"

#include "gl_utils.h"

namespace pelab {
namespace {

constexpr char kBitmapVertexShaderSource[] = R"(#version 300 es

layout(location = 0) in vec2 position;
layout(location = 1) in vec2 textureCoordinate;

out vec2 bitmapTextureCoordinate;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    bitmapTextureCoordinate = textureCoordinate;
}
)";

constexpr char kBitmapFragmentShaderSource[] = R"(#version 300 es

precision mediump float;

uniform sampler2D bitmapTexture;
uniform vec2 bitmapScale;

in vec2 bitmapTextureCoordinate;
out vec4 outputColor;

void main() {
    vec2 inputCoordinate =
            (bitmapTextureCoordinate - vec2(0.5)) / bitmapScale + vec2(0.5);
    inputCoordinate.y = 1.0 - inputCoordinate.y;

    if (inputCoordinate.x < 0.0 || inputCoordinate.x > 1.0
            || inputCoordinate.y < 0.0 || inputCoordinate.y > 1.0) {
        outputColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    outputColor = texture(bitmapTexture, inputCoordinate);
}
)";

}  // namespace

bool BitmapInputPass::Init() {
    return CreateBitmapProgram();
}

bool BitmapInputPass::SetBitmap(
        const void* pixels,
        int width,
        int height,
        int row_stride) {

    if (bitmap_texture_ != 0) {
        glDeleteTextures(1, &bitmap_texture_);
        bitmap_texture_ = 0;
    }
    if (pixels == nullptr || width <= 0 || height <= 0 || row_stride < width * 4) {
        return false;
    }

    glGenTextures(1, &bitmap_texture_);
    glBindTexture(GL_TEXTURE_2D, bitmap_texture_);
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

    bitmap_width_ = width;
    bitmap_height_ = height;
    return bitmap_texture_ != 0;
}

void BitmapInputPass::Render(
        GLuint output_framebuffer,
        int output_width,
        int output_height,
        GLuint vertex_array) const {

    const float bitmap_ratio =
            static_cast<float>(bitmap_width_) / static_cast<float>(bitmap_height_);
    const float output_ratio =
            static_cast<float>(output_width) / static_cast<float>(output_height);
    float scale_x = 1.0F;
    float scale_y = 1.0F;
    if (bitmap_ratio > output_ratio) {
        scale_y = output_ratio / bitmap_ratio;
    } else {
        scale_x = bitmap_ratio / output_ratio;
    }

    // bind normalized target
    glBindFramebuffer(GL_FRAMEBUFFER, output_framebuffer);
    glViewport(0, 0, output_width, output_height);

    // use bitmap input program
    glUseProgram(bitmap_program_);
    glBindVertexArray(vertex_array);

    // bind bitmap texture and scale
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, bitmap_texture_);
    glUniform1i(bitmap_texture_location_, 0);
    glUniform2f(bitmap_scale_location_, scale_x, scale_y);

    // render bitmap texture to normalized target
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_2D, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}

bool BitmapInputPass::CreateBitmapProgram() {
    bitmap_program_ = CreateProgram(kBitmapVertexShaderSource, kBitmapFragmentShaderSource);
    if (bitmap_program_ == 0) {
        return false;
    }

    bitmap_texture_location_ =
            glGetUniformLocation(bitmap_program_, "bitmapTexture");
    bitmap_scale_location_ =
            glGetUniformLocation(bitmap_program_, "bitmapScale");
    return true;
}

void BitmapInputPass::Release() {
    if (bitmap_texture_ != 0) {
        glDeleteTextures(1, &bitmap_texture_);
    }
    if (bitmap_program_ != 0) {
        glDeleteProgram(bitmap_program_);
    }

    bitmap_texture_ = 0;
    bitmap_program_ = 0;
    bitmap_texture_location_ = -1;
    bitmap_scale_location_ = -1;
    bitmap_width_ = 0;
    bitmap_height_ = 0;
}

}  // namespace pelab
