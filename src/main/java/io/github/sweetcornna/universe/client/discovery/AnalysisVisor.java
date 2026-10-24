package io.github.sweetcornna.universe.client.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import org.joml.Matrix3x2fStack;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.block.PlanetBloomBlock;
import io.github.sweetcornna.universe.block.PlanetLeavesBlock;
import io.github.sweetcornna.universe.block.PlanetLogBlock;
import io.github.sweetcornna.universe.block.PlanetPlantBlock;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.core.discovery.Survey;
import io.github.sweetcornna.universe.core.ecology.BodyPlan;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.Husbandry;
import io.github.sweetcornna.universe.core.ecology.PlantSpecies;
import io.github.sweetcornna.universe.core.ecology.TreeSpecies;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.AquaticCreature;
import io.github.sweetcornna.universe.entity.CreatureHusbandry;
import io.github.sweetcornna.universe.network.AnalyzePayload;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.tech.SuitTech;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.registry.sound.GearSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.Tags;

/**
 * 分析目镜（ADR-0012）：手持多功能工具按 V 打开，准星对准动物、植物、树、矿石或天上的天体，按住右键 1.2 秒分析，
 * 结果发给服务端记进发现日志。目镜开着时右键不开采矿激光。新发现时（包括降落、跃迁带来的）画面上方弹出提示。
 */
public final class AnalysisVisor {
    private static final int ANALYZE_TICKS = 24;
    private static final int COLOR = 0xFF5FE8FF;
    private static final int DIM = 0xFF8FA6AE;
    private static final TagKey<Block> ORES = Tags.BlockItems.ORES.block();
    private static boolean active;
    private static @Nullable Target target;
    private static int progress;
    private static int cooldown;
    private static long toggledAt;
    private static @Nullable Planet floraPlanet;
    private static @Nullable Flora flora;

    /**
     * 准星对着的东西。
     *
     * @param key    发现日志里对应的东西；认不出来时为 null
     * @param reward 第一个发现能拿几个勘探数据
     */
    private record Target(AnalyzePayload payload, @Nullable DiscoveryKey key, String name, Component detail, List<Component> extra, int reward) {
        Target(AnalyzePayload payload, @Nullable DiscoveryKey key, String name, Component detail, int reward) {
            this(payload, key, name, detail, List.of(), reward);
        }

    }

    private AnalysisVisor() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(Universe.id("visor"), AnalysisVisor::render));
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(InputEvent.InteractionKeyMappingTriggered.class, event -> {
            // 目镜开着时右键用来分析，不开采矿激光
            if (active && event.isUseItem()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            active = false;
            target = null;
        });
    }

    public static boolean active() {
        return active;
    }

    private static boolean holdingTool(LocalPlayer player) {
        return player.getMainHandItem().is(UniverseItems.MULTITOOL.get());
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            active = false;
            target = null;
            return;
        }
        while (UniverseKeys.ANALYZE.consumeClick()) {
            if (holdingTool(player)) {
                active = !active;
                toggledAt = Util.getMillis();
                player.playSound(active ? GearSounds.VISOR_OPEN.get() : GearSounds.VISOR_CLOSE.get(), 0.6F, 1.0F);
            } else {
                player.sendOverlayMessage(Component.translatable("hud.universe.visor.need_tool"));
            }
        }
        if (active && !holdingTool(player)) {
            active = false;
        }
        if (!active) {
            target = null;
            progress = 0;
            return;
        }
        Target found = find(minecraft, player);
        if (found == null || target == null || !found.payload().equals(target.payload())) {
            progress = 0;
        }
        target = found;
        if (cooldown > 0) {
            cooldown--;
        }
        boolean holding = minecraft.options.keyUse.isDown() && minecraft.gui.screen() == null;
        if (target != null && target.key() != null && holding && cooldown == 0) {
            progress++;
            int full = analyzeTicks(player);
            if (progress % 6 == 1) {
                player.playSound(GearSounds.VISOR_TICK.get(), 0.35F, 0.85F + 0.5F * progress / full);
            }
            if (progress >= full) {
                player.playSound(GearSounds.VISOR_COMPLETE.get(), 0.6F, 1.0F);
                ClientPacketDistributor.sendToServer(target.payload());
                progress = 0;
                cooldown = 20;
            }
        } else if (!holding) {
            progress = 0;
        }
    }

    /** 分析要多少刻：分析处理器模块缩短（ADR-0014）。 */
    private static int analyzeTicks(LocalPlayer player) {
        return Math.max(4, (int) Math.round(ANALYZE_TICKS * SuitTech.tool(player.getMainHandItem()).analysisTime()));
    }

    private static @Nullable Target find(Minecraft minecraft, LocalPlayer player) {
        Level level = minecraft.level;
        Galaxy galaxy = ClientGalaxy.galaxy().orElse(null);
        if (galaxy == null) {
            return null;
        }
        Optional<BodyId> body = Planets.bodyId(level.dimension());
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        BlockHitResult blocks = level.clip(new ClipContext(eye, eye.add(look.scale(Discoveries.ENTITY_RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double reach = blocks.getType() == HitResult.Type.MISS ? Discoveries.ENTITY_RANGE : blocks.getLocation().distanceTo(eye);
        Entity aimed = aimedCreature(level, player, eye, look, reach);
        if (aimed instanceof AlienCreature creature) {
            return creature(creature, body, galaxy);
        }
        if (aimed instanceof AquaticCreature creature) {
            return aquatic(creature, body, galaxy);
        }
        if (blocks.getType() == HitResult.Type.BLOCK && blocks.getLocation().distanceTo(eye) <= Discoveries.BLOCK_RANGE) {
            Target block = block(level, blocks.getBlockPos(), body, galaxy);
            if (block != null) {
                return block;
            }
        }
        if (blocks.getType() == HitResult.Type.MISS) {
            SkyTargets.Target sky = SkyTargets.aimedAt(new Vector3f((float) look.x, (float) look.y, (float) look.z));
            if (sky != null) {
                return sky(sky, galaxy);
            }
        }
        return null;
    }

    /**
     * 准星对着的动物（地上的和水里的）。碰撞箱放大一圈再算，远处的小动物也好瞄；动物站在方块边上时稍微越过方块一点也算。
     * 水挡不住视线，站在岸上也能分析水里的鱼。
     */
    private static @Nullable Entity aimedCreature(Level level, LocalPlayer player, Vec3 eye, Vec3 look, double reach) {
        double limit = Math.min(Discoveries.ENTITY_RANGE, reach + 0.8);
        Vec3 end = eye.add(look.scale(limit));
        AABB search = player.getBoundingBox().expandTowards(look.scale(limit)).inflate(2);
        Entity best = null;
        double bestDistance = limit;
        for (Entity candidate : level.getEntities((Entity) null, search,
                e -> e.isAlive() && (e instanceof AlienCreature || e instanceof AquaticCreature))) {
            AABB box = candidate.getBoundingBox().inflate(0.3 + eye.distanceTo(candidate.position()) * 0.01);
            Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
            if (hit.isPresent() && hit.get().distanceTo(eye) < bestDistance) {
                bestDistance = hit.get().distanceTo(eye);
                best = candidate;
            }
        }
        return best;
    }

    private static Target creature(AlienCreature creature, Optional<BodyId> here, Galaxy galaxy) {
        CreatureSpecies species = creature.species();
        // 驯服的动物按出生的星球记
        Optional<BodyId> body = creature.homeBody().or(() -> here);
        DiscoveryKey key = body.map(id -> DiscoveryKey.creature(id, species.index())).orElse(null);
        String name = key == null ? GalaxyText.name(species.name()) : ClientDiscoveries.name(key, species.name());
        Component detail = Component.translatable("hud.universe.visor.creature",
                Component.translatable("discovery.universe.plan." + species.plan().name().toLowerCase(Locale.ROOT)),
                Component.translatable("discovery.universe.temperament." + species.temperament().name().toLowerCase(Locale.ROOT)),
                GalaxyText.number(species.size(), 1));
        // 喂养：吃什么、产出什么、能不能骑；驯服的写上主人
        List<Component> extra = new ArrayList<>();
        extra.add(Component.translatable(Husbandry.rideable(species) ? "hud.universe.visor.husbandry_mount" : "hud.universe.visor.husbandry",
                CreatureHusbandry.dietName(Husbandry.diet(species.temperament())), CreatureHusbandry.product(species).getHoverName()));
        EntityReference<LivingEntity> owner = creature.getOwnerReference();
        if (owner != null) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner.getUUID());
            String ownerName = info == null ? "?" : info.getProfile().name();
            extra.add(Component.translatable(creature.isStaying() ? "hud.universe.visor.tamed_staying" : "hud.universe.visor.tamed", ownerName));
        }
        return new Target(AnalyzePayload.entity(creature.getId()), key, name, detail, extra, key == null ? 0 : Survey.reward(key, galaxy));
    }

    /** 水生动物（ADR-0026）：住在深水还是浅水、掉什么；水母写上会蜇人。 */
    private static Target aquatic(AquaticCreature creature, Optional<BodyId> here, Galaxy galaxy) {
        CreatureSpecies species = creature.species();
        DiscoveryKey key = here.map(id -> DiscoveryKey.creature(id, species.index())).orElse(null);
        String name = key == null ? GalaxyText.name(species.name()) : ClientDiscoveries.name(key, species.name());
        Component detail = Component.translatable("hud.universe.visor.creature",
                Component.translatable("discovery.universe.plan." + species.plan().name().toLowerCase(Locale.ROOT)),
                Component.translatable("discovery.universe.temperament." + species.temperament().name().toLowerCase(Locale.ROOT)),
                GalaxyText.number(species.size(), 1));
        List<Component> extra = new ArrayList<>();
        extra.add(Component.translatable("hud.universe.visor.aquatic",
                Component.translatable(Fauna.deepWater(species) ? "hud.universe.visor.deep_water" : "hud.universe.visor.shallow_water"),
                new ItemStack(UniverseItems.ALIEN_FISH.get()).getHoverName()));
        if (species.plan() == BodyPlan.JELLY) {
            extra.add(Component.translatable("hud.universe.visor.stings"));
        }
        return new Target(AnalyzePayload.entity(creature.getId()), key, name, detail, extra, key == null ? 0 : Survey.reward(key, galaxy));
    }

    private static @Nullable Target block(Level level, BlockPos pos, Optional<BodyId> body, Galaxy galaxy) {
        if (body.isEmpty()) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        AnalyzePayload payload = AnalyzePayload.block(pos.immutable());
        if (state.is(ORES)) {
            DiscoveryKey key = DiscoveryKey.mineral(body.get(), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            return new Target(payload, key, DiscoveryText.name(key), Component.translatable("discovery.universe.kind.mineral"),
                    Survey.reward(key, galaxy));
        }
        boolean plant = state.getBlock() instanceof PlanetPlantBlock;
        boolean tree = state.getBlock() instanceof PlanetLogBlock || state.getBlock() instanceof PlanetLeavesBlock
                || state.getBlock() instanceof PlanetBloomBlock;
        if (!plant && !tree) {
            return null;
        }
        Planet planet = galaxy.body(body.get()).orElse(null);
        if (planet == null) {
            return null;
        }
        if (planet != floraPlanet) {
            floraPlanet = planet;
            flora = Flora.of(planet);
        }
        int variant = state.getValue(FloraBlocks.VARIANT);
        if (plant) {
            PlanetPlantBlock block = (PlanetPlantBlock) state.getBlock();
            for (PlantSpecies species : flora.plants()) {
                if (species.form() == block.form() && species.variant() == variant) {
                    DiscoveryKey key = DiscoveryKey.plant(body.get(), species.index());
                    return new Target(payload, key, ClientDiscoveries.name(key, species.name()),
                            Component.literal(Component.translatable("discovery.universe.kind.plant").getString() + " · "
                                    + GalaxyText.pick(species.form().nameZh(), species.form().nameEn())),
                            Survey.reward(key, galaxy));
                }
            }
            return unidentified(payload);
        }
        if (variant >= flora.trees().size()) {
            return unidentified(payload);
        }
        TreeSpecies species = flora.trees().get(variant);
        DiscoveryKey key = DiscoveryKey.tree(body.get(), species.index());
        return new Target(payload, key, ClientDiscoveries.name(key, species.name()),
                Component.literal(Component.translatable("discovery.universe.kind.tree").getString() + " · "
                        + Component.translatable("discovery.universe.tree_shape." + species.shape().name().toLowerCase(Locale.ROOT)).getString()),
                Survey.reward(key, galaxy));
    }

    private static Target unidentified(AnalyzePayload payload) {
        return new Target(payload, null, Component.translatable("hud.universe.visor.unidentified").getString(), Component.empty(), 0);
    }

    private static @Nullable Target sky(SkyTargets.Target sky, Galaxy galaxy) {
        if (sky.planet() == null) {
            OptionalInt index = ClientGalaxy.currentSystem();
            StarSystem system = index.isPresent() ? galaxy.system(index.getAsInt()).orElse(null) : null;
            if (system == null) {
                return null;
            }
            DiscoveryKey key = DiscoveryKey.system(system.index());
            return new Target(AnalyzePayload.star(), key, ClientDiscoveries.name(key, system.name()),
                    Component.translatable("hud.universe.visor.star",
                            GalaxyText.pick(system.star().starClass().nameZh(), system.star().starClass().nameEn())),
                    Survey.reward(key, galaxy));
        }
        Planet planet = sky.planet();
        DiscoveryKey key = DiscoveryKey.body(planet.id());
        return new Target(AnalyzePayload.body(planet.id().path()), key, GalaxyText.planet(planet),
                Component.translatable("hud.universe.visor.body", GalaxyText.pick(planet.type().nameZh(), planet.type().nameEn()),
                        Math.round(planet.temperatureCelsius()), GalaxyText.number(planet.gravity(), 2)),
                Survey.reward(key, galaxy));
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.hud.isHidden() || minecraft.player == null) {
            return;
        }
        Font font = minecraft.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        long now = Util.getMillis();
        renderDiscovery(graphics, font, width, height, now);
        if (!active) {
            return;
        }
        int cx = width / 2;
        int cy = height / 2;
        float open = Math.min(1, (now - toggledAt) / 250.0F);
        // 目镜的边框：上下泛青，一条扫描线慢慢往下走
        int edge = ARGB.color((int) (90 * open), 0x0A3A48);
        graphics.fillGradient(0, 0, width, height / 5, edge, 0);
        graphics.fillGradient(0, height - height / 5, width, height, 0, edge);
        int scan = (int) ((now / 14) % height);
        graphics.fill(0, scan, width, scan + 1, ARGB.color(26, COLOR));

        graphics.centeredText(font, Component.translatable("hud.universe.visor.title"), cx, 8, COLOR);
        graphics.centeredText(font, Component.translatable("hud.universe.visor.hint"), cx, 19, DIM);

        Target shown = target;
        double pulse = 0.5 + 0.5 * Math.sin(now / 180.0);
        int size = shown == null ? 26 : (int) (14 + 2 * pulse);
        brackets(graphics, cx, cy, size, shown == null ? ARGB.color(150, COLOR) : COLOR);
        if (shown == null) {
            graphics.centeredText(font, Component.translatable("hud.universe.visor.no_target"), cx, cy + 34, DIM);
            return;
        }
        int x = cx + 24;
        int y = cy + 12;
        int panelWidth = Math.max(Math.max(font.width(shown.name()), font.width(shown.detail())), 150);
        for (Component line : shown.extra()) {
            panelWidth = Math.max(panelWidth, font.width(line));
        }
        panelWidth += 10;
        int lines = shown.extra().size() * 11;
        graphics.fill(x - 4, y - 4, x + panelWidth, y + 46 + lines, 0x90061418);
        graphics.fill(x - 4, y - 4, x - 3, y + 46 + lines, COLOR);
        graphics.text(font, shown.name(), x, y, 0xFFFFFFFF, true);
        graphics.text(font, shown.detail(), x, y + 11, DIM, false);
        for (Component line : shown.extra()) {
            y += 11;
            graphics.text(font, line, x, y + 11, 0xFFB8E6C8, false);
        }
        Component status;
        int statusColor;
        if (progress > 0) {
            status = Component.translatable("hud.universe.visor.analyzing");
            statusColor = COLOR;
        } else if (shown.key() == null) {
            status = Component.translatable("hud.universe.visor.unidentified");
            statusColor = 0xFFFFC14D;
        } else {
            Optional<DiscoveryLog.Entry> entry = ClientDiscoveries.entry(shown.key());
            if (entry.isPresent()) {
                status = entry.get().discoverer() == null ? Component.translatable("hud.universe.visor.known_charted")
                        : Component.translatable("hud.universe.visor.known", entry.get().discovererName());
                statusColor = 0xFF7FE08A;
            } else {
                status = Component.translatable("hud.universe.visor.new", shown.reward());
                statusColor = 0xFFFFD36B;
            }
        }
        graphics.text(font, status, x, y + 22, statusColor, false);
        if (progress > 0) {
            int bar = panelWidth - 6;
            graphics.fill(x, y + 35, x + bar, y + 38, 0x80000000);
            graphics.fill(x, y + 35, x + bar * Math.min(progress, analyzeTicks(minecraft.player)) / analyzeTicks(minecraft.player), y + 38, COLOR);
        }
    }

    /** 四个角的括号。 */
    private static void brackets(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        int l = Math.max(4, r / 2);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x = cx + sx * r;
                int y = cy + sy * r;
                graphics.fill(Math.min(x, x - sx * l), y, Math.max(x, x - sx * l) + 1, y + 1, color);
                graphics.fill(x, Math.min(y, y - sy * l), x + 1, Math.max(y, y - sy * l) + 1, color);
            }
        }
    }

    /** 新发现的提示：画面上方停几秒，慢慢淡出。 */
    private static void renderDiscovery(GuiGraphicsExtractor graphics, Font font, int width, int height, long now) {
        long age = now - ClientDiscoveries.lastRewardAt();
        DiscoveryKey key = ClientDiscoveries.lastKey();
        if (key == null || age > 4500 || ClientDiscoveries.lastReward() <= 0) {
            return;
        }
        float alpha = age < 300 ? age / 300.0F : age > 3500 ? 1 - (age - 3500) / 1000.0F : 1;
        int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
        if (a < 8) {
            return;
        }
        int cx = width / 2;
        int y = height / 5;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx, y);
        pose.scale(1.6F);
        graphics.centeredText(font, Component.translatable("hud.universe.visor.discovered"), 0, 0, ARGB.color(a, COLOR));
        pose.popMatrix();
        Component kind = Component.translatable("discovery.universe.kind." + key.kind().name().toLowerCase(Locale.ROOT));
        graphics.centeredText(font, Component.literal(kind.getString() + " · " + DiscoveryText.name(key)), cx, y + 18, ARGB.color(a, 0xFFFFFF));
        graphics.centeredText(font, Component.translatable("hud.universe.visor.reward", ClientDiscoveries.lastReward()), cx, y + 30,
                ARGB.color(a, 0xFFD36B));
    }
}
