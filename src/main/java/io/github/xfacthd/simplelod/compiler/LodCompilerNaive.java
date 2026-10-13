package io.github.xfacthd.simplelod.compiler;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.xfacthd.simplelod.data.LodSection;
import io.github.xfacthd.simplelod.data.LevelOfDetail;
import io.github.xfacthd.simplelod.util.Utils;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

final class LodCompilerNaive extends LodCompiler {
    @Override
    void compileSectionBlockVoxels(
            VertexConsumer buffer,
            LodSection section,
            @Nullable LodSection sectionBelow,
            @Nullable LodSection sectionAbove,
            SectionData sectionData,
            LevelOfDetail level,
            int index,
            int divisor,
            int size
    ) {
        int minY = index * 16;
        int[] colors = section.getColors(level);
        int[] colorsBelow = Utils.mapWithOrElse(sectionBelow, level, LodSection::getColors, EMPTY_COLORS);
        int[] colorsAbove = Utils.mapWithOrElse(sectionAbove, level, LodSection::getColors, EMPTY_COLORS);
        int[] colorsNorth = Utils.mapWithOrElse(sectionData.getAdjSection(Direction.NORTH, index), level, LodSection::getColors, EMPTY_COLORS);
        int[] colorsSouth = Utils.mapWithOrElse(sectionData.getAdjSection(Direction.SOUTH, index), level, LodSection::getColors, EMPTY_COLORS);
        int[] colorsWest = Utils.mapWithOrElse(sectionData.getAdjSection(Direction.WEST, index), level, LodSection::getColors, EMPTY_COLORS);
        int[] colorsEast = Utils.mapWithOrElse(sectionData.getAdjSection(Direction.EAST, index), level, LodSection::getColors, EMPTY_COLORS);
        boolean opaque = section.isFullyOpaque(level);

        if (opaque) {
            compilePerimeterBlockVoxels(buffer, level, colors, colorsBelow, colorsAbove, colorsNorth, colorsSouth, colorsWest, colorsEast, minY, divisor, size);
        } else {
            compileAllBlockVoxels(buffer, level, colors, colorsBelow, colorsAbove, colorsNorth, colorsSouth, colorsWest, colorsEast, minY, divisor, size);
        }
    }

    private static void compilePerimeterBlockVoxels(
            VertexConsumer buffer,
            LevelOfDetail level,
            int[] colors,
            int[] colorsBelow,
            int[] colorsAbove,
            int[] colorsNorth,
            int[] colorsSouth,
            int[] colorsWest,
            int[] colorsEast,
            int minY,
            int divisor,
            int size
    ) {
        int maxCoord = size - 1;
        for (int planeOne = 0; planeOne < size; planeOne++) {
            int horY = minY + planeOne * divisor;
            for (int planeTwo = 0; planeTwo < size; planeTwo++) {
                if (level.load(colorsBelow, planeOne, maxCoord, planeTwo) == 0) {
                    compileNaiveVoxelFace(buffer, planeOne, minY, planeTwo, divisor, Direction.DOWN, level.load(colors, planeOne, 0, planeTwo));
                }
                if (level.load(colorsAbove, planeOne, 0, planeTwo) == 0) {
                    compileNaiveVoxelFace(buffer, planeOne, minY + maxCoord * divisor, planeTwo, divisor, Direction.UP, level.load(colors, planeOne, maxCoord, planeTwo));
                }
                if (level.load(colorsWest, maxCoord, planeOne, planeTwo) == 0) {
                    compileNaiveVoxelFace(buffer, 0, horY, planeTwo, divisor, Direction.WEST, level.load(colors, 0, planeOne, planeTwo));
                }
                if (level.load(colorsEast, 0, planeOne, planeTwo) == 0) {
                    compileNaiveVoxelFace(buffer, maxCoord, horY, planeTwo, divisor, Direction.EAST, level.load(colors, maxCoord, planeOne, planeTwo));
                }
                if (level.load(colorsNorth, planeTwo, planeOne, maxCoord) == 0) {
                    compileNaiveVoxelFace(buffer, planeTwo, horY, 0, divisor, Direction.NORTH, level.load(colors, planeTwo, planeOne, 0));
                }
                if (level.load(colorsSouth, planeTwo, planeOne, 0) == 0) {
                    compileNaiveVoxelFace(buffer, planeTwo, horY, maxCoord, divisor, Direction.SOUTH, level.load(colors, planeTwo, planeOne, maxCoord));
                }
            }
        }
    }

    private static void compileAllBlockVoxels(
            VertexConsumer buffer,
            LevelOfDetail level,
            int[] colors,
            int[] colorsBelow,
            int[] colorsAbove,
            int[] colorsNorth,
            int[] colorsEast,
            int[] colorsSouth,
            int[] colorsWest,
            int minY,
            int divisor,
            int size
    ) {
        int maxCoord = size - 1;
        for (int y = 0; y < size; y++) {
            int minBlockY = minY + y * divisor;
            int[] colorsNegY = y == 0 ? colorsBelow : colors;
            int[] colorsPosY = y == maxCoord ? colorsAbove : colors;
            for (int x = 0; x < size; x++) {
                int minX = x * divisor;
                int[] colorsNegX = x == 0 ? colorsWest : colors;
                int[] colorsPosX = x == maxCoord ? colorsEast : colors;
                for (int z = 0; z < size; z++) {
                    int minZ = z * divisor;
                    int[] colorsNegZ = z == 0 ? colorsNorth : colors;
                    int[] colorsPosZ = z == maxCoord ? colorsSouth : colors;

                    int faceMask = FACE_MASK_ALL;
                    if (level.load(colorsNegY, x, Mth.positiveModulo(y - 1, size), z) != 0) {
                        faceMask &= ~FACE_MASK_DOWN;
                    }
                    if (level.load(colorsPosY, x, Mth.positiveModulo(y + 1, size), z) != 0) {
                        faceMask &= ~FACE_MASK_UP;
                    }
                    if (level.load(colorsNegX, Mth.positiveModulo(x - 1, size), y, z) != 0) {
                        faceMask &= ~FACE_MASK_WEST;
                    }
                    if (level.load(colorsPosX, Mth.positiveModulo(x + 1, size), y, z) != 0) {
                        faceMask &= ~FACE_MASK_EAST;
                    }
                    if (level.load(colorsNegZ, x, y, Mth.positiveModulo(z - 1, size)) != 0) {
                        faceMask &= ~FACE_MASK_NORTH;
                    }
                    if (level.load(colorsPosZ, x, y, Mth.positiveModulo(z + 1, size)) != 0) {
                        faceMask &= ~FACE_MASK_SOUTH;
                    }

                    if (faceMask != 0) {
                        int color = level.load(colors, x, y, z);
                        compileNaiveVoxel(buffer, minX, minBlockY, minZ, divisor, faceMask, color);
                    }
                }
            }
        }
    }

    private static void compileNaiveVoxel(VertexConsumer buffer, int x, int y, int z, int size, int faceMask, int color) {
        compileVoxel(buffer, x, y, z, x + size, y + size, z + size, faceMask, color);
    }

    private static void compileNaiveVoxelFace(VertexConsumer buffer, int x, int y, int z, int size, Direction side, int color) {
        compileVoxelFace(buffer, x, y, z, x + size, y + size, z + size, side, color);
    }
}
