package io.github.xfacthd.simplelod.generator;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.xfacthd.simplelod.SimpleLOD;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SpriteColorCache {
    private static final Identifier LISTENER_ID = SimpleLOD.id("sprite_color_cache");
    private static final Map<TextureAtlasSprite, Integer> COLOR_CACHE = new ConcurrentHashMap<>();

    static Integer getSpriteAverageColor(TextureAtlasSprite sprite) {
        return COLOR_CACHE.computeIfAbsent(sprite, SpriteColorCache::computeSpriteAverageColor);
    }

    private static Integer computeSpriteAverageColor(TextureAtlasSprite sprite) {
        int avgColor = 0;
        NativeImage image = sprite.contents().getOriginalImage();
        int frameWidth = sprite.contents().width();
        int frameHeight = sprite.contents().height();
        int imageWidth = image.getWidth();
        IntBuffer buffer = MemoryUtil.memIntBuffer(image.getPointer(), imageWidth * frameHeight);
        for (int y = 0; y < frameHeight; y++) {
            for (int x = 0; x < frameWidth; x++) {
                int color = ARGB.fromABGR(buffer.get(y * imageWidth + x));
                if (ARGB.alpha(color) != 0) {
                    if (avgColor == 0) {
                        avgColor = color;
                    } else {
                        avgColor = ARGB.average(avgColor, color);
                    }
                }
            }
        }
        return avgColor;
    }

    public static void register(AddClientReloadListenersEvent event) {
        event.addListener(LISTENER_ID, new Listener());
    }

    private static final class Listener implements ResourceManagerReloadListener {
        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            COLOR_CACHE.clear();
        }
    }

    private SpriteColorCache() { }
}
