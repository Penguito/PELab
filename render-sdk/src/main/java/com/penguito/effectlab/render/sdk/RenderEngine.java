package com.penguito.effectlab.render.sdk;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;

import java.io.Closeable;
import java.io.File;

public final class RenderEngine implements Closeable {

    public interface Listener {
        void onRenderReady(Surface inputSurface);

        void onDebugInfo(float frameDurationMillis, float framesPerSecond);

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
    private Listener listener;
    private ImageParams imageParams = ImageParams.defaults();
    private String lutPath;
    private long nativeHandle;
    private long debugInfoStartNanos;
    private long renderDurationNanos;
    private int renderedFrameCount;
    private boolean isClosed;

    public RenderEngine() {
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
    }

    public static String getNativeBridgeInfo() {
        return nativeGetBridgeInfo();
    }

    public void init(Surface outputSurface, PreviewResolution previewResolution, Listener listener) {
        renderHandler.post(() -> {
            releaseOnRenderThread();
            this.listener = listener;
            long handle = nativeInitRenderer(
                    outputSurface,
                    previewResolution.getHeight(),
                    previewResolution.getWidth());
            nativeHandle = handle;
            if (handle != 0L) {
                applyImageParams(imageParams);
                int textureId = nativeGetInputTexture(handle);

                // init surface texture
                inputSurfaceTexture = new SurfaceTexture(textureId);
                inputSurfaceTexture.setDefaultBufferSize(previewResolution.getWidth(), previewResolution.getHeight());
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

    public void setRenderParams(RenderBaseParams params) {
        if (!(params instanceof ImageParams imageParams)) {
            throw new IllegalArgumentException("params must be ImageParams");
        }
        if (isClosed) { return; }

        renderHandler.post(() -> applyImageParams(imageParams));
    }

    public void setFilter(String rootPath) {
        if (isClosed) {
            return;
        }
        String resolvedLutPath = resolveLutPath(rootPath);
        renderHandler.post(() -> lutPath = resolvedLutPath);
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

        long frameStartNanos = System.nanoTime();
        surfaceTexture.updateTexImage();
        surfaceTexture.getTransformMatrix(textureMatrix);
        nativeRenderFrame(nativeHandle, textureMatrix);
        updateDebugInfo(frameStartNanos, System.nanoTime());
    }

    private void applyImageParams(ImageParams params) {
        imageParams = params;
        if (nativeHandle == 0L) { return; }
        nativeSetImageParams(
                nativeHandle,
                params.getBrightness(),
                params.getWarmth()
        );
    }

    private String resolveLutPath(String rootPath) {
        if (rootPath == null || rootPath.trim().isEmpty()) {
            return null;
        }

        File lutFile = new File(rootPath, LUT_FILE_NAME);
        if (!lutFile.isFile()) {
            throw new IllegalArgumentException("lut.png not found in filter root: " + rootPath);
        }
        return lutFile.getAbsolutePath();
    }

    private void updateDebugInfo(long frameStartNanos, long frameEndNanos) {
        if (debugInfoStartNanos == 0L) {
            debugInfoStartNanos = frameStartNanos;
        }

        renderedFrameCount++;
        renderDurationNanos += frameEndNanos - frameStartNanos;
        long debugInfoDurationNanos = frameEndNanos - debugInfoStartNanos;
        if (debugInfoDurationNanos < DEBUG_INFO_INTERVAL_NANOS) {
            return;
        }

        float framesPerSecond = renderedFrameCount * (float) DEBUG_INFO_INTERVAL_NANOS / debugInfoDurationNanos;
        float frameDurationMillis = renderDurationNanos / (float) renderedFrameCount / NANOS_PER_MILLISECOND;
        Listener callback = listener;
        if (callback != null) {
            mainHandler.post(() ->
                callback.onDebugInfo(frameDurationMillis, framesPerSecond));
        }

        debugInfoStartNanos = frameEndNanos;
        renderDurationNanos = 0L;
        renderedFrameCount = 0;
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
        listener = null;
        debugInfoStartNanos = 0L;
        renderDurationNanos = 0L;
        renderedFrameCount = 0;
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

    private static native long nativeInitRenderer(Surface outputSurface, int normalizedWidth, int normalizedHeight);

    private static native int nativeGetInputTexture(long nativeHandle);

    private static native void nativeRenderFrame(long nativeHandle, float[] textureMatrix);

    private static native void nativeSetImageParams(long nativeHandle, float brightness, float warmth);

    private static native void nativeDestroyRenderer(long nativeHandle);

    private static final long DEBUG_INFO_INTERVAL_NANOS = 1_000_000_000L;
    private static final float NANOS_PER_MILLISECOND = 1_000_000F;
    private static final String LUT_FILE_NAME = "lut.png";
}
