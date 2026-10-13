package io.github.xfacthd.simplelod.render;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import io.github.xfacthd.simplelod.SimpleLOD;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Dumpable;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Path;

final class NoiseTexture extends AbstractTexture implements Dumpable, ResourceManagerReloadListener {
    private static final String LABEL = "simplelod_noise";
    private static final GpuFormat FORMAT = GpuFormat.RGBA8_UNORM;
    private static final int SIZE = 16;
    private static final int PIXEL_SIZE = FORMAT.blockSize();
    private static final int BUFFER_SIZE = SIZE * SIZE * PIXEL_SIZE;
    private static final double MIN = 0.93;
    private static final double MAX = 1.0;
    private static final long SEED = 0L;
    public static final Identifier RELOADER_ID = SimpleLOD.id("noise");

    NoiseTexture() {
        GpuDevice device = RenderSystem.getDevice();
        this.texture = device.createTexture(LABEL, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, FORMAT, SIZE, SIZE, 1, 1);
        this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
        this.textureView = device.createTextureView(texture);
        initContents();
    }

    private void initContents() {
        ByteBuffer buffer = MemoryUtil.memAlloc(BUFFER_SIZE);
        try {
            SimplexNoise noise = new SimplexNoise(RandomSource.create(SEED));
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double value = noise.get(x, y);
                    value = Mth.lerp(value, MIN, MAX);
                    buffer.putInt((y * SIZE + x) * PIXEL_SIZE, ARGB.gray((float) value));
                }
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(getTexture(), buffer, 0, 0, 0, 0, SIZE, SIZE);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    @Override
    public void dumpContents(Identifier selfId, Path dir) {
        TextureUtil.writeAsPNG(dir, selfId.toDebugFileName(), getTexture(), 0, argb -> argb);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        initContents();
    }
}
