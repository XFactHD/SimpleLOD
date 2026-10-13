package io.github.xfacthd.simplelod.generator;

import io.github.xfacthd.simplelod.data.LevelOfDetail;
import io.github.xfacthd.simplelod.storage.LodChunkStorage;
import io.github.xfacthd.simplelod.data.LodSection;
import io.github.xfacthd.simplelod.util.Config;
import io.github.xfacthd.simplelod.util.ThrottledTaskStore;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class LodGenerator {
    private static final ThrottledTaskStore TASK_STORE = ThrottledTaskStore.halfExecutors();

    public static void enqueueLodUpdate(long sectionPos, BlockAndTintGetter level, boolean empty) {
        TASK_STORE.addTask(future -> future
                .thenApplyAsync(_ -> generateSectionLod(sectionPos, level, empty), Util.backgroundExecutor())
                .handleAsync(LodGenerator::submitSectionLod, Minecraft.getInstance())
        );
    }

    public static void cancelTasks() {
        TASK_STORE.cancelTasks();
    }

    private static GenerateResult generateSectionLod(long sectionPos, BlockAndTintGetter level, boolean empty) {
        if (empty) {
            return new GenerateResult(sectionPos, null);
        }

        LodColorProvider.Type colorProviderType = Config.getColorProviderType();
        LodColorProvider colorProvider = colorProviderType.provider;
        int[] blocks = LevelOfDetail.BLOCK_INDIVIDUAL.makeArray();
        BlockPos origin = new BlockPos(
                SectionPos.sectionToBlockCoord(SectionPos.x(sectionPos)),
                SectionPos.sectionToBlockCoord(SectionPos.y(sectionPos)),
                SectionPos.sectionToBlockCoord(SectionPos.z(sectionPos))
        );
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RandomSource random = RandomSource.createThreadLocalInstance();
        IntList tintValues = new IntArrayList();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.setWithOffset(origin, x, y, z);

                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        int voxelColor = colorProvider.computeVoxelColor(level, pos, state, random, tintValues);
                        LevelOfDetail.BLOCK_INDIVIDUAL.store(blocks, x, y, z, voxelColor);
                    }

                    tintValues.clear();
                }
            }
        }
        return new GenerateResult(sectionPos, new LodSection(colorProviderType, blocks));
    }

    private static Void submitSectionLod(@Nullable GenerateResult result, @Nullable Throwable error) {
        if (result != null) {
            LodChunkStorage.get().updateSection(result.sectionPos, result.section);
        }
        return null;
    }

    private record GenerateResult(long sectionPos, @Nullable LodSection section) { }

    private LodGenerator() { }
}
