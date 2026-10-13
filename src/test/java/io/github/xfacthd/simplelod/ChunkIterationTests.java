package io.github.xfacthd.simplelod;

import io.github.xfacthd.simplelod.util.Utils;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public final class ChunkIterationTests {
    @Test
    void testStartRadiusZero() {
        List<ChunkPos> expected = List.of(
                new ChunkPos(0, 0),
                new ChunkPos(1, 0),
                new ChunkPos(1, 1),
                new ChunkPos(0, 1),
                new ChunkPos(-1, 1),
                new ChunkPos(-1, 0),
                new ChunkPos(-1, -1),
                new ChunkPos(0, -1),
                new ChunkPos(1, -1),
                new ChunkPos(2, -1),
                new ChunkPos(2, 0),
                new ChunkPos(2, 1),
                new ChunkPos(2, 2),
                new ChunkPos(1, 2),
                new ChunkPos(0, 2),
                new ChunkPos(-1, 2),
                new ChunkPos(-2, 2),
                new ChunkPos(-2, 1),
                new ChunkPos(-2, 0),
                new ChunkPos(-2, -1),
                new ChunkPos(-2, -2),
                new ChunkPos(-1, -2),
                new ChunkPos(0, -2),
                new ChunkPos(1, -2),
                new ChunkPos(2, -2)
        );
        List<ChunkPos> actual = new ArrayList<>();
        Utils.forAllChunksAroundSquare(new ChunkPos(0, 0), 0, 2, pos -> actual.add(ChunkPos.unpack(pos)));

        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testStartRadiusNonZero() {
        List<ChunkPos> expected = List.of(
                new ChunkPos(2, -2),
                new ChunkPos(2, -1),
                new ChunkPos(2, 0),
                new ChunkPos(2, 1),
                new ChunkPos(2, 2),
                new ChunkPos(1, 2),
                new ChunkPos(0, 2),
                new ChunkPos(-1, 2),
                new ChunkPos(-2, 2),
                new ChunkPos(-2, 1),
                new ChunkPos(-2, 0),
                new ChunkPos(-2, -1),
                new ChunkPos(-2, -2),
                new ChunkPos(-1, -2),
                new ChunkPos(0, -2),
                new ChunkPos(1, -2),
                new ChunkPos(2, -2),
                new ChunkPos(3, -2),
                new ChunkPos(3, -1),
                new ChunkPos(3, 0),
                new ChunkPos(3, 1),
                new ChunkPos(3, 2),
                new ChunkPos(3, 3),
                new ChunkPos(2, 3),
                new ChunkPos(1, 3),
                new ChunkPos(0, 3),
                new ChunkPos(-1, 3),
                new ChunkPos(-2, 3),
                new ChunkPos(-3, 3),
                new ChunkPos(-3, 2),
                new ChunkPos(-3, 1),
                new ChunkPos(-3, 0),
                new ChunkPos(-3, -1),
                new ChunkPos(-3, -2),
                new ChunkPos(-3, -3),
                new ChunkPos(-2, -3),
                new ChunkPos(-1, -3),
                new ChunkPos(0, -3),
                new ChunkPos(1, -3),
                new ChunkPos(2, -3),
                new ChunkPos(3, -3),
                new ChunkPos(4, -3),
                new ChunkPos(4, -2),
                new ChunkPos(4, -1),
                new ChunkPos(4, 0),
                new ChunkPos(4, 1),
                new ChunkPos(4, 2),
                new ChunkPos(4, 3),
                new ChunkPos(4, 4),
                new ChunkPos(3, 4),
                new ChunkPos(2, 4),
                new ChunkPos(1, 4),
                new ChunkPos(0, 4),
                new ChunkPos(-1, 4),
                new ChunkPos(-2, 4),
                new ChunkPos(-3, 4),
                new ChunkPos(-4, 4),
                new ChunkPos(-4, 3),
                new ChunkPos(-4, 2),
                new ChunkPos(-4, 1),
                new ChunkPos(-4, 0),
                new ChunkPos(-4, -1),
                new ChunkPos(-4, -2),
                new ChunkPos(-4, -3),
                new ChunkPos(-4, -4),
                new ChunkPos(-3, -4),
                new ChunkPos(-2, -4),
                new ChunkPos(-1, -4),
                new ChunkPos(0, -4),
                new ChunkPos(1, -4),
                new ChunkPos(2, -4),
                new ChunkPos(3, -4),
                new ChunkPos(4, -4)
        );
        List<ChunkPos> actual = new ArrayList<>();
        Utils.forAllChunksAroundSquare(new ChunkPos(0, 0), 2, 4, pos -> actual.add(ChunkPos.unpack(pos)));

        Assertions.assertEquals(expected, actual);
    }
}
