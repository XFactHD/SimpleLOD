package io.github.xfacthd.simplelod.compiler;

import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import io.github.xfacthd.simplelod.data.LodSectionMask;
import io.github.xfacthd.simplelod.data.LevelOfDetail;
import net.minecraft.Optionull;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;

public final class LodChunkMesh implements Closeable, UberGpuBuffer.UploadCallback<LodChunkMesh> {
    private final LodCompiler owner;
    private final LevelOfDetail level;
    private final LodSectionMask sectionMask;
    private final int indexCount;
    @Nullable
    private GpuBuffer buffer;
    private long offset;

    LodChunkMesh(LodCompiler owner, LevelOfDetail level, LodSectionMask sectionMask, int indexCount) {
        this.owner = owner;
        this.level = level;
        this.sectionMask = sectionMask;
        this.indexCount = indexCount;
    }

    @Override
    public void close() {
        owner.discardMesh(this);
    }

    public LevelOfDetail level() {
        return level;
    }

    public LodSectionMask sectionMask() {
        return sectionMask;
    }

    public int indexCount() {
        return indexCount;
    }

    public @Nullable GpuBuffer buffer() {
        return buffer;
    }

    public long offset() {
        return offset;
    }

    @Override
    public void bufferHasBeenUploaded(LodChunkMesh key) {
        owner.updateMeshBufferSlice(this);
    }

    void updateBuffer(@Nullable GpuBuffer buffer, long offset) {
        this.buffer = buffer;
        this.offset = offset;
    }

    @Override
    public String toString() {
        String bufferString = Optionull.mapOrDefault(buffer, buf -> buf.getClass().getSimpleName(), "null") + "@" + offset;
        return "LodChunkMesh[level=" + level + ", sectionMask=" + sectionMask + ", indexCount=" + indexCount + ", buffer=" + bufferString + ']';
    }
}
