package io.github.xfacthd.simplelod.util;

import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

public final class AccumulativeStopwatch {
    private static final int FRAME_TIME_COUNT = 120;

    private final long[] microPerFrame = new long[FRAME_TIME_COUNT];
    private long startNanos = 0L;
    private int frameTimeIndex = 0;

    public void start() {
        startNanos = System.nanoTime();
    }

    public void stop() {
        long elapsed = System.nanoTime() - startNanos;
        microPerFrame[frameTimeIndex] = TimeUnit.MICROSECONDS.convert(elapsed, TimeUnit.NANOSECONDS);
        frameTimeIndex = (frameTimeIndex + 1) % FRAME_TIME_COUNT;
    }

    public double getAverage() {
        return LongStream.of(microPerFrame).average().orElse(0D);
    }
}
