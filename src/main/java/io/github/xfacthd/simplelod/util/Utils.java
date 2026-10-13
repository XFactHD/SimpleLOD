package io.github.xfacthd.simplelod.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Contract;
import org.joml.FrustumIntersection;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class Utils {
    private static final Direction[] SPIRAL_OFFSET_ORDER = new Direction[] {
            Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH
    };
    public static final int MAX_RADIUS = 128;
    private static final int[] CIRCLE_STARTS = new int[MAX_RADIUS + 2];
    private static final int[] CIRCLE_OFFSETS = computeCircleOffsets();

    public static boolean isCubeInFrustum(Frustum frustum, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int intersection = frustum.cubeInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
        return intersection == FrustumIntersection.INTERSECT || intersection == FrustumIntersection.INSIDE;
    }

    public static void forAllChunksAroundCircular(long center, int startRadius, int endRadius, LongConsumer posConsumer) {
        int start = CIRCLE_STARTS[startRadius];
        int end = CIRCLE_STARTS[endRadius + 1];
        int cx = ChunkPos.getX(center);
        int cz = ChunkPos.getZ(center);
        for (int i = start; i < end; i += 2) {
            int x = cx + CIRCLE_OFFSETS[i];
            int z = cz + CIRCLE_OFFSETS[i + 1];
            posConsumer.accept(ChunkPos.pack(x, z));
        }
    }

    public static void forAllChunksAroundSquare(ChunkPos center, int startRadius, int endRadius, LongConsumer posConsumer) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(center.x(), 0, center.z())
                .move(SPIRAL_OFFSET_ORDER[0], Math.max(startRadius - 1, 0))
                .move(SPIRAL_OFFSET_ORDER[3], startRadius - 1);
        int legCount = 4 * endRadius;
        int leg = Math.max(4 * (startRadius - 1), -1);
        int legPosCount = 0;
        int legPosIndex = 0;

        while (true) {
            cursor.move(SPIRAL_OFFSET_ORDER[(leg + 4) % 4]);

            if (legPosIndex >= legPosCount) {
                if (leg >= legCount) {
                    break;
                }

                leg++;
                legPosIndex = 0;
                legPosCount = (leg / 2) + 1;
            }
            legPosIndex++;

            posConsumer.accept(ChunkPos.pack(cursor.getX(), cursor.getZ()));
        }
    }

    public static int getExpectedChunkCountBetween(int minRadius, int maxRadius) {
        int start = CIRCLE_STARTS[minRadius];
        int end = CIRCLE_STARTS[maxRadius + 1];
        return end - start;
    }

    /// Squared distance between two chunks based on calculation in [ChunkTrackingView#isWithinDistance(int, int, int, int, int, boolean)]
    public static long getSquareDistance(long centerChunk, long targetChunk) {
        int centerX = ChunkPos.getX(centerChunk);
        int centerZ = ChunkPos.getZ(centerChunk);
        int targetX = ChunkPos.getX(targetChunk);
        int targetZ = ChunkPos.getZ(targetChunk);
        long deltaX = Math.max(0, Math.abs(targetX - centerX) - 1);
        long deltaZ = Math.max(0, Math.abs(targetZ - centerZ) - 1);
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static int[] computeCircleOffsets() {
        Int2ObjectMap<IntList> circles = new Int2ObjectOpenHashMap<>();
        forAllChunksAroundSquare(new ChunkPos(0, 0), 0, 3, pos -> {
            int x = ChunkPos.getX(pos);
            int z = ChunkPos.getZ(pos);
            int i = Mth.chessboardDistance(0, 0, x, z);
            IntList circle = circles.computeIfAbsent(i, _ -> new IntArrayList());
            circle.add(x);
            circle.add(z);
        });
        forAllChunksAroundSquare(new ChunkPos(0, 0), 4, MAX_RADIUS, pos -> {
            int x = ChunkPos.getX(pos);
            int z = ChunkPos.getZ(pos);
            for (int i = MAX_RADIUS + 1; i >= 0; i--) {
                if (!ChunkTrackingView.isInViewDistance(0, 0, i - 1, x, z)) {
                    IntList circle = circles.computeIfAbsent(i, _ -> new IntArrayList());
                    circle.add(x);
                    circle.add(z);
                    break;
                }
            }
        });
        IntList offsets = new IntArrayList();
        for (int i = 0; i <= MAX_RADIUS; i++) {
            CIRCLE_STARTS[i] = offsets.size();
            offsets.addAll(circles.get(i));
        }
        CIRCLE_STARTS[MAX_RADIUS + 1] = offsets.size();
        return offsets.toIntArray();
    }

    public static <T> void ifPresent(@Nullable T value, Consumer<T> action) {
        if (value != null) {
            action.accept(value);
        }
    }

    @Contract("null,_,_->null")
    public static <T, C, R> @Nullable R mapWith(@Nullable T value, C context, BiFunction<T, C, R> action) {
        return value == null ? null : action.apply(value, context);
    }

    public static <T, C, R> R mapWithOrElse(@Nullable T value, C context, BiFunction<T, C, R> action, R defaultValue) {
        return value == null ? defaultValue : action.apply(value, context);
    }

    public static long offset(long chunkPos, int dx, int dz) {
        int x = ChunkPos.getX(chunkPos);
        int z = ChunkPos.getZ(chunkPos);
        return ChunkPos.pack(x + dx, z + dz);
    }

    private Utils() { }
}
