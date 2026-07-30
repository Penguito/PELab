package com.penguito.effectlab.render.sdk;

public enum PreviewResolution {
    P720(720, 1280, 720),
    P1080(1080, 1920, 1080);

    private final int key;
    private final int width;
    private final int height;

    PreviewResolution(int key, int width, int height) {
        this.key = key;
        this.width = width;
        this.height = height;
    }

    public int getKey() {
        return key;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
