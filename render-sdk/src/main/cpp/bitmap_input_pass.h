#pragma once

#include <GLES3/gl3.h>

namespace pelab {

class BitmapInputPass final {
public:
    bool Init();
    bool SetBitmap(const void* pixels, int width, int height, int row_stride);
    void Render(
            GLuint output_framebuffer,
            int output_width,
            int output_height,
            GLuint vertex_array) const;
    void Release();

private:
    bool CreateBitmapProgram();

    GLuint bitmap_texture_ = 0;
    GLuint bitmap_program_ = 0;
    GLint bitmap_texture_location_ = -1;
    GLint bitmap_scale_location_ = -1;
    int bitmap_width_ = 0;
    int bitmap_height_ = 0;
};

}  // namespace pelab
