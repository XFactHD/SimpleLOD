package io.github.xfacthd.simplelod.storage;

import net.minecraft.world.level.ChunkPos;

final class LodRegionPos {
    static final int REGION_BITS = 4;
    static final int REGION_SIZE = 1 << REGION_BITS;
    private static final int REGION_MASK = REGION_SIZE - 1;
    private static final long COORD_BITS = 32;
    private static final long COORD_MASK = (1L << COORD_BITS) - 1L;

    static long fromChunk(long chunkPos) {
        int chunkX = ChunkPos.getX(chunkPos);
        int chunkZ = ChunkPos.getZ(chunkPos);
        return pack(fromChunkCoord(chunkX), fromChunkCoord(chunkZ));
    }

    static long toChunk(long regionPos) {
        int chunkX = toChunkCoord(getX(regionPos));
        int chunkZ = toChunkCoord(getZ(regionPos));
        return ChunkPos.pack(chunkX, chunkZ);
    }

    static int fromChunkCoord(int coord) {
        return coord >> REGION_BITS;
    }

    static int toChunkCoord(int coord) {
        return coord << REGION_BITS;
    }

    static int getX(long regionPos) {
        return (int)(regionPos & COORD_MASK);
    }

    static int getZ(long regionPos) {
        return (int)((regionPos >>> COORD_BITS) & COORD_MASK);
    }

    static long pack(int regionX, int regionZ) {
        return (regionX & COORD_MASK) | (regionZ & COORD_MASK) << COORD_BITS;
    }

    static int getRegionRelative(long chunkPos) {
        int chunkX = ChunkPos.getX(chunkPos);
        int chunkZ = ChunkPos.getZ(chunkPos);
        return ((chunkZ & REGION_MASK) << REGION_BITS) | (chunkX & REGION_MASK);
    }

    private LodRegionPos() { }
}
