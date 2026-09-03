package com.penguito.effectlab.render.sdk;

final class RenderDebugTracker {

    interface Listener {
        void onDebugInfo(float sdkRenderMillis, float cameraFrameMillis, float framesPerSecond);
    }

    private final Listener listener;

    private long debugInfoStartNanos;
    private long lastFrameStartNanos;
    private long renderDurationNanos;
    private long cameraFrameDurationNanos;
    private int renderedFrameCount;
    private int cameraFrameCount;

    RenderDebugTracker(Listener listener) {
        this.listener = listener;
    }

    void record(long frameStartNanos, long frameEndNanos) {
        if (debugInfoStartNanos == 0L) {
            debugInfoStartNanos = frameStartNanos;
        }
        if (lastFrameStartNanos != 0L) {
            cameraFrameDurationNanos += frameStartNanos - lastFrameStartNanos;
            cameraFrameCount++;
        }
        lastFrameStartNanos = frameStartNanos;

        renderedFrameCount++;
        renderDurationNanos += frameEndNanos - frameStartNanos;
        long debugInfoDurationNanos = frameEndNanos - debugInfoStartNanos;
        if (debugInfoDurationNanos < DEBUG_INFO_INTERVAL_NANOS) {
            return;
        }

        float sdkRenderMillis = renderDurationNanos / (float) renderedFrameCount / NANOS_PER_MILLISECOND;
        float cameraFrameMillis = cameraFrameDurationNanos / (float) cameraFrameCount / NANOS_PER_MILLISECOND;
        float framesPerSecond = cameraFrameCount * (float) NANOS_PER_SECOND / cameraFrameDurationNanos;
        listener.onDebugInfo(sdkRenderMillis, cameraFrameMillis, framesPerSecond);

        debugInfoStartNanos = frameEndNanos;
        renderDurationNanos = 0L;
        cameraFrameDurationNanos = 0L;
        renderedFrameCount = 0;
        cameraFrameCount = 0;
    }

    void reset() {
        debugInfoStartNanos = 0L;
        lastFrameStartNanos = 0L;
        renderDurationNanos = 0L;
        cameraFrameDurationNanos = 0L;
        renderedFrameCount = 0;
        cameraFrameCount = 0;
    }

    private static final long DEBUG_INFO_INTERVAL_NANOS = 1_000_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final float NANOS_PER_MILLISECOND = 1_000_000F;
}
