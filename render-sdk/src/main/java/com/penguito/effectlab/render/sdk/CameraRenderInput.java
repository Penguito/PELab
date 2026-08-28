package com.penguito.effectlab.render.sdk;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.view.Surface;

final class CameraRenderInput implements RenderInput {

    private final RenderEngine renderEngine;
    private final float[] textureMatrix = new float[16];

    private SurfaceTexture surfaceTexture;
    private Surface inputSurface;

    CameraRenderInput(RenderEngine renderEngine, Handler renderHandler, PreviewResolution previewResolution) {
        this.renderEngine = renderEngine;

        int textureId = renderEngine.getCameraInputTexture();
        surfaceTexture = new SurfaceTexture(textureId);
        surfaceTexture.setDefaultBufferSize(
                previewResolution.getWidth(),
                previewResolution.getHeight());
        surfaceTexture.setOnFrameAvailableListener(this::renderFrame, renderHandler);
        inputSurface = new Surface(surfaceTexture);
    }

    Surface getInputSurface() {
        return inputSurface;
    }

    private void renderFrame(SurfaceTexture frameSurfaceTexture) {
        if (frameSurfaceTexture != surfaceTexture) {
            return;
        }

        frameSurfaceTexture.updateTexImage();
        frameSurfaceTexture.getTransformMatrix(textureMatrix);
        renderEngine.renderCameraFrame(textureMatrix);
    }

    @Override
    public void requestRender() {
        // camera parameters apply on the next frame
    }

    @Override
    public void release() {
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.setOnFrameAvailableListener(null);
            surfaceTexture.release();
            surfaceTexture = null;
        }
    }
}
