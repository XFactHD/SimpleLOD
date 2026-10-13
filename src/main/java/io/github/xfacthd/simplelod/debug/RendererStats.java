package io.github.xfacthd.simplelod.debug;

public record RendererStats(String mode, int visibleChunks, double extractTime, double prepareTime, double renderTime) {}
