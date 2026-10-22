package io.github.sweetcornna.universe.rules;

import io.github.sweetcornna.universe.core.darkfog.DarkFogSettings;
import io.github.sweetcornna.universe.core.factory.Veins;
import io.github.sweetcornna.universe.core.sentinel.SentinelStrength;
import io.github.sweetcornna.universe.registry.UniverseGameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * 建世界的选项（ADR-0028 §七）：模组的游戏规则，分在"寰宇"一类（{@link UniverseGameRules}）。除了恒星系数量，都随时能改、马上生效。
 */
public final class UniverseRules {
    private UniverseRules() {
    }

    /** 黑雾的选项。 */
    public static DarkFogSettings darkFog(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        return new DarkFogSettings(rules.get(UniverseGameRules.DARK_FOG.get()),
                rules.get(UniverseGameRules.DARK_FOG_AGGRESSIVENESS.get()),
                rules.get(UniverseGameRules.DARK_FOG_INITIAL_LEVEL.get()),
                percent(rules, UniverseGameRules.DARK_FOG_OCCUPATION.get()),
                percent(rules, UniverseGameRules.DARK_FOG_GROWTH.get()),
                percent(rules, UniverseGameRules.DARK_FOG_POWER_THREAT.get()),
                percent(rules, UniverseGameRules.DARK_FOG_COMBAT_THREAT.get()));
    }

    /** 守卫机的强度：1 是 100%；0 时守卫机不出现。 */
    public static double sentinelStrength(MinecraftServer server) {
        return SentinelStrength.clamp(percent(server.getGameRules(), UniverseGameRules.SENTINEL_STRENGTH.get()));
    }

    /** 资源倍率：1 是 100%；0 是无限。 */
    public static double resourceMultiplier(MinecraftServer server) {
        return Math.min(Veins.MAX_MULTIPLIER, percent(server.getGameRules(), UniverseGameRules.RESOURCE_MULTIPLIER.get()));
    }

    /** 星系里的恒星系数量：建世界时定下，以后改规则不变（{@link GalaxySettings}）。 */
    public static int starSystems(MinecraftServer server) {
        return GalaxySettings.starSystems(server);
    }

    private static double percent(GameRules rules, GameRule<Integer> rule) {
        return rules.get(rule) / 100.0;
    }
}
