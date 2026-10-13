package io.github.xfacthd.simplelod.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.DynamicGpuDataStorage;

import java.nio.ByteBuffer;

record LodChunkInfo(int x, int y, int z) implements DynamicGpuDataStorage.DynamicGpuData {
    static final int SIZE = new Std140SizeCalculator().putIVec3().get();

    @Override
    public void write(ByteBuffer buffer) {
        Std140Builder.intoBuffer(buffer).putIVec3(x, y, z);
    }
}
