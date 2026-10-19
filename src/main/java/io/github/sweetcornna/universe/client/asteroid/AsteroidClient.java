package io.github.sweetcornna.universe.client.asteroid;

import java.util.Locale;
import java.util.Map;

import io.github.sweetcornna.universe.asteroid.Asteroid;
import io.github.sweetcornna.universe.asteroid.AsteroidMaterials;
import io.github.sweetcornna.universe.core.space.AsteroidField;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 小行星的客户端部分（ADR-0017）：渲染器；采矿模式下光束指着小行星时，准星下面写它多大、含什么矿（稀有的标金色），画一条耐久。
 */
public final class AsteroidClient {
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int RARE_COLOR = 0xFFFFD45A;
    private static final int BAR_COLOR = 0xFFFF8A3D;

    private AsteroidClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(UniverseEntities.ASTEROID.get(), AsteroidRenderer::new));
    }

    /** 准星右边的说明：大小、矿、耐久条（飞船的准星画完后调用，{@code x} 是说明的左边，{@code y} 是顶边）。 */
    public static void info(GuiGraphicsExtractor graphics, Font font, int x, int y, Asteroid asteroid) {
        AsteroidField.Shape shape = asteroid.shape();
        Component title = Component.translatable("hud.universe.asteroid." + shape.size().name().toLowerCase(Locale.ROOT));
        MutableComponent ores = Component.empty();
        boolean first = true;
        for (Map.Entry<AsteroidField.Material, Integer> entry : shape.ores().entrySet()) {
            if (!first) {
                ores.append(Component.translatable("message.universe.list_separator"));
            }
            first = false;
            Component name = new ItemStack(AsteroidMaterials.item(entry.getKey())).getHoverName();
            ores.append(name.copy().withStyle(Style.EMPTY.withColor(entry.getKey().rare() ? RARE_COLOR : TEXT_COLOR)));
        }
        int width = Math.max(30, Math.max(font.width(title), font.width(ores)));
        graphics.fill(x - 3, y - 2, x + width + 3, y + 25, 0x90000000);
        graphics.text(font, title, x, y, 0xFFB8C4CC, true);
        graphics.text(font, ores, x, y + 10, TEXT_COLOR, true);
        graphics.fill(x, y + 21, x + width, y + 23, 0xFF303030);
        graphics.fill(x, y + 21, x + Math.round(width * asteroid.health()), y + 23, BAR_COLOR);
    }
}
