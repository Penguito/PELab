#pragma once

#include <GLES3/gl3.h>

namespace pelab {

class OesInputPass final {
public:
    bool Init();
    GLuint GetInputTexture() const;
    void Render(
            GLuint output_framebuffer,
            int output_width,
            int output_height,
            GLuint vertex_array,
            const float* texture_matrix) const;
    void Release();

private:
    bool CreateInputTexture();
    bool CreateOesProgram();

    GLuint input_texture_ = 0;
    GLuint oes_program_ = 0;
    GLint texture_matrix_location_ = -1;
    GLint input_texture_location_ = -1;
};

}  // namespace pelab
