package io.github.sweetcornna.universe.datagen;

import java.util.Map;

import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.BoneFamily;
import io.github.sweetcornna.universe.core.fossil.BonePart;

/** M19 的文字：化石（子代理 C，ADR-0034 §三）。40 块骨头的名字由类群名和部位名拼出来。 */
final class FossilLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    /** 类群：名字、骨架名字里的修饰、名词（中文、英文各一组）。 */
    private record FamilyText(String zh, String prefixZh, String nounZh, String en, String prefixEn, String nounEn) {
    }

    private static final Map<BoneFamily, FamilyText> FAMILIES = Map.of(
            BoneFamily.TITAN, new FamilyText("巨兽", "巨角", "巨兽", "Titan", "Horned", "Titan"),
            BoneFamily.PREDATOR, new FamilyText("掠食兽", "利齿", "掠食兽", "Predator", "Fanged", "Predator"),
            BoneFamily.LONGNECK, new FamilyText("长颈兽", "长颈", "长颈兽", "Longneck", "Longnecked", "Longneck"),
            BoneFamily.FLYER, new FamilyText("翼兽", "有翼", "翼兽", "Flyer", "Winged", "Flyer"),
            BoneFamily.WORM, new FamilyText("巨蠕", "蠕形", "巨蠕", "Wyrm", "Ringed", "Wyrm"),
            BoneFamily.ARTHROPOD, new FamilyText("甲壳兽", "甲壳", "甲壳兽", "Arthropod", "Armoured", "Arthropod"),
            BoneFamily.MARINE, new FamilyText("海兽", "鳍足", "海兽", "Leviathan", "Finned", "Leviathan"),
            BoneFamily.EXOTIC, new FamilyText("奇异种", "奇异", "异兽", "Aberration", "Aberrant", "Anomaly"));

    private static final Map<BonePart, String[]> PARTS = Map.of(
            BonePart.SKULL, new String[] {"头骨", "Skull"},
            BonePart.NECK, new String[] {"颈椎", "Neck"},
            BonePart.RIBCAGE, new String[] {"胸廓", "Ribcage"},
            BonePart.LIMBS, new String[] {"四肢", "Limbs"},
            BonePart.TAIL, new String[] {"尾骨", "Tail"});

    private FossilLanguage() {
    }

    static void add(Both both) {
        String f = "fossil.universe.";
        guide(both);
        for (BoneFamily family : BoneFamily.values()) {
            FamilyText text = FAMILIES.get(family);
            both.add(f + "family." + family.id(), text.zh(), text.en());
            both.add(f + "family." + family.id() + ".prefix", text.prefixZh(), text.prefixEn());
            both.add(f + "family." + family.id() + ".noun", text.nounZh(), text.nounEn());
        }
        for (BonePart part : BonePart.values()) {
            both.add(f + "part." + part.id(), PARTS.get(part)[0], PARTS.get(part)[1]);
        }
        both.add(f + "part.limbs.worm", "体节环", "Segment Rings");
        both.add(f + "rarity.common", "普通", "Common");
        both.add(f + "rarity.uncommon", "少见", "Uncommon");
        both.add(f + "rarity.rare", "稀有", "Rare");
        both.add(f + "rarity.very_rare", "极稀有", "Very Rare");
        // 骨架的名字：中文修饰直接连着名词（长颈掠食兽），英文中间空格
        both.add(f + "skeleton_name.0", "%s", "%s");
        both.add(f + "skeleton_name.1", "%s%s", "%s %s");
        both.add(f + "skeleton_name.2", "%s%s%s", "%s %s %s");
        both.add(f + "skeleton_name.partial", "残缺的骨架（%s/%s）", "Incomplete Skeleton (%s/%s)");

        String boneSummaryZh = "化石土里挖得到，石哨兵身上也有\n放进骨架展台对应的格，或者摆在化石底座上\n化石收藏家那里三块换一块";
        String boneSummaryEn = "Dug out of fossil dirt; stone sentries carry them too\nFits its slot on a Skeleton Display, or sits on a Fossil Pedestal\n"
                + "Fossil collectors trade three for one";
        for (Bone bone : Bone.all()) {
            FamilyText family = FAMILIES.get(bone.family());
            String[] part = PARTS.get(bone.part());
            boolean rings = bone.family() == BoneFamily.WORM && bone.part() == BonePart.LIMBS;
            both.add("item.universe." + bone.id(), family.zh() + (rings ? "体节环" : part[0]),
                    family.en() + " " + (rings ? "Segment Rings" : part[1]));
            both.add("tooltip.universe." + bone.id() + ".summary", boneSummaryZh, boneSummaryEn);
        }
        both.add("tooltip.universe.bone.family", "%s · %s", "%s · %s");
        both.add("tooltip.universe.bone.slot", "部位：%s", "Part: %s");

        both.add("item.universe.fossil_exhibit", "化石展品", "Fossil Exhibit");
        both.add("item.universe.fossil_exhibit.named", "化石展品：%s", "Fossil Exhibit: %s");
        both.add("tooltip.universe.fossil_exhibit.empty", "空的", "Empty");
        both.add("tooltip.universe.fossil_exhibit.part", "%s：%s", "%s: %s");
        both.add("tooltip.universe.fossil_exhibit.missing", "（空）", "(empty)");
        both.add("tooltip.universe.fossil_exhibit.pure", "五块同一类群", "All five from one family");
        both.add("tooltip.universe.fossil_exhibit.price", "收藏家出价 %s 信用点", "Collectors pay %s credits");
        both.add("tooltip.universe.fossil_exhibit.summary", "拿着对空的骨架展台右键，原样摆回去\n化石收藏家按骨头的价钱收：五块齐了翻倍，同一类群再乘 1.5",
                "Right-click an empty Skeleton Display to set it back up\n"
                        + "Fossil collectors pay what the bones are worth: double if complete, another ×1.5 if all one family");

        both.add("block.universe.fossil_pedestal", "化石底座", "Fossil Pedestal");
        both.add("tooltip.universe.fossil_pedestal.summary", "摆一块骨头，什么部位都行\n拿着骨头右键放上，空手右键取下",
                "Shows off one bone of any part\nRight-click with a bone to set it down, empty-handed to take it back");
        both.add("block.universe.skeleton_display", "骨架展台", "Skeleton Display");
        both.add("tooltip.universe.skeleton_display.summary",
                "五格：头骨、颈椎、胸廓、四肢、尾骨，哪个类群都能放\n拿着骨头右键放进对应的格，空手右键打开\n五块齐了，顶上显示骨架的名字\n界面里按\"封装\"收成一件展品",
                "Five slots: skull, neck, ribcage, limbs, tail. Any family fits\nRight-click with a bone to slot it, empty-handed to open\n"
                        + "A complete skeleton shows its name above\nPack it into an exhibit from the screen");
        both.add("block.universe.dormant_stone_sentry", "沉睡的石哨兵", "Dormant Stone Sentry");
        both.add("entity.universe.stone_sentry", "石哨兵", "Stone Sentry");
        both.add("entity.universe.sentry_rock", "飞石", "Thrown Rock");
        both.add("landmark.universe.dig_site", "古生物发掘场", "Fossil Dig Site");
        both.add("civ.universe.role.fossil_collector", "化石收藏家", "Fossil Collector");
        both.add("gui.universe.dialogue.service.fossil_collector", "看看化石", "Fossil Trade");
        both.add("tech.universe.paleontology", "古生物学", "Paleontology");

        both.add("message.universe.skeleton_display.occupied", "展台上还有骨头，先封装或者取下来", "The display isn't empty. Pack it or take the bones off first");
        both.add("message.universe.collector.no_exhibit", "先把展品放进格子里", "Put an exhibit in the slot first");
        both.add("message.universe.collector.sold", "卖出%s，得 %s 信用点", "Sold %s for %s credits");
        both.add("message.universe.collector.need_three", "要放够 %s 块骨头才能换", "Swapping takes %s bones");
        both.add("message.universe.collector.too_rare", "想换%s的骨头，放进来的至少要有一块一样稀有", "For a %s bone, offer at least one just as rare");
        both.add("message.universe.collector.swapped", "换到了%s%s", "Got a bone: %s %s");

        String d = "gui.universe.skeleton_display.";
        both.add(d + "pack", "封装", "Pack");
        both.add(d + "pack_hint", "把整副骨架收成一件展品，可以堆叠，也可以卖给化石收藏家",
                "Packs the skeleton into one exhibit item. Exhibits stack and sell to fossil collectors");
        both.add(d + "empty", "还没放骨头", "No bones yet");
        both.add(d + "incomplete", "骨架还不完整", "Skeleton incomplete");
        both.add(d + "count", "%s/%s 块", "%s/%s bones");
        both.add(d + "price", "%s 信用点", "%s credits");
        both.add(d + "pure", "同类", "Pure");
        both.add(d + "slot", "%s格", "%s slot");

        String c = "gui.universe.collector.";
        both.add(c + "exhibits", "收展品", "Exhibits");
        both.add(c + "swaps", "%s 块换 1 块", "%s for 1");
        both.add(c + "sell", "卖掉", "Sell");
        both.add(c + "swap", "换", "Swap");
        both.add(c + "no_exhibit", "放进一件展品", "Put in an exhibit");
        both.add(c + "offer", "%s 信用点", "%s credits");
        both.add(c + "bonus_pure", "全套同类 ×3", "Pure set ×3");
        both.add(c + "bonus_complete", "全套 ×2", "Complete ×2");
        both.add(c + "bonus_none", "不全，不加价", "No bonus");
        both.add(c + "ready", "可以换", "Ready");
        both.add(c + "need_three", "要放够 %s 块", "Needs %s bones");
        both.add(c + "too_rare", "不够稀有", "Too rare");

        String k = "commands.universe.fossil.";
        both.add(k + "given", "给了一块%s%s", "Gave %s %s");
        both.add(k + "exhibit", "给了展品：%s", "Gave exhibit: %s");
        both.add(k + "put", "放上了%s%s", "Placed %s %s");
        both.add(k + "no_display", "准星没对着骨架展台或化石底座", "Not looking at a skeleton display or fossil pedestal");
        both.add(k + "filled", "展台放满了：%s", "Display filled: %s");
        both.add(k + "empty", "展台是空的", "The display is empty");
        both.add(k + "packed", "封装好了：%s", "Packed: %s");
        both.add(k + "dug", "惊动了 %s 个沉睡的石哨兵", "Disturbed %s dormant sentries");
        both.add(k + "failed", "这里放不出石哨兵", "No room for a sentry here");
        both.add(k + "sentry", "唤醒了一个石哨兵", "Woke a stone sentry");
        both.add(k + "collector", "叫来了一位化石收藏家", "Called in a fossil collector");
        both.add(k + "none", "附近没有古生物发掘场", "No fossil dig site nearby");
        both.add(k + "located", "最近的古生物发掘场在 %s, %s（%s 格）", "Nearest fossil dig site: %s, %s (%s blocks)");
    }

    /** 手册的「古生物」一章。 */
    private static void guide(Both both) {
        String g = "guide.universe.paleontology";
        both.add(g, "古生物", "Paleontology");
        both.add(g + ".bones.title", "骨头", "Bones");
        both.add(g + ".bones",
                "骨头分 8 个类群，每个类群 5 个部位：头骨、颈椎、胸廓、四肢、尾骨（巨蠕的是体节环）。\n\n"
                        + "星球上挖化石土，四成会多带出一块骨头，哪个类群多看星球\n"
                        + "巨兽、掠食兽、长颈兽常见\n"
                        + "翼兽、海兽少见\n"
                        + "巨蠕、甲壳兽稀有\n"
                        + "奇异种极少见，只在奇异星球\n"
                        + "研究「古生物学」（交 4 块螺壳化石、2 块节肢动物化石）解锁底座和展台",
                "Bones come from 8 families, 5 parts each: skull, neck, ribcage, limbs, tail (a wyrm has segment rings for limbs).\n\n"
                        + "Planet fossil dirt gives a bone 40% of the time; the planet sets the families\n"
                        + "Titans, predators and longnecks are common\n"
                        + "Flyers and leviathans are uncommon\n"
                        + "Wyrms and arthropods are rare\n"
                        + "Aberrations: very rare, exotic worlds only\n"
                        + "Research Paleontology (4 coiled shell fossils, 2 arthropod fossils) for the pedestal and display");
        both.add(g + ".displays.title", "展台", "Displays");
        both.add(g + ".displays",
                "化石底座摆一块骨头，什么部位都行。骨架展台有五格，每格只收对应的部位，类群可以混着放。\n\n"
                        + "拿着骨头右键放进对应的格，空手右键打开\n"
                        + "五格放满就拼出一副骨架，顶上显示名字\n"
                        + "界面里点「封装」收成一件化石展品，能叠\n"
                        + "拿着展品对着空展台右键，原样摆回去",
                "A fossil pedestal holds one bone. A skeleton display has five slots, one per part; families can mix.\n"
                        + "Bone + right-click: slot it. Empty hand: open\n"
                        + "All five filled: it assembles, name above\n"
                        + "Pack: makes a stackable fossil exhibit\n"
                        + "Exhibit + right-click an empty display: rebuild");
        both.add(g + ".collectors.title", "化石收藏家", "Fossil Collectors");
        both.add(g + ".collectors",
                "空间站和古生物发掘场里有化石收藏家，右键和对方交易。\n\n"
                        + "收展品：按骨头的价钱，五块齐了 ×2，五块同一类群 ×3\n"
                        + "换骨头：交 3 块不要的，换 1 块指定类群、部位的\n"
                        + "想换稀有的，交的里面至少要有一块一样稀有\n"
                        + "展品只卖给收藏家",
                "Fossil collectors work at space stations and dig sites. Right-click to trade.\n\n"
                        + "Exhibits: priced on the bones, ×2 if complete, ×3 if all five share a family\n"
                        + "Swaps: 3 bones you don't need for 1 of the family and part you pick\n"
                        + "For a rare bone, offer at least one just as rare\n"
                        + "Only collectors buy exhibits");
        both.add(g + ".dig_sites.title", "发掘场和石哨兵", "Dig Sites and Sentries");
        both.add(g + ".dig_sites",
                "能降落的星球上有古生物发掘场，扫描能标出来：几顶帐篷、一位收藏家、一大片化石土。\n\n"
                        + "发掘场里睡着石哨兵，在旁边挖化石土，一次比一次躁动，挖到第 3 块醒过来\n"
                        + "醒了的石哨兵扔石块，血少了冲过来自爆\n"
                        + "打下来掉 1–2 块稀有的骨头\n"
                        + "和平难度下不醒",
                "Dig sites sit on landable planets and show up on scans: a few tents, a collector and a big patch of fossil dirt.\n\n"
                        + "Stone sentries sleep in the site. Each dig nearby stirs them, and the third wakes them\n"
                        + "Awake, they throw rocks, then charge and blow up when badly hurt\n"
                        + "They drop 1–2 rare bones\n"
                        + "On Peaceful they stay asleep");
    }
}
