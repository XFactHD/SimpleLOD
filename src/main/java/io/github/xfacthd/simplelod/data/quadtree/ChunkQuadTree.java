package io.github.xfacthd.simplelod.data.quadtree;

import io.github.xfacthd.simplelod.util.Utils;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.function.LongConsumer;

public final class ChunkQuadTree extends QuadTreeNode {
    private final @Nullable QuadTreeNode[] children = new QuadTreeNode[4];

    public static ChunkQuadTree create(long centerChunk, int minRadius, int maxRadius) {
        int size = Mth.smallestEncompassingPowerOfTwo(maxRadius * 2 + 1);
        int minChunkX = ChunkPos.getX(centerChunk) - (size / 2);
        int minChunkZ = ChunkPos.getZ(centerChunk) - (size / 2);
        ChunkQuadTree quadTree = new ChunkQuadTree(0, minChunkX, minChunkZ, size);
        Utils.forAllChunksAroundCircular(centerChunk, minRadius, maxRadius, quadTree::add);
        quadTree.trim();
        return quadTree;
    }

    private ChunkQuadTree(int depth, int minChunkX, int minChunkZ, int size) {
        super(minChunkX, minChunkZ, size);
        int childSize = size / 2;
        if (childSize <= LEAF_MIN_WIDTH || depth >= MAX_DEPTH) {
            children[0] = new QuadTreeLeaf(minChunkX + childSize, minChunkZ,             childSize);
            children[1] = new QuadTreeLeaf(minChunkX,             minChunkZ,             childSize);
            children[2] = new QuadTreeLeaf(minChunkX,             minChunkZ + childSize, childSize);
            children[3] = new QuadTreeLeaf(minChunkX + childSize, minChunkZ + childSize, childSize);
        } else {
            depth++;
            children[0] = new ChunkQuadTree(depth, minChunkX + childSize, minChunkZ,             childSize);
            children[1] = new ChunkQuadTree(depth, minChunkX,             minChunkZ,             childSize);
            children[2] = new ChunkQuadTree(depth, minChunkX,             minChunkZ + childSize, childSize);
            children[3] = new ChunkQuadTree(depth, minChunkX + childSize, minChunkZ + childSize, childSize);
        }
    }

    @Override
    public void forEachInFrustum(Frustum frustum, int minY, int maxY, LongConsumer chunkConsumer) {
        for (QuadTreeNode child : children) {
            if (child != null && child.isInFrustum(frustum, minY, maxY)) {
                child.forEachInFrustum(frustum, minY, maxY, chunkConsumer);
            }
        }
    }

    @Override
    void add(long chunkPos) {
        for (QuadTreeNode child : children) {
            if (child != null && child.contains(chunkPos)) {
                child.add(chunkPos);
                break;
            }
        }
    }

    @Override
    boolean trim() {
        boolean empty = true;
        for (int i = 0; i < children.length; i++) {
            QuadTreeNode child = children[i];
            if (child == null) {
                continue;
            }
            if (child.trim()) {
                children[i] = null;
            } else {
                empty = false;
            }
        }
        return empty;
    }
}
