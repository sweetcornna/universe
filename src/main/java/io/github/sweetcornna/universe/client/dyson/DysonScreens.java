package io.github.sweetcornna.universe.client.dyson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.factory.ClientResearch;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.RayReceiver;
import io.github.sweetcornna.universe.core.dyson.SwarmOrbit;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.dyson.EmRailEjectorBlockEntity;
import io.github.sweetcornna.universe.dyson.LaunchSiloBlockEntity;
import io.github.sweetcornna.universe.dyson.RayReceiverBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.network.DysonSyncPayload;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 戴森球设备的界面（ADR-0016）：电磁轨道弹射器、垂直发射井、射线接收站。左边读数，右边格子，格子下面一个按钮打开
 * 戴森球编辑器；在这些界面里按 Y 也能打开。
 */
public final class DysonScreens {
    /** 左边读数的宽度，右边从 152 起是格子和按钮。 */
    private static final int TEXT_WIDTH = 136;
    private static final int EDITOR_X = 152;
    /** 编辑器按钮放在标题那一行的右边，不和下面的状态文字抢地方。 */
    private static final int EDITOR_Y = 3;
    private static final int EDITOR_WIDTH = 42;
    private static final int BUTTON_HEIGHT = 12;

    private DysonScreens() {
    }

    private static Optional<DysonSphere> sphere() {
        return ClientDyson.currentSystem().isPresent() ? ClientDyson.sphere(ClientDyson.currentSystem().getAsInt()) : Optional.empty();
    }

    /** 共同的部分：空格子里画出该放的东西、按钮、Y 键。 */
    abstract static class DysonMachineScreen extends MachineScreen {
        private final List<Button> buttons = new ArrayList<>();

        private record Button(int x, int y, int width, Runnable action) {
            boolean contains(double localX, double localY) {
                return localX >= this.x && localX < this.x + this.width && localY >= this.y && localY < this.y + BUTTON_HEIGHT;
            }
        }

        DysonMachineScreen(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        /** 格子 slot 该放的东西；没有就是空。 */
        protected abstract ItemStack ghost(int slot);

        /** 空格子的提示多一行说明；没有就是空。 */
        protected Optional<Component> slotHint(int slot) {
            return Optional.empty();
        }

        protected abstract void info(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            super.extractBackground(graphics, mouseX, mouseY, a);
            List<Slot> ghosts = new ArrayList<>();
            for (int i = 0; i < this.menu.type().slots().size(); i++) {
                Slot slot = this.menu.getSlot(i);
                ItemStack ghost = this.ghost(i);
                if (!slot.hasItem() && !ghost.isEmpty()) {
                    graphics.fakeItem(ghost, this.leftPos + slot.x, this.topPos + slot.y);
                    ghosts.add(slot);
                }
            }
            graphics.nextStratum();
            for (Slot slot : ghosts) {
                int x = this.leftPos + slot.x;
                int y = this.topPos + slot.y;
                graphics.fill(x, y, x + 16, y + 16, 0x900A1015);
            }
        }

        @Override
        protected final void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.buttons.clear();
            this.info(graphics, mouseX, mouseY);
            this.button(graphics, Component.translatable("gui.universe.dyson.open_short"), EDITOR_X, EDITOR_Y, EDITOR_WIDTH, mouseX, mouseY,
                    this::openEditor);
        }

        /** 一个按钮，坐标相对界面左上角。 */
        protected void button(GuiGraphicsExtractor graphics, Component label, int x, int y, int width, int mouseX, int mouseY, Runnable action) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
            graphics.fill(x, y, x + width, y + BUTTON_HEIGHT, BORDER);
            graphics.fill(x + 1, y + 1, x + width - 1, y + BUTTON_HEIGHT - 1, hovered ? 0xFF1F4A5A : 0xFF12303B);
            graphics.centeredText(this.font, label, x + width / 2, y + 2, TEXT);
            this.buttons.add(new Button(x, y, width, action));
        }

        /** 让服务端的设备处理第 index 个按钮。 */
        protected void press(int index) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, index);
            }
        }

        /** 状态一行（可能折成两行），返回占了几行。 */
        protected int status(GuiGraphicsExtractor graphics, Component text, int y, int color) {
            TextWrap.draw(graphics, this.font, text, 8, y, TEXT_WIDTH, color, false);
            return TextWrap.split(this.font, text, TEXT_WIDTH).size();
        }

        private void openEditor() {
            this.onClose();
            this.minecraft.gui.setScreen(new DysonEditorScreen());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                for (Button button : List.copyOf(this.buttons)) {
                    if (button.contains(event.x() - this.leftPos, event.y() - this.topPos)) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        button.action.run();
                        return true;
                    }
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (UniverseKeys.DYSON_EDITOR.matches(event)) {
                this.openEditor();
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            super.extractTooltip(graphics, mouseX, mouseY);
            Slot slot = this.hoveredSlot;
            if (slot == null || slot.hasItem() || !this.menu.getCarried().isEmpty() || slot.index >= this.menu.type().slots().size()) {
                return;
            }
            ItemStack ghost = this.ghost(slot.index);
            if (ghost.isEmpty()) {
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(ghost.getHoverName());
            this.slotHint(slot.index).ifPresent(hint -> lines.add(hint.copy().withStyle(ChatFormatting.GRAY)));
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    /** 电磁轨道弹射器：射向哪条轨道（可以换）、那条轨道的帆和发电、状态、下一发的进度。 */
    public static final class Ejector extends DysonMachineScreen {
        public Ejector(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected ItemStack ghost(int slot) {
            return new ItemStack(UniverseItems.SOLAR_SAIL.get());
        }

        @Override
        protected void info(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.powerLine(graphics, 8, 20);
            int orbitId = this.menu.data(EmRailEjectorBlockEntity.ORBIT);
            graphics.text(this.font, Component.translatable("gui.universe.ejector.orbit", orbitId), 8, 33, TEXT, false);
            this.button(graphics, Component.literal("<"), 112, 31, 11, mouseX, mouseY, () -> this.press(EmRailEjectorBlockEntity.BUTTON_PREVIOUS));
            this.button(graphics, Component.literal(">"), 125, 31, 11, mouseX, mouseY, () -> this.press(EmRailEjectorBlockEntity.BUTTON_NEXT));
            Optional<DysonSphere> sphere = sphere();
            SwarmOrbit orbit = sphere.map(s -> s.orbit(orbitId)).orElse(null);
            if (orbit != null) {
                graphics.text(this.font, Component.translatable("gui.universe.ejector.orbit_info", String.format(Locale.ROOT, "%.3f", orbit.radius()),
                        Math.round(orbit.inclination())), 8, 44, DIM, false);
                double power = orbit.sails() * DysonSphere.SAIL_POWER * sphere.get().lightFactor();
                graphics.text(this.font, Component.translatable("gui.universe.ejector.swarm", String.format(Locale.ROOT, "%,d", orbit.sails()),
                        DysonEditorScreen.watts(power)), 8, 55, DIM, false);
            }
            EmRailEjectorBlockEntity.Status status = EmRailEjectorBlockEntity.Status.values()[Math.floorMod(
                    this.menu.data(EmRailEjectorBlockEntity.STATUS), EmRailEjectorBlockEntity.Status.values().length)];
            int color = switch (status) {
                case WORKING -> GOOD;
                case NO_SAILS, NO_ORBIT, NO_SUN -> WARN;
                default -> BAD;
            };
            this.status(graphics, Component.translatable("gui.universe.ejector.status." + status.ordinal()), 66, color);
            int progress = this.menu.data(EmRailEjectorBlockEntity.PROGRESS);
            bar(graphics, 8, 89, 98, 2, progress / 1000F, GOOD);
            graphics.text(this.font, Component.translatable("gui.universe.ejector.progress", progress / 10), 112, 86, DIM, false);
        }
    }

    /** 垂直发射井：状态、下一枚去哪一层、结构点、在飞的火箭、下一枚的进度。 */
    public static final class Silo extends DysonMachineScreen {
        public Silo(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected ItemStack ghost(int slot) {
            return new ItemStack(UniverseItems.SMALL_CARRIER_ROCKET.get());
        }

        @Override
        protected void info(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.powerLine(graphics, 8, 20);
            LaunchSiloBlockEntity.Status status = LaunchSiloBlockEntity.Status.values()[Math.floorMod(
                    this.menu.data(LaunchSiloBlockEntity.STATUS), LaunchSiloBlockEntity.Status.values().length)];
            int color = switch (status) {
                case WORKING -> GOOD;
                case NO_ROCKETS, NO_LAYER -> WARN;
                case BUILT -> DIM;
                default -> BAD;
            };
            this.status(graphics, Component.translatable("gui.universe.silo.status." + status.ordinal()), 32, color);
            double limit = ClientDyson.payload().map(DysonSyncPayload::latitudeLimit).orElse(DysonSphere.LATITUDE_BASE);
            Optional<DysonSphere> sphere = sphere();
            if (sphere.isPresent()) {
                long points = 0;
                long capacity = 0;
                DysonLayer target = null;
                for (DysonLayer layer : sphere.get().layers()) {
                    points += Math.min(layer.structurePoints(), layer.structureCapacity(limit));
                    capacity += layer.structureCapacity(limit);
                    if (target == null && layer.nextTarget(limit) != null) {
                        target = layer;
                    }
                }
                // 有要建的层就看这一层的进度，全建完了看整个戴森球的
                if (target != null) {
                    graphics.text(this.font, Component.translatable("gui.universe.silo.target", target.id()), 8, 53, TEXT, false);
                    points = Math.min(target.structurePoints(), target.structureCapacity(limit));
                    capacity = target.structureCapacity(limit);
                }
                graphics.text(this.font, Component.translatable("gui.universe.silo.structure", DysonEditorScreen.compact(points),
                        DysonEditorScreen.compact(capacity)), 8, 64, DIM, false);
            }
            int rockets = ClientDyson.payload().map(DysonSyncPayload::rockets).orElse(0);
            graphics.text(this.font, Component.translatable("gui.universe.dyson.rockets", rockets), 8, 75, DIM, false);
            int progress = this.menu.data(LaunchSiloBlockEntity.PROGRESS);
            bar(graphics, 8, 89, 98, 2, progress / 1000F, GOOD);
            graphics.text(this.font, Component.translatable("gui.universe.silo.progress", progress / 10), 112, 86, DIM, false);
        }
    }

    /** 射线接收站：模式（可以切换）、状态、连续接收和效率、输出或光子进度、引力透镜。 */
    public static final class Receiver extends DysonMachineScreen {
        public Receiver(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected ItemStack ghost(int slot) {
            return new ItemStack(slot == RayReceiverBlockEntity.LENS_SLOT ? UniverseItems.GRAVITON_LENS.get() : UniverseItems.CRITICAL_PHOTON.get());
        }

        @Override
        protected Optional<Component> slotHint(int slot) {
            return slot == RayReceiverBlockEntity.LENS_SLOT ? Optional.of(Component.translatable("gui.universe.receiver.lens_hint"))
                    : Optional.empty();
        }

        @Override
        protected void info(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            boolean photon = this.menu.data(RayReceiverBlockEntity.MODE) == 1;
            double continuity = this.menu.data(RayReceiverBlockEntity.CONTINUITY) / 1000.0;
            int lensLeft = this.menu.data(RayReceiverBlockEntity.LENS);
            boolean lensLoaded = this.menu.getSlot(RayReceiverBlockEntity.LENS_SLOT).getItem().is(UniverseItems.GRAVITON_LENS.get());
            boolean lens = lensLeft > 0 || lensLoaded;
            int share = this.menu.data(RayReceiverBlockEntity.SHARE);
            RayReceiverBlockEntity.Status status = RayReceiverBlockEntity.Status.values()[Math.floorMod(
                    this.menu.data(RayReceiverBlockEntity.STATUS), RayReceiverBlockEntity.Status.values().length)];
            boolean receiving = status == RayReceiverBlockEntity.Status.RECEIVING;
            if (photon) {
                double draw = receiving ? RayReceiver.photonRequest(continuity, lensLeft > 0) * share / 1000.0 : 0;
                graphics.text(this.font, Component.translatable("gui.universe.receiver.photon_draw", DysonEditorScreen.watts(draw), share / 10), 8,
                        20, receiving ? GOOD : DIM, false);
            } else {
                this.powerLine(graphics, 8, 20);
            }
            Component mode = Component.translatable("gui.universe.receiver.mode." + (photon ? 1 : 0));
            graphics.text(this.font, mode, 8, 33, ACCENT, false);
            Component change = Component.translatable("gui.universe.receiver.switch");
            this.button(graphics, change, 14 + this.font.width(mode), 31, this.font.width(change) + 10, mouseX, mouseY,
                    () -> this.press(RayReceiverBlockEntity.BUTTON_MODE));
            int color = switch (status) {
                case RECEIVING -> GOOD;
                case NO_SPHERE, NO_SUN, FULL -> WARN;
                default -> BAD;
            };
            this.status(graphics, Component.translatable("gui.universe.receiver.status." + status.ordinal()), 44, color);
            graphics.text(this.font, Component.translatable("gui.universe.receiver.continuity", Math.round(continuity * 100),
                    Math.round(RayReceiver.efficiency(continuity, Upgrades.rayLoss(ClientResearch.state().level(Upgrades.RAY_EFFICIENCY))) * 100)),
                    8, 64, TEXT, false);
            bar(graphics, 8, 74, TEXT_WIDTH, 1, (float) continuity, ACCENT);
            if (photon) {
                int next = this.menu.data(RayReceiverBlockEntity.PHOTON);
                graphics.text(this.font, Component.translatable("gui.universe.receiver.photon", next / 10), 8, 77, DIM, false);
            } else {
                graphics.text(this.font, Component.translatable("gui.universe.receiver.max_output",
                        DysonEditorScreen.watts(RayReceiver.maxOutput(continuity, lens)), share / 10), 8, 77, DIM, false);
            }
            if (lensLeft > 0 || lensLoaded) {
                graphics.text(this.font, Component.translatable("gui.universe.receiver.lens", lensLeft > 0 ? lensLeft / 10 : 100), 8, 87, ACCENT,
                        false);
            } else {
                graphics.text(this.font, Component.translatable("gui.universe.receiver.no_lens"), 8, 87, 0xFF5D7682, false);
            }
        }
    }
}
