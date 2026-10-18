package io.github.sweetcornna.universe.client.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.dyson.ClientDyson;
import io.github.sweetcornna.universe.client.ship.ShipClient;
import io.github.sweetcornna.universe.dyson.EmRailEjectorBlockEntity;
import io.github.sweetcornna.universe.dyson.LaunchSiloBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

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
 * <li>{@code button <编号>}：按当前设备界面的第几个按钮
 * <li>{@code click <x> <y> [shift] [ctrl]}：在当前界面上点一下，坐标相对界面中心（界面缩放后的像素）
 * <li>{@code await launch|shot <x> <y> <z> [最多等几刻]}：等那里的发射井发射、弹射器射出一次再往下走
 * <li>{@code dyson}：打开戴森球编辑器；{@code close}：关掉当前界面
 * <li>{@code hud on|off}：显示、隐藏界面（F1）
 * <li>{@code fly on|off}：创造模式下悬停，好在半空里取景
 * <li>{@code shot <名字>}：截图存到 {@code screenshots/<名字>.png}
 * <li>{@code log <文字>}：写一行日志（带上鼠标有没有被抓住、信用点、位置和重力）
 * <li>{@code quit}：退出游戏
 * </ul>
 */
public final class DevScript {
    private static final String PROPERTY = "universe.devScript";
    private static final boolean ENABLED = !System.getProperty(PROPERTY, "").isBlank();
    /** 进存档以后先等这么久，让区块和同步的数据到齐。 */
    private static final int START_DELAY = 60;

    private final Deque<String> lines;
    private int waiting = START_DELAY;
    private boolean started;
    private @Nullable Await await;

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
        if (!this.started) {
            this.started = true;
            Universe.LOGGER.info("Dev script: player in world, starting in {} ticks", START_DELAY);
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
        while (!this.lines.isEmpty() && this.waiting <= 0 && this.await == null) {
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

    private void run(Minecraft minecraft, LocalPlayer player, String line) {
        int space = line.indexOf(' ');
        String verb = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        String rest = space < 0 ? "" : line.substring(space + 1).strip();
        String[] args = rest.isEmpty() ? new String[0] : rest.split("\\s+");
        Universe.LOGGER.info("Dev script: {}", line);
        switch (verb) {
            case "wait" -> this.waiting = Integer.parseInt(args[0]);
            case "cmd" -> player.connection.sendCommand(rest.startsWith("/") ? rest.substring(1) : rest);
            case "look" -> {
                player.setYRot(Float.parseFloat(args[0]));
                player.setXRot(Float.parseFloat(args[1]));
                player.yRotO = player.getYRot();
                player.xRotO = player.getXRot();
            }
            case "use" -> {
                BlockPos pos = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
                if (minecraft.gameMode != null) {
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
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
                    screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, modifiers)), false);
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
                if (player.getAbilities().mayfly) {
                    player.getAbilities().flying = args.length == 0 || !args[0].equals("off");
                    player.onUpdateAbilities();
                }
            }
            case "shot" -> Screenshot.grab(minecraft.gameDirectory, name(args), minecraft.gameRenderer.mainRenderTarget(), 1,
                    message -> Universe.LOGGER.info("Dev script screenshot: {}", message.getString()));
            case "log" -> Universe.LOGGER.info("Dev script note: {} (mouse grabbed: {}, credits: {}, pos: {} {} {}, gravity: {})", rest,
                    minecraft.mouseHandler.isMouseGrabbed(), player.getData(UniverseAttachments.CREDITS), String.format("%.2f", player.getX()),
                    String.format("%.2f", player.getY()), String.format("%.2f", player.getZ()),
                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY));
            case "quit" -> minecraft.stop();
            default -> Universe.LOGGER.warn("Dev script: unknown command {}", verb);
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
}
