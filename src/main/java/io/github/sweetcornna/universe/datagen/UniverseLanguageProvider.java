package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
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
        both("universe.bed.no_sleep_on_planet", "这颗星球的昼夜和母星不同步，床只能用来设置重生点",
                "This planet's days don't follow the homeworld's; beds here only set your respawn point");
        for (BiomeKind kind : BiomeKind.values()) {
            both("biome.universe." + kind.serializedName(), kind.nameZh(), kind.nameEn());
        }

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
        block(UniverseBlocks.PLANET_STRATA.get(), "岩层", "Planet Strata");
        // 植物方块按形态命名，具体物种名以后由分析目镜显示
        UniverseBlocks.PLANTS.forEach((form, plant) -> block(plant.get(), "异星" + form.nameZh(), "Alien " + form.nameEn()));
        block(UniverseBlocks.PLANET_LOG.get(), "异星原木", "Alien Log");
        block(UniverseBlocks.PLANET_LEAVES.get(), "异星树叶", "Alien Leaves");
        block(UniverseBlocks.PLANET_CAP.get(), "肉质块", "Fleshy Block");
        block(UniverseBlocks.PLANET_BLOOM.get(), "异星花果", "Alien Bloom");

        both("entity.universe.alien_creature", "异星生物", "Alien Creature");
        both("entity.universe.alien_flyer", "异星飞兽", "Alien Flyer");
        // 物种名：实体把中英文两个名字都作为参数传进来，各语言挑自己的
        both("entity.universe.species", "%1$s", "%2$s");
        item(UniverseItems.ALIEN_MEAT.get(), "异兽生肉", "Raw Alien Meat");
        item(UniverseItems.COOKED_ALIEN_MEAT.get(), "熟异兽肉", "Cooked Alien Meat");
        item(UniverseItems.ALIEN_HIDE.get(), "异兽皮", "Alien Hide");
        item(UniverseItems.ALIEN_CREATURE_SPAWN_EGG.get(), "异星生物刷怪蛋", "Alien Creature Spawn Egg");
        item(UniverseItems.ALIEN_FLYER_SPAWN_EGG.get(), "异星飞兽刷怪蛋", "Alien Flyer Spawn Egg");
    }

    private void item(net.minecraft.world.item.Item item, String zh, String en) {
        this.add(item, this.chinese ? zh : en);
    }

    private void block(net.minecraft.world.level.block.Block block, String zh, String en) {
        this.add(block, this.chinese ? zh : en);
    }
}
