package io.github.xfacthd.simplelod.compiler;

import io.github.xfacthd.simplelod.data.LodSection;
import io.github.xfacthd.simplelod.data.LodSectionMask;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

record SectionData(int minSection, int maxSection, @Nullable LodSection[] sections, @Nullable LodSection[] @Nullable[] adjSectionsByDir, LodSectionMask mask) {
    @Nullable LodSection getSection(int index) {
        return mask.isPresent(index) ? sections[index] : null;
    }

    @Nullable LodSection getAdjSection(Direction offset, int index) {
        if (!mask.isPresent(index)) {
            return null;
        }
        @Nullable LodSection[] adjSections = adjSectionsByDir[offset.get2DDataValue()];
        return adjSections != null ? adjSections[index] : null;
    }
}
