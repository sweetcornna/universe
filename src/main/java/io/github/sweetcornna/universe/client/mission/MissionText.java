package io.github.sweetcornna.universe.client.mission;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.core.mission.MissionKind;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** 任务的文字：种类、目标、报酬，任务板和 HUD 共用。 */
final class MissionText {
    private MissionText() {
    }

    static int color(MissionKind kind) {
        return switch (kind) {
            case DELIVERY -> 0xFFF2C94C;
            case BOUNTY -> 0xFFFF6A5A;
            case MINING -> 0xFFFFA45C;
            case SURVEY -> 0xFF7CF2B0;
        };
    }

    static Component kind(MissionKind kind) {
        return Component.translatable("gui.universe.mission.kind." + kind.key());
    }

    static String systemName(int system) {
        return ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(system)).map(GalaxyText::system).orElse("#" + system);
    }

    static Component station(int system) {
        return Component.translatable("gui.universe.trade.station", systemName(system));
    }

    /** "送来 30 个铁锭""击落 4 架海盗战机"。 */
    static Component goal(Mission mission) {
        return switch (mission.kind()) {
            case DELIVERY -> {
                Identifier id = Identifier.tryParse(mission.item());
                Component item = id == null ? Component.literal(mission.item())
                        : BuiltInRegistries.ITEM.getOptional(id).map(i -> new ItemStack(i).getHoverName()).orElse(Component.literal(mission.item()));
                yield Component.translatable("gui.universe.mission.goal.delivery", item, mission.count());
            }
            case BOUNTY -> Component.translatable("gui.universe.mission.goal.bounty", mission.count());
            case MINING -> Component.translatable("gui.universe.mission.goal.mining", mission.count());
            case SURVEY -> Component.translatable("gui.universe.mission.goal.survey", systemName(mission.system()), mission.count());
        };
    }

    /** "报酬 880 信用点，外加 2 份导航数据"。 */
    static Component reward(Mission mission) {
        MutableComponent text = Component.translatable("gui.universe.mission.reward", Credits.format(mission.reward()));
        return switch (mission.bonus()) {
            case NAVIGATION_DATA -> text.append(Component.translatable("gui.universe.mission.bonus.navigation_data", mission.bonusCount()));
            case MODULE -> text.append(Component.translatable("gui.universe.mission.bonus.module"));
            case NONE -> text;
        };
    }
}
