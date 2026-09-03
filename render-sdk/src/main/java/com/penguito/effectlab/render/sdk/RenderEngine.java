package com.penguito.effectlab.render.sdk;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;

public final class RenderEngine implements Closeable {

    public interface InitListener {
        void onRenderReady(@Nullable Surface cameraSurface);

        void onRenderError();
    }

    public interface DebugInfoListener {
        void onDebugInfo(float sdkRenderMillis, float cameraFrameMillis, float framesPerSecond);
    }

    public interface CaptureCallback {
        void onCaptureCompleted(byte[] jpegData);

        void onCaptureError();
    }

    static {
        System.loadLibrary("pelab_sdk");
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread renderThread = new HandlerThread("PELab-Render");
    private final Handler renderHandler;
    private final RenderDebugTracker debugTracker;

    private RenderInput renderInput;
    private DebugInfoListener debugInfoListener;
    private ImageParams imageParams = ImageParams.defaults();
    private String lutPath;
    private long nativeHandle;
    private int captureWidth;
    private int captureHeight;
    private boolean isClosed;

    public RenderEngine() {
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
        debugTracker = new RenderDebugTracker((sdkRenderMillis, cameraFrameMillis, framesPerSecond) ->
            mainHandler.post(() -> {
                if (debugInfoListener != null) {
                    debugInfoListener.onDebugInfo(sdkRenderMillis, cameraFrameMillis, framesPerSecond);
                }
            })
        );
    }

    public static String getNativeBridgeInfo() {
        return nativeGetBridgeInfo();
    }

    public void init(Surface outputSurface, PreviewResolution previewResolution, RenderMode inputMode, InitListener listener) {
        init(outputSurface, previewResolution, inputMode, null, listener);
    }

    public void init(Surface outputSurface, PreviewResolution previewResolution, RenderMode inputMode, String imagePath, InitListener listener) {
        if (isClosed) {
            Log.e(LOG_TAG, "RenderEngine is closed");
            mainHandler.post(listener::onRenderError);
            return;
        }
        renderHandler.post(() -> initOnRenderThread(
            outputSurface,
            previewResolution,
            inputMode,
            imagePath,
            listener)
        );
    }

    public void setDebugInfoListener(@Nullable DebugInfoListener listener) {
        debugInfoListener = listener;
    }

    private void initOnRenderThread(
            Surface outputSurface,
            PreviewResolution previewResolution,
            RenderMode inputMode,
            String imagePath,
            InitListener listener) {
        releaseOnRenderThread();
        if (inputMode == null) {
            reportInitError("Render input mode is missing", listener);
            return;
        }
        if (inputMode == RenderMode.IMAGE && (imagePath == null || imagePath.trim().isEmpty())) {
            reportInitError("Image path is missing", listener);
            return;
        }
        if (!initRendererOnRenderThread(outputSurface, previewResolution)) {
            reportInitError("Native renderer initialization failed", listener);
            return;
        }

        Surface cameraSurface = null;
        if (inputMode == RenderMode.CAMERA) {
            CameraRenderInput cameraInput = new CameraRenderInput(this, renderHandler, previewResolution);
            renderInput = cameraInput;
            cameraSurface = cameraInput.getInputSurface();
        } else {
            BitmapRenderInput bitmapInput = new BitmapRenderInput(this);
            if (!bitmapInput.init(imagePath)) {
                reportInitError("Bitmap input initialization failed: " + imagePath, listener);
                return;
            }
            renderInput = bitmapInput;
            renderInput.requestRender();
        }

        Surface readyCameraSurface = cameraSurface;
        mainHandler.post(() -> listener.onRenderReady(readyCameraSurface));
    }

    private void reportInitError(String message, InitListener listener) {
        Log.e(LOG_TAG, message);
        releaseOnRenderThread();
        mainHandler.post(listener::onRenderError);
    }

    public void setRenderParams(RenderBaseParams params) {
        if (!(params instanceof ImageParams imageParams)) {
            throw new IllegalArgumentException("params must be ImageParams");
        }
        if (isClosed) { return; }

        renderHandler.post(() -> setImageParams(imageParams));
    }

    public void setFilter(String rootPath) {
        if (isClosed) {
            return;
        }
        String resolvedLutPath = resolveLutPath(rootPath);
        renderHandler.post(() -> applyFilter(resolvedLutPath));
    }

    public void captureFrame(CaptureCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        if (isClosed) {
            mainHandler.post(callback::onCaptureError);
            return;
        }

        renderHandler.post(() -> captureFrameOnRenderThread(callback));
    }

    public void stop() {
        if (isClosed) {
            return;
        }
        renderHandler.post(this::releaseOnRenderThread);
    }

    private boolean initRendererOnRenderThread(Surface outputSurface, PreviewResolution previewResolution) {
        nativeHandle = nativeInitRenderer(outputSurface, previewResolution.getHeight(), previewResolution.getWidth());
        if (nativeHandle == 0L) {
            return false;
        }

        captureWidth = previewResolution.getHeight();
        captureHeight = previewResolution.getWidth();
        setImageParams(imageParams);
        applyFilter(lutPath);
        return true;
    }

    private void setImageParams(ImageParams params) {
        imageParams = params;
        if (nativeHandle == 0L) { return; }
        nativeSetImageParams(
                nativeHandle,
                params.getBrightness(),
                params.getWarmth()
        );
        requestRenderOnRenderThread();
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

    private void applyFilter(String path) {
        lutPath = path;
        if (nativeHandle == 0L) {
            return;
        }
        if (path == null) {
            nativeSetLutTexture(nativeHandle, null);
            requestRenderOnRenderThread();
            return;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap lutBitmap = BitmapFactory.decodeFile(path, options);
        if (lutBitmap == null) {
            nativeSetLutTexture(nativeHandle, null);
            requestRenderOnRenderThread();
            return;
        }

        boolean uploaded = nativeSetLutTexture(nativeHandle, lutBitmap);
        lutBitmap.recycle();
        if (!uploaded) {
            nativeSetLutTexture(nativeHandle, null);
        }
        requestRenderOnRenderThread();
    }

    private void requestRenderOnRenderThread() {
        if (renderInput != null) {
            renderInput.requestRender();
        }
    }

    int getCameraInputTexture() {
        return nativeGetCameraInputTexture(nativeHandle);
    }

    void renderCameraFrame(float[] textureMatrix) {
        if (nativeHandle != 0L) {
            long frameStartNanos = System.nanoTime();
            nativeRenderCameraFrame(nativeHandle, textureMatrix);
            debugTracker.record(frameStartNanos, System.nanoTime());
        }
    }

    boolean setBitmap(Bitmap bitmap) {
        return nativeHandle != 0L && nativeSetBitmap(nativeHandle, bitmap);
    }

    void renderBitmap() {
        if (nativeHandle != 0L) {
            long frameStartNanos = System.nanoTime();
            nativeRenderBitmap(nativeHandle);
            debugTracker.record(frameStartNanos, System.nanoTime());
        }
    }

    private void captureFrameOnRenderThread(CaptureCallback callback) {
        if (nativeHandle == 0L) {
            mainHandler.post(callback::onCaptureError);
            return;
        }

        Bitmap bitmap = Bitmap.createBitmap(
                captureWidth,
                captureHeight,
                Bitmap.Config.ARGB_8888);

        // native capture failed
        if (!nativeCaptureFrame(nativeHandle, bitmap)) {
            bitmap.recycle();
            mainHandler.post(callback::onCaptureError);
            return;
        }

        Matrix flipMatrix = new Matrix();
        flipMatrix.setScale(1.0F, -1.0F);
        Bitmap outputBitmap = Bitmap.createBitmap(bitmap, 0, 0, captureWidth, captureHeight, flipMatrix, false);
        bitmap.recycle();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputBitmap.compress(
                Bitmap.CompressFormat.JPEG,
                95,
                outputStream);
        outputBitmap.recycle();

        byte[] jpegData = outputStream.toByteArray();
        mainHandler.post(() -> callback.onCaptureCompleted(jpegData));
    }

    private void releaseOnRenderThread() {
        if (renderInput != null) {
            renderInput.release();
            renderInput = null;
        }
        if (nativeHandle != 0L) {
            nativeDestroyRenderer(nativeHandle);
            nativeHandle = 0L;
        }
        captureWidth = 0;
        captureHeight = 0;
        debugTracker.reset();
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

    private static native int nativeGetCameraInputTexture(long nativeHandle);

    private static native void nativeRenderCameraFrame(long nativeHandle, float[] textureMatrix);

    private static native boolean nativeSetBitmap(long nativeHandle, Bitmap bitmap);

    private static native void nativeRenderBitmap(long nativeHandle);

    private static native void nativeSetImageParams(long nativeHandle, float brightness, float warmth);

    private static native boolean nativeSetLutTexture(long nativeHandle, Bitmap lutBitmap);

    private static native boolean nativeCaptureFrame(long nativeHandle, Bitmap bitmap);

    private static native void nativeDestroyRenderer(long nativeHandle);

    private static final String LUT_FILE_NAME = "lut.png";
    private static final String LOG_TAG = "PELabRender";
}
