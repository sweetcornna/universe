package io.github.sweetcornna.universe.registry;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameRuleCategoryEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 建世界的选项（ADR-0028 §七）：模组的游戏规则，分在"寰宇"一类。26.x 的游戏规则是注册表，原版建世界界面的"游戏规则"里能改，
 * 专用服务器用 {@code /gamerule universe:dark_fog false} 这样改。百分比的规则存整数百分比。读规则走 {@code rules.UniverseRules}。
 */
public final class UniverseGameRules {
    public static final DeferredRegister<GameRule<?>> GAME_RULES = DeferredRegister.create(Registries.GAME_RULE, Universe.MOD_ID);
    /** "寰宇"一类，排在原版的几类后面。 */
    public static final GameRuleCategory CATEGORY = new GameRuleCategory(Universe.id("universe"));

    /** 关了不生成黑雾基地，已有的停摆。 */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> DARK_FOG = bool("dark_fog", true);
    /** 0–5：被动、迟钝、普通、敏锐、好斗、狂暴。 */
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> DARK_FOG_AGGRESSIVENESS = integer("dark_fog_aggressiveness", 2, 0, 5);
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> DARK_FOG_INITIAL_LEVEL = integer("dark_fog_initial_level", 0, 0, 30);
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> DARK_FOG_OCCUPATION = integer("dark_fog_occupation", 100, 0, 300);
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> DARK_FOG_GROWTH = integer("dark_fog_growth", 100, 25, 400);
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> DARK_FOG_POWER_THREAT = integer("dark_fog_power_threat", 100, 0, 400);
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> DARK_FOG_COMBAT_THREAT = integer("dark_fog_combat_threat", 100, 0, 400);
    /** 守卫机的血量、伤害和每波的架数；0 时守卫机不出现。 */
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> SENTINEL_STRENGTH = integer("sentinel_strength", 100, 0, 300);
    /** 矿脉节点的储量、涌泉的产量；0 是无限。 */
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> RESOURCE_MULTIPLIER = integer("resource_multiplier", 100, 0, 1000);
    /** 星系里的恒星系数量：只在建世界时读一次（{@code rules.GalaxySettings}），以后改不了。 */
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> STAR_SYSTEMS = integer("star_systems", 64, 32, 256);
    /** 局部危害的频率（ADR-0034 §六）：火山喷发、孢子云、尘卷风多久来一次，100 是默认；0 时这些和间歇泉都停下。 */
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> LOCAL_HAZARDS = integer("local_hazards", 100, 0, 300);

    private UniverseGameRules() {
    }

    private static DeferredHolder<GameRule<?>, GameRule<Boolean>> bool(String name, boolean defaultValue) {
        return GAME_RULES.register(name, () -> new GameRule<>(CATEGORY, GameRuleType.BOOL, BoolArgumentType.bool(),
                GameRuleTypeVisitor::visitBoolean, Codec.BOOL, value -> value ? 1 : 0, defaultValue, FeatureFlagSet.of()));
    }

    private static DeferredHolder<GameRule<?>, GameRule<Integer>> integer(String name, int defaultValue, int min, int max) {
        return GAME_RULES.register(name, () -> new GameRule<>(CATEGORY, GameRuleType.INT, IntegerArgumentType.integer(min, max),
                GameRuleTypeVisitor::visitInteger, Codec.intRange(min, max), value -> value, defaultValue, FeatureFlagSet.of()));
    }

    public static void register(IEventBus modBus) {
        GAME_RULES.register(modBus);
        modBus.addListener(RegisterGameRuleCategoryEvent.class, event -> event.register(CATEGORY));
    }
}
