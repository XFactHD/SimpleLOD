package io.github.xfacthd.simplelod.compiler;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.xfacthd.simplelod.data.LodSection;
import io.github.xfacthd.simplelod.data.LevelOfDetail;
import io.github.xfacthd.simplelod.util.Utils;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

final class LodCompilerGreedy extends LodCompiler {
    private static final Direction.Axis[] AXES = Direction.Axis.values();

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
        int[] voxelMaskNeg = new int[size * size];
        int[] voxelMaskPos = new int[size * size];
        boolean opaque = section.isFullyOpaque(level);
        for (Direction.Axis axis : AXES) {
            VoxelReader mainReader = VoxelReader.of(axis, level, colors);

            boolean y = axis == Direction.Axis.Y;
            LodSection negAdjSection = y ? sectionBelow : sectionData.getAdjSection(axis.getNegative(), index);
            LodSection posAdjSection = y ? sectionAbove : sectionData.getAdjSection(axis.getPositive(), index);
            VoxelReader negAdjReader = VoxelReader.of(axis, level, Utils.mapWith(negAdjSection, level, LodSection::getColors));
            VoxelReader posAdjReader = VoxelReader.of(axis, level, Utils.mapWith(posAdjSection, level, LodSection::getColors));

            VoxelFaceCompiler negCompiler = VoxelFaceCompiler.of(axis, buffer, minY, divisor, Direction.Axis::getNegative);
            VoxelFaceCompiler posCompiler = VoxelFaceCompiler.of(axis, buffer, minY, divisor, Direction.Axis::getPositive);

            buildVoxelMask(voxelMaskNeg, size, mainReader, negAdjReader, posAdjReader, opaque);
            System.arraycopy(voxelMaskNeg, 0, voxelMaskPos, 0, voxelMaskNeg.length);
            for (int normal = 0; normal < size; normal++) {
                compileVoxelPlaneGreedy(voxelMaskNeg, mainReader, -1, size, 0, negCompiler, size - 1 - normal);
                compileVoxelPlaneGreedy(voxelMaskPos, mainReader,  1, size, 1, posCompiler, normal);
            }
        }
    }

    private static void buildVoxelMask(int[] voxelMask, int size, VoxelReader mainReader, VoxelReader negAdjReader, VoxelReader posAdjReader, boolean opaque) {
        int opaqueColMask = ~(-1 << size) << 1;
        int posAdjMask = 1 << (size + 1);
        for (int planeOne = 0; planeOne < size; planeOne++) {
            for (int planeTwo = 0; planeTwo < size; planeTwo++) {
                int colMask;
                if (opaque) {
                    colMask = opaqueColMask;
                } else {
                    colMask = 0;
                    for (int normal = 0; normal < size; normal++) {
                        if (mainReader.read(planeOne, planeTwo, normal) != 0) {
                            colMask |= 0b10 << normal;
                        }
                    }
                }
                if (negAdjReader.read(planeOne, planeTwo, size - 1) != 0) {
                    colMask |= 1;
                }
                if (posAdjReader.read(planeOne, planeTwo, 0) != 0) {
                    colMask |= posAdjMask;
                }
                voxelMask[planeOne * size + planeTwo] = colMask;
            }
        }
    }

    private static void compileVoxelPlaneGreedy(int[] voxelMask, VoxelReader reader, int occlusionOffset, int size, int normalOffset, VoxelFaceCompiler faceCompiler, int normal) {
        for (int planeOne = 0; planeOne < size; planeOne++) {
            for (int planeTwo = 0; planeTwo < size; planeTwo++) {
                int column = voxelMask[planeOne * size + planeTwo];
                int bitIdx = (normal + 1);
                if (!isSet(column, bitIdx) || isSet(column, bitIdx + occlusionOffset)) {
                    continue;
                }

                int startColor = reader.read(planeOne, planeTwo, normal);
                int endPlaneTwo = scanRow(voxelMask, reader, startColor, normal, planeOne, planeTwo + 1, size - 1, size, occlusionOffset);
                int endPlaneOne = scanRows(voxelMask, reader, startColor, normal, planeOne + 1, planeTwo, endPlaneTwo, size, occlusionOffset);

                clearBits(voxelMask, normal, planeOne, endPlaneOne, planeTwo, endPlaneTwo, size);
                faceCompiler.compile(normal + normalOffset, planeOne, planeTwo, endPlaneOne + 1, endPlaneTwo + 1, startColor);

                planeTwo = endPlaneTwo;
            }
        }
    }

    // Scan along a row (i.e. X axis for a Z face) to find the longest continuous line of non-air voxels of the same color
    private static int scanRow(int[] voxelMask, VoxelReader reader, int startColor, int normal, int planeOne, int planeTwoStart, int planeTwoEnd, int size, int occlusionOffset) {
        int bitIdx = (normal + 1);
        for (int planeTwo = planeTwoStart; planeTwo <= planeTwoEnd; planeTwo++) {
            int column = voxelMask[planeOne * size + planeTwo];
            if (!isSet(column, bitIdx) || isSet(column, bitIdx + occlusionOffset)) {
                return planeTwo - 1;
            }
            int color = reader.read(planeOne, planeTwo, normal);
            if (color != startColor) {
                return planeTwo - 1;
            }
        }
        return planeTwoEnd;
    }

    // Scan rows (i.e. along the Y axis for an X/Z face) to find the tallest stack of rows of non-air voxels of the same color
    private static int scanRows(int[] voxelMask, VoxelReader reader, int startColor, int normal, int startPlaneOne, int startPlaneTwo, int endPlaneTwo, int size, int occlusionOffset) {
        for (int planeOne = startPlaneOne; planeOne < size; planeOne++) {
            int end = scanRow(voxelMask, reader, startColor, normal, planeOne, startPlaneTwo, endPlaneTwo, size, occlusionOffset);
            if (end < endPlaneTwo) {
                return planeOne - 1;
            }
        }
        return size - 1;
    }

    private static void compileVoxelFaceGreedy(VertexConsumer buffer, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int baseY, int divisor, Direction side, int color) {
        compileVoxelFace(buffer, minX * divisor, baseY + minY * divisor, minZ * divisor, maxX * divisor, baseY + maxY * divisor, maxZ * divisor, side, color);
    }

    private static boolean isSet(int mask, int idx) {
        return (mask & (1 << idx)) != 0;
    }

    private static void clearBits(int[] voxelMask, int normal, int startPlaneOne, int endPlaneOne, int startPlaneTwo, int endPlaneTwo, int size) {
        int mask = ~(1 << (normal + 1));
        for (int planeOne = startPlaneOne; planeOne <= endPlaneOne; planeOne++) {
            for (int planeTwo = startPlaneTwo; planeTwo <= endPlaneTwo; planeTwo++) {
                voxelMask[planeOne * size + planeTwo] &= mask;
            }
        }
    }

    private interface VoxelReader {
        VoxelReader EMPTY = (_, _, _) -> 0;

        int read(int planeOne, int planeTwo, int normal);

        static VoxelReader of(Direction.Axis axis, LevelOfDetail level, int @Nullable[] colors) {
            if (colors == null) {
                return EMPTY;
            }
            return switch (axis) {
                case X -> (y, z, x) -> level.load(colors, x, y, z);
                case Y -> (x, z, y) -> level.load(colors, x, y, z);
                case Z -> (y, x, z) -> level.load(colors, x, y, z);
            };
        }
    }

    private interface VoxelFaceCompiler {
        void compile(int normal, int minPlaneOne, int minPlaneTwo, int maxPlaneOne, int maxPlaneTwo, int color);

        static VoxelFaceCompiler of(Direction.Axis axis, VertexConsumer buffer, int minY, int divisor, Function<Direction.Axis, Direction> dirGetter) {
            return switch (axis) {
                case X -> (normal, minPlaneOne, minPlaneTwo, maxPlaneOne, maxPlaneTwo, color) ->
                        compileVoxelFaceGreedy(buffer, normal, minPlaneOne, minPlaneTwo, normal, maxPlaneOne, maxPlaneTwo, minY, divisor, dirGetter.apply(axis), color);
                case Y -> (normal, minPlaneOne, minPlaneTwo, maxPlaneOne, maxPlaneTwo, color) ->
                        compileVoxelFaceGreedy(buffer, minPlaneOne, normal, minPlaneTwo, maxPlaneOne, normal, maxPlaneTwo, minY, divisor, dirGetter.apply(axis), color);
                case Z -> (normal, minPlaneOne, minPlaneTwo, maxPlaneOne, maxPlaneTwo, color) ->
                        compileVoxelFaceGreedy(buffer, minPlaneTwo, minPlaneOne, normal, maxPlaneTwo, maxPlaneOne, normal, minY, divisor, dirGetter.apply(axis), color);
            };
        }
    }
}
