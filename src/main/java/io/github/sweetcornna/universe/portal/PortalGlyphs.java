package io.github.sweetcornna.universe.portal;

import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.portal.Glyphs;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** 玩家学会的先驱者符文（ADR-0024）：巨石、第一次走到的传送门、探索远征各教一个没学过的。 */
public final class PortalGlyphs {
    private PortalGlyphs() {
    }

    public static int known(ServerPlayer player) {
        return player.getData(UniverseAttachments.GLYPHS);
    }

    /** 学一个没学过的，告诉本人学会了第几个；全会了返回空。 */
    public static OptionalInt learn(ServerPlayer player, Rng rng) {
        int known = known(player);
        int glyph = Glyphs.next(known, rng);
        if (glyph < 0) {
            return OptionalInt.empty();
        }
        int now = known | 1 << glyph;
        player.setData(UniverseAttachments.GLYPHS, now);
        player.sendSystemMessage(Component.translatable("message.universe.glyph.learned", Glyphs.count(now), Glyphs.count(Glyphs.ALL)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        return OptionalInt.of(glyph);
    }
}
