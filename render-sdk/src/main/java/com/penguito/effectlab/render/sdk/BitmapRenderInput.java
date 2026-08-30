package com.penguito.effectlab.render.sdk;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

final class BitmapRenderInput implements RenderInput {

    private final RenderEngine renderEngine;

    BitmapRenderInput(RenderEngine renderEngine) {
        this.renderEngine = renderEngine;
    }

    boolean init(String imagePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
        if (bitmap == null) {
            return false;
        }

        boolean uploaded = renderEngine.setBitmap(bitmap);
        bitmap.recycle();
        return uploaded;
    }

    @Override
    public void requestRender() {
        renderEngine.renderBitmap();
    }

    @Override
    public void release() {
        // bitmap texture is released with NativeRenderer
    }
}
