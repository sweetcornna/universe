package io.github.sweetcornna.universe.bio;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.chem.reactor.ReactorClimate;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.core.microbe.BioProcess;
import io.github.sweetcornna.universe.core.microbe.Culture;
import io.github.sweetcornna.universe.core.microbe.Isolation;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.core.power.Solar;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachines;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.microbe.MicrobeCodecs;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 生物反应器（ADR-0036 §五）：一罐活的培养物，持续喂料、持续出货。
 *
 * <p>菌种格里放一管菌种，罐空着（或者上一罐死光了）时整管倒进罐里接种，管子不退。罐里的菌按 core 的 {@link Culture}
 * 一刻一刻地长：有料、有电（搅拌、通气、控温）就长，满罐了停在那里；断料、断电两分钟以后开始死，热过最高温度死得快，
 * 密度掉到 1% 以下这罐就没了，要重新接种。界面上选配方（工艺），罐里的菌要带那种工艺的基因才做得了。
 *
 * <p>产出的速度 = 电网满足率 × 密度 × 菌的产率 × 罐温下的生长系数，配方的时间按满罐、产率 1、最适温度写。
 *
 * <p>电：有活菌就要控温和搅拌的电（{@link #HOLD_POWER}，罐温和星球气温差得越多越费）；在出货时再加上工艺的电。
 * 藻类培养要光，看得到天、是白天就用太阳；不然开补光灯，每做一批用 {@link #LIGHT_JOULES_PER_BATCH} 焦耳，
 * 比做出来的藻类生物质烧掉能换回的电多，不能拿电灯养藻来发电。
 *
 * <p>格子：0–2 原料，3–4 产物（生物浸出铜偶尔多出的镍锭放在第二个产物格），5 是菌种格。
 */
public class BioreactorBlockEntity extends RecipeMachineBlockEntity {
    public static final int INPUTS = 3;
    public static final int OUTPUTS = 2;
    public static final int STRAIN_SLOT = INPUTS + OUTPUTS;

    /** 罐里的状态：0 是空罐，1 起是 {@link Culture.State} 的序号 + 1。 */
    public static final int CULTURE = RecipeMachineBlockEntity.DATA_COUNT;
    /** 密度，千分数。 */
    public static final int DENSITY = CULTURE + 1;
    /** 设定温度（K）。 */
    public static final int SETPOINT = CULTURE + 2;
    /** 罐里现在的温度（K）：有电时是设定温度，没电时跟着星球气温。 */
    public static final int TANK = CULTURE + 3;
    /** 星球气温（K）。 */
    public static final int AMBIENT = CULTURE + 4;
    /** 罐温下的生长系数，千分数。 */
    public static final int GROWTH = CULTURE + 5;
    /** 第 0 位：有阳光；第 1 位：补光灯开着；第 2 位：罐里的菌做得了选的配方。 */
    public static final int FLAGS = CULTURE + 6;
    /** 罐里的菌能跑的工艺，按 {@link BioProcess} 的序号占位。 */
    public static final int PROCESSES = CULTURE + 7;
    /** 从这里起 {@link #HISTORY_POINTS} 格：最近的密度，千分数，从旧到新。 */
    public static final int HISTORY = CULTURE + 8;
    public static final int HISTORY_POINTS = 40;
    public static final int DATA_COUNT = HISTORY + HISTORY_POINTS;

    /** 界面按钮：倒掉这罐。 */
    public static final int FLUSH_BUTTON = 1001;
    /** 设温度：加上开尔文数。 */
    public static final int TEMPERATURE_BUTTON = 2000;

    /** 状态（接在 {@link RecipeMachineBlockEntity} 的后面）：罐里没有活菌。 */
    public static final int NO_CULTURE = 7;
    /** 罐里的菌没有选的这种工艺要的基因。 */
    public static final int WRONG_STRAIN = 8;
    /** 罐温下菌不长（太冷或太热），也就不出货。 */
    public static final int BAD_TEMPERATURE = 9;

    /** 有活菌时控温、搅拌的底数（焦耳/刻，60 kW）。 */
    public static final double HOLD_POWER = 3;
    /** 罐温和星球气温每差一开尔文多用的电（焦耳/刻，2 kW）。 */
    public static final double HOLD_PER_KELVIN = 0.1;
    /**
     * 补光灯每做一批藻类用的电（焦耳）。一批出一个藻类生物质，烧掉能换 1600 刻 × 15 J = 24 000 J；
     * 这里取 1.5 倍，和电解的规矩一样（ADR-0036 §规矩），加上增产剂的额外产出也换不回来。
     */
    public static final double LIGHT_JOULES_PER_BATCH = 36_000;
    /** 生物浸出铜时每做一批出一个镍锭的机会：铜镍硫化矿里伴生的镍（现实的塔尔维瓦拉矿就是生物堆浸出镍）。 */
    public static final double NICKEL_CHANCE = 0.1;
    /** 默认的设定温度（K），37 °C。 */
    public static final double DEFAULT_SETPOINT = 310;
    /** 太阳高过这个系数算白天（约 12°）。 */
    private static final double DAYLIGHT_ELEVATION = 0.2;
    /** 隔多久看一次天和星球气温（刻）。 */
    private static final int RECHECK = 100;
    /** 密度曲线隔多久记一个点（刻）：四十个点是两分钟。 */
    private static final int HISTORY_INTERVAL = 60;
    /** 自动化最多往菌种格塞几管。 */
    private static final int STRAIN_BUFFER = 2;

    private @Nullable Organism organism;
    private Culture culture = Culture.empty();
    private double setpoint = DEFAULT_SETPOINT;
    private double ambient = Double.NaN;
    private boolean daylight;
    private int recheck;
    /** 上一刻电网给控温的满足率；没要电时是 0。 */
    private double heated;
    /** 上一刻在出货。 */
    private boolean producing;
    /** 上一刻补光灯开着。 */
    private boolean lit;
    /** 上一刻每刻做多少批（不算电网满足率），补光灯的电按它算。 */
    private double lightRate;
    /** 这一刻在等工作速度时停下的原因。 */
    private int reason = NO_POWER;
    /** 这一刻做完了一批。 */
    private boolean crafted;
    private final int[] history = new int[HISTORY_POINTS];
    private int historyHead;

    public BioreactorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.BIOREACTOR.get(), pos, state, RecipeMachines.BIOREACTOR, 1);
    }

    // ---- 菌

    public @Nullable Organism organism() {
        return this.culture.alive() ? this.organism : null;
    }

    /** 上一罐的菌，死光了也记着（界面上写"某某菌死光了"）。 */
    public @Nullable Organism lastOrganism() {
        return this.organism;
    }

    public Culture culture() {
        return this.culture;
    }

    public double setpoint() {
        return this.setpoint;
    }

    public void setSetpoint(double kelvin) {
        this.setpoint = Math.round(Math.clamp(kelvin, Isolation.MIN_SETTING_K, Isolation.MAX_SETTING_K));
        this.setChanged();
    }

    /** 接种一罐：温度设到菌的最适温度。测试和菌种格都走这里。 */
    public void inoculate(Organism organism) {
        this.organism = organism;
        this.culture = Culture.inoculated();
        this.setSetpoint(organism.tolerance().optK());
        Arrays.fill(this.history, 0);
        this.changed();
    }

    /** 倒掉这罐。 */
    public void flush() {
        this.organism = null;
        this.culture = Culture.empty();
        Arrays.fill(this.history, 0);
        this.changed();
    }

    /** 测试用：直接设密度。 */
    public void setDensity(double density) {
        this.culture = new Culture(density, 0);
        this.setChanged();
    }

    /** 菌和死活变了：存盘，告诉客户端（界面上的菌名从客户端的方块实体读）。 */
    private void changed() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 选的配方是哪种工艺；没选为 {@code null}。 */
    public @Nullable BioProcess process() {
        RecipeHolder<MachineRecipe> recipe = this.recipe();
        return recipe == null ? null : BioRecipes.process(recipe.id().identifier());
    }

    /** 罐里的菌做得了选的配方。 */
    private boolean capable(@Nullable BioProcess process) {
        Organism organism = this.organism();
        return organism != null && process != null && organism.has(process.gene());
    }

    /** 罐里现在的温度：有电时是设定温度，没电时跟着星球气温。 */
    public double tankTemperature() {
        return this.heated > 0 ? this.setpoint : this.ambient();
    }

    private double ambient() {
        if (Double.isNaN(this.ambient) && this.level instanceof ServerLevel serverLevel) {
            this.ambient = Math.round(ReactorClimate.ambient(serverLevel));
        }
        return Double.isNaN(this.ambient) ? ReactorClimate.DEFAULT_AMBIENT : this.ambient;
    }

    private void recheckSky(ServerLevel level) {
        if (--this.recheck > 0) {
            return;
        }
        this.recheck = RECHECK;
        this.ambient = Double.NaN;
        BlockPos above = this.worldPosition.above();
        boolean space = SpaceDimensions.system(level.dimension()).isPresent();
        double sun = space ? 1 : Solar.elevation(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, this.worldPosition));
        this.daylight = level.dimensionType().hasSkyLight() && level.canSeeSky(above) && sun > DAYLIGHT_ELEVATION;
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (button == FLUSH_BUTTON) {
            if (this.organism == null) {
                return false;
            }
            this.flush();
            return true;
        }
        if (button >= TEMPERATURE_BUTTON) {
            this.setSetpoint(button - TEMPERATURE_BUTTON);
            return true;
        }
        return super.clickButton(player, button);
    }

    @Override
    public @Nullable CompoundTag blueprintSettings() {
        CompoundTag tag = super.blueprintSettings();
        if (tag == null) {
            tag = new CompoundTag();
        }
        tag.putDouble("Setpoint", this.setpoint);
        return tag;
    }

    @Override
    public void applyBlueprintSettings(CompoundTag settings) {
        if (settings.contains("Recipe")) {
            super.applyBlueprintSettings(settings);
        }
        this.setSetpoint(settings.getDoubleOr("Setpoint", this.setpoint));
    }

    // ---- 电

    @Override
    public long demand() {
        if (!this.culture.alive()) {
            return 0;
        }
        double power = HOLD_POWER + HOLD_PER_KELVIN * Math.abs(this.setpoint - this.ambient());
        if (this.producing) {
            power += super.demand();
            RecipeHolder<MachineRecipe> recipe = this.recipe();
            if (this.lit && recipe != null) {
                power += LIGHT_JOULES_PER_BATCH / recipe.value().time() * this.lightRate;
            }
        }
        return Math.max(1, Math.round(power));
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        // 没要电的那一刻满足率也可能是 1（电网没有负载），不能算数
        this.heated = this.demand() > 0 ? satisfaction : 0;
    }

    // ---- 养菌

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        this.recheckSky(serverLevel);
        this.tryInoculate();
        this.producing = false;
        this.lit = false;
        this.crafted = false;
        this.reason = NO_POWER;
        boolean worked = super.tickMachine();
        if (this.crafted) {
            this.byproducts(serverLevel);
        }
        if (this.culture.alive() && this.organism != null) {
            RecipeHolder<MachineRecipe> recipe = this.recipe();
            BioProcess process = recipe == null ? null : BioRecipes.process(recipe.id().identifier());
            // 有料：选了这罐菌做得了的配方，原料格里够一批，有电（搅拌、通气、控温；要光的补光灯也是电）
            boolean fed = this.heated > 0 && recipe != null && this.capable(process) && this.inputsPresent(recipe.value());
            if (this.culture.step(this.organism, 1, fed, this.tankTemperature()) == Culture.State.LOST) {
                this.changed();
            }
        }
        if (serverLevel.getGameTime() % HISTORY_INTERVAL == 0) {
            this.history[this.historyHead] = (int) Math.round(this.culture.density() * 1000);
            this.historyHead = (this.historyHead + 1) % HISTORY_POINTS;
        }
        return worked || this.culture.alive() && this.heated > 0;
    }

    /** 罐里没有活菌、菌种格里有一管菌种时接种。 */
    private void tryInoculate() {
        if (this.culture.alive()) {
            return;
        }
        ItemStack tube = this.getItem(STRAIN_SLOT);
        Organism organism = tube.isEmpty() ? null : tube.get(UniverseDataComponents.ORGANISM.get());
        if (organism == null) {
            return;
        }
        if (this.level instanceof ServerLevel serverLevel) {
            ProductionLog.consumed(serverLevel, tube.getItem(), 1);
        }
        tube.shrink(1);
        this.inoculate(organism);
    }

    @Override
    protected double workRate(double supplied) {
        Organism organism = this.organism();
        if (organism == null) {
            this.reason = NO_CULTURE;
            return 0;
        }
        RecipeHolder<MachineRecipe> recipe = this.recipe();
        BioProcess process = recipe == null ? null : BioRecipes.process(recipe.id().identifier());
        if (!this.capable(process)) {
            this.reason = WRONG_STRAIN;
            return 0;
        }
        double growth = organism.temperatureFactor(this.tankTemperature());
        if (growth <= 0) {
            this.reason = BAD_TEMPERATURE;
            return 0;
        }
        double rate = this.culture.outputRate(organism) * growth;
        // 补光灯：要光的工艺在没有阳光时开，每批的电固定，所以按每刻做多少批算
        if (process.needsLight() && !this.daylight && recipe != null) {
            int level = this.batchLevel(recipe.value());
            Proliferation.Mode mode = productive(recipe.value()) ? this.mode() : Proliferation.Mode.SPEEDUP;
            this.lit = true;
            this.lightRate = rate * Proliferation.speedFactor(mode, level) * Proliferation.powerFactor(level);
        }
        if (supplied <= 0) {
            this.reason = NO_POWER;
            return 0;
        }
        this.producing = true;
        return supplied * rate;
    }

    @Override
    protected int starvedStatus() {
        return this.reason;
    }

    /** 和 {@link RecipeMachineBlockEntity} 算增产等级的办法一样：每种原料的平均点数，取最低的。 */
    private int batchLevel(MachineRecipe recipe) {
        int level = Proliferation.MAX_LEVEL;
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            level = Math.min(level, this.inputLevel(i));
        }
        return recipe.ingredients().isEmpty() ? 0 : level;
    }

    private boolean inputsPresent(MachineRecipe recipe) {
        for (int i = 0; i < recipe.ingredients().size() && i < INPUTS; i++) {
            SizedIngredient ingredient = recipe.ingredients().get(i);
            if (!ingredient.test(this.getItem(i))) {
                return false;
            }
        }
        return recipe.ingredients().size() <= INPUTS;
    }

    /** 做配方时扣原料：第一格扣了就是做完了一批。 */
    @Override
    protected int consumeInput(int slot, int n) {
        if (slot == 0) {
            this.crafted = true;
        }
        return super.consumeInput(slot, n);
    }

    /** 生物浸出铜偶尔多出一个镍锭，放第二个产物格，放不下就掉在上面。 */
    private void byproducts(ServerLevel level) {
        RecipeHolder<MachineRecipe> recipe = this.recipe();
        if (recipe == null || BioRecipes.process(recipe.id().identifier()) != BioProcess.BIOLEACHING
                || !recipe.value().results().getFirst().create().is(UniverseItems.COPPER_LEACH_SOLUTION.get())
                || level.getRandom().nextDouble() >= NICKEL_CHANCE) {
            return;
        }
        ItemStack nickel = new ItemStack(UniverseItems.NICKEL_INGOT.get());
        ProductionLog.produced(level, nickel);
        if (this.addOutput(INPUTS + recipe.value().results().size(), nickel) > 0) {
            Containers.dropItemStack(level, this.worldPosition.getX(), this.worldPosition.getY() + 1, this.worldPosition.getZ(), nickel);
        }
    }

    // ---- 物品栏

    private static boolean strainTube(ItemStack stack) {
        return stack.is(UniverseItems.STRAIN_TUBE.get()) && stack.has(UniverseDataComponents.ORGANISM.get());
    }

    @Override
    protected boolean isInput(int slot) {
        return slot == STRAIN_SLOT || super.isInput(slot);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == STRAIN_SLOT ? strainTube(stack) : super.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (slot == STRAIN_SLOT) {
            return strainTube(stack) && this.getItem(slot).getCount() < STRAIN_BUFFER;
        }
        return super.canPlaceItemThroughFace(slot, stack, side);
    }

    /** 菌种格里放错的东西能抽走，菌种不抽。 */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == STRAIN_SLOT ? !strainTube(stack) : super.canTakeItemThroughFace(slot, stack, side);
    }

    // ---- 界面

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        if (index >= HISTORY && index < HISTORY + HISTORY_POINTS) {
            return this.history[(this.historyHead + index - HISTORY) % HISTORY_POINTS];
        }
        return switch (index) {
            case CULTURE -> this.organism == null ? 0 : this.culture.state().ordinal() + 1;
            case DENSITY -> (int) Math.round(this.culture.density() * 1000);
            case SETPOINT -> (int) Math.round(this.setpoint);
            case TANK -> (int) Math.round(this.tankTemperature());
            case AMBIENT -> (int) Math.round(this.ambient());
            case GROWTH -> {
                Organism organism = this.organism();
                yield organism == null ? 0 : (int) Math.round(organism.temperatureFactor(this.tankTemperature()) * 1000);
            }
            case FLAGS -> (this.daylight ? 1 : 0) | (this.lit && this.producing ? 2 : 0) | (this.capable(this.process()) ? 4 : 0);
            case PROCESSES -> {
                Organism organism = this.organism();
                int bits = 0;
                if (organism != null) {
                    for (BioProcess process : BioProcess.available(organism)) {
                        bits |= 1 << process.ordinal();
                    }
                }
                yield bits;
            }
            default -> super.dataValue(index);
        };
    }

    // ---- 存档和同步

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.organism = input.read("Organism", MicrobeCodecs.ORGANISM).orElse(null);
        this.culture = this.organism == null ? Culture.empty()
                : new Culture(input.getDoubleOr("Density", 0), input.getIntOr("Starved", 0));
        this.setpoint = Math.clamp(input.getDoubleOr("Setpoint", DEFAULT_SETPOINT), Isolation.MIN_SETTING_K, Isolation.MAX_SETTING_K);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.organism != null) {
            output.store("Organism", MicrobeCodecs.ORGANISM, this.organism);
        }
        output.putDouble("Density", this.culture.density());
        output.putInt("Starved", this.culture.starvedTicks());
        output.putDouble("Setpoint", this.setpoint);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
