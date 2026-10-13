package io.github.xfacthd.simplelod.generator;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

public abstract sealed class LodColorProvider permits LodColorProviderMapColor, LodColorProviderParticleTexture, LodColorProviderBlockModel {
    abstract int computeVoxelColor(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, IntList tintValues);

    public enum Type implements StringRepresentable {
        MAP_COLOR(new LodColorProviderMapColor()),
        PARTICLE_TEXTURE(new LodColorProviderParticleTexture()),
        BLOCK_MODEL(new LodColorProviderBlockModel()),
        ;

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final String name = toString().toLowerCase(Locale.ROOT);
        final LodColorProvider provider;

        Type(LodColorProvider provider) {
            this.provider = provider;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
