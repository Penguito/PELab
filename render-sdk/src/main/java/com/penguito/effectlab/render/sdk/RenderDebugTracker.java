package com.penguito.effectlab.render.sdk;

final class RenderDebugTracker {

    interface Listener {
        void onDebugInfo(float frameDurationMillis, float framesPerSecond);
    }

    private final Listener listener;

    private long debugInfoStartNanos;
    private long renderDurationNanos;
    private int renderedFrameCount;

    RenderDebugTracker(Listener listener) {
        this.listener = listener;
    }

    void record(long frameStartNanos, long frameEndNanos) {
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
        listener.onDebugInfo(frameDurationMillis, framesPerSecond);

        debugInfoStartNanos = frameEndNanos;
        renderDurationNanos = 0L;
        renderedFrameCount = 0;
    }

    void reset() {
        debugInfoStartNanos = 0L;
        renderDurationNanos = 0L;
        renderedFrameCount = 0;
    }

    private static final long DEBUG_INFO_INTERVAL_NANOS = 1_000_000_000L;
    private static final float NANOS_PER_MILLISECOND = 1_000_000F;
}
