package io.github.sweetcornna.universe.client.galaxy;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.TEXT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.WARN;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.core.sentinel.SentinelLevel;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.darkfog.ClientDarkFogMap;
import io.github.sweetcornna.universe.client.discovery.ClientDiscoveries;
import io.github.sweetcornna.universe.client.portal.PortalDialScreen;
import io.github.sweetcornna.universe.client.factory.ClientResearch;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.Resource;
import io.github.sweetcornna.universe.core.galaxy.Star;
import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.galaxy.StarMapCamera;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.portal.PortalAddress;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.core.sky.BodyLook;
import io.github.sweetcornna.universe.core.space.BlackHoles;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.Warp;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipWarp;
import io.github.sweetcornna.universe.network.DarkFogMapPayload;
import io.github.sweetcornna.universe.network.WarpRequestPayload;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 星图（玩法设计 §1.1、§5.1）：星团里的全部恒星系画在三维空间里。左键拖动旋转、右键拖动平移、滚轮缩放，
 * 每颗恒星往银道面（y = 0）拉一条垂线，看得出上下远近。点一个恒星系，右边列出恒星、距离和行星。
 *
 * <p>驾驶装了跃迁引擎的飞船在太空里打开时，画出跃迁射程，详情下面有跃迁按钮（ADR-0012）。
 */
public class StarMapScreen extends Screen {
    private static final Identifier GLOW = Universe.id("textures/gui/star_glow.png");
    private static final Identifier RING = Universe.id("textures/gui/ring.png");
    private static final Identifier DISC = Universe.id("textures/gui/disc.png");
    private static final int TEXTURE = 64;
    private static final int PANEL_WIDTH = 168;
    private static final int HEADER = 18;
    private static final int FOOTER = 14;
    private static final int PADDING = 8;
    private static final int BACKGROUND = 0xFF02050A;
    private static final int PANEL = 0xF00A1218;
    private static final int GRID = 0x2C4FB4D0;
    private static final int GRID_AXIS = 0x484FB4D0;
    /** 银道面上的网格：每 5 光年一圈。 */
    private static final double GRID_STEP = 5;
    /** 离焦点多近的恒星系标出名字（光年）。 */
    private static final double LABEL_RANGE = 14;
    private static final int STRIP_HEIGHT = 30;
    /** 详情底部跃迁那一块的高度。 */
    private static final int WARP_HEIGHT = 52;
    private static final int BUTTON_HEIGHT = 16;
    private static final int WARP = 0xFFB98CFF;
    /** 黑雾的巢穴和种子（ADR-0028 §八）。 */
    private static final int DARK_FOG = 0xFF2A48;
    /** 巢穴的六边形最大多大（像素）。 */
    private static final double HIVE_MARK = 12;

    private final StarMapCamera camera = new StarMapCamera();
    private final @Nullable Galaxy galaxy;
    private final OptionalInt current;
    private final double gridRadius;
    private final float[] backdrop;
    private int selected;
    private int hovered = -1;
    private int hoveredPlanet = -1;
    /** 面板上的行星要不要列出资源（宇宙探索，ADR-0022）。 */
    private boolean showResources;
    /** 选中的恒星系里有没有另外放的黑洞（ADR-0024），换了恒星系才重算。 */
    private int holeSystem = -1;
    private boolean holeHere;
    /** 缓存：哪个星系离最近的去过的星系多远。 */
    private int nearestFor = -1;
    private double nearestExplored = Double.POSITIVE_INFINITY;
    private boolean rotating;
    private boolean panning;
    private double panelScroll;
    private int panelContentHeight;
    private long lastFrame;
    private StarMapCamera.@Nullable Projected[] projected = new StarMapCamera.Projected[0];
    /** 玩家正在驾驶的飞船；没在驾驶时为空。 */
    private final @Nullable ShipEntity ship;
    private List<SpaceLayout.Body> currentBodies = List.of();
    private boolean warpReady;

    public StarMapScreen() {
        super(Component.translatable("gui.universe.star_map"));
        this.galaxy = ClientGalaxy.galaxy().orElse(null);
        this.current = ClientGalaxy.currentSystem();
        this.selected = this.current.orElse(GalaxyGenerator.HOME_SYSTEM);
        double radius = 0;
        if (this.galaxy != null) {
            for (StarSystem system : this.galaxy.systems()) {
                radius = Math.max(radius, Math.hypot(system.x(), system.z()));
            }
        }
        this.gridRadius = Math.ceil(radius / GRID_STEP) * GRID_STEP;
        // 背景里的暗星点：固定种子，每次打开都一样
        Rng rng = Rng.of(0x5EED_57A2L);
        this.backdrop = new float[3 * 260];
        for (int i = 0; i < 260; i++) {
            this.backdrop[i * 3] = (float) rng.nextDouble();
            this.backdrop[i * 3 + 1] = (float) rng.nextDouble();
            this.backdrop[i * 3 + 2] = (float) Math.pow(rng.nextDouble(), 3);
        }
        LocalPlayer player = Minecraft.getInstance().player;
        this.ship = player != null && player.getVehicle() instanceof ShipEntity vehicle && vehicle.pilot() == player ? vehicle : null;
        if (this.current.isPresent() && this.galaxy != null) {
            this.galaxy.system(this.current.getAsInt()).ifPresent(system -> this.currentBodies = SpaceLayout.of(system));
        }
        StarSystem start = this.system(this.selected);
        if (start != null) {
            this.camera.jumpTo(start.x(), start.y(), start.z());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private @Nullable StarSystem system(int index) {
        return this.galaxy == null ? null : this.galaxy.system(index).orElse(null);
    }

    // ---- 排版

    private int mapRight() {
        return this.width - PANEL_WIDTH;
    }

    private double centerX() {
        return this.mapRight() / 2.0;
    }

    private double centerY() {
        return (HEADER + this.height - FOOTER) / 2.0;
    }

    private double mapHeight() {
        return this.height - HEADER - FOOTER;
    }

    private boolean inMap(double x, double y) {
        return x < this.mapRight() && y >= HEADER && y < this.height - FOOTER;
    }

    private StarMapCamera.@Nullable Projected project(double x, double y, double z) {
        return this.camera.project(x, y, z, this.centerX(), this.centerY(), this.mapHeight());
    }

    /** 恒星在星图上的大小（直径，屏幕单位）。 */
    private static double starSize(Star star, StarMapCamera.Projected p) {
        double factor = switch (star.starClass()) {
            case O -> 1.7;
            case B -> 1.45;
            case A -> 1.2;
            case F -> 1.1;
            case G -> 1.0;
            case K -> 0.9;
            case M -> 0.75;
            case RED_GIANT -> 1.6;
            case WHITE_DWARF -> 0.6;
            case NEUTRON_STAR -> 0.55;
            case BLACK_HOLE -> 1.2;
        };
        return Math.clamp(factor * 1.5 * p.scale(), 4, 30);
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, BACKGROUND);
        for (int i = 0; i < this.backdrop.length; i += 3) {
            int x = (int) (this.backdrop[i] * this.width);
            int y = (int) (this.backdrop[i + 1] * this.height);
            int alpha = 30 + (int) (this.backdrop[i + 2] * 150);
            graphics.fill(x, y, x + 1, y + 1, ARGB.color(alpha, 200, 215, 255));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        long now = Util.getMillis();
        double seconds = this.lastFrame == 0 ? 0 : Math.min(0.1, (now - this.lastFrame) / 1000.0);
        this.lastFrame = now;
        this.camera.update(seconds);
        if (this.galaxy == null) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.star_map.unknown"), this.width / 2, this.height / 2, DIM);
            return;
        }
        List<StarSystem> systems = this.galaxy.systems();
        this.projected = new StarMapCamera.Projected[systems.size()];
        for (int i = 0; i < systems.size(); i++) {
            StarSystem system = systems.get(i);
            this.projected[i] = this.project(system.x(), system.y(), system.z());
        }
        this.hovered = this.inMap(mouseX, mouseY) && !this.rotating && !this.panning ? this.systemAt(mouseX, mouseY) : -1;

        graphics.enableScissor(0, HEADER, this.mapRight(), this.height - FOOTER);
        this.drawGrid(graphics);
        this.drawRange(graphics, systems);
        this.drawDropLines(graphics, systems);
        this.drawRoute(graphics, systems);
        this.drawSeeds(graphics, systems, now);
        this.drawStars(graphics, systems, now);
        this.drawHives(graphics, systems, now);
        this.drawLabels(graphics, systems);
        graphics.disableScissor();

        this.drawHeader(graphics);
        this.drawPanel(graphics, mouseX, mouseY);
        graphics.fill(0, this.height - FOOTER, this.width, this.height, 0xE0050A0E);
        Component hint = Component.translatable("gui.universe.star_map.hint", UniverseKeys.STAR_MAP.getTranslatedKeyMessage());
        float hintScale = Math.min(1.0F, (this.width - 12) / (float) this.font.width(hint));
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(6, this.height - FOOTER / 2.0F - 4 * hintScale);
        pose.scale(hintScale);
        graphics.text(this.font, hint, 0, 0, DIM, false);
        pose.popMatrix();
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        int segments = 96;
        for (double r = GRID_STEP; r <= this.gridRadius + 1e-6; r += GRID_STEP) {
            StarMapCamera.Projected last = null;
            for (int i = 0; i <= segments; i++) {
                double angle = i * Math.PI * 2 / segments;
                StarMapCamera.Projected p = this.project(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                if (last != null && p != null) {
                    line(graphics, last.x(), last.y(), p.x(), p.y(), 0.6F, GRID);
                }
                last = p;
            }
        }
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            StarMapCamera.Projected inner = this.project(0, 0, 0);
            StarMapCamera.Projected outer = this.project(Math.cos(angle) * this.gridRadius, 0, Math.sin(angle) * this.gridRadius);
            if (inner != null && outer != null) {
                line(graphics, inner.x(), inner.y(), outer.x(), outer.y(), 0.6F, i % 3 == 0 ? GRID_AXIS : GRID);
            }
        }
    }

    /** 飞船的跃迁射程（ly）；没在驾驶带跃迁引擎的船、或者不在太空里时为 0。 */
    private double warpRange() {
        if (this.ship == null || this.ship.structure() == null || !SpaceDimensions.isSpace(this.ship.level().dimension())) {
            return 0;
        }
        return this.ship.structure().stats().warpRange();
    }

    /** 跃迁射程：以当前恒星系为圆心、在它那一层水平面上画一个圈。 */
    private void drawRange(GuiGraphicsExtractor graphics, List<StarSystem> systems) {
        double range = this.warpRange();
        if (range <= 0 || this.current.isEmpty()) {
            return;
        }
        StarSystem here = systems.get(this.current.getAsInt());
        int segments = 72;
        StarMapCamera.Projected last = null;
        for (int i = 0; i <= segments; i++) {
            double angle = i * Math.PI * 2 / segments;
            StarMapCamera.Projected p = this.project(here.x() + Math.cos(angle) * range, here.y(), here.z() + Math.sin(angle) * range);
            if (last != null && p != null) {
                line(graphics, last.x(), last.y(), p.x(), p.y(), 0.9F, ARGB.color(0x90, WARP));
            }
            last = p;
        }
    }

    /** 每颗恒星到银道面的垂线和落点。 */
    private void drawDropLines(GuiGraphicsExtractor graphics, List<StarSystem> systems) {
        for (int i = 0; i < systems.size(); i++) {
            StarSystem system = systems.get(i);
            StarMapCamera.Projected star = this.projected[i];
            StarMapCamera.Projected foot = this.project(system.x(), 0, system.z());
            if (star == null || foot == null) {
                continue;
            }
            boolean focused = i == this.selected || i == this.hovered;
            int color = ARGB.color(focused ? 0x90 : 0x40, system.star().color());
            line(graphics, star.x(), star.y(), foot.x(), foot.y(), 0.6F, color);
            boolean reachable = this.current.isPresent() && Warp.inReach(systems.get(this.current.getAsInt()), system, this.warpRange())
                    && this.warpRange() > 0 && this.ship != null && this.ship.structure() != null
                    && ShipWarp.missingDrive(this.ship.structure().stats().drive(), system) == null;
            sprite(graphics, DISC, foot.x(), foot.y(), reachable ? 3.2 : 2.2, reachable ? WARP : color);
        }
    }

    /** 从当前恒星系到选中的恒星系画一条虚线，中间偏下写上距离（名字标在恒星右边，同一高度，正中间容易压住）。 */
    private void drawRoute(GuiGraphicsExtractor graphics, List<StarSystem> systems) {
        if (this.current.isEmpty() || this.current.getAsInt() == this.selected) {
            return;
        }
        StarSystem from = systems.get(this.current.getAsInt());
        StarSystem to = systems.get(this.selected);
        int dashes = 24;
        for (int i = 0; i < dashes; i++) {
            double t0 = (double) i / dashes;
            double t1 = (i + 0.55) / dashes;
            StarMapCamera.Projected a = this.project(lerp(from.x(), to.x(), t0), lerp(from.y(), to.y(), t0), lerp(from.z(), to.z(), t0));
            StarMapCamera.Projected b = this.project(lerp(from.x(), to.x(), t1), lerp(from.y(), to.y(), t1), lerp(from.z(), to.z(), t1));
            if (a != null && b != null) {
                line(graphics, a.x(), a.y(), b.x(), b.y(), 0.8F, 0xC09FE6FF);
            }
        }
        StarMapCamera.Projected start = this.projected[from.index()];
        StarMapCamera.Projected end = this.projected[to.index()];
        StarMapCamera.Projected middle = this.project((from.x() + to.x()) / 2, (from.y() + to.y()) / 2, (from.z() + to.z()) / 2);
        if (middle != null && start != null && end != null && Math.hypot(end.x() - start.x(), end.y() - start.y()) > 48) {
            Component text = Component.translatable("gui.universe.star_map.light_years", GalaxyText.number(from.distanceTo(to), 1));
            int w = this.font.width(text);
            int x = (int) middle.x();
            int y = (int) middle.y() + 10;
            graphics.fill(x - w / 2 - 2, y - 6, x + w / 2 + 2, y + 5, 0xB0000000);
            graphics.centeredText(this.font, text, x, y - 4, ACCENT);
        }
    }

    private void drawStars(GuiGraphicsExtractor graphics, List<StarSystem> systems, long now) {
        // 由远到近画，近的盖住远的
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < systems.size(); i++) {
            if (this.projected[i] != null) {
                order.add(i);
            }
        }
        order.sort(Comparator.comparingDouble((Integer i) -> this.projected[i].depth()).reversed());
        for (int i : order) {
            Star star = systems.get(i).star();
            StarMapCamera.Projected p = this.projected[i];
            double size = starSize(star, p);
            int color = ARGB.opaque(star.color());
            if (star.starClass() == StarClass.BLACK_HOLE) {
                // 黑洞：吸积盘的光晕中间一个黑色的圆
                sprite(graphics, GLOW, p.x(), p.y(), size * 2.2, ARGB.color(0xC0, star.color()));
                sprite(graphics, RING, p.x(), p.y(), size * 0.9, color);
                sprite(graphics, DISC, p.x(), p.y(), size * 0.55, 0xFF000000);
            } else {
                sprite(graphics, GLOW, p.x(), p.y(), size * 2.4, color);
                sprite(graphics, GLOW, p.x(), p.y(), size * 0.9, 0xFFFFFFFF);
                if (star.starClass() == StarClass.NEUTRON_STAR) {
                    // 脉冲星：两道光束慢慢转
                    double angle = now / 700.0 + i;
                    double dx = Math.cos(angle) * size * 2.2;
                    double dy = Math.sin(angle) * size * 2.2;
                    line(graphics, p.x() - dx, p.y() - dy, p.x() + dx, p.y() + dy, 0.7F, 0xB0B4A0FF);
                } else if (star.starClass() == StarClass.WHITE_DWARF) {
                    // 白矮星：衍射星芒
                    double reach = size * 1.6;
                    line(graphics, p.x() - reach, p.y(), p.x() + reach, p.y(), 0.5F, 0x90FFFFFF);
                    line(graphics, p.x(), p.y() - reach, p.x(), p.y() + reach, 0.5F, 0x90FFFFFF);
                }
            }
        }
        // 标记：当前位置（青色、呼吸）、选中（白色）、鼠标指着的（灰色）
        if (this.current.isPresent() && this.projected[this.current.getAsInt()] != null) {
            StarMapCamera.Projected p = this.projected[this.current.getAsInt()];
            double pulse = 0.5 + 0.5 * Math.sin(now / 300.0);
            double size = starSize(systems.get(this.current.getAsInt()).star(), p) * 1.9 + 4 + pulse * 2;
            sprite(graphics, RING, p.x(), p.y(), size, ARGB.color((int) (150 + pulse * 100), ACCENT));
        }
        if (this.projected[this.selected] != null) {
            StarMapCamera.Projected p = this.projected[this.selected];
            this.brackets(graphics, p.x(), p.y(), starSize(systems.get(this.selected).star(), p) * 1.2 + 5, 0xFFFFFFFF);
        }
        if (this.hovered >= 0 && this.hovered != this.selected) {
            StarMapCamera.Projected p = this.projected[this.hovered];
            this.brackets(graphics, p.x(), p.y(), starSize(systems.get(this.hovered).star(), p) * 1.2 + 5, 0x90FFFFFF);
        }
    }

    /** 黑雾的种子（ADR-0028 §八）：从出发的恒星系到目标画一条暗红的虚线，种子走到哪画一个一闪一闪的红点。 */
    private void drawSeeds(GuiGraphicsExtractor graphics, List<StarSystem> systems, long now) {
        for (DarkFogMapPayload.Seed seed : ClientDarkFogMap.seeds()) {
            if (seed.from() < 0 || seed.from() >= systems.size() || seed.to() < 0 || seed.to() >= systems.size()) {
                continue;
            }
            StarSystem from = systems.get(seed.from());
            StarSystem to = systems.get(seed.to());
            int dashes = 32;
            for (int i = 0; i < dashes; i++) {
                double t0 = (double) i / dashes;
                double t1 = (i + 0.45) / dashes;
                StarMapCamera.Projected a = this.project(lerp(from.x(), to.x(), t0), lerp(from.y(), to.y(), t0), lerp(from.z(), to.z(), t0));
                StarMapCamera.Projected b = this.project(lerp(from.x(), to.x(), t1), lerp(from.y(), to.y(), t1), lerp(from.z(), to.z(), t1));
                if (a != null && b != null) {
                    line(graphics, a.x(), a.y(), b.x(), b.y(), 0.8F, ARGB.color(0x90, DARK_FOG));
                }
            }
            double t = ClientDarkFogMap.progress(seed);
            StarMapCamera.Projected p = this.project(lerp(from.x(), to.x(), t), lerp(from.y(), to.y(), t), lerp(from.z(), to.z(), t));
            if (p != null) {
                double pulse = 0.5 + 0.5 * Math.sin(now / 250.0 + seed.id());
                sprite(graphics, GLOW, p.x(), p.y(), 7 + pulse * 3, ARGB.color((int) (150 + pulse * 100), DARK_FOG));
                sprite(graphics, DISC, p.x(), p.y(), 2.4, 0xFFFFD0D6);
            }
        }
    }

    /**
     * 有黑雾巢穴的恒星系外面一圈慢慢转的小六边形；核心打掉了、等着重建的暗下去。差不多每个恒星系都有巢穴，
     * 六边形不跟着恒星放大，免得近处的几圈糊满屏幕。
     */
    private void drawHives(GuiGraphicsExtractor graphics, List<StarSystem> systems, long now) {
        for (DarkFogMapPayload.Hive hive : ClientDarkFogMap.hives().values()) {
            if (hive.system() < 0 || hive.system() >= systems.size() || this.projected[hive.system()] == null) {
                continue;
            }
            StarMapCamera.Projected p = this.projected[hive.system()];
            double radius = Math.min(starSize(systems.get(hive.system()).star(), p) * 0.6 + 4, HIVE_MARK);
            double turn = now / 2400.0 + hive.system();
            int color = hive.alive() ? ARGB.color(0xA8, DARK_FOG) : ARGB.color(0x50, DARK_FOG);
            hexagon(graphics, p.x(), p.y(), radius, turn, hive.alive() ? 0.8F : 0.6F, color);
            if (hive.alive() && hive.threat() > 0.05) {
                // 威胁越高，里面那圈越亮
                hexagon(graphics, p.x(), p.y(), radius * 0.72, -turn, 0.6F, ARGB.color(0x30 + (int) (hive.threat() * 0xC0), DARK_FOG));
            }
        }
    }

    private static void hexagon(GuiGraphicsExtractor graphics, double x, double y, double radius, double turn, float width, int color) {
        for (int k = 0; k < 6; k++) {
            double a0 = turn + k * Math.PI / 3;
            double a1 = a0 + Math.PI / 3;
            line(graphics, x + Math.cos(a0) * radius, y + Math.sin(a0) * radius, x + Math.cos(a1) * radius, y + Math.sin(a1) * radius, width,
                    color);
        }
    }

    /** 四个角的选框。 */
    private void brackets(GuiGraphicsExtractor graphics, double x, double y, double half, int color) {
        double arm = Math.max(2.5, half * 0.45);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                double cx = x + sx * half;
                double cy = y + sy * half;
                line(graphics, cx, cy, cx - sx * arm, cy, 0.8F, color);
                line(graphics, cx, cy, cx, cy - sy * arm, 0.8F, color);
            }
        }
    }

    /** 恒星系的名字：选中、当前、鼠标指着的一定画；焦点附近的按远近画，和已经画了的重叠就跳过。 */
    private void drawLabels(GuiGraphicsExtractor graphics, List<StarSystem> systems) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < systems.size(); i++) {
            if (this.projected[i] != null) {
                order.add(i);
            }
        }
        order.sort(Comparator.comparingInt((Integer i) -> this.important(i) ? 0 : 1)
                .thenComparingDouble(i -> this.projected[i].depth()));
        List<double[]> placed = new ArrayList<>();
        for (int i : order) {
            StarMapCamera.Projected p = this.projected[i];
            StarSystem system = systems.get(i);
            boolean important = this.important(i);
            double fromFocus = Math.sqrt(square(system.x() - this.camera.focusX()) + square(system.y() - this.camera.focusY())
                    + square(system.z() - this.camera.focusZ()));
            if (!important && (fromFocus > LABEL_RANGE || this.camera.distance() > 70)) {
                continue;
            }
            String name = GalaxyText.system(system);
            double size = starSize(system.star(), p);
            float scale = important ? 1.0F : 0.75F;
            double x = p.x() + size * 0.35 + 3;
            double y = p.y() - 4 * scale;
            double[] box = {x - 1, y - 1, x + this.font.width(name) * scale + 1, y + 9 * scale + 1};
            if (!important && placed.stream().anyMatch(other -> box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3])) {
                continue;
            }
            placed.add(box);
            int color = important ? TEXT : ARGB.color(0xB0, 0xB8C4CC);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate((float) x, (float) y);
            pose.scale(scale);
            graphics.text(this.font, name, 0, 0, color, true);
            pose.popMatrix();
        }
    }

    private boolean important(int index) {
        return index == this.selected || index == this.hovered || (this.current.isPresent() && index == this.current.getAsInt());
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, this.width, HEADER, 0xE0050A0E);
        graphics.fill(0, HEADER - 1, this.width, HEADER, 0x604FB4D0);
        graphics.text(this.font, this.title, 6, 5, ACCENT, false);
        int x = 8 + this.font.width(this.title);
        Component location = this.current.isPresent()
                ? Component.translatable("gui.universe.star_map.location", GalaxyText.system(this.galaxy.systems().get(this.current.getAsInt())))
                : Component.translatable("gui.universe.star_map.location_unknown");
        graphics.text(this.font, Component.literal("· ").append(location), x, 5, TEXT, false);
        Component count = Component.translatable("gui.universe.star_map.systems", this.galaxy.systems().size());
        graphics.text(this.font, count, this.width - this.font.width(count) - 6, 5, DIM, false);
    }

    // ---- 右边的详情

    private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = this.mapRight();
        graphics.fill(left, HEADER, this.width, this.height - FOOTER, PANEL);
        graphics.fill(left, HEADER, left + 1, this.height - FOOTER, 0x604FB4D0);
        StarSystem system = this.system(this.selected);
        if (system == null) {
            return;
        }
        int x = left + PADDING;
        int width = PANEL_WIDTH - PADDING * 2;
        int top = HEADER + 6;
        int bottom = this.height - FOOTER - 4 - WARP_HEIGHT;
        graphics.enableScissor(left + 1, HEADER, this.width, bottom);
        int y = top - (int) this.panelScroll;

        graphics.text(this.font, GalaxyText.system(system), x, y, ACCENT, false);
        y += 11;
        Star star = system.star();
        graphics.text(this.font, GalaxyText.pick(star.starClass().nameZh(), star.starClass().nameEn()), x, y, ARGB.opaque(star.color()), false);
        y += 11;
        for (Component line : this.distanceLines(system)) {
            graphics.text(this.font, line, x, y, TEXT, false);
            y += 10;
        }
        Component stats = star.starClass() == StarClass.BLACK_HOLE
                ? Component.translatable("gui.universe.star_map.mass", GalaxyText.magnitude(star.mass()))
                : Component.translatable("gui.universe.star_map.star_stats", GalaxyText.magnitude(star.luminosity()),
                        Math.round(star.temperature()), GalaxyText.magnitude(star.mass()));
        y = TextWrap.draw(graphics, this.font, stats, x, y, width, DIM, false) + 4;
        y = this.drawDarkFog(graphics, system, x, y, width);

        this.hoveredPlanet = -1;
        DiscoveryLog.Entry discovery = ClientDiscoveries.entry(DiscoveryKey.system(system.index())).orElse(null);
        Upgrades.Reveal reveal = this.reveal(system, discovery != null);
        this.showResources = reveal == Upgrades.Reveal.RESOURCES;
        if (reveal == Upgrades.Reveal.STAR) {
            // 没去过的恒星系（ADR-0012）：只看得到恒星；研究宇宙探索能看得更远（ADR-0022）
            y = this.smallText(graphics, Component.translatable("gui.universe.star_map.unexplored"), x, y, width, WARN) + 4;
            graphics.disableScissor();
            this.panelContentHeight = y + (int) this.panelScroll - top;
            this.drawWarp(graphics, system, x, bottom + 4, width, mouseX, mouseY);
            return;
        }
        if (discovery == null) {
            y = this.smallText(graphics, Component.translatable("gui.universe.star_map.surveyed"), x, y, width, DIM) + 3;
        } else if (discovery.discoverer() != null) {
            y = this.smallText(graphics, Component.translatable("gui.universe.star_map.discovered_by", discovery.discovererName()), x, y, width,
                    DIM) + 3;
        }
        if (!system.planets().isEmpty()) {
            // 空间站的经济和财富（ADR-0017）
            Economy economy = Economy.of(system);
            Component line = Component.translatable("gui.universe.star_map.economy",
                    Component.translatable("gui.universe.trade.economy." + economy.type().key()),
                    Component.translatable("gui.universe.trade.wealth." + economy.wealth().key()));
            int color = switch (economy.wealth()) {
                case POOR -> DIM;
                case AVERAGE -> TEXT;
                case WEALTHY -> 0xFFF2C94C;
            };
            y = this.smallText(graphics, line, x, y, width, color) + 3;
            // 主导种族（ADR-0027）
            Race race = Race.dominant(system);
            y = this.smallText(graphics, Component.translatable("gui.universe.star_map.civilization", Civ.raceName(race)), x, y, width,
                    ARGB.opaque(race.color())) + 3;
        }
        if (system.index() != this.holeSystem) {
            this.holeSystem = system.index();
            this.holeHere = star.starClass() != StarClass.BLACK_HOLE && BlackHoles.in(system).isPresent();
        }
        if (this.holeHere) {
            y = this.smallText(graphics, Component.translatable("gui.universe.star_map.black_hole"), x, y, width, WARN) + 3;
        }
        y = this.drawStrip(graphics, system, x, y, width, mouseX, mouseY) + 4;

        graphics.text(this.font, Component.translatable("gui.universe.star_map.planets", system.planets().size()), x, y, TEXT, false);
        y += 12;
        List<Planet> planets = system.planets();
        for (int i = 0; i < planets.size(); i++) {
            Planet planet = planets.get(i);
            int rowTop = y;
            y = this.drawPlanet(graphics, planet, x, y, width, false);
            for (Planet moon : planet.moons()) {
                y = this.drawPlanet(graphics, moon, x + 8, y, width - 8, true);
            }
            if (i == this.hoveredPlanet) {
                graphics.fill(x - 3, rowTop - 1, x - 2, y - 2, ACCENT);
            }
            y += 3;
        }
        graphics.disableScissor();
        this.panelContentHeight = y + (int) this.panelScroll - top;
        // 内容比面板长时画一条滚动条
        int visible = bottom - top;
        if (this.panelContentHeight > visible) {
            int barHeight = Math.max(12, visible * visible / this.panelContentHeight);
            int barTop = top + (int) ((visible - barHeight) * this.panelScroll / (this.panelContentHeight - visible));
            graphics.fill(this.width - 3, barTop, this.width - 1, barTop + barHeight, 0x804FB4D0);
        }
        this.drawWarp(graphics, system, x, bottom + 4, width, mouseX, mouseY);
    }

    /** 黑雾（ADR-0028 §八）：巢穴的等级和威胁、往外飞的中继站，飞来和飞走的种子。没去过的恒星系也看得到。 */
    private int drawDarkFog(GuiGraphicsExtractor graphics, StarSystem system, int x, int y, int width) {
        int start = y;
        Optional<DarkFogMapPayload.Hive> hive = ClientDarkFogMap.hive(system.index());
        if (hive.isPresent()) {
            Component line = hive.get().alive()
                    ? Component.translatable("gui.universe.star_map.dark_fog.hive", hive.get().level(), Math.round(hive.get().threat() * 100))
                    : Component.translatable("gui.universe.star_map.dark_fog.rebuilding");
            y = this.smallText(graphics, line, x, y, width, hive.get().alive() ? ARGB.opaque(DARK_FOG) : ARGB.color(0xFF, 0xA05060)) + 3;
            if (hive.get().relays() > 0) {
                y = this.smallText(graphics, Component.translatable("gui.universe.star_map.dark_fog.relays", hive.get().relays()), x, y, width, DIM)
                        + 3;
            }
        }
        for (DarkFogMapPayload.Seed seed : ClientDarkFogMap.seeds()) {
            StarSystem target = this.system(seed.to());
            if (seed.to() == system.index()) {
                int minutes = Math.max(1, (int) Math.ceil(ClientDarkFogMap.remaining(seed) / 1200.0));
                y = this.smallText(graphics, Component.translatable("gui.universe.star_map.dark_fog.seed_incoming", minutes), x, y, width, WARN)
                        + 3;
            } else if (seed.from() == system.index() && target != null) {
                y = this.smallText(graphics, Component.translatable("gui.universe.star_map.dark_fog.seed_outgoing", GalaxyText.system(target)), x, y,
                        width, DIM) + 3;
            }
        }
        return y == start ? y : y + 1;
    }

    /** 跃迁：能不能跳、为什么不能，以及按钮。 */
    private void drawWarp(GuiGraphicsExtractor graphics, StarSystem target, int x, int y, int width, int mouseX, int mouseY) {
        graphics.fill(x - 4, y - 3, x + width + 4, y - 2, 0x404FB4D0);
        Component status = this.warpStatus(target);
        double range = this.warpRange();
        if (range > 0) {
            graphics.text(this.font, Component.translatable("gui.universe.star_map.warp.range", (int) range), x, y, WARP, false);
        }
        this.smallText(graphics, status, x, y + 10, width, this.warpReady ? TEXT : (this.ship == null ? DIM : WARN));
        int buttonY = y + WARP_HEIGHT - BUTTON_HEIGHT - 4;
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT;
        int fill = !this.warpReady ? 0xFF1A1A22 : hover ? 0xFF5A3A8A : 0xFF3E2766;
        graphics.fill(x, buttonY, x + width, buttonY + BUTTON_HEIGHT, fill);
        graphics.outline(x, buttonY, width, BUTTON_HEIGHT, this.warpReady ? WARP : 0xFF3A3A48);
        graphics.centeredText(this.font, Component.translatable("gui.universe.star_map.warp"), x + width / 2, buttonY + 4,
                this.warpReady ? 0xFFF0E6FF : 0xFF60606C);
    }

    /** 跃迁按钮上面那行说明；顺便算出按钮能不能按。和服务端的检查一致。 */
    private Component warpStatus(StarSystem target) {
        this.warpReady = false;
        if (this.ship == null || this.ship.structure() == null) {
            return Component.translatable("message.universe.warp.not_piloting");
        }
        OptionalInt here = SpaceDimensions.system(this.ship.level().dimension());
        if (here.isEmpty()) {
            return Component.translatable("message.universe.warp.not_in_space");
        }
        int drives = this.ship.structure().stats().warpDrives();
        if (drives == 0) {
            return Component.translatable("message.universe.warp.no_drive");
        }
        if (target.index() == here.getAsInt()) {
            return Component.translatable("gui.universe.star_map.warp.here");
        }
        StarSystem from = this.galaxy.systems().get(here.getAsInt());
        double range = this.ship.structure().stats().warpRange();
        if (!Warp.inReach(from, target, range)) {
            return Component.translatable("message.universe.warp.out_of_range", GalaxyText.number(from.distanceTo(target), 1),
                    (int) range);
        }
        Component drive = ShipWarp.missingDrive(this.ship.structure().stats().drive(), target);
        if (drive != null) {
            return drive;
        }
        switch (Warp.check(this.currentBodies, this.ship.getX(), this.ship.getY(), this.ship.getZ())) {
            case NEAR_STAR -> {
                return Component.translatable("message.universe.warp.near_star");
            }
            case NEAR_BODY -> {
                Planet planet = Warp.blocker(this.currentBodies, this.ship.getX(), this.ship.getY(), this.ship.getZ())
                        .orElseThrow().planet();
                String name = GalaxyText.planet(planet);
                return Component.translatable("message.universe.warp.near_body", name, name);
            }
            case OK -> {
            }
        }
        LocalPlayer player = this.minecraft.player;
        if (player != null && player.hasInfiniteMaterials()) {
            this.warpReady = true;
            return Component.translatable("gui.universe.star_map.warp.ready_creative");
        }
        int warpers = 0;
        if (player != null) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(UniverseItems.SPACE_WARPER.get())) {
                    warpers += stack.getCount();
                }
            }
        }
        if (warpers == 0) {
            return Component.translatable("message.universe.warp.no_warper");
        }
        this.warpReady = true;
        return Component.translatable("gui.universe.star_map.warp.ready", warpers);
    }

    private List<Component> distanceLines(StarSystem system) {
        List<Component> lines = new ArrayList<>();
        if (this.current.isPresent() && this.current.getAsInt() == system.index()) {
            lines.add(Component.translatable("gui.universe.star_map.here"));
        } else if (this.current.isPresent()) {
            StarSystem from = this.galaxy.systems().get(this.current.getAsInt());
            lines.add(Component.translatable("gui.universe.star_map.distance", GalaxyText.number(from.distanceTo(system), 1)));
        }
        if (system.index() != GalaxyGenerator.HOME_SYSTEM && (this.current.isEmpty() || this.current.getAsInt() != GalaxyGenerator.HOME_SYSTEM)) {
            lines.add(Component.translatable("gui.universe.star_map.from_home", GalaxyText.number(this.galaxy.home().distanceTo(system), 1)));
        }
        return lines;
    }

    /** 恒星系的示意图：恒星在左，行星按轨道远近排开，大小按半径。 */
    private int drawStrip(GuiGraphicsExtractor graphics, StarSystem system, int x, int y, int width, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + STRIP_HEIGHT, 0x60000000);
        double cy = y + STRIP_HEIGHT / 2.0;
        int starColor = ARGB.opaque(system.star().color());
        if (system.star().starClass() == StarClass.BLACK_HOLE) {
            sprite(graphics, GLOW, x + 2, cy, 26, ARGB.color(0xC0, system.star().color()));
            sprite(graphics, DISC, x + 2, cy, 9, 0xFF000000);
        } else {
            sprite(graphics, GLOW, x + 2, cy, 30, starColor);
        }
        List<Planet> planets = system.planets();
        if (planets.isEmpty()) {
            return y + STRIP_HEIGHT;
        }
        double inner = Math.log(planets.getFirst().orbit().radius());
        double outer = Math.log(planets.getLast().orbit().radius());
        double from = x + 22;
        double to = x + width - 8;
        graphics.fill((int) from - 4, (int) cy, (int) to + 4, (int) cy + 1, 0x304FB4D0);
        for (int i = 0; i < planets.size(); i++) {
            Planet planet = planets.get(i);
            double t = outer - inner < 1e-6 ? 0.5 : (Math.log(planet.orbit().radius()) - inner) / (outer - inner);
            double px = planets.size() == 1 ? (from + to) / 2 : from + (to - from) * t;
            double size = Math.clamp(3 + planet.radius() * 2.2, 3, 12);
            if (Math.abs(mouseX - px) <= Math.max(4, size / 2 + 1) && mouseY >= y && mouseY < y + STRIP_HEIGHT) {
                this.hoveredPlanet = i;
                sprite(graphics, RING, px, cy, size + 6, ACCENT);
            }
            if (planet.rings()) {
                line(graphics, px - size * 0.9, cy + size * 0.15, px + size * 0.9, cy - size * 0.15, 0.7F, 0xA0D8C8A0);
            }
            sprite(graphics, DISC, px, cy, size, ARGB.opaque(planetColor(planet)));
            for (int m = 0; m < planet.moons().size(); m++) {
                sprite(graphics, DISC, px + (m - (planet.moons().size() - 1) / 2.0) * 3.5, cy + size / 2 + 4, 2, 0xFFB8B8B8);
            }
        }
        return y + STRIP_HEIGHT;
    }

    /** 一颗行星或卫星：名字、类型；温度、重力、大气；危害。 */
    private int drawPlanet(GuiGraphicsExtractor graphics, Planet planet, int x, int y, int width, boolean moon) {
        sprite(graphics, DISC, x + 3, y + 4, moon ? 4 : 6, ARGB.opaque(planetColor(planet)));
        String type = GalaxyText.planetType(planet);
        int typeWidth = this.font.width(type);
        String name = GalaxyText.planet(planet);
        int nameRoom = width - 10 - typeWidth - 4;
        String shown = this.font.plainSubstrByWidth(name, nameRoom);
        graphics.text(this.font, shown, x + 9, y, moon ? 0xFFC8D0D6 : TEXT, false);
        graphics.text(this.font, type, x + width - typeWidth, y, planet.landable() ? DIM : WARN, false);
        y += 10;
        Component details = planet.type() == PlanetType.GAS_GIANT
                ? Component.translatable("gui.universe.star_map.gas_giant", GalaxyText.number(planet.radius(), 1))
                : Component.translatable("gui.universe.star_map.details", Math.round(planet.temperatureCelsius()),
                        GalaxyText.number(planet.gravity(), 2),
                        GalaxyText.pick(planet.atmosphere().kind().nameZh(), planet.atmosphere().kind().nameEn()));
        y = this.smallText(graphics, details, x + 9, y, width - 9, DIM);
        if (!planet.hazards().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Hazard hazard : planet.hazards()) {
                names.add(GalaxyText.pick(hazard.nameZh(), hazard.nameEn()));
            }
            Component hazards = Component.translatable("gui.universe.star_map.hazards",
                    String.join(GalaxyText.pick("、", ", "), names));
            y = this.smallText(graphics, hazards, x + 9, y, width - 9, WARN);
        }
        if (this.showResources && !planet.resources().isEmpty()) {
            List<String> names = new ArrayList<>();
            planet.resources().stream().sorted(Comparator.comparingDouble(Resource.Deposit::abundance).reversed())
                    .forEach(deposit -> names.add(GalaxyText.pick(deposit.resource().nameZh(), deposit.resource().nameEn())));
            y = this.smallText(graphics, Component.translatable("gui.universe.star_map.resources",
                    String.join(GalaxyText.pick("、", ", "), names)), x + 9, y, width - 9, 0xFF9FD8A8);
        }
        if (planet.landable()) {
            SentinelLevel sentinels = ClientGalaxy.sentinels(planet);
            y = this.smallText(graphics, Component.translatable("gui.universe.star_map.sentinels",
                    GalaxyText.pick(sentinels.nameZh(), sentinels.nameEn())), x + 9, y, width - 9,
                    sentinels.ordinal() >= SentinelLevel.NORMAL.ordinal() ? WARN : DIM);
        }
        if (planet.landable() && ClientDiscoveries.known(DiscoveryKey.body(planet.id()))) {
            // 发现过的星球：先驱者传送门的地址（ADR-0024）
            Component label = Component.translatable("gui.universe.star_map.portal");
            int next = this.smallText(graphics, label, x + 9, y, width - 9, DIM);
            int glyphX = x + 9 + (int) Math.ceil(this.font.width(label) * 0.75F) + 3;
            int[] glyphs = new PortalAddress(planet.id().system(), planet.id().planet(), planet.id().moon()).glyphs(this.galaxy.seed());
            for (int i = 0; i < glyphs.length; i++) {
                PortalDialScreen.glyph(graphics, glyphs[i], glyphX + i * 9, y - 1, 8, 0xFFC9A8FF);
            }
            y = Math.max(next, y + 8);
        }
        return y + 1;
    }

    /** 宇宙探索（ADR-0022）：这个星系在星图上看得到多少。离最近的去过的星系有多远按选中的星系缓存。 */
    private Upgrades.Reveal reveal(StarSystem system, boolean explored) {
        int level = ClientResearch.state().level(Upgrades.UNIVERSE_EXPLORATION);
        if (explored || level < 2) {
            return Upgrades.reveal(level, explored, Double.POSITIVE_INFINITY);
        }
        if (this.nearestFor != system.index()) {
            double nearest = Double.POSITIVE_INFINITY;
            for (StarSystem other : this.galaxy.systems()) {
                if (ClientDiscoveries.entry(DiscoveryKey.system(other.index())).isPresent()) {
                    nearest = Math.min(nearest, system.distanceTo(other));
                }
            }
            this.nearestFor = system.index();
            this.nearestExplored = nearest;
        }
        return Upgrades.reveal(level, false, this.nearestExplored);
    }

    /** 缩小到 0.75 倍、自动换行的一段字，返回下一行的位置。 */
    private int smallText(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        float scale = 0.75F;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        int end = TextWrap.draw(graphics, this.font, text, 0, 0, (int) (width / scale), color, false);
        pose.popMatrix();
        return y + (int) Math.ceil(end * scale);
    }

    private static int planetColor(Planet planet) {
        return switch (BodyLook.of(planet).surface()) {
            case OCEAN -> planet.palette().liquid();
            case GAS -> planet.palette().accent();
            case ICE -> 0xDCEBF2;
            case LAVA -> 0x8A3A22;
            case ROCKY -> planet.palette().surface();
        };
    }

    // ---- 输入

    private int systemAt(double mouseX, double mouseY) {
        if (this.galaxy == null) {
            return -1;
        }
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < this.projected.length; i++) {
            StarMapCamera.Projected p = this.projected[i];
            if (p == null) {
                continue;
            }
            double reach = Math.max(6, starSize(this.galaxy.systems().get(i).star(), p) * 0.7);
            double d = Math.hypot(p.x() - mouseX, p.y() - mouseY);
            // 差不多一样近时取离相机近的那颗
            double score = d + p.depth() * 0.01;
            if (d <= reach && score < bestDistance) {
                best = i;
                bestDistance = score;
            }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        int buttonY = this.height - FOOTER - BUTTON_HEIGHT - 4;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.warpReady && x >= this.mapRight() + PADDING
                && x < this.width - PADDING && y >= buttonY && y < buttonY + BUTTON_HEIGHT) {
            ClientPacketDistributor.sendToServer(new WarpRequestPayload(this.selected));
            this.click();
            this.onClose();
            return true;
        }
        if (!this.inMap(x, y)) {
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int hit = this.systemAt(x, y);
            if (hit >= 0) {
                if (hit != this.selected) {
                    this.selected = hit;
                    this.panelScroll = 0;
                    this.click();
                }
                if (doubleClick) {
                    this.focusOn(hit);
                }
            } else {
                this.rotating = true;
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.panning = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.rotating) {
            this.camera.rotate(dx * 0.5, dy * 0.4);
            return true;
        }
        if (this.panning) {
            this.camera.pan(dx, dy, this.mapHeight());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.rotating = false;
        this.panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x >= this.mapRight()) {
            int visible = this.height - FOOTER - 4 - WARP_HEIGHT - (HEADER + 6);
            this.panelScroll = Math.clamp(this.panelScroll - scrollY * 12, 0, Math.max(0, this.panelContentHeight - visible));
        } else {
            this.camera.zoom(scrollY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (UniverseKeys.STAR_MAP.matches(event)) {
            this.onClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_H && this.current.isPresent()) {
            this.selected = this.current.getAsInt();
            this.panelScroll = 0;
            this.focusOn(this.selected);
            return true;
        }
        if (event.key() == InputConstants.KEY_F) {
            this.focusOn(this.selected);
            return true;
        }
        return super.keyPressed(event);
    }

    private void focusOn(int index) {
        StarSystem system = this.system(index);
        if (system != null) {
            this.camera.focus(system.x(), system.y(), system.z());
        }
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---- 小工具

    /** 任意方向的线：把一个 1×1 的方块转过去、拉长。 */
    public static void line(GuiGraphicsExtractor graphics, double x0, double y0, double x1, double y1, float width, int color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.05) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) x0, (float) y0);
        pose.rotate((float) Math.atan2(dy, dx));
        pose.translate(0, -width / 2);
        pose.scale((float) length, width);
        graphics.fill(0, 0, 1, 1, color);
        pose.popMatrix();
    }

    /** 以 (x, y) 为中心画一张白色贴图，乘上颜色。 */
    public static void sprite(GuiGraphicsExtractor graphics, Identifier texture, double x, double y, double size, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) (x - size / 2), (float) (y - size / 2));
        pose.scale((float) (size / TEXTURE));
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, TEXTURE, TEXTURE, TEXTURE, TEXTURE, color);
        pose.popMatrix();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double square(double v) {
        return v * v;
    }
}
