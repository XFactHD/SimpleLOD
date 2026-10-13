package io.github.xfacthd.simplelod.debug;

import io.github.xfacthd.simplelod.SimpleLOD;
import io.github.xfacthd.simplelod.compiler.LodCompiler;
import io.github.xfacthd.simplelod.render.LodRenderer;
import io.github.xfacthd.simplelod.storage.LodChunkStorage;
import io.github.xfacthd.simplelod.util.Config;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class DebugEntryLodStats implements DebugScreenEntry {
    public static final Identifier ID = SimpleLOD.id("lod_stats");

    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
        LodChunkStorage storage = LodChunkStorage.tryGet();
        if (storage == null) {
            return;
        }

        StorageStats storageStats = storage.getDebugStats();
        RendererStats rendererStats = LodRenderer.getDebugStats();
        displayer.addToGroup(ID, List.of(
                "SimpleLOD Renderer",
                String.format(Locale.ROOT, "Render Distance: %d", Config.getRenderDistance()),
                String.format(Locale.ROOT, "Loaded Chunks: %d", storageStats.loadedChunks()),
                String.format(Locale.ROOT, "Visible Chunks: %d", rendererStats.visibleChunks()),
                String.format(Locale.ROOT, "Meshing Mode: %s", LodCompiler.getMeshMode()),
                String.format(Locale.ROOT, "Render Mode: %s", rendererStats.mode()),
                String.format(Locale.ROOT, "Update: %dus", Math.round(storageStats.updateTime())),
                String.format(Locale.ROOT, "Extract: %dus", Math.round(rendererStats.extractTime())),
                String.format(Locale.ROOT, "Prepare: %dus", Math.round(rendererStats.prepareTime())),
                String.format(Locale.ROOT, "Render: %dus", Math.round(rendererStats.renderTime()))
        ));
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return true;
    }

    public static void onRegisterDebugEntries(RegisterDebugEntriesEvent event) {
        event.register(ID, new DebugEntryLodStats());
        if (!FMLLoader.getCurrent().isProduction()) {
            event.includeInProfile(ID, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
        }
    }
}
