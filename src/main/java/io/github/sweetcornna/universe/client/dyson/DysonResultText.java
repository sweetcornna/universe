package io.github.sweetcornna.universe.client.dyson;

import io.github.sweetcornna.universe.network.DysonResultPayload;
import net.minecraft.network.chat.Component;

/** 把编辑结果（ADR-0030 §四）变成一行字：成了只说结果，不成的后面跟上具体原因。 */
final class DysonResultText {
    private DysonResultText() {
    }

    static Component of(DysonResultPayload result) {
        if (result.detail().isEmpty()) {
            return Component.translatable(result.key());
        }
        return Component.translatable("gui.universe.dyson.result_detail", Component.translatable(result.key()),
                Component.translatable(result.detail()));
    }
}
