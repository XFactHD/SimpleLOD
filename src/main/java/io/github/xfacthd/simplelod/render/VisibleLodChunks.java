package io.github.xfacthd.simplelod.render;

import io.github.xfacthd.simplelod.compiler.LodChunkMesh;
import io.github.xfacthd.simplelod.compiler.LodCompiler;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

record VisibleLodChunks(int minBlockY, List<Entry> chunks) {
    int forEach(List<LodChunkInfo> chunkInfos, MeshConsumer consumer) {
        int maxIndexCount = 0;
        for (Entry chunk : chunks) {
            LodChunkMesh mesh = chunk.mesh;
            if (mesh.buffer() == null) {
                continue;
            }

            long chunkPos = chunk.chunkPos;
            int chunkX = SectionPos.sectionToBlockCoord(ChunkPos.getX(chunkPos));
            int chunkZ = SectionPos.sectionToBlockCoord(ChunkPos.getZ(chunkPos));
            int infoIndex = chunkInfos.size();
            chunkInfos.add(new LodChunkInfo(chunkX, minBlockY, chunkZ));
            int baseVertex = (int) (mesh.offset() / LodCompiler.VERTEX_SIZE);
            consumer.accept(mesh, baseVertex, infoIndex);
            maxIndexCount = Math.max(maxIndexCount, mesh.indexCount());
        }
        return maxIndexCount;
    }

    void finish() {
        if (LodCompiler.uploadStagedBuffers()) {
            chunks.forEach(entry -> entry.mesh.bufferHasBeenUploaded(entry.mesh));
        }
    }

    record Entry(long chunkPos, LodChunkMesh mesh) { }

    interface MeshConsumer {
        void accept(LodChunkMesh mesh, int baseVertex, int infoIndex);
    }
}
