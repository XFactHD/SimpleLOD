package io.github.xfacthd.simplelod.generator;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.fluid.FluidTintSource;

final class LodColorProviderParticleTexture extends LodColorProvider {
    private static final BlockTintSource GRASS_BLOCK_REPLACEMENT_SOURCE = BlockTintSources.grass();
    @SuppressWarnings("deprecation")
    private static final SpriteId GRASS_BLOCK_NORMAL_SPRITE = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Identifier.withDefaultNamespace("block/grass_block_top"));
    @SuppressWarnings("deprecation")
    private static final SpriteId GRASS_BLOCK_SNOWY_SPRITE = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Identifier.withDefaultNamespace("block/snow"));

    @Override
    int computeVoxelColor(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, IntList tintValues) {
        // Grass is stupid and uses dirt as its particle
        if (state.is(Blocks.GRASS_BLOCK)) {
            boolean snowy = state.getValue(BlockStateProperties.SNOWY);
            TextureAtlasSprite grassSprite = Minecraft.getInstance().getAtlasManager().get(snowy ? GRASS_BLOCK_SNOWY_SPRITE : GRASS_BLOCK_NORMAL_SPRITE);
            int baseColor = SpriteColorCache.getSpriteAverageColor(grassSprite);
            if (snowy) {
                return baseColor;
            }
            int tintColor = GRASS_BLOCK_REPLACEMENT_SOURCE.colorAsTerrainParticle(state, level, pos);
            return ARGB.multiply(baseColor, tintColor);
        }

        if (!state.getFluidState().isEmpty() && !state.is(BlockTags.BLOCKS_MOTION)) {
            FluidModel fluidModel = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(state.getFluidState());
            int baseColor = SpriteColorCache.getSpriteAverageColor(fluidModel.stillMaterial().sprite());
            FluidTintSource tintSource = fluidModel.fluidTintSource();
            if (tintSource != null) {
                return ARGB.multiply(baseColor, tintSource.colorAsTerrainParticle(state, level, pos));
            }
            return baseColor;
        }

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getModelManager()
                .getBlockStateModelSet()
                .get(state)
                .particleMaterial(level, pos, state)
                .sprite();
        int baseColor = SpriteColorCache.getSpriteAverageColor(sprite);
        BlockTintSource tintSource = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
        if (tintSource != null) {
            return ARGB.multiply(baseColor, tintSource.colorAsTerrainParticle(state, level, pos));
        }
        IClientBlockExtensions.of(state).collectDynamicTintValues(state, level, pos, tintValues);
        if (!tintValues.isEmpty()) {
            return ARGB.multiply(baseColor, tintValues.getInt(0));
        }
        return baseColor;
    }
}
