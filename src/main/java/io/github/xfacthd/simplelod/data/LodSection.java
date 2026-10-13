package io.github.xfacthd.simplelod.data;

import io.github.xfacthd.simplelod.generator.LodColorProvider;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public final class LodSection {
    private static final LevelOfDetail[] SUB_SECTION_LEVELS = Arrays.stream(LevelOfDetail.values())
            .filter(LevelOfDetail::isSubSection)
            .toArray(LevelOfDetail[]::new);
    private static final int LEVEL_COUNT = SUB_SECTION_LEVELS.length + 1;

    private final LodColorProvider.Type colorType;
    private final int[] blocks;
    private final int[] twoByTwo = LevelOfDetail.BLOCK_2X2.makeArray();
    private final int[] fourByFour = LevelOfDetail.BLOCK_4X4.makeArray();
    private final int[] eightByEight = LevelOfDetail.BLOCK_8X8.makeArray();
    private final int sectionLevelColor;
    private final boolean[] opaque;

    public LodSection(LodColorProvider.Type colorType, int[] blocks) {
        this.colorType = colorType;
        this.blocks = blocks;
        this.sectionLevelColor = resolve();
        this.opaque = computeSectionOpacity();
    }

    public LodColorProvider.Type getColorType() {
        return colorType;
    }

    public int[] getColors(LevelOfDetail level) {
        return switch (level) {
            case BLOCK_INDIVIDUAL -> blocks;
            case BLOCK_2X2 -> twoByTwo;
            case BLOCK_4X4 -> fourByFour;
            case BLOCK_8X8 -> eightByEight;
            default -> throw new IllegalArgumentException("Cannot query chunk-level or section-level detail on section");
        };
    }

    public int getSectionLevelColor() {
        return sectionLevelColor;
    }

    public boolean isFullyOpaque(LevelOfDetail level) {
        return opaque[level.ordinal()];
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj == this || (obj instanceof LodSection other && colorType == other.colorType && Arrays.equals(blocks, other.blocks));
    }

    private int resolve() {
        resolveLevel(blocks, twoByTwo, LevelOfDetail.BLOCK_INDIVIDUAL);
        resolveLevel(twoByTwo, fourByFour, LevelOfDetail.BLOCK_2X2);
        resolveLevel(fourByFour, eightByEight, LevelOfDetail.BLOCK_4X4);
        return resolveGroup(eightByEight, 0, 0, 0, LevelOfDetail.BLOCK_8X8);
    }

    private boolean[] computeSectionOpacity() {
        boolean[] opaque = new boolean[LEVEL_COUNT];
        for (LevelOfDetail level : SUB_SECTION_LEVELS) {
            opaque[level.ordinal()] = isLevelOpaque(level, getColors(level));
        }
        opaque[LevelOfDetail.SECTION.ordinal()] = sectionLevelColor != 0;
        return opaque;
    }

    private static boolean isLevelOpaque(LevelOfDetail level, int[] colors) {
        int size = level.getAxisSize();
        int max = size - 1;
        for (int edgeOne = 0; edgeOne < size; edgeOne++) {
            for (int edgeTwo = 0; edgeTwo < size; edgeTwo++) {
                if (level.load(colors, 0, edgeOne, edgeTwo) == 0 || level.load(colors, max, edgeOne, edgeTwo) == 0) {
                    return false;
                }
                if (level.load(colors, edgeOne, 0, edgeTwo) == 0 || level.load(colors, edgeOne, max, edgeTwo) == 0) {
                    return false;
                }
                if (level.load(colors, edgeTwo, edgeOne, 0) == 0 || level.load(colors, edgeTwo, edgeOne, max) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void resolveLevel(int[] source, int[] dest, LevelOfDetail sourceLevel) {
        LevelOfDetail destLevel = sourceLevel.getNext();
        int outSize = destLevel.getAxisSize();
        for (int y = 0; y < outSize; y++) {
            for (int x = 0; x < outSize; x++) {
                for (int z = 0; z < outSize; z++) {
                    int color = resolveGroup(source, y * 2, x * 2, z * 2, sourceLevel);
                    destLevel.store(dest, x, y, z, color);
                }
            }
        }
    }

    private static int resolveGroup(int[] source, int y, int x, int z, LevelOfDetail sourceLevel) {
        int color = 0;
        boolean first = true;
        for (int i = 0; i < 8; i++) {
            int yOff = i & 1;
            int xOff = (i >> 1) & 1;
            int zOff = (i >> 2) & 1;
            int srcColor = sourceLevel.load(source, x + xOff, y + yOff, z + zOff);
            if (ARGB.alpha(srcColor) == 0) {
                continue;
            }
            if (first) {
                color = srcColor;
                first = false;
            } else {
                color = ARGB.average(color, srcColor);
            }
        }
        return color;
    }
}
