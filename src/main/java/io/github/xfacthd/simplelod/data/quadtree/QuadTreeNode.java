package io.github.xfacthd.simplelod.data.quadtree;

import io.github.xfacthd.simplelod.util.Utils;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.function.LongConsumer;

abstract sealed class QuadTreeNode permits ChunkQuadTree, QuadTreeLeaf {
    protected static final int MAX_DEPTH = 6;
    protected static final int LEAF_MIN_WIDTH = 8;

    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;

    QuadTreeNode(int minChunkX, int minChunkZ, int size) {
        this.minX = SectionPos.sectionToBlockCoord(minChunkX);
        this.minZ = SectionPos.sectionToBlockCoord(minChunkZ);
        this.maxX = SectionPos.sectionToBlockCoord(minChunkX + size);
        this.maxZ = SectionPos.sectionToBlockCoord(minChunkZ + size);
    }

    abstract void forEachInFrustum(Frustum frustum, int minY, int maxY, LongConsumer chunkConsumer);

    abstract void add(long chunkPos);

    abstract boolean trim();

    final boolean contains(long chunkPos) {
        int x = SectionPos.sectionToBlockCoord(ChunkPos.getX(chunkPos));
        int z = SectionPos.sectionToBlockCoord(ChunkPos.getZ(chunkPos));
        return x >= minX && z >= minZ && x < maxX && z < maxZ;
    }

    final boolean isInFrustum(Frustum frustum, int minY, int maxY) {
        return Utils.isCubeInFrustum(frustum, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
