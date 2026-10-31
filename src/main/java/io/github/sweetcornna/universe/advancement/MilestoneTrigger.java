package io.github.sweetcornna.universe.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.story.Milestone;
import io.github.sweetcornna.universe.registry.UniverseTriggers;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * 进度触发器 {@code universe:milestone}（ADR-0018）：原版触发器管不到的事（造好飞船、第一笔交易、打退海盗……），
 * 服务端在发生的地方报一个编号，进度按编号匹配。世界级的剧情节点用 {@code story/<节点>}，到达时给所有在线玩家报，
 * 之后上线的玩家登录时补报。
 */
public class MilestoneTrigger extends SimpleCriterionTrigger<MilestoneTrigger.TriggerInstance> {
    public static final String SHIP = "ship";
    public static final String SPACE = "space";
    public static final String WARP = "warp";
    public static final String TRADE = "trade";
    public static final String MISSION = "mission";
    public static final String PIRATES = "pirates";
    public static final String SPECIES = "species";
    public static final String SURVEY = "survey";
    public static final String MONOLITH = "monolith";
    public static final String TAME = "tame";
    public static final String EXOCRAFT = "exocraft";
    public static final String TELEPORT = "teleport";
    public static final String BLUEPRINT = "blueprint";
    /** 有一颗星球改造到能直接呼吸（ADR-0036 §六），全服的人都算。 */
    public static final String TERRAFORMED = "terraformed";

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String milestone) {
        this.trigger(player, instance -> instance.milestone().equals(milestone));
    }

    /** 给这个玩家报一个里程碑。 */
    public static void reach(ServerPlayer player, String milestone) {
        UniverseTriggers.MILESTONE.get().trigger(player, milestone);
    }

    public static String story(Milestone milestone) {
        return "story/" + milestone.id();
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player, String milestone) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.fieldOf("milestone").forGetter(TriggerInstance::milestone)
        ).apply(i, TriggerInstance::new));

        public static Criterion<TriggerInstance> reached(String milestone) {
            return UniverseTriggers.MILESTONE.get().createCriterion(new TriggerInstance(Optional.empty(), milestone));
        }
    }
}
