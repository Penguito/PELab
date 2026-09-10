package com.penguito.effectlab.render.sdk;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

final class BitmapRenderInput implements RenderInput {

    private final RenderEngine renderEngine;
    private Bitmap bitmap;

    BitmapRenderInput(RenderEngine renderEngine) {
        this.renderEngine = renderEngine;
    }

    boolean load(String imagePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        bitmap = BitmapFactory.decodeFile(imagePath, options);
        return bitmap != null;
    }

    int getWidth() {
        return bitmap.getWidth();
    }

    int getHeight() {
        return bitmap.getHeight();
    }

    boolean upload() {
        boolean uploaded = renderEngine.setBitmap(bitmap);
        bitmap.recycle();
        bitmap = null;
        return uploaded;
    }

    @Override
    public void requestRender() {
        renderEngine.renderBitmap();
    }

    @Override
    public void release() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
        // bitmap texture is released with NativeRenderer
    }
}
