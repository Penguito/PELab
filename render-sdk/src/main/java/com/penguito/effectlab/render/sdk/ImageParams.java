package com.penguito.effectlab.render.sdk;

/** image adjustment parameters */
public final class ImageParams extends RenderBaseParams {

    private static final float MIN_SIGNED_VALUE = -1.0F;
    private static final float MAX_VALUE = 1.0F;
    private static final float MIN_UNSIGNED_VALUE = 0.0F;

    private final float brightness;
    private final float contrast;
    private final float exposure;
    private final float highlights;
    private final float shadows;
    private final float warmth;
    private final float tint;
    private final float saturation;
    private final float vibrance;
    private final float grain;
    private final float vignette;

    private ImageParams(Builder builder) {
        brightness = clampSigned(builder.brightness);
        contrast = clampSigned(builder.contrast);
        exposure = clampSigned(builder.exposure);
        highlights = clampSigned(builder.highlights);
        shadows = clampSigned(builder.shadows);
        warmth = clampSigned(builder.warmth);
        tint = clampSigned(builder.tint);
        saturation = clampSigned(builder.saturation);
        vibrance = clampSigned(builder.vibrance);
        grain = clampUnsigned(builder.grain);
        vignette = clampUnsigned(builder.vignette);
    }

    public static ImageParams defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ImageParams source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        return new Builder(source);
    }

    public float getBrightness() {
        return brightness;
    }

    public float getContrast() {
        return contrast;
    }

    public float getExposure() {
        return exposure;
    }

    public float getHighlights() {
        return highlights;
    }

    public float getShadows() {
        return shadows;
    }

    public float getWarmth() {
        return warmth;
    }

    public float getTint() {
        return tint;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getVibrance() {
        return vibrance;
    }

    public float getGrain() {
        return grain;
    }

    public float getVignette() {
        return vignette;
    }

    private static float clampSigned(float value) {
        return Math.max(MIN_SIGNED_VALUE, Math.min(MAX_VALUE, value));
    }

    private static float clampUnsigned(float value) {
        return Math.max(MIN_UNSIGNED_VALUE, Math.min(MAX_VALUE, value));
    }

    public static final class Builder {

        private float brightness = 0.0F;
        private float contrast = 0.0F;
        private float exposure = 0.0F;
        private float highlights = 0.0F;
        private float shadows = 0.0F;
        private float warmth = 0.0F;
        private float tint = 0.0F;
        private float saturation = 0.0F;
        private float vibrance = 0.0F;
        private float grain = 0.0F;
        private float vignette = 0.0F;

        private Builder() {

        }

        private Builder(ImageParams source) {
            brightness = source.brightness;
            contrast = source.contrast;
            exposure = source.exposure;
            highlights = source.highlights;
            shadows = source.shadows;
            warmth = source.warmth;
            tint = source.tint;
            saturation = source.saturation;
            vibrance = source.vibrance;
            grain = source.grain;
            vignette = source.vignette;
        }

        public Builder setBrightness(float brightness) {
            this.brightness = brightness;
            return this;
        }

        public Builder setContrast(float contrast) {
            this.contrast = contrast;
            return this;
        }

        public Builder setExposure(float exposure) {
            this.exposure = exposure;
            return this;
        }

        public Builder setHighlights(float highlights) {
            this.highlights = highlights;
            return this;
        }

        public Builder setShadows(float shadows) {
            this.shadows = shadows;
            return this;
        }

        public Builder setWarmth(float warmth) {
            this.warmth = warmth;
            return this;
        }

        public Builder setTint(float tint) {
            this.tint = tint;
            return this;
        }

        public Builder setSaturation(float saturation) {
            this.saturation = saturation;
            return this;
        }

        public Builder setVibrance(float vibrance) {
            this.vibrance = vibrance;
            return this;
        }

        public Builder setGrain(float grain) {
            this.grain = grain;
            return this;
        }

        public Builder setVignette(float vignette) {
            this.vignette = vignette;
            return this;
        }

        public ImageParams build() {
            return new ImageParams(this);
        }
    }
}
