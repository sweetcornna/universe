package io.github.sweetcornna.universe.client.dev;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.asteroid.Asteroid;
import io.github.sweetcornna.universe.client.ClientConfig;
import io.github.sweetcornna.universe.client.ItemSummaries;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.blueprint.GhostClient;
import io.github.sweetcornna.universe.client.dyson.ClientDyson;
import io.github.sweetcornna.universe.client.dyson.DysonSkyRenderer;
import io.github.sweetcornna.universe.client.guide.GuideScreen;
import io.github.sweetcornna.universe.client.mecha.FlyersClient;
import io.github.sweetcornna.universe.client.pirate.PirateClient;
import io.github.sweetcornna.universe.client.ship.ShipClient;
import io.github.sweetcornna.universe.dyson.EmRailEjectorBlockEntity;
import io.github.sweetcornna.universe.dyson.LaunchSiloBlockEntity;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.network.ResearchActionPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.survival.SuitHud;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 开发用的客户端脚本：启动时带了 {@code -Duniverse.devScript=<文件>}（{@code ./gradlew runClient -PdevScript=<文件>}）
 * 才启用。进了存档以后逐行执行，用来在不碰键盘鼠标的情况下做游戏内验收，窗口可以一直放在屏幕外。
 *
 * <p>每行一条，{@code #} 开头是注释：
 * <ul>
 * <li>{@code wait <刻>}：等一会儿
 * <li>{@code cmd <命令>}：以玩家身份执行命令（不带斜杠）
 * <li>{@code look <偏航> <俯仰>}：转视角
 * <li>{@code use <x> <y> <z>}：右键点方块的顶面（打开设备界面）
 * <li>{@code usenear <方块 ID>}：右键点 8 格内最近的这种方块的顶面
 * <li>{@code approach <方块 ID>}：传送到 16 格内最近的这种方块正面两格的地方，面朝它
 * <li>{@code button <编号>}：按当前设备界面的第几个按钮
 * <li>{@code click <x> <y> [shift] [ctrl]}：在当前界面上点一下，坐标相对界面中心（界面缩放后的像素）
 * <li>{@code type <文字>}：往当前界面里有焦点的输入框打字；{@code enter}：按一下回车
 * <li>{@code await launch|shot <x> <y> <z> [最多等几刻]}：等那里的发射井发射、弹射器射出一次再往下走
 * <li>{@code dyson}：打开戴森球编辑器；{@code close}：关掉当前界面
 * <li>{@code hud on|off}：显示、隐藏界面（F1）
 * <li>{@code fly on|off}：创造模式下悬停，好在半空里取景
 * <li>{@code track on|steer|off}：每刻把视线转向最近的海盗战机（驾驶飞船时测试开炮用）；{@code steer} 还按左右键把船头转过去
 * <li>{@code key forward|back|left|right|jump|sneak|sprint|use on|off}：按住、松开一个键（每刻重新按下，开飞船、按住右键分析用）
 * <li>{@code near <实体 ID> <距离> [最多等几刻]}：等这种实体进到这么近以内再往下走
 * <li>{@code shot <名字>}：截图存到 {@code screenshots/<名字>.png}
 * <li>{@code log <文字>}：写一行日志（带上鼠标有没有被抓住、信用点、位置和重力）
 * <li>{@code quit}：退出游戏
 * </ul>
 */
public final class DevScript {
    private static final String PROPERTY = "universe.devScript";
    /** 兼容模组的插件加的脚本命令（例如 JEI 插件的 jei &lt;物品&gt;），这里不直接引用那些模组的类。 */
    public static final Map<String, Consumer<String[]>> EXTRA_VERBS = new HashMap<>();
    private static final boolean ENABLED = !System.getProperty(PROPERTY, "").isBlank();
    /** 进存档以后先等这么久，让区块和同步的数据到齐。 */
    private static final int START_DELAY = 60;

    private final Deque<String> lines;
    private int waiting = START_DELAY;
    private boolean started;
    private @Nullable Await await;
    private boolean tracking;
    private boolean steering;
    private boolean trackAsteroids;
    /** 脚本按住的键（每刻重新按下，免得被界面开关之类的复位）。 */
    private final Set<KeyMapping> held = new HashSet<>();
    private @Nullable Near near;
    private @Nullable Players players;

    /** 在等在线人数变成某个数（多人测试：主机等第二个客户端连上、再等它退出）。 */
    private record Players(int count, long deadline) {
    }

    /** 在等某种实体靠近。 */
    private record Near(Identifier type, double distance, long deadline) {
    }

    /** 在等某台设备动一下：记下开始时的发射时刻，变了就往下走。 */
    private record Await(BlockPos pos, long previous, long deadline) {
    }

    private DevScript(List<String> lines) {
        this.lines = new ArrayDeque<>(lines);
    }

    /** 带了脚本启动：这时不抓鼠标（{@code MouseHandlerMixin}），窗口放在屏幕外也不会拽走鼠标。 */
    public static boolean enabled() {
        return ENABLED;
    }

    public static void registerEvents(IEventBus gameBus) {
        if (!ENABLED) {
            return;
        }
        String path = System.getProperty(PROPERTY);
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (IOException e) {
            Universe.LOGGER.error("Dev script {} unreadable", path, e);
            return;
        }
        Universe.LOGGER.info("Dev script {} loaded ({} lines)", path, lines.size());
        DevScript script = new DevScript(lines);
        gameBus.addListener(ClientTickEvent.Post.class, event -> script.tick());
    }

    private void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        // 脚本按的键不算玩家输入，原版一分钟没输入就把帧率压到 30，量出来的帧率就不对了
        minecraft.getFramerateLimitTracker().onInputReceived();
        if (!this.started) {
            this.started = true;
            Universe.LOGGER.info("Dev script: player in world, starting in {} ticks", START_DELAY);
        }
        if (this.tracking) {
            track(minecraft, player, this.steering, this.trackAsteroids);
        }
        for (KeyMapping key : this.held) {
            key.setDown(true);
        }
        if (--this.waiting > 0) {
            return;
        }
        if (this.await != null) {
            long last = lastAction(minecraft, this.await.pos());
            if (last == this.await.previous() && minecraft.level.getGameTime() < this.await.deadline()) {
                return;
            }
            Universe.LOGGER.info("Dev script: await done ({})", last == this.await.previous() ? "timeout" : "fired");
            this.await = null;
        }
        if (this.near != null) {
            Near n = this.near;
            boolean close = !minecraft.level.getEntities(player, player.getBoundingBox().inflate(n.distance()),
                    e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(n.type()) && e.distanceTo(player) <= n.distance()).isEmpty();
            if (!close && minecraft.level.getGameTime() < n.deadline()) {
                return;
            }
            Universe.LOGGER.info("Dev script: near done ({})", close ? "close" : "timeout");
            this.near = null;
        }
        if (this.players != null) {
            int online = player.connection.getOnlinePlayers().size();
            if (online != this.players.count() && minecraft.level.getGameTime() < this.players.deadline()) {
                return;
            }
            Universe.LOGGER.info("Dev script: players done ({} online)", online);
            this.players = null;
        }
        while (!this.lines.isEmpty() && this.waiting <= 0 && this.await == null && this.near == null && this.players == null) {
            String line = this.lines.poll().strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                this.run(minecraft, player, line);
            } catch (RuntimeException e) {
                Universe.LOGGER.error("Dev script line failed: {}", line, e);
            }
        }
    }

    /** 视线转向最近的海盗战机；{@code steer} 时按左右键把船头转向它。 */
    private static void track(Minecraft minecraft, LocalPlayer player, boolean steer, boolean asteroids) {
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        List<? extends Entity> candidates = asteroids
                ? player.level().getEntitiesOfClass(Asteroid.class, player.getBoundingBox().inflate(160))
                : PirateClient.nearby(player);
        for (Entity candidate : candidates) {
            double d = candidate.distanceToSqr(player);
            if (d < best) {
                best = d;
                nearest = candidate;
            }
        }
        if (nearest == null) {
            if (steer) {
                minecraft.options.keyLeft.setDown(false);
                minecraft.options.keyRight.setDown(false);
                minecraft.options.keyJump.setDown(false);
                minecraft.options.keyShift.setDown(false);
            }
            return;
        }
        Vec3 to = nearest.getBoundingBox().getCenter().subtract(player.getEyePosition());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        if (steer && player.getVehicle() instanceof ShipEntity ship) {
            // 偏航变大是向右转
            float delta = Mth.wrapDegrees(yaw - ship.getYRot());
            minecraft.options.keyRight.setDown(delta > 6);
            minecraft.options.keyLeft.setDown(delta < -6);
            // 上下也对齐（空格升、Shift 降）
            double dy = nearest.getBoundingBox().getCenter().y - ship.getBoundingBox().getCenter().y;
            minecraft.options.keyJump.setDown(dy > 1.5);
            minecraft.options.keyShift.setDown(dy < -1.5);
        }
        player.setYRot(yaw);
        player.setXRot((float) -Math.toDegrees(Math.atan2(to.y, horizontal)));
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
    }

    private static int count(LocalPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int coord(String token, int base) {
        return token.startsWith("~") ? base + (token.length() > 1 ? Integer.parseInt(token.substring(1)) : 0) : Integer.parseInt(token);
    }

    private void run(Minecraft minecraft, LocalPlayer player, String line) {
        int space = line.indexOf(' ');
        String verb = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        String rest = space < 0 ? "" : line.substring(space + 1).strip();
        String[] args = rest.isEmpty() ? new String[0] : rest.split("\\s+");
        Universe.LOGGER.info("Dev script: {}", line);
        switch (verb) {
            case "wait" -> this.waiting = Integer.parseInt(args[0]);
            case "cmd" -> player.connection.sendCommand(rest.startsWith("/") ? rest.substring(1) : rest);
            case "track" -> {
                // track [asteroid] [steer] | track off：盯着最近的海盗（或小行星）
                List<String> options = List.of(args);
                this.tracking = !options.contains("off");
                this.steering = options.contains("steer");
                this.trackAsteroids = options.contains("asteroid");
                if (!this.steering) {
                    minecraft.options.keyLeft.setDown(this.held.contains(minecraft.options.keyLeft));
                    minecraft.options.keyRight.setDown(this.held.contains(minecraft.options.keyRight));
                    minecraft.options.keyJump.setDown(this.held.contains(minecraft.options.keyJump));
                    minecraft.options.keyShift.setDown(this.held.contains(minecraft.options.keyShift));
                }
            }
            case "key" -> {
                KeyMapping key = switch (args[0]) {
                    case "forward" -> minecraft.options.keyUp;
                    case "back" -> minecraft.options.keyDown;
                    case "left" -> minecraft.options.keyLeft;
                    case "right" -> minecraft.options.keyRight;
                    case "jump" -> minecraft.options.keyJump;
                    case "sneak" -> minecraft.options.keyShift;
                    case "sprint" -> minecraft.options.keySprint;
                    case "use" -> minecraft.options.keyUse;
                    default -> throw new IllegalArgumentException("unknown key " + args[0]);
                };
                boolean down = args.length < 2 || args[1].equals("on");
                key.setDown(down);
                if (down) {
                    this.held.add(key);
                } else {
                    this.held.remove(key);
                }
            }
            case "near" -> {
                int timeout = args.length > 2 ? Integer.parseInt(args[2]) : 1200;
                this.near = new Near(Identifier.parse(args[0]), Double.parseDouble(args[1]), minecraft.level.getGameTime() + timeout);
            }
            case "look" -> {
                // look <偏航> <俯仰> | look ship：顺着所乘的飞船的朝向看，水平
                boolean ship = args[0].equals("ship") && player.getVehicle() != null;
                player.setYRot(ship ? player.getVehicle().getYRot() : Float.parseFloat(args[0]));
                player.setXRot(ship ? 0 : Float.parseFloat(args[1]));
                player.yRotO = player.getYRot();
                player.xRotO = player.getXRot();
            }
            case "use" -> {
                // 坐标可以用 ~ 写成相对玩家脚下方块的
                BlockPos base = player.blockPosition();
                BlockPos pos = new BlockPos(coord(args[0], base.getX()), coord(args[1], base.getY()), coord(args[2], base.getZ()));
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
                if (minecraft.gameMode != null) {
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                }
            }
            case "usenear" -> {
                BlockPos found = nearest(minecraft, player, Identifier.parse(args[0]), 8);
                if (found != null && minecraft.gameMode != null) {
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(found).add(0, 0.5, 0), Direction.UP, found, false);
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                }
            }
            case "approach" -> {
                BlockPos found = nearest(minecraft, player, Identifier.parse(args[0]), 16);
                if (found != null) {
                    BlockState state = minecraft.level.getBlockState(found);
                    Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                            ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
                    BlockPos stand = found.relative(facing, 2);
                    player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %d %.1f %.1f 0", stand.getX() + 0.5, stand.getY(),
                            stand.getZ() + 0.5, facing.getOpposite().toYRot()));
                }
            }
            case "interact", "attack" -> {
                // interact|attack <实体类型>：对最近的那个实体右键或左键（16 格内）
                Identifier type = Identifier.parse(args[0]);
                Entity target = null;
                double best = 256;
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    double d = entity.distanceToSqr(player);
                    if (entity != player && d < best && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(type)) {
                        best = d;
                        target = entity;
                    }
                }
                if (target != null && minecraft.gameMode != null) {
                    if (verb.equals("attack")) {
                        minecraft.gameMode.attack(player, target);
                    } else {
                        minecraft.gameMode.interact(player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND);
                    }
                }
            }
            case "tooltip" -> {
                // tooltip [shift]：把主手物品的说明写进日志；shift 时换成按住 Shift 看到的那几行
                ItemStack stack = player.getMainHandItem();
                List<Component> lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level, player), player,
                        TooltipFlag.NORMAL));
                if (args.length > 0 && args[0].equals("shift") && !lines.isEmpty()) {
                    // 键盘上的 Shift 没按，事件接上的是提示那一行，换成说明
                    lines.removeLast();
                    ItemSummaries.append(stack, lines, true);
                }
                for (Component tooltipLine : lines) {
                    Universe.LOGGER.info("Dev script tooltip: {}", tooltipLine.getString());
                }
            }
            case "respawn" -> {
                // 上次验收死在存档里时先复活
                if (player.isDeadOrDying()) {
                    player.respawn();
                }
            }
            case "press" -> {
                // press analyze|techtree|rotate|starmap|discovery|stats：按一下本模组的键（开关分析目镜、打开科技树、转蓝图、
                // 打开星图、发现日志、生产统计）
                KeyMapping key = switch (args[0]) {
                    case "analyze" -> UniverseKeys.ANALYZE;
                    case "techtree" -> UniverseKeys.TECH_TREE;
                    case "rotate" -> UniverseKeys.ROTATE_BLUEPRINT;
                    case "starmap" -> UniverseKeys.STAR_MAP;
                    case "discovery" -> UniverseKeys.DISCOVERY_LOG;
                    case "stats" -> UniverseKeys.STATS;
                    case "mecha" -> UniverseKeys.MECHA;
                    default -> throw new IllegalArgumentException("unknown key " + args[0]);
                };
                KeyMapping.click(key.getKey());
            }
            case "rightclick" -> KeyMapping.click(minecraft.options.keyUse.getKey()); // 按一下右键，走原版的完整流程
            case "advancements" -> minecraft.gui.setScreen(new AdvancementsScreen(player.connection.getAdvancements()));
            // 和科技树上"研究"按钮发的一样，服务端照样检查前置
            case "research" -> ClientPacketDistributor.sendToServer(new ResearchActionPayload(ResearchActionPayload.Action.START, args[0]));
            case "guide" -> {
                // guide check：把接到下一页的正文写进日志；guide <章> <页>：打开手册翻到那一页（从 0 数）
                if (args[0].equals("check")) {
                    List<String> report = GuideScreen.continued(minecraft.font, minecraft.level);
                    Universe.LOGGER.info("Dev script guide check: {} continued pages", report.size());
                    report.forEach(entry -> Universe.LOGGER.info("Dev script guide continued: {}", entry));
                } else {
                    minecraft.gui.setScreen(GuideScreen.at(Integer.parseInt(args[0]), Integer.parseInt(args[1])));
                }
            }
            case "useitem" -> {
                // 对着空气右键（用手里的东西）
                if (minecraft.gameMode != null) {
                    minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                }
            }
            case "button" -> {
                if (minecraft.gameMode != null && player.containerMenu != null) {
                    minecraft.gameMode.handleInventoryButtonClick(player.containerMenu.containerId, Integer.parseInt(args[0]));
                }
            }
            case "click" -> {
                Screen screen = minecraft.gui.screen();
                if (screen != null) {
                    // 修饰键的位：Shift 1、Ctrl 64（InputWithModifiers）
                    int modifiers = 0;
                    for (int i = 2; i < args.length; i++) {
                        modifiers |= args[i].equals("shift") ? 1 : args[i].equals("ctrl") ? 64 : 0;
                    }
                    double x = screen.width / 2.0 + Double.parseDouble(args[0]);
                    double y = screen.height / 2.0 + Double.parseDouble(args[1]);
                    MouseButtonEvent click = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, modifiers));
                    screen.mouseClicked(click, false);
                    // 手上拿着东西点格子时，原版要等松开鼠标才放下去
                    screen.mouseReleased(click);
                }
            }
            case "scroll" -> {
                // scroll 横 竖：在界面中间转滚轮
                Screen screen = minecraft.gui.screen();
                if (screen != null) {
                    screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, Double.parseDouble(args[0]), Double.parseDouble(args[1]));
                }
            }
            case "type" -> {
                Screen screen = minecraft.gui.screen();
                if (screen != null) {
                    rest.codePoints().forEach(codepoint -> screen.charTyped(new CharacterEvent(codepoint)));
                }
            }
            case "enter" -> {
                Screen screen = minecraft.gui.screen();
                if (screen != null) {
                    screen.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                }
            }
            case "await" -> {
                BlockPos pos = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                int timeout = args.length > 4 ? Integer.parseInt(args[4]) : 1200;
                this.await = new Await(pos, lastAction(minecraft, pos), minecraft.level.getGameTime() + timeout);
            }
            case "dyson" -> ClientDyson.openEditor();
            case "fire" -> ShipClient.forceFire = args.length == 0 || args[0].equals("on");
            case "shipmode" -> ShipClient.toggleWeaponMode();
            case "close" -> minecraft.gui.setScreen(null);
            case "hud" -> {
                boolean show = args.length == 0 || !args[0].equals("off");
                if (minecraft.gui.hud.isHidden() == show) {
                    minecraft.gui.hud.toggle();
                }
            }
            case "fly" -> {
                // mayFly 也算上机甲的驱动引擎（NeoForge 的 creative_flight 属性）
                if (player.mayFly()) {
                    player.getAbilities().flying = args.length == 0 || !args[0].equals("off");
                    player.onUpdateAbilities();
                }
            }
            // 机甲：核心电量、在不在飞、工地还剩几格（服务端的和客户端虚影的）、缺什么、在飞的无人机和机器人
            case "mecha" -> {
                SuitHud hud = player.getData(UniverseAttachments.SUIT_HUD);
                Universe.LOGGER.info("Dev script mecha: {} energy {}/{}, flying {}, build {}, ghosts {}, missing {}, flyers {}, iron {}, cobblestone {}",
                        rest, Math.round(hud.energy()), Math.round(hud.energyMax()), player.getAbilities().flying, hud.build(),
                        GhostClient.remaining(), hud.missing() < 0 ? "-" : BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.byId(hud.missing())),
                        FlyersClient.count(), count(player, Items.IRON_INGOT),
                        count(player, Items.COBBLESTONE));
            }
            case "shot" -> Screenshot.grab(minecraft.gameDirectory, name(args), minecraft.gameRenderer.mainRenderTarget(), 1,
                    message -> Universe.LOGGER.info("Dev script screenshot: {}", message.getString()));
            case "log" -> Universe.LOGGER.info("Dev script note: {} ({} in {}, mouse grabbed: {}, credits: {}, pos: {} {} {}, gravity: {}, fps: {})", rest,
                    player.getName().getString(), minecraft.level.dimension().identifier(),
                    minecraft.mouseHandler.isMouseGrabbed(), player.getData(UniverseAttachments.CREDITS), String.format("%.2f", player.getX()),
                    String.format("%.2f", player.getY()), String.format("%.2f", player.getZ()),
                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY), minecraft.getFps()
                            + ", dyson sky " + DysonSkyRenderer.takeStats() + ", " + io.github.sweetcornna.universe.client.sky.SpaceDust.stats());
            // 测帧率：不限帧、关垂直同步（无头验收结束后 options.txt 会还原）
            case "unlimitedfps" -> {
                minecraft.options.framerateLimit().set(260);
                minecraft.options.enableVsync().set(false);
                minecraft.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            }
            case "dysonsky" -> ClientConfig.DYSON_SKY.set(ClientConfig.DysonSky.valueOf(args[0].toUpperCase(java.util.Locale.ROOT)));
            // 多人测试的主机：对局域网开放，给客人用命令的权限，不验证正版账号（开发客户端都是离线账号）。
            // 只监听回环地址，见 IntegratedServerMixin
            case "lan" -> {
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.setUsesAuthentication(false);
                    boolean published = server.publishServer(MinecraftServer.MultiplayerScope.LAN, true, Integer.parseInt(args[0]));
                    Universe.LOGGER.info("Dev script: LAN on port {}: {}", args[0], published);
                }
            }
            case "waitplayers" -> this.players = new Players(Integer.parseInt(args[0]),
                    minecraft.level.getGameTime() + (args.length > 1 ? Integer.parseInt(args[1]) : 6000));
            case "quit" -> minecraft.stop();
            default -> {
                Consumer<String[]> extra = EXTRA_VERBS.get(verb);
                if (extra != null) {
                    extra.accept(args);
                } else {
                    Universe.LOGGER.warn("Dev script: unknown command {}", verb);
                }
            }
        }
    }

    /** 发射井最近一次发射、弹射器最近一次射出的游戏刻。 */
    private static long lastAction(Minecraft minecraft, BlockPos pos) {
        BlockEntity blockEntity = minecraft.level == null ? null : minecraft.level.getBlockEntity(pos);
        if (blockEntity instanceof LaunchSiloBlockEntity silo) {
            return silo.lastLaunch();
        }
        if (blockEntity instanceof EmRailEjectorBlockEntity ejector) {
            return ejector.lastShot();
        }
        return Long.MIN_VALUE;
    }

    private static @Nullable String name(String[] args) {
        return args.length == 0 ? null : args[0].endsWith(".png") ? args[0] : args[0] + ".png";
    }

    /** 范围内离玩家最近的这种方块。 */
    private static @Nullable BlockPos nearest(Minecraft minecraft, LocalPlayer player, Identifier id, int radius) {
        BlockPos origin = player.blockPosition();
        BlockPos found = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            double d = pos.distSqr(origin);
            if (d < best && BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(pos).getBlock()).equals(id)) {
                best = d;
                found = pos.immutable();
            }
        }
        return found;
    }
}
