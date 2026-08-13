#pragma once

#include <GLES3/gl3.h>

namespace pelab {

GLuint CreateProgram(
        const char* vertex_shader_source,
        const char* fragment_shader_source);

}  // namespace pelab
