package com.penguito.effectlab.render.sdk;

/** image adjustment parameters */
public final class ImageParams extends RenderBaseParams {

    private static final float MIN_VALUE = -1.0F;
    private static final float MAX_VALUE = 1.0F;

    private final float brightness;
    private final float warmth;

    private ImageParams(Builder builder) {
        brightness = clamp(builder.brightness);
        warmth = clamp(builder.warmth);
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

    public float getWarmth() {
        return warmth;
    }

    private static float clamp(float value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    public static final class Builder {

        private float brightness = 0.0F;
        private float warmth = 0.0F;

        private Builder() {

        }

        private Builder(ImageParams source) {
            brightness = source.brightness;
            warmth = source.warmth;
        }

        public Builder setBrightness(float brightness) {
            this.brightness = brightness;
            return this;
        }

        public Builder setWarmth(float warmth) {
            this.warmth = warmth;
            return this;
        }

        public ImageParams build() {
            return new ImageParams(this);
        }
    }
}
