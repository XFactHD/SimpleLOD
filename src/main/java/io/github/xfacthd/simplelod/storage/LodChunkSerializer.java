package io.github.xfacthd.simplelod.storage;

import io.github.xfacthd.simplelod.data.LodChunk;
import io.github.xfacthd.simplelod.data.LodSection;
import io.github.xfacthd.simplelod.generator.LodColorProvider;
import io.github.xfacthd.simplelod.data.LevelOfDetail;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

final class LodChunkSerializer {
    static PackedData packSections(LodChunk chunk) {
        @Nullable PackedSection[] sections = new PackedSection[chunk.getChunkHeight()];
        for (int i = 0; i < chunk.getChunkHeight(); i++) {
            LodSection section = chunk.getSection(i);
            if (section != null) {
                int[] colors = section.getColors(LevelOfDetail.BLOCK_INDIVIDUAL);
                sections[i] = new PackedSection(section.getColorType(), colors.clone());
            }
        }
        return new PackedData(chunk.getChunkPos(), chunk.getChunkHeight(), sections);
    }

    static CompoundTag serialize(PackedData packed) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", packed.chunkPos);
        ListTag sectionList = new ListTag(packed.chunkHeight);
        for (int i = 0; i < packed.chunkHeight; i++) {
            PackedSection section = packed.sections[i];
            if (section != null) {
                CompoundTag sectionTag = new CompoundTag();
                sectionTag.putInt("index", i);
                sectionTag.store("provider", LodColorProvider.Type.CODEC, section.colorProviderType);
                sectionTag.putIntArray("colors", section.colors);
                sectionList.add(sectionTag);
            }
        }
        tag.put("sections", sectionList);
        return tag;
    }

    static @Nullable PackedData deserialize(long chunkPos, int chunkHeight, @Nullable CompoundTag tag) {
        if (tag == null) {
            return null;
        }

        Optional<Long> tagPos = tag.getLong("pos");
        if (tagPos.isEmpty() || tagPos.get() != chunkPos) {
            return null;
        }

        @Nullable PackedSection[] sections = new PackedSection[chunkHeight];
        ListTag sectionList = tag.getListOrEmpty("sections");
        for (int i = 0; i < sectionList.size(); i++) {
            Optional<CompoundTag> optSectionTag = sectionList.getCompound(i);
            if (optSectionTag.isEmpty()) {
                continue;
            }

            CompoundTag sectionTag = optSectionTag.get();
            int index = sectionTag.getIntOr("index", -1);
            if (index < 0 || index >= chunkHeight) {
                continue;
            }

            LodColorProvider.Type colorProviderType = sectionTag.read("provider", LodColorProvider.Type.CODEC).orElse(LodColorProvider.Type.MAP_COLOR);
            Optional<int[]> colors = sectionTag.getIntArray("colors");
            if (colors.isPresent()) {
                sections[index] = new PackedSection(colorProviderType, colors.get());
            }
        }
        return new PackedData(chunkPos, chunkHeight, sections);
    }

    @Contract("!null,_ -> !null")
    static @Nullable LodChunk unpack(@Nullable PackedData packed, LodChunk.Factory chunkFactory) {
        if (packed == null) {
            return null;
        }

        LodChunk chunk = chunkFactory.get(packed.chunkPos);
        for (int i = 0; i < packed.sections.length; i++) {
            PackedSection section = packed.sections[i];
            if (section != null) {
                chunk.updateSection(i, new LodSection(section.colorProviderType, section.colors), true);
            }
        }
        chunk.updateChunkLodInfo();
        return chunk;
    }

    record PackedData(long chunkPos, int chunkHeight, @Nullable PackedSection[] sections) {}

    record PackedSection(LodColorProvider.Type colorProviderType, int[] colors) {}

    private LodChunkSerializer() {}
}
