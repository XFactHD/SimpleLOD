package io.github.xfacthd.simplelod.data.quadtree;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.client.renderer.culling.Frustum;

import java.util.function.LongConsumer;

final class QuadTreeLeaf extends QuadTreeNode {
    private final LongList chunks = new LongArrayList();

    QuadTreeLeaf(int minChunkX, int minChunkZ, int size) {
        super(minChunkX, minChunkZ, size);
    }

    @Override
    void forEachInFrustum(Frustum frustum, int minY, int maxY, LongConsumer chunkConsumer) {
        chunks.forEach(chunkConsumer);
    }

    @Override
    void add(long chunkPos) {
        chunks.add(chunkPos);
    }

    @Override
    boolean trim() {
        return chunks.isEmpty();
    }
}
