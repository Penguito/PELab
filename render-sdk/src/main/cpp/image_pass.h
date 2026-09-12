#pragma once

#include <GLES3/gl3.h>

namespace pelab {

class ImagePass final {
public:
    bool Init(int width, int height);
    void SetParams(
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
            float vignette);
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
    GLint contrast_location_ = -1;
    GLint exposure_location_ = -1;
    GLint highlights_location_ = -1;
    GLint shadows_location_ = -1;
    GLint warmth_location_ = -1;
    float brightness_ = 0.0F;
    float contrast_ = 0.0F;
    float exposure_ = 0.0F;
    float highlights_ = 0.0F;
    float shadows_ = 0.0F;
    float warmth_ = 0.0F;
    float tint_ = 0.0F;
    float saturation_ = 0.0F;
    float vibrance_ = 0.0F;
    float grain_ = 0.0F;
    float vignette_ = 0.0F;
    int width_ = 0;
    int height_ = 0;
};

}  // namespace pelab
