package io.github.sweetcornna.universe.chem.reactor;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.chem.reaction.Catalyst;
import io.github.sweetcornna.universe.core.chem.reaction.ItemCount;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * 反应釜的配方（ADR-0036 §四、ADR-0037）：每条反应一个 {@code universe:reactor/<反应>} 的设备配方，按转化率 100% 写，
 * 给 JEI、统计和生存可达性的推导用。配方里的科技是把这条反应列进化学手册的那项（{@link Reaction#research()}），
 * 反应釜不看它：能不能选看发现了没有。数据包删掉配方就等于关掉这条反应。
 */
public final class ReactorRecipes {
    /** 配方 ID 的目录，也是配方类型的名字。 */
    public static final String DIR = "reactor";
    static final double TICKS_PER_MINUTE = 1200;

    private ReactorRecipes() {
    }

    public static ResourceKey<Recipe<?>> key(Reaction reaction) {
        return ResourceKey.create(Registries.RECIPE, Universe.id(DIR + "/" + reaction.id()));
    }

    /** 配方 ID 对应的反应；不是反应釜的配方为 {@code null}。 */
    public static @Nullable Reaction reaction(Identifier id) {
        String prefix = DIR + "/";
        if (!id.getNamespace().equals(Universe.MOD_ID) || !id.getPath().startsWith(prefix)) {
            return null;
        }
        return Reaction.byName(id.getPath().substring(prefix.length()).toUpperCase(Locale.ROOT));
    }

    /** 配方的标准时间（刻）：单程转化率为 1 时一批要多久。 */
    public static int time(Reaction reaction) {
        return (int) Math.round(TICKS_PER_MINUTE / reaction.baseBatches());
    }

    public static Item item(ItemCount count) {
        return item(count.item());
    }

    public static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    /** 这一堆是哪种催化剂；不是催化剂为 {@code null}。 */
    public static @Nullable Catalyst catalyst(ItemStack stack) {
        return stack.isEmpty() ? null : Catalyst.byItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** 温度写成摄氏度，整数。 */
    public static String celsius(double kelvin) {
        return String.format(Locale.ROOT, "%.0f °C", kelvin - ReactorSettings.ZERO_CELSIUS);
    }

    /** 压强：10 bar 以下带一位小数。 */
    public static String bar(double pressure) {
        return String.format(Locale.ROOT, pressure < 10 ? "%.1f bar" : "%.0f bar", pressure);
    }

    /** "450 °C，200 bar"：教科书的工况，JEI、界面和手册上用。 */
    public static Component conditions(Reaction reaction) {
        ReactorSettings defaults = reaction.defaults();
        return Component.translatable("gui.universe.chemical_reactor.conditions", celsius(defaults.temperature()), bar(defaults.pressure()));
    }
}
