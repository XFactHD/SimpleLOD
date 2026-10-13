package io.github.xfacthd.simplelod.generator;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// TODO: reconsider quads as a color source, the implementation is comparatively memory-hungry and slow for little to no benefit
final class LodColorProviderBlockModel extends LodColorProvider {
    private static final Direction[] QUERY_DIRECTIONS = { Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };
    private static final Map<BlockState, BaseColor[]> SIMPLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Object, BaseColor[]> DYNAMIC_CACHE = new ConcurrentHashMap<>();
    private static final BaseColor[] UNKNOWN_COLOR = { new BaseColor(-1, -1) };

    @Override
    int computeVoxelColor(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, IntList tintValues) {
        if (!state.getFluidState().isEmpty() && !state.is(BlockTags.BLOCKS_MOTION)) {
            return Type.PARTICLE_TEXTURE.provider.computeVoxelColor(level, pos, state, random, tintValues);
        }

        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        long seed = state.getSeed(pos);

        random.setSeed(seed);
        Object geometryKey = model.createGeometryKey(level, pos, state, random);
        random.setSeed(seed);
        BaseColor[] baseColors;
        if (geometryKey == null || geometryKey == model || model instanceof WeightedVariants) {
            // Assume that blocks without a geometry key don't change dynamically in a way that is relevant to the color
            baseColors = computeColorSimple(model, state, random);
        } else {
            baseColors = computeColorDynamic(model, level, pos, state, random, geometryKey);
        }

        List<BlockTintSource> tintSources = Minecraft.getInstance().getBlockColors().getTintSources(state);
        if (!tintSources.isEmpty()) {
            return computeFinalColor(baseColors, ColorReader.forTintSources(level, pos, state, tintSources));
        }
        IClientBlockExtensions.of(state).collectDynamicTintValues(state, level, pos, tintValues);
        if (!tintValues.isEmpty()) {
            return computeFinalColor(baseColors, ColorReader.forDynamicTints(tintValues));
        }
        return computeFinalColor(baseColors, _ -> -1);
    }

    private static int computeFinalColor(BaseColor[] colors, ColorReader reader) {
        int finalColor = 0;
        for (BaseColor color : colors) {
            int resolved = color.color;
            if (color.tintIndex != -1) {
                resolved = ARGB.multiply(resolved, reader.read(color.tintIndex));
            }
            if (finalColor == 0) {
                finalColor = resolved;
            } else {
                finalColor = ARGB.average(finalColor, resolved);
            }
        }
        return finalColor;
    }

    private static BaseColor[] computeColorSimple(BlockStateModel model, BlockState state, RandomSource random) {
        BaseColor[] color = SIMPLE_CACHE.get(state);
        if (color == null) {
            color = computeColorUncached(model, state, BlockAndTintGetter.EMPTY, BlockPos.ZERO, random);
            SIMPLE_CACHE.put(state, color);
        }
        return color;
    }

    private static BaseColor[] computeColorDynamic(BlockStateModel model, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Object geometryKey) {
        BaseColor[] color = DYNAMIC_CACHE.get(geometryKey);
        if (color == null) {
            color = computeColorUncached(model, state, level, pos, random);
            DYNAMIC_CACHE.put(geometryKey, color);
        }
        return color;
    }

    private static BaseColor[] computeColorUncached(BlockStateModel model, BlockState state, BlockAndTintGetter level, BlockPos pos, RandomSource random) {
        List<BlockStateModelPart> parts = new ObjectArrayList<>();
        model.collectParts(level, pos, state, random, parts);
        if (parts.isEmpty()) {
            return UNKNOWN_COLOR;
        }

        List<BakedQuad> quads = new ObjectArrayList<>();
        for (Direction face : QUERY_DIRECTIONS) {
            for (BlockStateModelPart part : parts) {
                quads.addAll(part.getQuads(face));
            }
            if (quads.isEmpty()) {
                for (BlockStateModelPart part : parts) {
                    for (BakedQuad quad : part.getQuads(null)) {
                        if (quad.direction() == face) {
                            quads.add(quad);
                        }
                    }
                }
            }
            if (!quads.isEmpty()) {
                Set<BaseColor> colors = new ObjectArraySet<>();
                for (BakedQuad quad : quads) {
                    Integer spriteColor = SpriteColorCache.getSpriteAverageColor(quad.materialInfo().sprite());
                    colors.add(new BaseColor(spriteColor, quad.materialInfo().tintIndex()));
                }
                return colors.toArray(BaseColor[]::new);
            }
        }
        return UNKNOWN_COLOR;
    }

    private record BaseColor(int color, int tintIndex) {}

    private interface ColorReader {
        int read(int tintIndex);

        static ColorReader forTintSources(BlockAndTintGetter level, BlockPos pos, BlockState state, List<BlockTintSource> sources) {
            return index -> {
                if (index >= 0 && index < sources.size()) {
                    return sources.get(index).colorInWorld(state, level, pos);
                }
                return -1;
            };
        }

        static ColorReader forDynamicTints(IntList tintValues) {
            return index -> index >= 0 && index < tintValues.size() ? tintValues.getInt(index) : -1;
        }
    }
}
