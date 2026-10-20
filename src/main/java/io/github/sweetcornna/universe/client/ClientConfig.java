package io.github.sweetcornna.universe.client;

import java.util.Locale;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/** 客户端设置（config/universe-client.toml），模组列表里的"配置"按钮打开 NeoForge 自带的设置界面。 */
public final class ClientConfig {
    /** 天空里戴森球的画质（ADR-0019）。 */
    public enum DysonSky implements TranslatableEnum {
        /** 完整：球壳贴着球面细分，框架每 6° 一段，每条轨道最多 700 个帆点。 */
        HIGH,
        /** 简化：球壳不细分，框架每 15° 一段，背面的框架和节点不画，每条轨道最多 200 个帆点。 */
        LOW,
        OFF;

        @Override
        public Component getTranslatedName() {
            return Component.translatable(Universe.MOD_ID + ".configuration.dysonSky." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<DysonSky> DYSON_SKY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DYSON_SKY = builder.comment("Dyson sphere in the sky: HIGH, LOW (fewer segments, no far side), OFF").defineEnum("dysonSky", DysonSky.HIGH);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /** 设置文件还没读进来时（进世界之前）按默认值。 */
    public static DysonSky dysonSky() {
        return SPEC.isLoaded() ? DYSON_SKY.get() : DysonSky.HIGH;
    }
}
