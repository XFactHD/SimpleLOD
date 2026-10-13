package io.github.xfacthd.simplelod.generator;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

final class LodColorProviderMapColor extends LodColorProvider {
    private static final int[] COLORS = Util.make(() -> {
        int[] colors = new int[64];
        for (int i = 0; i < colors.length; i++) {
            MapColor mapColor = MapColor.byId(i);
            colors[i] = mapColor.calculateARGBColor(MapColor.Brightness.HIGH);
        }
        return colors;
    });

    @Override
    int computeVoxelColor(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, IntList tintValues) {
        return COLORS[state.getMapColor(level, pos).id];
    }
}
