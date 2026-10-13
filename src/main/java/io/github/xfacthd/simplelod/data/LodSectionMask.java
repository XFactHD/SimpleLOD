package io.github.xfacthd.simplelod.data;

import net.minecraft.util.Util;
import net.minecraft.world.level.dimension.DimensionType;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HexFormat;

public abstract sealed class LodSectionMask {
    private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase().withPrefix("0x");

    public static LodSectionMask create(int sectionCount) {
        if (sectionCount > 32) {
            return new Jumbo(Math.ceilDiv(sectionCount, 32));
        } else {
            return new Regular();
        }
    }

    @SuppressWarnings("SameParameterValue")
    public static LodSectionMask getConstant(int sectionCount, boolean full) {
        if (sectionCount <= 32) {
            return full ? Regular.FULL : Regular.EMPTY;
        } else {
            return full ? Jumbo.FULL : Jumbo.EMPTY;
        }
    }

    public abstract void setPresent(int index, boolean present);

    public abstract boolean isPresent(int index);

    public abstract boolean isEmpty();

    public abstract LodSectionMask copy();

    public abstract void clear(boolean allSet);

    public abstract void and(LodSectionMask other);

    public abstract int getMinSectionRelativeY();

    public abstract int getMaxSectionRelativeY();

    @Override
    public abstract boolean equals(Object obj);

    private static final class Regular extends LodSectionMask {
        private static final Regular EMPTY = new Regular(0);
        private static final Regular FULL = new Regular(-1);

        private int mask = 0;

        private Regular() { }

        private Regular(int mask) {
            this.mask = mask;
        }

        @Override
        public void setPresent(int index, boolean present) {
            int indexMask = 1 << index;
            if (present) {
                mask |= indexMask;
            } else {
                mask &= ~indexMask;
            }
        }

        @Override
        public boolean isPresent(int index) {
            int indexMask = 1 << index;
            return (mask & indexMask) != 0;
        }

        @Override
        public boolean isEmpty() {
            return mask == 0L;
        }

        @Override
        public LodSectionMask copy() {
            return switch (mask) {
                case 0 -> EMPTY;
                case -1 -> FULL;
                default -> new Regular(mask);
            };
        }

        @Override
        public void clear(boolean allSet) {
            mask = allSet ? -1 : 0;
        }

        @Override
        public void and(LodSectionMask other) {
            mask &= ((Regular) other).mask;
        }

        @Override
        public int getMinSectionRelativeY() {
            int zeros = Integer.numberOfTrailingZeros(mask);
            return zeros == 32 ? 0 : zeros;
        }

        @Override
        public int getMaxSectionRelativeY() {
            return 32 - Integer.numberOfLeadingZeros(mask);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj == this || (obj instanceof Regular other && mask == other.mask);
        }

        @Override
        public String toString() {
            return HEX_FORMAT.toHexDigits(mask, 8);
        }
    }

    private static final class Jumbo extends LodSectionMask {
        private static final Jumbo EMPTY = Util.make(new Jumbo(DimensionType.Y_SIZE / 16), mask -> mask.clear(false));
        private static final Jumbo FULL = Util.make(new Jumbo(DimensionType.Y_SIZE / 16), mask -> mask.clear(true));

        private final int[] subMasks;

        private Jumbo(int maskCount) {
            this.subMasks = new int[maskCount];
        }

        private Jumbo(int[] subMasks) {
            this.subMasks = subMasks;
        }

        @Override
        public void setPresent(int index, boolean present) {
            int maskIndex = index / 32;
            int indexMask = 1 << (index % 32);
            if (present) {
                subMasks[maskIndex] |= indexMask;
            } else {
                subMasks[maskIndex] &= ~indexMask;
            }
        }

        @Override
        public boolean isPresent(int index) {
            int maskIndex = index / 32;
            int indexMask = 1 << (index % 32);
            return (subMasks[maskIndex] & indexMask) != 0;
        }

        @Override
        public boolean isEmpty() {
            if (this == EMPTY) {
                return true;
            }

            for (int subMask : subMasks) {
                if (subMask != 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public LodSectionMask copy() {
            if (this == EMPTY || this == FULL) {
                return this;
            }
            return new Jumbo(subMasks.clone());
        }

        @Override
        public void clear(boolean allSet) {
            Arrays.fill(subMasks, allSet ? -1 : 0);
        }

        @Override
        public void and(LodSectionMask other) {
            int[] otherMasks = ((Jumbo) other).subMasks;
            for (int i = 0; i < subMasks.length; i++) {
                subMasks[i] &= otherMasks[i];
            }
        }

        @Override
        public int getMinSectionRelativeY() {
            for (int subMask : subMasks) {
                int zeros = Integer.numberOfTrailingZeros(subMask);
                if (zeros != 32) {
                    return zeros;
                }
            }
            return 0;
        }

        @Override
        public int getMaxSectionRelativeY() {
            for (int i = subMasks.length - 1; i >= 0; i--) {
                int zeros = Integer.numberOfLeadingZeros(subMasks[i]);
                if (zeros != 32) {
                    return 32 - zeros;
                }
            }
            return 0;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj == this || (obj instanceof Jumbo other && Arrays.equals(subMasks, other.subMasks));
        }
    }
}
