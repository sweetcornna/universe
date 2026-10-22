package io.github.sweetcornna.universe.client.exocraft;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.client.ship.AutopilotScreen;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.network.VehiclePayloads;
import io.github.sweetcornna.universe.network.VehiclePayloads.Vehicle;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 载具召唤台的界面（ADR-0026）：你放在世界里的车，一行一个（种类或名字、在哪、多远），同一个维度的在前。点了就叫到台子旁边。车多时分页。
 */
public class VehicleSummonerScreen extends Screen {
    private static final int WIDTH = 260;
    private static final int PAGE = 8;
    private static final int ACCENT = 0xFFFFB866;
    private final BlockPos summoner;
    private final List<Vehicle> vehicles;
    private int page;
    private int left;
    private int top;
    private int panelHeight;

    public VehicleSummonerScreen(VehiclePayloads.Listing payload) {
        super(Component.translatable("gui.universe.summoner.title"));
        this.summoner = payload.summoner();
        this.vehicles = payload.vehicles();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int pages() {
        return Math.max(1, (this.vehicles.size() + PAGE - 1) / PAGE);
    }

    @Override
    protected void init() {
        int rows = Math.max(1, Math.min(PAGE, this.vehicles.size()));
        this.panelHeight = 40 + rows * 24 + (this.pages() > 1 ? 26 : 4) + 8;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.page = Math.min(this.page, this.pages() - 1);
        int from = this.page * PAGE;
        for (int i = from; i < Math.min(this.vehicles.size(), from + PAGE); i++) {
            Vehicle vehicle = this.vehicles.get(i);
            this.addRenderableWidget(Button.builder(label(vehicle), b -> this.summon(vehicle))
                    .bounds(this.left + 12, this.top + 36 + (i - from) * 24, WIDTH - 24, 20)
                    .build());
        }
        if (this.pages() > 1) {
            int y = this.top + 36 + rows * 24 + 2;
            Button previous = Button.builder(Component.literal("<"), b -> this.turn(-1)).bounds(this.left + 12, y, 20, 20).build();
            Button next = Button.builder(Component.literal(">"), b -> this.turn(1)).bounds(this.left + WIDTH - 32, y, 20, 20).build();
            previous.active = this.page > 0;
            next.active = this.page < this.pages() - 1;
            this.addRenderableWidget(previous);
            this.addRenderableWidget(next);
        }
    }

    private void turn(int delta) {
        this.page = Math.max(0, Math.min(this.pages() - 1, this.page + delta));
        this.rebuildWidgets();
    }

    private void summon(Vehicle vehicle) {
        ClientPacketDistributor.sendToServer(new VehiclePayloads.Summon(this.summoner, vehicle.id()));
        this.onClose();
    }

    /** "探索车 · 地球 · 120 格"；起了名字的写名字；在别的维度不写距离。 */
    static Component label(Vehicle vehicle) {
        Component name = vehicle.name().isEmpty() ? Component.translatable("entity.universe." + vehicle.kind()) : Component.literal(vehicle.name());
        Component where = dimensionName(vehicle.dimension());
        if (vehicle.distance() < 0) {
            return Component.translatable("gui.universe.summoner.entry_far", name, where);
        }
        return Component.translatable("gui.universe.summoner.entry", name, where, AutopilotScreen.distance(vehicle.distance()));
    }

    /** 维度的名字：星系、星球照星图写，原版维度用原版的叫法（照 {@code StatsScreen} 的写法）。 */
    static Component dimensionName(String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        if (id == null) {
            return Component.literal(dimension);
        }
        if (id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            return Component.translatable("gui.universe.summoner.dimension." + id.getPath());
        }
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (galaxy.isEmpty()) {
            return Component.literal(id.getPath());
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        OptionalInt space = SpaceDimensions.system(key);
        if (space.isPresent()) {
            return galaxy.get().system(space.getAsInt())
                    .<Component>map(system -> Component.translatable("gui.universe.stats.space", GalaxyText.system(system)))
                    .orElse(Component.literal(id.getPath()));
        }
        Optional<BodyId> body = Planets.bodyId(key);
        if (body.isPresent()) {
            return galaxy.get().body(body.get()).<Component>map(planet -> Component.literal(GalaxyText.planet(planet)))
                    .orElse(Component.literal(id.getPath()));
        }
        return Component.literal(id.getPath());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + this.panelHeight + 1, 0xFF8A5A2E);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + this.panelHeight, 0xF00E0B08);
        graphics.text(this.font, this.title, this.left + 12, this.top + 10, ACCENT, false);
        graphics.text(this.font, Component.translatable("gui.universe.summoner.hint"), this.left + 12, this.top + 22, 0xFFAEA49A, false);
        if (this.vehicles.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.summoner.empty"), this.left + 12, this.top + 40, 0xFFAEA49A, false);
        }
        if (this.pages() > 1) {
            Component pageText = Component.literal((this.page + 1) + " / " + this.pages());
            int rows = Math.min(PAGE, this.vehicles.size());
            graphics.text(this.font, pageText, this.left + (WIDTH - this.font.width(pageText)) / 2, this.top + 36 + rows * 24 + 8,
                    0xFFAEA49A, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
