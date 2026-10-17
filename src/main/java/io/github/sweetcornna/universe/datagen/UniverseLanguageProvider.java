package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** 语言文件。中英文放在一起写，免得漏翻（项目约定同时维护 zh_cn 和 en_us）。 */
abstract class UniverseLanguageProvider extends LanguageProvider {
    private final boolean chinese;

    private UniverseLanguageProvider(PackOutput output, String locale, boolean chinese) {
        super(output, Universe.MOD_ID, locale);
        this.chinese = chinese;
    }

    static final class Chinese extends UniverseLanguageProvider {
        Chinese(PackOutput output) {
            super(output, "zh_cn", true);
        }
    }

    static final class English extends UniverseLanguageProvider {
        English(PackOutput output) {
            super(output, "en_us", false);
        }
    }

    private void both(String key, String zh, String en) {
        this.add(key, this.chinese ? zh : en);
    }

    @Override
    protected void addTranslations() {
        both("itemGroup.universe", "寰宇", "Universe");

        block(UniverseBlocks.PLANET_ROCK.get(), "星岩", "Planet Rock");
        block(UniverseBlocks.PLANET_COBBLESTONE.get(), "星岩碎石", "Planet Cobblestone");
        block(UniverseBlocks.PLANET_SOIL.get(), "星壤", "Planet Soil");
        block(UniverseBlocks.PLANET_TURF.get(), "星原草皮", "Planet Turf");
        block(UniverseBlocks.PLANET_REGOLITH.get(), "风化层", "Regolith");
        block(UniverseBlocks.PLANET_SAND.get(), "星沙", "Planet Sand");
        block(UniverseBlocks.PLANET_SNOW.get(), "星雪", "Planet Snow");
        block(UniverseBlocks.PLANET_ICE.get(), "星冰", "Planet Ice");
        block(UniverseBlocks.PLANET_ASH.get(), "灰烬", "Ash");
        block(UniverseBlocks.PLANET_CRYSTAL.get(), "星晶", "Planet Crystal");
    }

    private void block(net.minecraft.world.level.block.Block block, String zh, String en) {
        this.add(block, this.chinese ? zh : en);
    }
}
