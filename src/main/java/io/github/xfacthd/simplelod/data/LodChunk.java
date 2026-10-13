package io.github.xfacthd.simplelod.data;

import io.github.xfacthd.simplelod.compiler.LodCompiler;
import io.github.xfacthd.simplelod.storage.LodChunkStorage;
import io.github.xfacthd.simplelod.compiler.LodChunkMesh;
import io.github.xfacthd.simplelod.util.Utils;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.Objects;

public final class LodChunk implements Closeable {
    private final long chunkPos;
    private final int chunkHeight;
    private final @Nullable LodSection[] sections;
    private final LodSectionMask sectionMask;
    private int chunkLevelColor = -1;
    private boolean chunkEmpty;
    private int sectionMinY;
    private int sectionMaxY;
    private MeshState meshState = MeshState.OUTDATED;
    @Nullable
    private LodChunkMesh mesh;

    public LodChunk(long chunkPos, int chunkHeight) {
        this.chunkPos = chunkPos;
        this.chunkHeight = chunkHeight;
        this.sections = new LodSection[chunkHeight];
        this.sectionMask = LodSectionMask.create(chunkHeight);
    }

    public long getChunkPos() {
        return chunkPos;
    }

    public int getChunkHeight() {
        return chunkHeight;
    }

    public @Nullable LodSection getSection(int index) {
        return sections[index];
    }

    public int getChunkLevelColor() {
        return chunkLevelColor;
    }

    public boolean isEmpty() {
        return chunkEmpty;
    }

    /// Returns the section index of the lowest non-empty section
    public int getMinSection() {
        return sectionMinY;
    }

    /// Returns the section index of the highest non-empty section
    public int getMaxSection() {
        return sectionMaxY;
    }

    public @Nullable LodSection[] getSectionsClone() {
        return sections.clone();
    }

    public boolean updateSection(int index, @Nullable LodSection section, boolean fromDisk) {
        if (!Objects.equals(sections[index], section)) {
            sections[index] = section;
            if (!fromDisk) {
                updateChunkLodInfo();
            }
            sectionMask.setPresent(index, section != null);
            return true;
        }
        return false;
    }

    public LodSectionMask getSectionMask() {
        return sectionMask;
    }

    public boolean isInFrustum(Frustum frustum, int minChunkY, LodSectionMask sectionMask) {
        int minX = SectionPos.sectionToBlockCoord(ChunkPos.getX(chunkPos));
        int minZ = SectionPos.sectionToBlockCoord(ChunkPos.getZ(chunkPos));
        int minY = SectionPos.sectionToBlockCoord(minChunkY + sectionMask.getMinSectionRelativeY());
        int maxY = SectionPos.sectionToBlockCoord(minChunkY + sectionMask.getMaxSectionRelativeY());
        return Utils.isCubeInFrustum(frustum, minX, minY, minZ, minX + 16, maxY, minZ + 16);
    }

    public void invalidateMesh() {
        meshState = MeshState.OUTDATED;
    }

    @Override
    public void close() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
    }

    public @Nullable LodChunkMesh getMesh(LodChunkStorage storage, long cameraChunkPos, int vanillaRenderDistanceSquared, LodSectionMask sectionMask) {
        if (chunkEmpty) {
            return null;
        }
        LevelOfDetail level = computeLevelOfDetail(cameraChunkPos, vanillaRenderDistanceSquared);
        if (isRecompileNeeded(meshState, mesh, level, sectionMask, cameraChunkPos, vanillaRenderDistanceSquared)) {
            meshState = MeshState.COMPILING;
            LodCompiler.compile(storage, this, level, sectionMask);
        }
        return mesh;
    }

    public void onCompileComplete(@Nullable LodChunkMesh newMesh, boolean success) {
        // FIXME: freshly re-compiled chunks disappear briefly because the old mesh is discarded before the new one is uploaded
        if (success) {
            if (mesh != null) {
                mesh.close();
            }
            mesh = newMesh;
        }
        if (meshState == MeshState.COMPILING) {
            meshState = MeshState.READY;
        }
    }

    private static boolean isRecompileNeeded(
            MeshState meshState,
            @Nullable LodChunkMesh mesh,
            LevelOfDetail level,
            LodSectionMask mask,
            long cameraChunkPos,
            int vanillaRenderDistanceSquared
    ) {
        return switch (meshState) {
            case COMPILING -> false;
            case OUTDATED -> true;
            case READY -> {
                if (mesh == null) {
                    yield false;
                }
                if (level != mesh.level() || !mask.equals(mesh.sectionMask())) {
                    yield true;
                }
                // TODO: trigger re-compile when the detail level of directly adjacent neighbors changed or they switched between vanilla and LOD
                yield false;
            }
        };
    }

    private LevelOfDetail computeLevelOfDetail(long cameraChunkPos, int renderDistanceSquared) {
        // TODO: find a sensible heuristic for distance-based level selection
        /*long distanceSquared = Utils.getSquareDistance(cameraChunkPos, chunkPos) - renderDistanceSquared;
        if (distanceSquared > 500) {
            return LevelOfDetail.BLOCK_4X4;
        } else if (distanceSquared > 250) {
            return LevelOfDetail.BLOCK_2X2;
        } else {
            return LevelOfDetail.BLOCK_INDIVIDUAL;
        }*/
        return LevelOfDetail.BLOCK_INDIVIDUAL;
    }

    public void updateChunkLodInfo() {
        int color = -1;
        boolean empty = true;
        boolean first = true;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < sections.length; i++) {
            LodSection section = sections[i];
            if (section == null) {
                continue;
            }

            empty = false;
            if (first) {
                color = section.getSectionLevelColor();
                first = false;
            } else {
                color = ARGB.average(color, section.getSectionLevelColor());
            }

            minY = Math.min(minY, i);
            maxY = Math.max(maxY, i);
        }
        chunkLevelColor = color;
        chunkEmpty = empty;
        sectionMinY = empty ? 0 : minY;
        sectionMaxY = empty ? 0 : maxY;
        invalidateMesh();
    }

    @FunctionalInterface
    public interface Factory extends Long2ObjectFunction<LodChunk> {
        @Override
        LodChunk get(long chunkPos);
    }

    private enum MeshState {
        READY,
        COMPILING,
        OUTDATED,
    }
}
