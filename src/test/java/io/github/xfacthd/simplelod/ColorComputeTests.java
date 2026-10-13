package io.github.xfacthd.simplelod;

import io.github.xfacthd.simplelod.generator.LodColorProvider;
import io.github.xfacthd.simplelod.data.LevelOfDetail;
import io.github.xfacthd.simplelod.data.LodSection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

public final class ColorComputeTests {
    @Test
    void testLodResolve2x2() {
        testLodResolve(LevelOfDetail.BLOCK_2X2);
    }

    @Test
    void testLodResolve4x4() {
        testLodResolve(LevelOfDetail.BLOCK_4X4);
    }

    @Test
    void testLodResolve8x8() {
        testLodResolve(LevelOfDetail.BLOCK_8X8);
    }

    @Test
    void testLodResolveSection() {
        testLodResolve(LevelOfDetail.SECTION);
    }

    private static void testLodResolve(LevelOfDetail targetLevel) {
        int[] colors = fillColors(LevelOfDetail.BLOCK_INDIVIDUAL, targetLevel.getVoxelSize());
        LodSection section = new LodSection(LodColorProvider.Type.MAP_COLOR, colors);
        int[] expected = fillColors(targetLevel, 1);
        int[] actual = targetLevel == LevelOfDetail.SECTION ? new int[] { section.getSectionLevelColor() } : section.getColors(targetLevel);

        String[] expectedColors = IntStream.of(expected)
                .mapToObj(Integer::toHexString)
                .toArray(String[]::new);
        String[] actualColors = IntStream.of(actual)
                .mapToObj(Integer::toHexString)
                .toArray(String[]::new);
        Assertions.assertArrayEquals(expectedColors, actualColors);
    }

    private static int[] fillColors(LevelOfDetail level, int groupSize) {
        int[] colors = level.makeArray();
        int axisSize = level.getAxisSize();
        for (int y = 0; y < axisSize; y++) {
            boolean yEvenVoxel = (y / groupSize) % 2 == 0;
            for (int x = 0; x < axisSize; x++) {
                boolean xEvenVoxel = (x / groupSize) % 2 == 0;
                for (int z = 0; z < axisSize; z++) {
                    boolean zEvenVoxel = (z / groupSize) % 2 == 0;
                    int color;
                    if (yEvenVoxel ^ xEvenVoxel ^ zEvenVoxel) {
                        color = 0xFFFF0000;
                    } else {
                        color = 0xFF00FF00;
                    }
                    level.store(colors, x, y, z, color);
                }
            }
        }
        return colors;
    }
}
