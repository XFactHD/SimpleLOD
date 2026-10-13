package io.github.xfacthd.simplelod;

import io.github.xfacthd.simplelod.compiler.LodCompiler;
import io.github.xfacthd.simplelod.generator.SpriteColorCache;
import io.github.xfacthd.simplelod.render.LodRenderPipelines;
import io.github.xfacthd.simplelod.render.LodRenderer;
import io.github.xfacthd.simplelod.storage.LodChunkStorage;
import io.github.xfacthd.simplelod.util.Config;
import io.github.xfacthd.simplelod.debug.DebugEntryLodStats;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

@Mod(value = SimpleLOD.MOD_ID, dist = Dist.CLIENT)
@SuppressWarnings("UtilityClassWithPublicConstructor")
public final class SimpleLOD {
    public static final String MOD_ID = "simplelod";

    public SimpleLOD(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(LodRenderPipelines::onRegisterRenderPipelines);
        modBus.addListener(DebugEntryLodStats::onRegisterDebugEntries);
        modBus.addListener(SimpleLOD::onRegisterReloadListeners);

        NeoForge.EVENT_BUS.addListener(LodChunkStorage::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(LodChunkStorage::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(LodChunkStorage::onClientTick);
        if (SharedConstants.DEBUG_HOTKEYS) {
            NeoForge.EVENT_BUS.addListener(LodChunkStorage::onDebugKeyInput);
        }
        NeoForge.EVENT_BUS.addListener(SimpleLOD::onGameShutdown);

        Config.init(modContainer);
    }

    private static void onRegisterReloadListeners(AddClientReloadListenersEvent event) {
        LodCompiler.setup();
        LodRenderer.setup(event);
        SpriteColorCache.register(event);
    }

    private static void onGameShutdown(GameShuttingDownEvent ignoredEvent) {
        LodRenderer.shutdown();
        LodCompiler.shutdown();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
