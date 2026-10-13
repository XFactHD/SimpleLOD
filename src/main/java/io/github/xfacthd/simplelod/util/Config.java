package io.github.xfacthd.simplelod.util;

import io.github.xfacthd.simplelod.generator.LodColorProvider;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue RENDER_DISTANCE;
    private static final ModConfigSpec.EnumValue<LodColorProvider.Type> COLOR_PROVIDER_TYPE;
    private static final ModConfigSpec.BooleanValue FORCE_NAIVE_MESHER;
    private static final ModConfigSpec.BooleanValue FORCE_NAIVE_RENDERER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        RENDER_DISTANCE = builder
                .comment("Controls the combined render distance of vanilla and LOD chunks")
                .worldRestart()
                .defineInRange("lod_render_distance", 32, 8, Utils.MAX_RADIUS);
        COLOR_PROVIDER_TYPE = builder
                .comment(
                        "Controls how the color of blocks in LODs is determined.",
                        "- MAP_COLOR: the color is computed from the block's map color used by vanilla maps",
                        "- PARTICLE_TEXTURE: the color is computed from the block's particle texture",
                        "- BLOCK_MODEL: the color is computed from the block's model"
                )
                .worldRestart()
                .defineEnum("lod_color_provider", LodColorProvider.Type.PARTICLE_TEXTURE);
        FORCE_NAIVE_MESHER = builder
                .comment("Force use of the naive mesher. Only use if the greedy meshing causes issues!")
                .gameRestart()
                .define("force_naive_mesher", false);
        FORCE_NAIVE_RENDERER = builder
                .comment("Force use of the naive renderer. Only use if the MultiDrawIndirect renderer causes issues!")
                .gameRestart()
                .define("force_naive_renderer", false);

        SPEC = builder.build();
    }

    public static void init(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    public static int getRenderDistance() {
        return RENDER_DISTANCE.getAsInt();
    }

    public static LodColorProvider.Type getColorProviderType() {
        return COLOR_PROVIDER_TYPE.get();
    }

    public static boolean isGreedyMesher() {
        return !FORCE_NAIVE_MESHER.getAsBoolean();
    }

    public static boolean isIndirectRenderer() {
        return !FORCE_NAIVE_RENDERER.getAsBoolean();
    }

    private Config() { }
}
