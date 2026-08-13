#pragma once

#include <GLES3/gl3.h>

namespace pelab {

class ImagePass final {
public:
    bool Init(int width, int height);
    void SetParams(float brightness, float warmth);
    void Render(GLuint input_texture, GLuint vertex_array) const;
    GLuint GetOutputTexture() const;
    void Release();

private:
    bool CreateTarget();
    bool CreateImageProgram();

    GLuint image_texture_ = 0;
    GLuint image_framebuffer_ = 0;
    GLuint image_program_ = 0;
    GLint input_texture_location_ = -1;
    GLint brightness_location_ = -1;
    GLint warmth_location_ = -1;
    float brightness_ = 0.0F;
    float warmth_ = 0.0F;
    int width_ = 0;
    int height_ = 0;
};

}  // namespace pelab
