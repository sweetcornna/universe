package io.github.sweetcornna.universe.civ;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.civ.AlienTongue;
import io.github.sweetcornna.universe.core.civ.CivState;
import io.github.sweetcornna.universe.core.civ.Encounter;
import io.github.sweetcornna.universe.core.civ.Encounters;
import io.github.sweetcornna.universe.core.civ.Gifts;
import io.github.sweetcornna.universe.core.civ.Lexicon;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Reward;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.civ.Value;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.tech.ModuleHost;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.network.DialoguePayloads;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import io.github.sweetcornna.universe.tech.ToolWeaponItem;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 和外星 NPC 对话（ADR-0027 §四）。每个 NPC 每人每天：答一次题、收一次礼、教一个词（声望到"认识"以后）。
 *
 * <ul>
 *   <li>答题：NPC 说一段外星话，三个选项分别对上利、知、勇，选中这个种族看重的那个算对。选对声望 +3、从这段话里学一个词、
 *       给一份奖励；选错声望 −1。</li>
 *   <li>送礼：把手里的东西送出去一个。合这个种族心意的声望 +2、回教一个词；不合的收下，声望不变。</li>
 *   <li>请教：教一个还不认识的词。</li>
 * </ul>
 * 敌视（声望小于 0）时 NPC 不理人，右键只在动作栏提示一句。
 */
public final class Dialogues {
    /** 离 NPC 这么远以内才能对话（界面开着走远了，按钮就不管用了）。 */
    private static final double REACH = 6;

    private Dialogues() {
    }

    /** 今天是第几个游戏日（按主世界的游戏刻，和 {@code /time set} 无关）。 */
    public static long day(MinecraftServer server) {
        return server.overworld().getGameTime() / 24000L;
    }

    /** 这个 NPC 今天的题（选项顺序也定了）。 */
    public static Encounter encounter(AlienNpc npc, long day) {
        return Encounters.pick(npc.seed() ^ day);
    }

    /** 右键 NPC：敌视时提示一句，否则打开对话界面。 */
    public static void open(ServerPlayer player, AlienNpc npc) {
        Standing.Tier tier = Civ.state(player).tier(npc.race());
        if (!Standing.canTalk(tier)) {
            player.sendOverlayMessage(Component.translatable("message.universe.npc.refuses", npc.label()).withStyle(ChatFormatting.RED));
            return;
        }
        npc.getLookControl().setLookAt(player);
        npc.gesture();
        send(player, npc, true, List.of());
    }

    public static void handleAction(DialoguePayloads.Action payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entity());
        if (!(entity instanceof AlienNpc npc) || !npc.isAlive() || player.distanceToSqr(npc) > REACH * REACH) {
            return;
        }
        if (!Standing.canTalk(Civ.state(player).tier(npc.race()))) {
            player.sendOverlayMessage(Component.translatable("message.universe.npc.refuses", npc.label()).withStyle(ChatFormatting.RED));
            return;
        }
        if (payload.action() == DialoguePayloads.Action.SERVICE) {
            switch (npc.role()) {
                case TECH_MERCHANT -> TechShop.open(player, npc);
                case GUILD_ENVOY -> GuildEnvoy.open(player, npc);
                default -> {
                }
            }
            return;
        }
        List<Component> notes = switch (payload.action()) {
            case DialoguePayloads.Action.ANSWER -> answer(player, npc, payload.option());
            case DialoguePayloads.Action.GIFT -> gift(player, npc);
            case DialoguePayloads.Action.ASK -> ask(player, npc);
            default -> List.of();
        };
        send(player, npc, false, notes);
    }

    // ---- 三件事

    /** 回答今天的题：选第几项（0–2）。返回界面上写的结果。 */
    public static List<Component> answer(ServerPlayer player, AlienNpc npc, int option) {
        String key = "talk:" + npc.key();
        if (Civ.usedToday(player, key)) {
            return List.of(note("gui.universe.dialogue.answered_today"));
        }
        long day = day(player.level().getServer());
        Encounter encounter = encounter(npc, day);
        if (option < 0 || option >= encounter.options().size()) {
            return List.of();
        }
        Race race = npc.race();
        Civ.markUsed(player, key);
        List<Component> notes = new ArrayList<>();
        if (!encounter.correct(race, option)) {
            notes.add(reply(race, false));
            Civ.changeStanding(player, race, Standing.WRONG_ANSWER);
            notes.add(standing(Standing.WRONG_ANSWER));
            return notes;
        }
        long seed = npc.seed() ^ day;
        notes.add(reply(race, true));
        Civ.changeStanding(player, race, Standing.RIGHT_ANSWER);
        notes.add(standing(Standing.RIGHT_ANSWER));
        learned(player, race, Civ.teachWord(player, race, encounter.wordMask(), Seeds.derive(seed, "word")), notes);
        notes.add(give(player, Reward.forAnswer(race, Seeds.derive(seed, "reward")), Seeds.derive(seed, "item")));
        player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), WorldSounds.DIALOGUE, SoundSource.NEUTRAL, 0.8F, 1.0F);
        return notes;
    }

    /** 把主手的东西送一个出去。 */
    public static List<Component> gift(ServerPlayer player, AlienNpc npc) {
        String key = "gift:" + npc.key();
        if (Civ.usedToday(player, key)) {
            return List.of(note("gui.universe.dialogue.gifted_today"));
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return List.of(note("gui.universe.dialogue.empty_hand"));
        }
        Race race = npc.race();
        Gifts.Kind kind = giftKind(stack);
        Component name = stack.getHoverName().copy();
        stack.shrink(1);
        Civ.markUsed(player, key);
        List<Component> notes = new ArrayList<>();
        notes.add(Component.translatable("gui.universe.dialogue.gave", name).withStyle(ChatFormatting.GRAY));
        if (!Gifts.liked(race, kind)) {
            notes.add(Component.translatable("civ.universe.gift." + race.id() + ".plain"));
            return notes;
        }
        notes.add(Component.translatable("civ.universe.gift." + race.id() + ".liked"));
        Civ.changeStanding(player, race, Standing.LIKED_GIFT);
        notes.add(standing(Standing.LIKED_GIFT));
        long seed = npc.seed() ^ day(player.level().getServer());
        learned(player, race, Civ.teachWord(player, race, -1, Seeds.derive(seed, "gift")), notes);
        return notes;
    }

    /** 请教一个词（声望到"认识"以后，每个 NPC 每天一个）。 */
    public static List<Component> ask(ServerPlayer player, AlienNpc npc) {
        Race race = npc.race();
        if (!Standing.canAskWord(Civ.state(player).tier(race))) {
            return List.of(note("gui.universe.dialogue.ask_locked"));
        }
        String key = "ask:" + npc.key();
        if (Civ.usedToday(player, key)) {
            return List.of(note("gui.universe.dialogue.asked_today"));
        }
        Civ.markUsed(player, key);
        long seed = npc.seed() ^ day(player.level().getServer());
        List<Component> notes = new ArrayList<>();
        learned(player, race, Civ.teachWord(player, race, -1, Seeds.derive(seed, "ask")), notes);
        return notes;
    }

    // ---- 礼物和奖励

    /** 手里的东西算哪一类礼物。 */
    public static Gifts.Kind giftKind(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ToolWeaponItem) {
            return Gifts.Kind.WEAPON;
        }
        if (item instanceof TechModuleItem module) {
            return module.type() == ModuleType.WEAPON_CALIBRATOR || module.type() == ModuleType.FIRE_CONTROL ? Gifts.Kind.WEAPON : Gifts.Kind.TECH;
        }
        if (item == UniverseItems.CIRCUIT_BOARD.get()) {
            return Gifts.Kind.TECH;
        }
        if (item == UniverseItems.NAVIGATION_DATA.get() || item == UniverseItems.SURVEY_DATA.get()) {
            return Gifts.Kind.DATA;
        }
        if (item == UniverseItems.SHELL_FOSSIL.get() || item == UniverseItems.ARTHROPOD_FOSSIL.get() || item == UniverseItems.SKULL_FOSSIL.get()
                || item == UniverseItems.LIVING_PEARL.get()) {
            return Gifts.Kind.RELIC;
        }
        if (item == UniverseItems.SENTINEL_SCRAP.get() || item == UniverseItems.QUADRUPED_SERVO.get() || item == UniverseItems.WALKER_BRAIN.get()) {
            return Gifts.Kind.SENTINEL_PART;
        }
        if (item == UniverseItems.CREDIT_CHIP.get() || item == UniverseItems.SALVAGED_SCRAP.get() || item == Items.GOLD_INGOT
                || item == Items.DIAMOND || item == Items.EMERALD || item == Items.NETHERITE_INGOT) {
            return Gifts.Kind.VALUABLES;
        }
        if (stack.has(DataComponents.FOOD)) {
            return Gifts.Kind.FOOD;
        }
        return Gifts.Kind.OTHER;
    }

    /** 发奖励，返回界面上写的那一行。贸易品是打捞来的旧部件（只能卖）；模块是 C 级随机一份。 */
    static Component give(ServerPlayer player, Reward reward, long seed) {
        int amount = Math.max(1, reward.amount());
        if (reward.kind() == Reward.Kind.CREDITS) {
            Credits.add(player, amount);
            return Component.translatable("gui.universe.dialogue.credits", Credits.format(amount)).withStyle(ChatFormatting.GOLD);
        }
        ItemStack stack = switch (reward.kind()) {
            case TRADE_GOODS -> new ItemStack(UniverseItems.SALVAGED_SCRAP.get(), amount);
            case NAVIGATION_DATA -> new ItemStack(UniverseItems.NAVIGATION_DATA.get(), amount);
            case SURVEY_DATA -> new ItemStack(UniverseItems.SURVEY_DATA.get(), amount);
            case SENTINEL_SCRAP -> new ItemStack(UniverseItems.SENTINEL_SCRAP.get(), amount);
            case MAGNETIC_COIL -> new ItemStack(UniverseItems.MAGNETIC_COIL.get(), amount);
            case TECH_MODULE -> module(techModule(seed), seed);
            case WEAPON_MODULE -> module(ModuleType.WEAPON_CALIBRATOR, seed);
            case CREDITS -> ItemStack.EMPTY;
        };
        Component line = Component.translatable("gui.universe.dialogue.item", stack.getHoverName(), stack.getCount()).withStyle(ChatFormatting.GOLD);
        // GameTest 的模拟玩家没有连接，不发格子更新
        player.getInventory().placeItemBackInInventory(stack, player.connection != null, Prediction.SERVER_ONLY);
        return line;
    }

    /** 技术模块的奖励：外骨骼和多功能工具上的，武器校准除外。 */
    private static ModuleType techModule(long seed) {
        List<ModuleType> types = new ArrayList<>();
        for (ModuleType type : ModuleType.values()) {
            if (type.host() != ModuleHost.SHIP && type != ModuleType.WEAPON_CALIBRATOR) {
                types.add(type);
            }
        }
        return Rng.of(seed).pick(types);
    }

    private static ItemStack module(ModuleType type, long seed) {
        ItemStack stack = new ItemStack(UniverseItems.MODULES.get(type).get());
        TechModuleItem.calibrate(stack, seed);
        return stack;
    }

    // ---- 界面

    private static void learned(ServerPlayer player, Race race, int concept, List<Component> notes) {
        if (concept < 0) {
            notes.add(note("gui.universe.dialogue.no_new_word"));
            return;
        }
        String alien = Civ.tongue(player.level().getServer(), race).word(concept);
        notes.add(Component.translatable("gui.universe.dialogue.learned", Component.literal(alien).withColor(race.color()),
                Component.translatable("civ.universe.word." + Lexicon.id(concept)).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.AQUA));
    }

    private static Component reply(Race race, boolean right) {
        return Component.translatable("civ.universe.reply." + race.id() + (right ? ".right" : ".wrong")).withStyle(ChatFormatting.ITALIC);
    }

    private static Component standing(int delta) {
        return Component.translatable("gui.universe.dialogue.standing", delta > 0 ? "+" + delta : String.valueOf(delta))
                .withStyle(delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static Component note(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }

    /** 把对话界面的内容发给玩家。 */
    static void send(ServerPlayer player, AlienNpc npc, boolean open, List<Component> notes) {
        MinecraftServer server = player.level().getServer();
        Race race = npc.race();
        CivState state = Civ.state(player);
        Standing.Tier tier = state.tier(race);
        Encounter encounter = encounter(npc, day(server));
        AlienTongue tongue = Civ.tongue(server, race);
        List<String> alien = new ArrayList<>();
        for (int concept : encounter.words()) {
            alien.add(tongue.word(concept));
        }
        List<Integer> options = new ArrayList<>();
        for (Value value : encounter.options()) {
            options.add(value.ordinal());
        }
        int flags = 0;
        if (Civ.usedToday(player, "talk:" + npc.key())) {
            flags |= DialoguePayloads.ANSWERED;
        }
        if (Civ.usedToday(player, "gift:" + npc.key())) {
            flags |= DialoguePayloads.GIFTED;
        }
        if (Civ.usedToday(player, "ask:" + npc.key())) {
            flags |= DialoguePayloads.ASKED;
        }
        if (Standing.canAskWord(tier)) {
            flags |= DialoguePayloads.CAN_ASK;
        }
        PacketDistributor.sendToPlayer(player, new DialoguePayloads.State(open, npc.getId(), npc.npcName().zh(), npc.npcName().en(), race.ordinal(),
                npc.role().ordinal(), tier.ordinal(), state.standing(race), encounter.id(), encounter.words(), alien, state.words(race), options, flags,
                notes));
    }
}
