package io.github.sweetcornna.universe.client.mecha;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BAD;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BORDER;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.GOOD;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.PANEL;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.TEXT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.WARN;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.core.mecha.LogisticsSlot;
import io.github.sweetcornna.universe.core.mecha.combat.MechaWeapon;
import io.github.sweetcornna.universe.core.mecha.combat.ShieldBurst;
import io.github.sweetcornna.universe.core.mecha.combat.WeaponMode;
import io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind;
import io.github.sweetcornna.universe.core.mecha.fleet.Squadrons;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.core.survival.Shield;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.mecha.MechaMenu;
import io.github.sweetcornna.universe.mecha.MechaSettings;
import io.github.sweetcornna.universe.mecha.combat.MechaCombat;
import io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem;
import io.github.sweetcornna.universe.mecha.fleet.MechaFleet;
import io.github.sweetcornna.universe.network.MechaPayloads;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.survival.SuitHud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 机甲面板（ADR-0023）：左边能量核心、燃烧室和机甲的各项数值，右边 12 个物流栏位和选中那格的上下限，下面背包。
 *
 * <p>物流栏位：拿着物品点一格设成这种物品，右键清掉，空手点一格选中它；上下限的 −/+ 一次 16，按住 Shift 一次 64，
 * 在数字上滚轮一次 1。
 *
 * <p>右上角三个页签（ADR-0033）："舰队"页左边是五个编队，右边是选中那个的格子和开关；"战斗"页是护盾、护盾爆发和三门武器。
 */
public class MechaScreen extends AbstractContainerScreen<MechaMenu> {
    private static final int LEFT_WIDTH = 112;
    private static final int BAR_Y = 31;
    private static final int REFUEL_X = 36;
    private static final int REFUEL_Y = 75;
    private static final int REFUEL_WIDTH = 82;
    private static final int STATS_Y = 90;
    private static final int RIGHT_X = MechaMenu.LOGISTICS_X;
    private static final int EDIT_Y = 72;
    private static final int MIN_Y = 84;
    private static final int MAX_Y = 100;
    private static final int MINUS_X = RIGHT_X + 34;
    private static final int VALUE_X = MINUS_X + 12;
    private static final int VALUE_WIDTH = 34;
    private static final int PLUS_X = VALUE_X + VALUE_WIDTH;
    private static final int SMALL = 11;
    private static final int CANCEL_Y = 130;
    private static final int CANCEL_WIDTH = 108;
    private static final int ENERGY = 0xFF5BE0B0;
    private static final int SHIELD = 0xFF7FB8FF;
    private static final int LOCKED = 0xFF151B20;
    // 页签
    private static final int TAB_X = 116;
    private static final int TAB_Y = 3;
    private static final int TAB_WIDTH = 42;
    private static final int TAB_HEIGHT = 12;
    // 舰队页
    private static final int ROW_Y = 31;
    private static final int ROW_HEIGHT = 17;
    private static final int ROW_WIDTH = 106;
    private static final int FLEET_STATS_Y = 54;
    private static final int DEPLOY_Y = 66;
    private static final int REPLENISH_Y = 82;
    private static final int BUILDINGS_Y = 98;
    private static final int AUTO_Y = 114;
    private static final int RIGHT_WIDTH = 108;
    // 战斗页
    private static final int WEAPON_Y = 31;
    private static final int WEAPON_HEIGHT = 32;
    private static final int MODE_X = RIGHT_X + 68;
    private static final int MODE_WIDTH = 40;
    /** 战斗页左边一栏的字写到这里为止，下面是背包的标题。 */
    private static final int BURST_BOTTOM = MechaMenu.INVENTORY_Y - 12;

    private int selected = -1;
    private int burstY = Integer.MAX_VALUE;

    public MechaScreen(MechaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MechaMenu.WIDTH, MechaMenu.HEIGHT);
        this.inventoryLabelX = MechaMenu.INVENTORY_X;
        this.inventoryLabelY = MechaMenu.INVENTORY_Y - 11;
    }

    private MechaSettings settings() {
        return this.minecraft.player.getData(UniverseAttachments.MECHA_SETTINGS);
    }

    private boolean unlocked() {
        return Research.isUnlocked(this.minecraft.level, MechaMenu.LOGISTICS_TECH);
    }

    private int level(String upgrade) {
        return Research.level(this.minecraft.level, upgrade);
    }

    private int page() {
        return this.menu.page();
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL);
        graphics.fill(x + 6, y + 16, x + this.imageWidth - 6, y + 17, 0x602E7A92);
        graphics.fill(x + LEFT_WIDTH + 10, y + 20, x + LEFT_WIDTH + 11, y + MechaMenu.INVENTORY_Y - 14, 0x402E7A92);
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                MachineScreen.slotFrame(graphics, x + slot.x, y + slot.y);
            }
        }
        switch (this.page()) {
            case MechaMenu.PAGE_FLEET -> this.fleetBackground(graphics, x, y);
            case MechaMenu.PAGE_COMBAT -> this.combatBackground(graphics, x, y);
            default -> this.coreBackground(graphics, x, y);
        }
    }

    private void coreBackground(GuiGraphicsExtractor graphics, int x, int y) {
        // 能量条
        long capacity = Math.max(1, this.menu.capacity());
        float share = Mth.clamp(this.menu.energy() / (float) capacity, 0, 1);
        graphics.fill(x + 8, y + BAR_Y, x + 8 + LEFT_WIDTH - 2, y + BAR_Y + 6, 0xFF1A2A30);
        graphics.fill(x + 8, y + BAR_Y, x + 8 + Math.round((LEFT_WIDTH - 2) * share), y + BAR_Y + 6, share < 0.15F ? BAD : ENERGY);
        graphics.outline(x + 7, y + BAR_Y - 1, LEFT_WIDTH, 8, 0xFF2E7A92);
        // 燃烧室的火：剩多少画多高
        int fx = x + MechaMenu.FUEL_X + 20;
        int fy = y + MechaMenu.FUEL_Y;
        graphics.fill(fx, fy, fx + 4, fy + 16, 0xFF1A2A30);
        int flame = Math.round(16 * this.menu.burning());
        if (flame > 0) {
            graphics.fill(fx, fy + 16 - flame, fx + 4, fy + 16, 0xFFFF9A3C);
        }
        // 物流栏位
        MechaSettings settings = this.settings();
        boolean unlocked = this.unlocked();
        for (int i = 0; i < MechaSettings.SLOTS; i++) {
            int sx = x + slotX(i);
            int sy = y + slotY(i);
            if (!unlocked) {
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF1C2A31);
                graphics.fill(sx, sy, sx + 16, sy + 16, LOCKED);
                continue;
            }
            MachineScreen.slotFrame(graphics, sx, sy);
            if (i == this.selected) {
                graphics.outline(sx - 1, sy - 1, 18, 18, ACCENT);
            }
            LogisticsSlot slot = settings.slot(i);
            Item item = item(slot);
            if (item != Items.AIR) {
                graphics.fakeItem(new ItemStack(item), sx, sy);
            }
        }
    }

    private void fleetBackground(GuiGraphicsExtractor graphics, int x, int y) {
        int squadron = this.menu.squadron();
        for (int s = 0; s < Squadrons.COUNT; s++) {
            int ry = y + rowY(s);
            graphics.fill(x + 8, ry, x + 8 + ROW_WIDTH, ry + ROW_HEIGHT - 2, s == squadron ? 0xFF1E3540 : 0xFF121E25);
            if (s == squadron) {
                graphics.outline(x + 8, ry, ROW_WIDTH, ROW_HEIGHT - 2, ACCENT);
            }
            if (this.menu.deployed(s)) {
                graphics.fill(x + 11, ry + 5, x + 15, ry + 9, GOOD);
            }
        }
        boolean open = this.menu.open(squadron);
        for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
            int sx = x + MechaMenu.FLEET_X + slot * 18;
            int sy = y + MechaMenu.FLEET_Y;
            if (!open) {
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF1C2A31);
                graphics.fill(sx, sy, sx + 16, sy + 16, LOCKED);
                continue;
            }
            // 被打掉、等着补员的那格：画一个暗的影子
            int index = Squadrons.flatIndex(squadron, slot);
            FleetUnitKind pending = this.menu.pending(index);
            if (pending != null && this.unit(index).isEmpty()) {
                graphics.fakeItem(new ItemStack(item(pending)), sx, sy);
                graphics.fill(sx, sy, sx + 16, sy + 16, 0xB0101A21);
            }
            if (this.menu.deployed(squadron)) {
                graphics.fill(sx, sy + 15, sx + 16, sy + 16, GOOD);
            }
        }
    }

    private void combatBackground(GuiGraphicsExtractor graphics, int x, int y) {
        SuitHud hud = this.minecraft.player.getData(UniverseAttachments.SUIT_HUD);
        float share = hud.shieldMax() <= 0 ? 0 : Mth.clamp(hud.shield() / hud.shieldMax(), 0, 1);
        graphics.fill(x + 8, y + BAR_Y, x + 8 + LEFT_WIDTH - 2, y + BAR_Y + 6, 0xFF1A2A30);
        graphics.fill(x + 8, y + BAR_Y, x + 8 + Math.round((LEFT_WIDTH - 2) * share), y + BAR_Y + 6, SHIELD);
        graphics.outline(x + 7, y + BAR_Y - 1, LEFT_WIDTH, 8, 0xFF2E7A92);
        for (MechaWeapon weapon : MechaWeapon.values()) {
            int wy = y + weaponY(weapon);
            graphics.fill(x + RIGHT_X - 2, wy - 3, x + RIGHT_X + RIGHT_WIDTH + 2, wy + WEAPON_HEIGHT - 5, 0xFF121E25);
        }
    }

    private static int slotX(int index) {
        return RIGHT_X + index % MechaMenu.LOGISTICS_COLUMNS * 18;
    }

    private static int slotY(int index) {
        return MechaMenu.LOGISTICS_Y + index / MechaMenu.LOGISTICS_COLUMNS * 18;
    }

    private static int rowY(int squadron) {
        return ROW_Y + squadron * ROW_HEIGHT;
    }

    private static int weaponY(MechaWeapon weapon) {
        return WEAPON_Y + weapon.ordinal() * WEAPON_HEIGHT;
    }

    private static Item item(LogisticsSlot slot) {
        if (slot.isEmpty()) {
            return Items.AIR;
        }
        Identifier id = Identifier.tryParse(slot.item());
        return id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
    }

    static Item item(FleetUnitKind kind) {
        return switch (kind) {
            case PROTOTYPE -> UniverseItems.PROTOTYPE.get();
            case PRECISION_DRONE -> UniverseItems.PRECISION_DRONE.get();
            case ATTACK_DRONE -> UniverseItems.ATTACK_DRONE.get();
            case CORVETTE -> UniverseItems.CORVETTE.get();
            case DESTROYER -> UniverseItems.DESTROYER.get();
        };
    }

    /** 编队格（按 {@link Squadrons#flatIndex}）里放着的。 */
    private ItemStack unit(int index) {
        return this.menu.getSlot(MechaMenu.fleetSlot(index)).getItem();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int mx = mouseX - this.leftPos;
        int my = mouseY - this.topPos;
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, ACCENT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, DIM, false);
        String[] tabs = {"core", "fleet", "combat"};
        for (int i = 0; i < MechaMenu.PAGES; i++) {
            this.tab(graphics, i, Component.translatable("gui.universe.mecha.tab." + tabs[i]), mx, my);
        }
        switch (this.page()) {
            case MechaMenu.PAGE_FLEET -> this.fleetLabels(graphics, mx, my);
            case MechaMenu.PAGE_COMBAT -> this.combatLabels(graphics, mx, my);
            default -> this.coreLabels(graphics, mx, my);
        }
    }

    private void tab(GuiGraphicsExtractor graphics, int page, Component label, int mx, int my) {
        int x = TAB_X + page * (TAB_WIDTH + 2);
        boolean current = this.page() == page;
        boolean hover = inside(mx, my, x, TAB_Y, TAB_WIDTH, TAB_HEIGHT);
        graphics.fill(x, TAB_Y, x + TAB_WIDTH, TAB_Y + TAB_HEIGHT, current ? 0xFF24404D : hover ? 0xFF1C323C : 0xFF121E25);
        graphics.outline(x, TAB_Y, TAB_WIDTH, TAB_HEIGHT, current ? ACCENT : BORDER);
        graphics.centeredText(this.font, label, x + TAB_WIDTH / 2, TAB_Y + 2, current ? ACCENT : TEXT);
    }

    private void coreLabels(GuiGraphicsExtractor graphics, int mx, int my) {
        boolean chest = Mecha.chest(this.minecraft.player);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.core"), 8, 20, TEXT, false);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.energy", Upgrades.energy(this.menu.energy()),
                Upgrades.energy(this.menu.capacity())), 8, 41, TEXT, false);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.flow", Upgrades.watts(this.menu.inflow()),
                Upgrades.watts(this.menu.outflow())), 8, 52, DIM, false);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.chamber", Upgrades.watts(this.menu.fuelPower())), REFUEL_X,
                MechaMenu.FUEL_Y, TEXT, false);
        boolean refuel = this.settings().autoRefuel();
        this.button(graphics, REFUEL_X, REFUEL_Y, REFUEL_WIDTH, Component.translatable(refuel ? "gui.universe.mecha.refuel.on"
                : "gui.universe.mecha.refuel.off"), mx, my, refuel ? GOOD : DIM);
        // 各项数值
        int y = STATS_Y;
        if (!chest) {
            graphics.text(this.font, Component.translatable("gui.universe.mecha.no_chest"), 8, y, BAD, false);
            y += 10;
        }
        int frame = (int) Math.round(Upgrades.frameBonus(this.level(Upgrades.MECHANICAL_FRAME)) * 100);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.speed", frame), 8, y, TEXT, false);
        y += 10;
        double flight = Upgrades.flightSpeed(this.level(Upgrades.DRIVE_ENGINE));
        graphics.text(this.font, flight > 0 ? Component.translatable("gui.universe.mecha.flight", Upgrades.effectArgs(Upgrades.DRIVE_ENGINE,
                this.level(Upgrades.DRIVE_ENGINE)).getFirst()) : Component.translatable("gui.universe.mecha.no_flight"), 8, y, flight > 0 ? TEXT : DIM,
                false);
        y += 10;
        int control = this.level(Upgrades.COMMUNICATION_CONTROL);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.drones", Upgrades.drones(control),
                Upgrades.droneSpeed(this.level(Upgrades.DRONE_ENGINE))), 8, y, TEXT, false);
        y += 10;
        graphics.text(this.font, Component.translatable("gui.universe.mecha.tasks", Upgrades.droneTasks(control)), 8, y, DIM, false);
        y += 10;
        SuitHud hud = this.minecraft.player.getData(UniverseAttachments.SUIT_HUD);
        graphics.text(this.font, hud.build() > 0 ? Component.translatable("gui.universe.mecha.sites", hud.build())
                : Component.translatable("gui.universe.mecha.no_sites"), 8, y, hud.build() > 0 ? ACCENT : DIM, false);
        y += 10;
        if (hud.missing() >= 0) {
            graphics.text(this.font, Component.translatable("gui.universe.mecha.missing", new ItemStack(BuiltInRegistries.ITEM.byId(hud.missing()))
                    .getHoverName()), 8, y, BAD, false);
        }
        // 右边：物流栏位
        graphics.text(this.font, Component.translatable("gui.universe.mecha.logistics"), RIGHT_X, 20, TEXT, false);
        if (!this.unlocked()) {
            this.wrapped(graphics, Component.translatable("gui.universe.mecha.logistics.locked"), RIGHT_X, EDIT_Y, DIM);
        } else {
            this.counts(graphics);
            this.editor(graphics, mx, my);
        }
        this.button(graphics, RIGHT_X, CANCEL_Y, CANCEL_WIDTH, Component.translatable("gui.universe.mecha.cancel"), mx, my,
                hud.build() > 0 ? TEXT : DIM);
    }

    private void fleetLabels(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.text(this.font, Component.translatable("gui.universe.squadron.squadrons"), 8, 20, TEXT, false);
        for (int s = 0; s < Squadrons.COUNT; s++) {
            int ry = rowY(s);
            boolean open = this.menu.open(s);
            int color = !open ? DIM : this.menu.deployed(s) ? GOOD : TEXT;
            graphics.text(this.font, MechaFleet.name(s), 18, ry + 4, color, false);
            String count = open ? this.count(s) + "/" + Squadrons.slots(s) : "—";
            graphics.text(this.font, count, 8 + ROW_WIDTH - 4 - this.font.width(count), ry + 4, open ? DIM : 0xFF4E626C, false);
        }
        this.wrapped(graphics, Component.translatable("gui.universe.squadron.key", CombatClient.FLEET.getTranslatedKeyMessage()), 8,
                rowY(Squadrons.COUNT) + 4, LEFT_WIDTH - 4, DIM);

        int squadron = this.menu.squadron();
        boolean open = this.menu.open(squadron);
        boolean out = this.menu.deployed(squadron);
        Component state = Component.translatable(!open ? "gui.universe.squadron.locked" : out ? "gui.universe.squadron.out" : "gui.universe.squadron.docked");
        graphics.text(this.font, MechaFleet.name(squadron), RIGHT_X, 20, TEXT, false);
        graphics.text(this.font, state, RIGHT_X + RIGHT_WIDTH - this.font.width(state), 20, !open ? DIM : out ? GOOD : DIM, false);
        if (!open) {
            this.wrapped(graphics, Component.translatable(lockHint(squadron)), RIGHT_X, FLEET_STATS_Y, RIGHT_WIDTH, DIM);
            return;
        }
        List<FleetUnitKind> units = this.units(squadron);
        if (units.isEmpty()) {
            this.wrapped(graphics, Component.translatable(Squadrons.space(squadron) ? "gui.universe.squadron.empty.space"
                    : "gui.universe.squadron.empty.ground"), RIGHT_X, FLEET_STATS_Y, RIGHT_WIDTH, DIM);
        } else {
            double dps = 0;
            for (FleetUnitKind kind : units) {
                dps += kind.dps();
            }
            graphics.text(this.font, Component.translatable("gui.universe.squadron.stats", Upgrades.watts(Squadrons.idleJoules(units)),
                    number(dps)), RIGHT_X, FLEET_STATS_Y, DIM, false);
        }
        this.button(graphics, RIGHT_X, DEPLOY_Y, RIGHT_WIDTH, Component.translatable(out ? "gui.universe.squadron.recall" : "gui.universe.squadron.deploy"),
                mx, my, out ? WARN : units.isEmpty() ? DIM : GOOD);
        this.toggle(graphics, REPLENISH_Y, "gui.universe.squadron.replenish", this.menu.replenish(squadron), mx, my);
        this.toggle(graphics, BUILDINGS_Y, "gui.universe.squadron.buildings", this.menu.buildings(squadron), mx, my);
        this.toggle(graphics, AUTO_Y, "gui.universe.squadron.auto_deploy", this.menu.autoDeploy(squadron), mx, my);
        if (out) {
            graphics.text(this.font, Component.translatable("gui.universe.squadron.busy"), RIGHT_X, CANCEL_Y + 3, DIM, false);
        }
    }

    private static String lockHint(int squadron) {
        if (Squadrons.space(squadron)) {
            return Squadrons.ordinal(squadron) == 0 ? "gui.universe.squadron.locked.space" : "gui.universe.squadron.locked.more_space";
        }
        return Squadrons.ordinal(squadron) == 0 ? "gui.universe.squadron.locked.ground" : "gui.universe.squadron.locked.more_ground";
    }

    private void toggle(GuiGraphicsExtractor graphics, int y, String key, boolean on, int mx, int my) {
        this.button(graphics, RIGHT_X, y, RIGHT_WIDTH, Component.translatable(key + (on ? ".on" : ".off")), mx, my, on ? GOOD : DIM);
    }

    private int count(int squadron) {
        int count = 0;
        for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
            if (!this.unit(Squadrons.flatIndex(squadron, slot)).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private List<FleetUnitKind> units(int squadron) {
        List<FleetUnitKind> units = new ArrayList<>();
        for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
            FleetUnitKind kind = FleetUnitItem.kindOf(this.unit(Squadrons.flatIndex(squadron, slot)));
            if (kind != null) {
                units.add(kind);
            }
        }
        return units;
    }

    private void combatLabels(GuiGraphicsExtractor graphics, int mx, int my) {
        SuitHud hud = this.minecraft.player.getData(UniverseAttachments.SUIT_HUD);
        boolean chest = Mecha.chest(this.minecraft.player);
        graphics.text(this.font, Component.translatable("gui.universe.combat.shield"), 8, 20, TEXT, false);
        graphics.text(this.font, Component.translatable("gui.universe.combat.shield_value", number(hud.shield()), number(hud.shieldMax())), 8, 41,
                TEXT, false);
        float bonus = MechaCombat.shieldBonus(this.minecraft.player);
        int y = 52;
        if (bonus > 0) {
            graphics.text(this.font, Component.translatable("gui.universe.combat.shield_bonus", number(bonus)), 8, y, DIM, false);
            y += 10;
        }
        // 核心回充的说明在护盾这一块的悬停提示里
        y += 6;
        this.burstY = y;
        graphics.text(this.font, Component.translatable("gui.universe.combat.burst"), 8, y, TEXT, false);
        y += 11;
        // 写不下的（英文长）截掉，悬停这一块看全文
        for (Component line : this.burstLines()) {
            for (var part : this.font.split(line, LEFT_WIDTH - 4)) {
                if (y + 9 > BURST_BOTTOM) {
                    break;
                }
                graphics.text(this.font, part, 8, y, DIM, false);
                y += 10;
            }
        }

        graphics.text(this.font, Component.translatable("gui.universe.combat.weapons"), RIGHT_X, 20, TEXT, false);
        MechaSettings settings = this.settings();
        for (MechaWeapon weapon : MechaWeapon.values()) {
            int wy = weaponY(weapon);
            WeaponMode mode = settings.mode(weapon);
            String name = Component.translatable("gui.universe.combat.weapon." + weapon.id()).getString();
            graphics.text(this.font, this.font.plainSubstrByWidth(name, MODE_X - RIGHT_X - 3), RIGHT_X, wy, TEXT, false);
            this.button(graphics, MODE_X, wy - 3, MODE_WIDTH, Component.translatable("gui.universe.combat.mode." + mode.name().toLowerCase(Locale.ROOT)),
                    mx, my, mode == WeaponMode.OFF ? DIM : mode == WeaponMode.AIM ? ACCENT : GOOD);
            int[] ammo = this.menu.ammo(weapon);
            boolean none = ammo[0] <= 0 && ammo[1] <= 0;
            graphics.text(this.font, none ? Component.translatable("gui.universe.combat.no_ammo")
                    : Component.translatable("gui.universe.combat.ammo", ammo[0], ammo[1]), RIGHT_X, wy + 11, none ? BAD : DIM, false);
            graphics.text(this.font, Component.translatable("gui.universe.combat.stats", number(weapon.range()), number(20.0 / weapon.interval())),
                    RIGHT_X, wy + 20, 0xFF6F8794, false);
        }
        graphics.text(this.font, Component.translatable(chest ? "gui.universe.combat.sneak" : "gui.universe.mecha.no_chest"), RIGHT_X,
                CANCEL_Y + 4, chest ? DIM : BAD, false);
    }

    static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** 每格右下角写身上有几个：不够下限红色，超过上限黄色。 */
    private void counts(GuiGraphicsExtractor graphics) {
        MechaSettings settings = this.settings();
        for (int i = 0; i < MechaSettings.SLOTS; i++) {
            LogisticsSlot slot = settings.slot(i);
            Item item = item(slot);
            if (item == Items.AIR) {
                continue;
            }
            int count = this.carried(item);
            String text = count > 999 ? "999+" : Integer.toString(count);
            int color = count < slot.min() ? 0xFFFF7A6A : count > slot.max() ? 0xFFFFD45A : 0xFFFFFFFF;
            graphics.pose().pushMatrix();
            graphics.pose().translate(slotX(i) + 17, slotY(i) + 11);
            graphics.pose().scale(0.6F, 0.6F);
            graphics.text(this.font, text, -this.font.width(text), 0, color, true);
            graphics.pose().popMatrix();
        }
    }

    private void editor(GuiGraphicsExtractor graphics, int mx, int my) {
        LogisticsSlot slot = this.selected >= 0 ? this.settings().slot(this.selected) : LogisticsSlot.EMPTY;
        Item item = item(slot);
        if (item == Items.AIR) {
            this.wrapped(graphics, Component.translatable("gui.universe.mecha.logistics.hint"), RIGHT_X, EDIT_Y, DIM);
            return;
        }
        graphics.text(this.font, this.font.plainSubstrByWidth(new ItemStack(item).getHoverName().getString(), CANCEL_WIDTH), RIGHT_X, EDIT_Y,
                TEXT, false);
        this.bound(graphics, MIN_Y, Component.translatable("gui.universe.mecha.min"), slot.min(), mx, my);
        this.bound(graphics, MAX_Y, Component.translatable("gui.universe.mecha.max"), slot.max(), mx, my);
    }

    private void bound(GuiGraphicsExtractor graphics, int y, Component label, int value, int mx, int my) {
        graphics.text(this.font, label, RIGHT_X, y + 3, DIM, false);
        this.button(graphics, MINUS_X, y, SMALL, Component.literal("-"), mx, my, TEXT);
        graphics.fill(VALUE_X, y, VALUE_X + VALUE_WIDTH, y + 14, 0xFF0C1418);
        graphics.centeredText(this.font, Component.literal(Integer.toString(value)), VALUE_X + VALUE_WIDTH / 2, y + 3, TEXT);
        this.button(graphics, PLUS_X, y, SMALL, Component.literal("+"), mx, my, TEXT);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, int mx, int my, int color) {
        boolean hover = inside(mx, my, x, y, width, 14);
        graphics.fill(x, y, x + width, y + 14, hover ? 0xFF24404D : 0xFF16262E);
        graphics.outline(x, y, width, 14, BORDER);
        graphics.centeredText(this.font, label, x + width / 2 + 1, y + 3, color);
    }

    private void wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        this.wrapped(graphics, text, x, y, CANCEL_WIDTH, color);
    }

    /** 折行写一段字，返回下一行的 y。 */
    private int wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        for (var line : this.font.split(text, width)) {
            graphics.text(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private int carried(Item item) {
        int count = 0;
        Inventory inventory = this.minecraft.player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // ---- 提示

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int x = mouseX - this.leftPos;
        int y = mouseY - this.topPos;
        List<Component> lines = switch (this.page()) {
            case MechaMenu.PAGE_FLEET -> this.fleetTooltip(x, y);
            case MechaMenu.PAGE_COMBAT -> this.combatTooltip(x, y);
            default -> this.tooltip(x, y);
        };
        if (!lines.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    private List<Component> tooltip(int x, int y) {
        List<Component> lines = new ArrayList<>();
        if (inside(x, y, REFUEL_X, REFUEL_Y, REFUEL_WIDTH, 14)) {
            lines.add(Component.translatable("gui.universe.mecha.refuel.hint").withStyle(ChatFormatting.GRAY));
        } else if (inside(x, y, RIGHT_X, CANCEL_Y, CANCEL_WIDTH, 14)) {
            lines.add(Component.translatable("gui.universe.mecha.cancel.hint").withStyle(ChatFormatting.GRAY));
        } else if (inside(x, y, 7, BAR_Y - 1, LEFT_WIDTH, 8)) {
            lines.add(Component.translatable("gui.universe.mecha.core.hint").withStyle(ChatFormatting.GRAY));
        } else if (this.unlocked()) {
            int slot = this.slotAt(x, y);
            if (slot >= 0) {
                LogisticsSlot setting = this.settings().slot(slot);
                Item item = item(setting);
                if (item != Items.AIR) {
                    lines.add(new ItemStack(item).getHoverName());
                    lines.add(Component.translatable("gui.universe.mecha.carried", this.carried(item)).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.universe.mecha.bounds", setting.min(), setting.max()).withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("gui.universe.mecha.logistics.hint").withStyle(ChatFormatting.DARK_GRAY));
            } else if (this.selected >= 0 && (inside(x, y, MINUS_X, MIN_Y, PLUS_X + SMALL - MINUS_X, 14)
                    || inside(x, y, MINUS_X, MAX_Y, PLUS_X + SMALL - MINUS_X, 14))) {
                lines.add(Component.translatable("gui.universe.mecha.limit_hint").withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    private List<Component> fleetTooltip(int x, int y) {
        List<Component> lines = new ArrayList<>();
        int squadron = this.menu.squadron();
        if (!this.menu.open(squadron)) {
            return lines;
        }
        if (inside(x, y, RIGHT_X, REPLENISH_Y, RIGHT_WIDTH, 14)) {
            lines.add(Component.translatable("gui.universe.squadron.replenish.hint").withStyle(ChatFormatting.GRAY));
        } else if (inside(x, y, RIGHT_X, BUILDINGS_Y, RIGHT_WIDTH, 14)) {
            lines.add(Component.translatable("gui.universe.squadron.buildings.hint").withStyle(ChatFormatting.GRAY));
        } else if (inside(x, y, RIGHT_X, AUTO_Y, RIGHT_WIDTH, 14)) {
            lines.add(Component.translatable("gui.universe.squadron.auto_deploy.hint").withStyle(ChatFormatting.GRAY));
        } else if (inside(x, y, RIGHT_X, DEPLOY_Y, RIGHT_WIDTH, 14)) {
            lines.add(Component.translatable(Squadrons.space(squadron) ? "gui.universe.squadron.deploy.hint_space" : "gui.universe.squadron.deploy.hint_ground")
                    .withStyle(ChatFormatting.GRAY));
        } else if (this.hoveredSlot == null) {
            // 等着补员的空格
            for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
                int index = Squadrons.flatIndex(squadron, slot);
                FleetUnitKind pending = this.menu.pending(index);
                if (inside(x, y, MechaMenu.FLEET_X + slot * 18 - 1, MechaMenu.FLEET_Y - 1, 18, 18) && pending != null && this.unit(index).isEmpty()) {
                    lines.add(Component.translatable("gui.universe.squadron.pending", new ItemStack(item(pending)).getHoverName())
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
        return lines;
    }

    private List<Component> combatTooltip(int x, int y) {
        List<Component> lines = new ArrayList<>();
        for (MechaWeapon weapon : MechaWeapon.values()) {
            int wy = weaponY(weapon);
            if (inside(x, y, MODE_X, wy - 3, MODE_WIDTH, 14)) {
                lines.add(Component.translatable("gui.universe.combat.mode.hint.auto").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.universe.combat.mode.hint.aim").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.universe.combat.mode.hint.off").withStyle(ChatFormatting.GRAY));
            } else if (inside(x, y, RIGHT_X - 2, wy - 3, MODE_X - RIGHT_X - 2, WEAPON_HEIGHT - 2)) {
                lines.add(Component.translatable("gui.universe.combat.weapon." + weapon.id()));
                lines.add(Component.translatable("gui.universe.combat.uses." + weapon.id()).withStyle(ChatFormatting.GRAY));
                for (AmmoKind ammo : AmmoKind.values()) {
                    if (weapon.accepts(ammo)) {
                        Item item = BuiltInRegistries.ITEM.getValue(io.github.sweetcornna.universe.Universe.id(ammo.id()));
                        lines.add(Component.translatable("gui.universe.combat.ammo_damage", new ItemStack(item).getHoverName(),
                                number(weapon.damage(ammo))).withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
                lines.add(Component.translatable("gui.universe.combat.shot_energy", Upgrades.energy(MechaWeapon.SHOT_JOULES))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (inside(x, y, 7, BAR_Y - 1, LEFT_WIDTH, this.burstY - BAR_Y - 4)) {
            lines.add(Component.translatable("gui.universe.combat.shield.hint").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.universe.combat.recharge", number(Shield.CORE_JOULES_PER_POINT),
                    number(Shield.CORE_POINTS_PER_SECOND)).withStyle(ChatFormatting.GRAY));
        }
        if (inside(x, y, 7, this.burstY - 1, LEFT_WIDTH, BURST_BOTTOM - this.burstY + 1)) {
            lines.add(Component.translatable("gui.universe.combat.burst"));
            for (Component line : this.burstLines()) {
                lines.add(line.copy().withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    /** 护盾爆发的说明，一句一行。 */
    private List<Component> burstLines() {
        return List.of(Component.translatable("gui.universe.combat.burst_hint", CombatClient.SHIELD_BURST.getTranslatedKeyMessage()),
                Component.translatable("gui.universe.combat.burst_cost", ShieldBurst.MAX_CHARGE / 20),
                Component.translatable("gui.universe.combat.burst_damage", number(ShieldBurst.DAMAGE_PER_POINT), number(ShieldBurst.RADIUS)),
                Component.translatable("gui.universe.combat.burst_cooldown", ShieldBurst.COOLDOWN / 20));
    }

    private int slotAt(int x, int y) {
        for (int i = 0; i < MechaSettings.SLOTS; i++) {
            if (inside(x, y, slotX(i) - 1, slotY(i) - 1, 18, 18)) {
                return i;
            }
        }
        return -1;
    }

    // ---- 点击

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = (int) Math.floor(event.x() - this.leftPos);
        int y = (int) Math.floor(event.y() - this.topPos);
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        if (left) {
            for (int page = 0; page < MechaMenu.PAGES; page++) {
                if (inside(x, y, TAB_X + page * (TAB_WIDTH + 2), TAB_Y, TAB_WIDTH, TAB_HEIGHT)) {
                    if (page != this.page()) {
                        this.menu.setPage(page);
                        this.selected = -1;
                        return this.press(MechaMenu.PAGE + page);
                    }
                    return true;
                }
            }
        }
        return switch (this.page()) {
            case MechaMenu.PAGE_FLEET -> this.fleetClicked(x, y, left) || super.mouseClicked(event, doubleClick);
            case MechaMenu.PAGE_COMBAT -> this.combatClicked(x, y, left) || super.mouseClicked(event, doubleClick);
            default -> this.coreClicked(event, doubleClick, x, y);
        };
    }

    private boolean fleetClicked(int x, int y, boolean left) {
        if (!left) {
            return false;
        }
        for (int s = 0; s < Squadrons.COUNT; s++) {
            if (inside(x, y, 8, rowY(s), ROW_WIDTH, ROW_HEIGHT - 2)) {
                this.menu.setSquadron(s);
                return this.press(MechaMenu.SELECT + s);
            }
        }
        int squadron = this.menu.squadron();
        if (!this.menu.open(squadron)) {
            return false;
        }
        if (inside(x, y, RIGHT_X, DEPLOY_Y, RIGHT_WIDTH, 14)) {
            return this.press(MechaMenu.DEPLOY + squadron);
        }
        if (inside(x, y, RIGHT_X, REPLENISH_Y, RIGHT_WIDTH, 14)) {
            return this.press(MechaMenu.REPLENISH + squadron);
        }
        if (inside(x, y, RIGHT_X, BUILDINGS_Y, RIGHT_WIDTH, 14)) {
            return this.press(MechaMenu.BUILDINGS + squadron);
        }
        if (inside(x, y, RIGHT_X, AUTO_Y, RIGHT_WIDTH, 14)) {
            return this.press(MechaMenu.AUTO_DEPLOY + squadron);
        }
        return false;
    }

    private boolean combatClicked(int x, int y, boolean left) {
        if (!left) {
            return false;
        }
        for (MechaWeapon weapon : MechaWeapon.values()) {
            if (inside(x, y, MODE_X, weaponY(weapon) - 3, MODE_WIDTH, 14)) {
                return this.press(MechaMenu.WEAPON_MODE + weapon.ordinal());
            }
        }
        return false;
    }

    private boolean coreClicked(MouseButtonEvent event, boolean doubleClick, int x, int y) {
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (left && inside(x, y, REFUEL_X, REFUEL_Y, REFUEL_WIDTH, 14)) {
            return this.press(MechaMenu.TOGGLE_REFUEL);
        }
        if (left && inside(x, y, RIGHT_X, CANCEL_Y, CANCEL_WIDTH, 14)) {
            return this.press(MechaMenu.CANCEL_BUILD);
        }
        if (this.unlocked()) {
            int slot = this.slotAt(x, y);
            if (slot >= 0) {
                if (right) {
                    if (this.selected == slot) {
                        this.selected = -1;
                    }
                    return this.press(MechaMenu.CLEAR_SLOT + slot);
                }
                if (left) {
                    if (this.menu.getCarried().isEmpty()) {
                        this.selected = slot;
                        this.click();
                        return true;
                    }
                    this.selected = slot;
                    return this.press(MechaMenu.SET_SLOT + slot);
                }
            }
            if (left && this.selected >= 0) {
                int step = event.hasShiftDown() ? 64 : 16;
                if (inside(x, y, MINUS_X, MIN_Y, SMALL, 14)) {
                    return this.adjust(-step, 0);
                }
                if (inside(x, y, PLUS_X, MIN_Y, SMALL, 14)) {
                    return this.adjust(step, 0);
                }
                if (inside(x, y, MINUS_X, MAX_Y, SMALL, 14)) {
                    return this.adjust(0, -step);
                }
                if (inside(x, y, PLUS_X, MAX_Y, SMALL, 14)) {
                    return this.adjust(0, step);
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = (int) Math.floor(mouseX - this.leftPos);
        int y = (int) Math.floor(mouseY - this.topPos);
        if (this.page() == MechaMenu.PAGE_CORE && this.selected >= 0 && this.unlocked() && scrollY != 0) {
            int step = scrollY > 0 ? 1 : -1;
            if (inside(x, y, VALUE_X, MIN_Y, VALUE_WIDTH, 14)) {
                return this.adjust(step, 0);
            }
            if (inside(x, y, VALUE_X, MAX_Y, VALUE_WIDTH, 14)) {
                return this.adjust(0, step);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** 改选中那格的上下限：下限调高时上限跟着，上限调低时下限跟着。 */
    private boolean adjust(int minStep, int maxStep) {
        LogisticsSlot slot = this.settings().slot(this.selected);
        if (slot.isEmpty()) {
            return false;
        }
        int min = Math.clamp(slot.min() + minStep, 0, LogisticsSlot.LIMIT);
        int max = Math.clamp(slot.max() + maxStep, 0, LogisticsSlot.LIMIT);
        if (minStep != 0 && min > max) {
            max = min;
        }
        if (maxStep != 0 && max < min) {
            min = max;
        }
        ClientPacketDistributor.sendToServer(new MechaPayloads.Bounds(this.selected, min, max));
        this.click();
        return true;
    }

    private boolean press(int button) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
        this.click();
        return true;
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
