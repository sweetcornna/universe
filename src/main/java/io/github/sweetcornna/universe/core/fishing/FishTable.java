package io.github.sweetcornna.universe.core.fishing;

import static io.github.sweetcornna.universe.core.fishing.FishSpecies.Depth.DEEP;
import static io.github.sweetcornna.universe.core.fishing.FishSpecies.Depth.SHALLOW;
import static io.github.sweetcornna.universe.core.fishing.FishSpecies.Time.DAY;
import static io.github.sweetcornna.universe.core.fishing.FishSpecies.Time.NIGHT;
import static io.github.sweetcornna.universe.core.fishing.FishSpecies.Weather.STORM;
import static io.github.sweetcornna.universe.core.fishing.Rarity.COMMON;
import static io.github.sweetcornna.universe.core.fishing.Rarity.LEGENDARY;
import static io.github.sweetcornna.universe.core.fishing.Rarity.RARE;
import static io.github.sweetcornna.universe.core.fishing.Rarity.UNCOMMON;
import static io.github.sweetcornna.universe.core.fishing.SizeClass.HUGE;
import static io.github.sweetcornna.universe.core.fishing.SizeClass.LARGE;
import static io.github.sweetcornna.universe.core.fishing.SizeClass.MEDIUM;
import static io.github.sweetcornna.universe.core.fishing.SizeClass.SMALL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.sweetcornna.universe.core.fishing.FishSpecies.Depth;
import io.github.sweetcornna.universe.core.fishing.FishSpecies.Time;
import io.github.sweetcornna.universe.core.fishing.FishSpecies.Weather;

/**
 * 96 种鱼（ADR-0034 §一），8 种水域各 12 种。顺序就是图鉴的顺序，也是同步时的编号，只能往后加。
 *
 * <p>照现实里同类的鱼设计：淡水是鲦、鲈、鳟、鲇、鲟；暖水是热带河里的脂鲤、丽鱼、骨舌鱼和温泉鳉；近海是鲱、鲭、鲷、石斑；
 * 深海的会发光（灯笼鱼、蝰鱼、鮟鱇），风暴天浮上来的皇带鱼；冰下的体内有抗冻蛋白、长得慢、个头大（冰鱼、犬牙鱼、睡鲨）；
 * 酸水里的是嗜极生物，壳厚、颜色暗（甲鲇、盾头鱼、盾皮鱼）；暗河的没眼睛、颜色淡；奇异星球上的骨骼里沉着晶体和重金属。
 */
public final class FishTable {
    public static final List<FishSpecies> ALL;
    private static final Map<String, FishSpecies> BY_ID = new HashMap<>();
    private static final Map<Habitat, List<FishSpecies>> BY_HABITAT = new EnumMap<>(Habitat.class);

    static {
        Builder b = new Builder();
        // ---- 淡水：温带的溪流、湖泊
        b.add("creek_dace", "蓝鳍溪鲦", "Bluefin Creek Dace", Habitat.FRESH, COMMON, SMALL);
        b.add("banded_perch", "横纹河鲈", "Banded River Perch", Habitat.FRESH, COMMON, SMALL);
        b.add("silver_crucian", "银鳞鲫", "Silverscale Crucian", Habitat.FRESH, COMMON, SMALL, Time.ANY, Weather.ANY, SHALLOW);
        b.add("reed_gudgeon", "苇丛鮈", "Reed Gudgeon", Habitat.FRESH, COMMON, SMALL, DAY, Weather.ANY, Depth.ANY);
        b.add("mud_loach", "斑点泥鳅", "Spotted Mud Loach", Habitat.FRESH, COMMON, SMALL, NIGHT, Weather.ANY, Depth.ANY);
        b.add("brook_trout", "红点溪鳟", "Red-spotted Brook Trout", Habitat.FRESH, UNCOMMON, MEDIUM);
        b.add("whiskered_catfish", "长须鲇", "Long-whiskered Catfish", Habitat.FRESH, UNCOMMON, MEDIUM, NIGHT, Weather.ANY, DEEP);
        b.add("goldback_carp", "金背鲤", "Goldback Carp", Habitat.FRESH, UNCOMMON, MEDIUM);
        b.add("striped_pike", "条纹狗鱼", "Striped Pike", Habitat.FRESH, UNCOMMON, LARGE);
        b.add("thunder_char", "雷雨红点鲑", "Thunderstorm Char", Habitat.FRESH, RARE, MEDIUM, Time.ANY, STORM, Depth.ANY);
        b.add("dusky_eel", "暗纹湖鳗", "Dusky Lake Eel", Habitat.FRESH, RARE, MEDIUM, Time.ANY, Weather.ANY, DEEP);
        b.add("river_sturgeon", "巨鳞河鲟", "Great River Sturgeon", Habitat.FRESH, LEGENDARY, HUGE, Time.ANY, Weather.ANY, DEEP);
        // ---- 暖水：雨林、菌林的河，灼热星球上的温泉
        b.add("ember_tetra", "火焰脂鲤", "Ember Tetra", Habitat.WARM, COMMON, SMALL);
        b.add("eyespot_cichlid", "眼斑丽鱼", "Eyespot Cichlid", Habitat.WARM, COMMON, SMALL);
        b.add("sawtooth_pacu", "红腹锯脂鲤", "Red-bellied Sawtooth", Habitat.WARM, COMMON, MEDIUM);
        b.add("spring_pupfish", "温泉鳉", "Hot Spring Pupfish", Habitat.WARM, COMMON, SMALL, DAY, Weather.ANY, SHALLOW);
        b.add("leaf_fish", "枯叶鱼", "Leaf Fish", Habitat.WARM, COMMON, SMALL, NIGHT, Weather.ANY, Depth.ANY);
        b.add("silver_bonytongue", "银鳞骨舌鱼", "Silver Bonytongue", Habitat.WARM, UNCOMMON, LARGE);
        b.add("ghost_knifefish", "黑带刀鱼", "Black Ghost Knifefish", Habitat.WARM, UNCOMMON, MEDIUM, NIGHT, Weather.ANY, Depth.ANY);
        b.add("spotted_lungfish", "斑点肺鱼", "Spotted Lungfish", Habitat.WARM, UNCOMMON, MEDIUM, Time.ANY, Weather.ANY, SHALLOW);
        b.add("spore_goby", "孢斑虾虎", "Sporefleck Goby", Habitat.WARM, UNCOMMON, SMALL);
        b.add("thunder_eel", "雷纹电鳗", "Thunder Eel", Habitat.WARM, RARE, LARGE, Time.ANY, STORM, Depth.ANY);
        b.add("redtail_catfish", "红尾鲇", "Redtail Catfish", Habitat.WARM, RARE, LARGE, Time.ANY, Weather.ANY, DEEP);
        b.add("giant_arapaima", "巨骨舌鱼", "Giant Arapaima", Habitat.WARM, LEGENDARY, HUGE);
        // ---- 近海：海滩外、水深不到 12 格的海
        b.add("banded_sprat", "银带鲱", "Silver-banded Sprat", Habitat.COASTAL, COMMON, SMALL);
        b.add("tidepool_wrasse", "潮池隆头鱼", "Tidepool Wrasse", Habitat.COASTAL, COMMON, SMALL, DAY, Weather.ANY, SHALLOW);
        b.add("striped_mackerel", "条纹鲭", "Striped Mackerel", Habitat.COASTAL, COMMON, MEDIUM);
        b.add("kelp_rockfish", "海藻平鲉", "Kelp Rockfish", Habitat.COASTAL, COMMON, MEDIUM);
        b.add("spotted_flounder", "斑点牙鲆", "Spotted Flounder", Habitat.COASTAL, COMMON, MEDIUM, NIGHT, Weather.ANY, Depth.ANY);
        b.add("goldline_bream", "金线海鲷", "Goldline Sea Bream", Habitat.COASTAL, UNCOMMON, MEDIUM);
        b.add("starry_conger", "星斑康吉鳗", "Starry Conger", Habitat.COASTAL, UNCOMMON, MEDIUM, NIGHT, Weather.ANY, Depth.ANY);
        b.add("spiny_puffer", "棘刺鲀", "Spiny Puffer", Habitat.COASTAL, UNCOMMON, SMALL);
        b.add("moonscale_pomfret", "月鳞鲳", "Moonscale Pomfret", Habitat.COASTAL, UNCOMMON, MEDIUM);
        b.add("gale_jack", "风暴鲹", "Gale Jack", Habitat.COASTAL, RARE, LARGE, Time.ANY, STORM, Depth.ANY);
        b.add("coral_grouper", "朱斑石斑鱼", "Coral Grouper", Habitat.COASTAL, RARE, LARGE, Time.ANY, Weather.ANY, DEEP);
        b.add("silver_tarpon", "银王大海鲢", "Silver King Tarpon", Habitat.COASTAL, LEGENDARY, HUGE);
        // ---- 深海：会发光的居多；灯笼鱼、蝰鱼夜里才游上来
        b.add("starspot_lanternfish", "星点灯笼鱼", "Starspot Lanternfish", Habitat.DEEP, COMMON, SMALL, NIGHT, Weather.ANY, Depth.ANY);
        b.add("silver_hatchetfish", "银斧鱼", "Silver Hatchetfish", Habitat.DEEP, COMMON, SMALL);
        b.add("rattail_grenadier", "长尾鼠鳕", "Rattail Grenadier", Habitat.DEEP, COMMON, MEDIUM, Time.ANY, Weather.ANY, DEEP);
        b.add("deepwater_cod", "深水鳕", "Deepwater Cod", Habitat.DEEP, COMMON, MEDIUM);
        b.add("bluewing_flyingfish", "蓝翼飞鱼", "Bluewing Flyingfish", Habitat.DEEP, COMMON, SMALL, DAY, Weather.ANY, Depth.ANY);
        b.add("fangtooth_viperfish", "长牙蝰鱼", "Fangtooth Viperfish", Habitat.DEEP, UNCOMMON, SMALL, NIGHT, Weather.ANY, Depth.ANY);
        b.add("lantern_angler", "灯钓鮟鱇", "Lantern Anglerfish", Habitat.DEEP, UNCOMMON, MEDIUM, Time.ANY, Weather.ANY, DEEP);
        b.add("pelican_gulper", "囊咽鳗", "Pelican Gulper Eel", Habitat.DEEP, UNCOMMON, MEDIUM);
        b.add("broadbill_swordfish", "剑鱼", "Broadbill Swordfish", Habitat.DEEP, UNCOMMON, LARGE);
        b.add("king_oarfish", "皇带鱼", "King Oarfish", Habitat.DEEP, RARE, LARGE, Time.ANY, STORM, Depth.ANY);
        b.add("abyssal_coelacanth", "深渊腔棘鱼", "Abyssal Coelacanth", Habitat.DEEP, RARE, LARGE, Time.ANY, Weather.ANY, DEEP);
        b.add("megamouth", "巨口鲨", "Megamouth Shark", Habitat.DEEP, LEGENDARY, HUGE, Time.ANY, Weather.ANY, DEEP);
        // ---- 冰下：体内有抗冻蛋白，长得慢，个头大
        b.add("ice_smelt", "冰胡瓜鱼", "Ice Smelt", Habitat.ICE, COMMON, SMALL);
        b.add("polar_cod", "极地鳕", "Polar Cod", Habitat.ICE, COMMON, SMALL);
        b.add("whiteblood_icefish", "白血冰鱼", "Whiteblood Icefish", Habitat.ICE, COMMON, MEDIUM);
        b.add("arctic_char", "北极红点鲑", "Arctic Char", Habitat.ICE, COMMON, MEDIUM, DAY, Weather.ANY, Depth.ANY);
        b.add("frost_sculpin", "冰杜父鱼", "Frost Sculpin", Habitat.ICE, COMMON, SMALL, NIGHT, Weather.ANY, SHALLOW);
        b.add("ice_toothfish", "冰下犬牙鱼", "Ice Toothfish", Habitat.ICE, UNCOMMON, LARGE, Time.ANY, Weather.ANY, DEEP);
        b.add("burbot", "江鳕", "Burbot", Habitat.ICE, UNCOMMON, MEDIUM, NIGHT, Weather.ANY, Depth.ANY);
        b.add("glacier_grayling", "冰川茴鱼", "Glacier Grayling", Habitat.ICE, UNCOMMON, MEDIUM);
        b.add("glass_snailfish", "透明狮子鱼", "Glass Snailfish", Habitat.ICE, UNCOMMON, SMALL);
        b.add("blizzard_trout", "雪暴湖鳟", "Blizzard Lake Trout", Habitat.ICE, RARE, LARGE, Time.ANY, STORM, Depth.ANY);
        b.add("spotted_wolffish", "斑纹狼鳚", "Spotted Wolffish", Habitat.ICE, RARE, LARGE);
        b.add("sleeper_shark", "冰下睡鲨", "Ice Sleeper Shark", Habitat.ICE, LEGENDARY, HUGE, Time.ANY, Weather.ANY, DEEP);
        // ---- 酸水：嗜极生物，壳厚、颜色暗
        b.add("sulfur_killifish", "硫泉鳉", "Sulfur Spring Killifish", Habitat.ACID, COMMON, SMALL);
        b.add("blackshell_catfish", "黑甲鲇", "Blackshell Catfish", Habitat.ACID, COMMON, MEDIUM);
        b.add("marsh_loach", "酸沼鳅", "Acid Marsh Loach", Habitat.ACID, COMMON, SMALL, NIGHT, Weather.ANY, SHALLOW);
        b.add("rustscale_barb", "锈鳞鲃", "Rustscale Barb", Habitat.ACID, COMMON, SMALL);
        b.add("plated_shieldhead", "骨甲盾头鱼", "Plated Shieldhead", Habitat.ACID, COMMON, MEDIUM, DAY, Weather.ANY, Depth.ANY);
        b.add("sulfide_lungfish", "硫化肺鱼", "Sulfide Lungfish", Habitat.ACID, UNCOMMON, MEDIUM);
        b.add("charcoal_eel", "炭纹鳗", "Charcoal Eel", Habitat.ACID, UNCOMMON, MEDIUM, NIGHT, Weather.ANY, Depth.ANY);
        b.add("acidophile_cichlid", "嗜酸丽鱼", "Acidophile Cichlid", Habitat.ACID, UNCOMMON, SMALL);
        b.add("thickplate_sturgeon", "厚甲鲟", "Thickplate Sturgeon", Habitat.ACID, UNCOMMON, LARGE, Time.ANY, Weather.ANY, DEEP);
        b.add("stormlit_boxfish", "雷纹箱鲀", "Stormlit Boxfish", Habitat.ACID, RARE, SMALL, Time.ANY, STORM, Depth.ANY);
        b.add("armored_gar", "铠鳞雀鳝", "Armored Gar", Habitat.ACID, RARE, LARGE);
        b.add("great_placoderm", "巨盾皮鱼", "Great Placoderm", Habitat.ACID, LEGENDARY, HUGE, Time.ANY, Weather.ANY, DEEP);
        // ---- 暗河：洞里的水，没眼睛、颜色淡；风暴天地表的水冲下来，带进洪流盲鳗
        b.add("eyeless_killifish", "无眼洞鳉", "Eyeless Cave Killifish", Habitat.CAVE, COMMON, SMALL);
        b.add("pale_cave_barb", "白洞鲃", "Pale Cave Barb", Habitat.CAVE, COMMON, SMALL);
        b.add("blind_loach", "盲鳅", "Blind Loach", Habitat.CAVE, COMMON, SMALL, Time.ANY, Weather.ANY, SHALLOW);
        b.add("skylight_dace", "天窗鲦", "Skylight Dace", Habitat.CAVE, COMMON, SMALL, DAY, Weather.ANY, Depth.ANY);
        b.add("pinkgill_eel", "粉鳃洞鳗", "Pinkgill Cave Eel", Habitat.CAVE, COMMON, MEDIUM);
        b.add("blind_catfish", "盲鲇", "Blind Catfish", Habitat.CAVE, UNCOMMON, MEDIUM, NIGHT, Weather.ANY, Depth.ANY);
        b.add("cave_lungfish", "穴居肺鱼", "Cave Lungfish", Habitat.CAVE, UNCOMMON, MEDIUM);
        b.add("phosphor_cavefish", "磷光盲鳉", "Phosphor Cavefish", Habitat.CAVE, UNCOMMON, SMALL, NIGHT, Weather.ANY, Depth.ANY);
        b.add("albino_cave_perch", "白化洞鲈", "Albino Cave Perch", Habitat.CAVE, UNCOMMON, MEDIUM, Time.ANY, Weather.ANY, DEEP);
        b.add("floodwater_eel", "洪流盲鳗", "Floodwater Blind Eel", Habitat.CAVE, RARE, LARGE, Time.ANY, STORM, Depth.ANY);
        b.add("cave_sturgeon", "洞穴白鲟", "Pale Cave Sturgeon", Habitat.CAVE, RARE, LARGE, Time.ANY, Weather.ANY, DEEP);
        b.add("underriver_catfish", "暗河巨鲇", "Giant Underriver Catfish", Habitat.CAVE, LEGENDARY, HUGE);
        // ---- 奇异：奇异星球和辐射星球，骨骼里沉着晶体和重金属
        b.add("crystalscale_dace", "晶鳞鲦", "Crystalscale Dace", Habitat.EXOTIC, COMMON, SMALL);
        b.add("twintail_killifish", "双尾鳉", "Twin-tailed Killifish", Habitat.EXOTIC, COMMON, SMALL);
        b.add("glowspot_perch", "荧斑鲈", "Glowspot Perch", Habitat.EXOTIC, COMMON, SMALL, NIGHT, Weather.ANY, Depth.ANY);
        b.add("sixfin_gurnard", "六鳍鲂鮄", "Six-finned Gurnard", Habitat.EXOTIC, COMMON, MEDIUM);
        b.add("mirrorscale_carp", "镜鳞鲤", "Mirrorscale Carp", Habitat.EXOTIC, COMMON, MEDIUM, DAY, Weather.ANY, Depth.ANY);
        b.add("parasol_fin", "伞鳍鱼", "Parasol Fin", Habitat.EXOTIC, UNCOMMON, SMALL, NIGHT, Weather.ANY, Depth.ANY);
        b.add("three_eyed_puffer", "三眼鲀", "Three-eyed Puffer", Habitat.EXOTIC, UNCOMMON, SMALL);
        b.add("spiral_eel", "螺旋鳗", "Spiral Eel", Habitat.EXOTIC, UNCOMMON, MEDIUM);
        b.add("silica_catfish", "硅甲鲇", "Silica Catfish", Habitat.EXOTIC, UNCOMMON, MEDIUM, Time.ANY, Weather.ANY, DEEP);
        b.add("aurora_ray", "极光鳐", "Aurora Ray", Habitat.EXOTIC, RARE, LARGE, Time.ANY, STORM, Depth.ANY);
        b.add("leadfin_grouper", "铅鳍石斑鱼", "Leadfin Grouper", Habitat.EXOTIC, RARE, LARGE, Time.ANY, Weather.ANY, DEEP);
        b.add("crystalback_sturgeon", "晶背巨鲟", "Crystalback Sturgeon", Habitat.EXOTIC, LEGENDARY, HUGE);
        ALL = Collections.unmodifiableList(b.list);
        for (Habitat habitat : Habitat.values()) {
            BY_HABITAT.put(habitat, new ArrayList<>());
        }
        for (FishSpecies species : ALL) {
            BY_ID.put(species.id(), species);
            BY_HABITAT.get(species.habitat()).add(species);
        }
        BY_HABITAT.replaceAll((habitat, list) -> List.copyOf(list));
    }

    private FishTable() {
    }

    public static Optional<FishSpecies> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** 编号对应的鱼；越界返回空。 */
    public static Optional<FishSpecies> byIndex(int index) {
        return index >= 0 && index < ALL.size() ? Optional.of(ALL.get(index)) : Optional.empty();
    }

    /** 这种水域的 12 种鱼，按图鉴的顺序。 */
    public static List<FishSpecies> of(Habitat habitat) {
        return BY_HABITAT.get(habitat);
    }

    private static final class Builder {
        final List<FishSpecies> list = new ArrayList<>();

        void add(String id, String zh, String en, Habitat habitat, Rarity rarity, SizeClass size) {
            this.add(id, zh, en, habitat, rarity, size, Time.ANY, Weather.ANY, Depth.ANY);
        }

        void add(String id, String zh, String en, Habitat habitat, Rarity rarity, SizeClass size, Time time, Weather weather, Depth depth) {
            this.list.add(new FishSpecies(this.list.size(), id, zh, en, habitat, rarity, size, time, weather, depth));
        }
    }
}
