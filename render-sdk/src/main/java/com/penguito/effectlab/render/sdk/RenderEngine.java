package com.penguito.effectlab.render.sdk;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;

import java.io.Closeable;

public final class RenderEngine implements Closeable {

    public interface Listener {
        void onRenderReady();

        void onRenderError();
    }

    static {
        System.loadLibrary("pelab_sdk");
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread renderThread = new HandlerThread("PELab-Render");
    private final Handler renderHandler;

    private long nativeHandle;
    private boolean isClosed;

    public RenderEngine() {
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
    }

    public static String getNativeBridgeInfo() {
        return nativeGetBridgeInfo();
    }

    public void init(Surface outputSurface, Listener listener) {
        renderHandler.post(() -> {
            releaseOnRenderThread();
            long handle = nativeInitRenderer(outputSurface);
            nativeHandle = handle;
            mainHandler.post(() -> {
                if (handle != 0L) {
                    listener.onRenderReady();
                } else {
                    listener.onRenderError();
                }
            });
        });
    }

    public void stop() {
        if (isClosed) {
            return;
        }
        renderHandler.post(this::releaseOnRenderThread);
    }

    private void releaseOnRenderThread() {
        if (nativeHandle != 0L) {
            nativeDestroyRenderer(nativeHandle);
            nativeHandle = 0L;
        }
    }

    @Override
    public void close() {
        if (isClosed) {
            return;
        }
        isClosed = true;

        renderHandler.post(() -> {
            releaseOnRenderThread();
            renderThread.quitSafely();
        });
    }

    private static native String nativeGetBridgeInfo();

    private static native long nativeInitRenderer(Surface outputSurface);

    private static native void nativeDestroyRenderer(long nativeHandle);
}
