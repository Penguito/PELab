package com.penguito.effectlab.render.sdk;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;

import java.io.Closeable;

public final class RenderEngine implements Closeable {

    public interface Listener {
        void onRenderReady(Surface inputSurface);

        void onRenderError();
    }

    static {
        System.loadLibrary("pelab_sdk");
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread renderThread = new HandlerThread("PELab-Render");
    private final Handler renderHandler;
    private final float[] textureMatrix = new float[16];

    private SurfaceTexture inputSurfaceTexture;
    private Surface inputSurface;
    private long nativeHandle;
    private boolean isClosed;

    public RenderEngine() {
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
    }

    public static String getNativeBridgeInfo() {
        return nativeGetBridgeInfo();
    }

    public void init(
            Surface outputSurface,
            int inputWidth,
            int inputHeight,
            Listener listener) {
        renderHandler.post(() -> {
            releaseOnRenderThread();
            long handle = nativeInitRenderer(outputSurface);
            nativeHandle = handle;
            if (handle != 0L) {
                int textureId = nativeGetInputTexture(handle);

                // init surface texture
                inputSurfaceTexture = new SurfaceTexture(textureId);
                inputSurfaceTexture.setDefaultBufferSize(inputWidth, inputHeight);
                inputSurfaceTexture.setOnFrameAvailableListener(this::renderFrameOnRenderThread, renderHandler);
                inputSurface = new Surface(inputSurfaceTexture);
            }

            Surface cameraInputSurface = inputSurface;
            mainHandler.post(() -> {
                if (cameraInputSurface != null) {
                    listener.onRenderReady(cameraInputSurface);
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

    private void renderFrameOnRenderThread(SurfaceTexture surfaceTexture) {
        if (surfaceTexture != inputSurfaceTexture) {
            return;
        }

        surfaceTexture.updateTexImage();
        surfaceTexture.getTransformMatrix(textureMatrix);
        nativeRenderFrame(nativeHandle, textureMatrix);
    }

    private void releaseOnRenderThread() {
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        if (inputSurfaceTexture != null) {
            inputSurfaceTexture.setOnFrameAvailableListener(null);
            inputSurfaceTexture.release();
            inputSurfaceTexture = null;
        }
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

    private static native int nativeGetInputTexture(long nativeHandle);

    private static native void nativeRenderFrame(long nativeHandle, float[] textureMatrix);

    private static native void nativeDestroyRenderer(long nativeHandle);
}
