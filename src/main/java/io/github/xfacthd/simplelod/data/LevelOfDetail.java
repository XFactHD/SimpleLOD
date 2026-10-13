package io.github.xfacthd.simplelod.data;

public enum LevelOfDetail {
    /// Blocks are rendered individually
    BLOCK_INDIVIDUAL(1),
    /// Blocks are rendered in 2x2 groups
    BLOCK_2X2(2),
    /// Blocks are rendered in 4x4 groups
    BLOCK_4X4(4),
    /// Blocks are rendered in 8x8 groups
    BLOCK_8X8(8),
    /// An entire chunk section (16x16 blocks) is rendered as one voxel
    SECTION(16),
    /// An entire chunk (16x16x\<level height\>) is rendered as one voxel
    CHUNK(16),
    ;

    private final boolean subSection;
    private final int voxelSize;
    private final int axisSize;
    private final int multX;
    private final int multY;

    LevelOfDetail(int voxelSize) {
        this.subSection = voxelSize < 16;
        this.voxelSize = voxelSize;
        this.axisSize = 16 / voxelSize;
        this.multX = axisSize;
        this.multY = axisSize * axisSize;
    }

    public int getVoxelSize() {
        return voxelSize;
    }

    public int getAxisSize() {
        return axisSize;
    }

    boolean isSubSection() {
        return subSection;
    }

    public int[] makeArray() {
        return new int[axisSize * axisSize * axisSize];
    }

    public int load(int[] source, int x, int y, int z) {
        return source[index(x, y, z, multY, multX)];
    }

    public void store(int[] dest, int x, int y, int z, int color) {
        dest[index(x, y, z, multY, multX)] = color;
    }

    private static int index(int x, int y, int z, int yMult, int xMult) {
        return y * yMult + x * xMult + z;
    }

    LevelOfDetail getNext() {
        return switch (this) {
            case BLOCK_INDIVIDUAL -> BLOCK_2X2;
            case BLOCK_2X2 -> BLOCK_4X4;
            case BLOCK_4X4 -> BLOCK_8X8;
            case BLOCK_8X8 -> SECTION;
            case SECTION -> CHUNK;
            case CHUNK -> throw new IllegalArgumentException("Cannot get next level of LevelOfDetail.CHUNK");
        };
    }
}
