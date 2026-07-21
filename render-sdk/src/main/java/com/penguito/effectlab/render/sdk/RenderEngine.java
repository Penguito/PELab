package com.penguito.effectlab.render.sdk;

public final class RenderEngine {

    static {
        System.loadLibrary("pelab_sdk");
    }

    private RenderEngine() { }

    public static String getNativeBridgeInfo() {
        return nativeGetBridgeInfo();
    }

    private static native String nativeGetBridgeInfo();
}
