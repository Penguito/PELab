#include "oes_input_pass.h"

#include "gl_utils.h"

#include <GLES2/gl2ext.h>

namespace pelab {
namespace {

constexpr char kOesVertexShaderSource[] = R"(#version 300 es

layout(location = 0) in vec2 position;
layout(location = 1) in vec2 textureCoordinate;

uniform mat4 textureMatrix;

out vec2 oesTextureCoordinate;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    oesTextureCoordinate =
            (textureMatrix * vec4(textureCoordinate, 0.0, 1.0)).xy;
}
)";

constexpr char kOesFragmentShaderSource[] = R"(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

uniform samplerExternalOES inputTexture;

in vec2 oesTextureCoordinate;
out vec4 outputColor;

void main() {
    outputColor = texture(inputTexture, oesTextureCoordinate);
}
)";

}  // namespace

bool OesInputPass::Init() {
    if (!CreateInputTexture()) {
        return false;
    }
    return CreateOesProgram();
}

GLuint OesInputPass::GetInputTexture() const {
    return input_texture_;
}

void OesInputPass::Render(
        GLuint output_framebuffer,
        int output_width,
        int output_height,
        GLuint vertex_array,
        const float* texture_matrix) const {

    // bind normalized target
    glBindFramebuffer(GL_FRAMEBUFFER, output_framebuffer);
    glViewport(0, 0, output_width, output_height);

    // use OES input program
    glUseProgram(oes_program_);
    glBindVertexArray(vertex_array);

    // bind OES texture and apply texture matrix
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, input_texture_);
    glUniform1i(input_texture_location_, 0);
    glUniformMatrix4fv(
            texture_matrix_location_,
            1,
            GL_FALSE,
            texture_matrix);

    // render OES texture to normalized target
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}

bool OesInputPass::CreateInputTexture() {
    glGenTextures(1, &input_texture_);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, input_texture_);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    return input_texture_ != 0;
}

bool OesInputPass::CreateOesProgram() {
    oes_program_ = CreateProgram(kOesVertexShaderSource, kOesFragmentShaderSource);
    if (oes_program_ == 0) {
        return false;
    }

    texture_matrix_location_ =
            glGetUniformLocation(oes_program_, "textureMatrix");
    input_texture_location_ =
            glGetUniformLocation(oes_program_, "inputTexture");
    return true;
}

void OesInputPass::Release() {
    if (input_texture_ != 0) {
        glDeleteTextures(1, &input_texture_);
    }
    if (oes_program_ != 0) {
        glDeleteProgram(oes_program_);
    }

    input_texture_ = 0;
    oes_program_ = 0;
    texture_matrix_location_ = -1;
    input_texture_location_ = -1;
}

}  // namespace pelab
