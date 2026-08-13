#include "gl_utils.h"

#include <android/log.h>

namespace pelab {
namespace {

constexpr char kLogTag[] = "PELabEGL";

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

}  // namespace pelab
