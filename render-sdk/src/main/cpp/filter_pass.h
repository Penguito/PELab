#pragma once

#include <GLES3/gl3.h>

namespace pelab {

class FilterPass final {
public:
    bool Init(int width, int height);
    bool SetLutTexture(const void* pixels, int width, int height, int row_stride);
    bool IsEnabled() const;
    void Render(GLuint input_texture, GLuint vertex_array) const;
    GLuint GetOutputTexture() const;
    void Release();

private:
    bool CreateTarget();
    bool CreateFilterProgram();

    GLuint filter_texture_ = 0;
    GLuint filter_framebuffer_ = 0;
    GLuint lut_texture_ = 0;
    GLuint filter_program_ = 0;
    GLint input_texture_location_ = -1;
    GLint lut_texture_location_ = -1;
    GLint lut_size_location_ = -1;
    int width_ = 0;
    int height_ = 0;
    int lut_width_ = 0;
    int lut_height_ = 0;
};

}  // namespace pelab
