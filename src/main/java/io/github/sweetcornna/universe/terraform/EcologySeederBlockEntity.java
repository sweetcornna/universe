package io.github.sweetcornna.universe.terraform;

import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.microbe.EngineeredPlant;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 生态播种站（ADR-0036 §六）：放在能改造的星球地表，头顶要露天。上面一格放菌种管或改造种苗，环境里的每一样条件都有基因扛住
 * 才种得活（{@link io.github.sweetcornna.universe.core.terraform.Pioneer}）；种得活就通上电慢慢播，一份播完消耗一个，
 * 星球记下这组基因、生物量加一点（{@link Terraform#seed}）。
 *
 * <p>下面一格放水：星球本来没有水时，有水的站给星球加灌溉（{@link TerraformRules#irrigation}，四座浇满），一个水浇一分钟，
 * 没水就停。星球有生物量以后，站周围的岩石、土慢慢长上地衣和苔藓（{@link Greening}）。
 *
 * <p>站所在的区块卸载了还算在浇水（和轨道反射镜一样，星球的进度不管有没有人在跟前都照算）；拆掉才不算。
 */
public class EcologySeederBlockEntity extends MachineBlockEntity {
    public static final int SEED_SLOT = 0;
    public static final int WATER_SLOT = 1;
    public static final int SLOTS = 2;
    /** 用电（焦耳/刻）：界面上是 800 W。培养液的保温、喷洒泵。 */
    public static final long POWER = 40;
    /** 满电时播一份要几刻。 */
    public static final int SEED_TICKS = 200;
    /** 一个水浇多少刻（一分钟）。 */
    public static final int WATER_TICKS = 1200;
    /** 多久重新看一次头顶、所在的星球和种不种得活（刻）。 */
    private static final int CHECK_INTERVAL = 40;

    /** 界面数据：状态（{@link #OK} 等）。 */
    public static final int STATUS = BASE_DATA;
    /** 这一份播了多少，千分数。 */
    public static final int PROGRESS = BASE_DATA + 1;
    /** 这座站一共播了几份，两格。 */
    public static final int SEEDED = BASE_DATA + 2;
    /** 剩下的水还够浇几秒；不在浇为 0。 */
    public static final int WATER_LEFT = BASE_DATA + 4;
    /** 这颗星球上有几座站在浇水。 */
    public static final int STATIONS = BASE_DATA + 5;
    /** 星球本来有水（不用浇）为 1。 */
    public static final int NATURAL_WATER = BASE_DATA + 6;
    public static final int DATA_COUNT = BASE_DATA + 7;

    /** 在播。 */
    public static final int OK = 0;
    /** 不在能改造的星球上（母星、太空、气态巨行星）。 */
    public static final int NOT_TERRAFORMABLE = 1;
    /** 头顶有遮挡。 */
    public static final int NO_SKY = 2;
    /** 没放菌种管或种苗。 */
    public static final int NO_SEED = 3;
    /** 放了，但有条件扛不住。 */
    public static final int CANNOT_SURVIVE = 4;

    private @Nullable BodyId target;
    private boolean sky;
    private boolean naturalWater;
    private int checkTimer;
    private int greenTimer;
    private double progress;
    private boolean active;
    private double supplied;
    private long seeded;
    private int waterLeft;
    /** 登记成了在浇水的站。 */
    private boolean irrigating;
    /** 读过存档里的登记了。 */
    private boolean synced;
    /** 上回看过的那组基因和结果；放进别的东西、环境变了就重新看。 */
    private @Nullable Set<Gene> checked;
    private boolean viable;
    /** 游戏内测试用：不管在哪个维度，往这颗星球里播、给它浇水。 */
    private @Nullable BodyId forced;

    public EcologySeederBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.ECOLOGY_SEEDER.get(), pos, state, SLOTS);
    }

    /** 菌种管里的菌、改造种苗的基因（天生的加编辑进去的）；别的东西为空。 */
    public static Optional<Set<Gene>> genes(ItemStack stack) {
        if (stack.is(UniverseItems.STRAIN_TUBE.get())) {
            return Optional.ofNullable(stack.get(UniverseDataComponents.ORGANISM.get())).map(Organism::genes);
        }
        if (stack.is(UniverseItems.ENGINEERED_SEEDLING.get())) {
            return Optional.ofNullable(stack.get(UniverseDataComponents.ENGINEERED_PLANT.get())).map(EngineeredPlant::genes);
        }
        return Optional.empty();
    }

    public static boolean isWater(ItemStack stack) {
        return stack.is(UniverseItems.WATER.get());
    }

    @Override
    public long demand() {
        return this.active ? POWER : 0;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.supplied = this.active ? satisfaction : 0;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.checkTimer = 0;
    }

    /** 方块拆掉、被别的方块换掉时（区块卸载不算）：不再算它浇的水。 */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel level) {
            Terraforming.removeSeeder(level, pos);
        }
    }

    @Override
    protected boolean tickMachine() {
        double rate = this.supplied;
        this.supplied = 0;
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        if (--this.checkTimer <= 0) {
            this.checkTimer = CHECK_INTERVAL;
            this.check(level);
        }
        boolean watering = this.water(level);
        Optional<Set<Gene>> genes = genes(this.getItem(SEED_SLOT));
        if (genes.isEmpty()) {
            this.checked = null;
            this.viable = false;
            this.progress = 0;
        } else if (!genes.get().equals(this.checked)) {
            this.checked = genes.get();
            this.viable = this.target != null && Terraforming.seeding(level.getServer(), this.target, genes.get()).viable();
        }
        this.active = this.target != null && this.sky && this.viable;
        boolean seeding = false;
        if (this.active && rate > 0) {
            seeding = true;
            this.progress += rate;
            if (this.progress >= SEED_TICKS) {
                this.progress -= SEED_TICKS;
                this.plant(level, genes.orElseThrow());
                this.setChanged();
            } else {
                this.markUnsaved();
            }
        }
        if (--this.greenTimer <= 0) {
            this.greenTimer = Greening.INTERVAL;
            this.green(level);
        }
        return seeding || watering;
    }

    /** 播完一份：种活了就消耗一个。 */
    private void plant(ServerLevel level, Set<Gene> genes) {
        if (this.target == null) {
            return;
        }
        Item item = this.getItem(SEED_SLOT).getItem();
        if (Terraforming.seed(level.getServer(), this.target, genes)) {
            this.consumeInput(SEED_SLOT, 1);
            ProductionLog.consumed(level, item, 1);
            this.seeded++;
        }
        // 播下去以后环境（灌溉、生物量）也许变了，下一刻重新看
        this.checked = null;
    }

    /**
     * 浇水：星球本来没有水时，水用完了从水格里补一个，有水就登记成在浇水的站，没水就撤掉登记。
     *
     * @return 在浇水
     */
    private boolean water(ServerLevel level) {
        boolean wanted = this.target != null && !this.naturalWater;
        boolean drank = false;
        if (wanted && this.waterLeft <= 0 && isWater(this.getItem(WATER_SLOT))) {
            Item item = this.getItem(WATER_SLOT).getItem();
            this.consumeInput(WATER_SLOT, 1);
            ProductionLog.consumed(level, item, 1);
            this.waterLeft = WATER_TICKS;
            drank = true;
        }
        boolean watering = wanted && this.waterLeft > 0;
        if (watering != this.irrigating) {
            if (watering) {
                Terraforming.addSeeder(level, this.worldPosition, this.target);
            } else {
                Terraforming.removeSeeder(level, this.worldPosition);
            }
            this.irrigating = watering;
        }
        if (watering) {
            this.waterLeft--;
            // 只有用掉一格水的那一刻物品栏变了
            if (drank) {
                this.setChanged();
            } else {
                this.markUnsaved();
            }
        }
        return watering;
    }

    /** 星球有生物量以后让周围长地衣、苔藓。游戏内测试往别的星球播时不长（测试场地不在那颗星球上）。 */
    private void green(ServerLevel level) {
        if (this.forced != null || this.target == null) {
            return;
        }
        double biomass = Terraforming.state(level.getServer(), this.target).biomass();
        Greening.spread(level, this.worldPosition, TerraformRules.greeningRadius(biomass), biomass, level.getRandom(), Greening.ATTEMPTS);
    }

    /** 重新看一次所在的星球、头顶、星球有没有水，以及存档里的浇水登记对不对得上。 */
    private void check(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (!this.synced) {
            this.irrigating = Terraforming.seeder(level, this.worldPosition).isPresent();
            this.synced = true;
        }
        if (this.forced != null) {
            this.target = this.forced;
            this.sky = true;
        } else {
            this.target = Planets.planet(level).filter(Terraform::terraformable).map(Planet::id).orElse(null);
            this.sky = level.dimensionType().hasSkyLight() && level.canSeeSky(this.worldPosition.above());
        }
        BodyId id = this.target;
        this.naturalWater = id != null && Planets.galaxy(server).body(id).map(base -> Terraforming.environment(server, base).naturalWater()).orElse(false);
        if (this.irrigating) {
            boolean matches = id != null && Terraforming.seeder(level, this.worldPosition).filter(seeder -> seeder.planet().equals(id.path())).isPresent();
            if (!matches) {
                Terraforming.removeSeeder(level, this.worldPosition);
                this.irrigating = false;
            }
        }
        this.checked = null;
    }

    /** 游戏内测试用：往这颗星球里播、给它浇水，不看所在的维度和头顶。 */
    public void forceTarget(BodyId planet) {
        this.forced = planet;
        this.checkTimer = 0;
        this.checked = null;
    }

    public long seeded() {
        return this.seeded;
    }

    public boolean irrigating() {
        return this.irrigating;
    }

    private int status() {
        if (this.target == null) {
            return NOT_TERRAFORMABLE;
        }
        if (!this.sky) {
            return NO_SKY;
        }
        if (genes(this.getItem(SEED_SLOT)).isEmpty()) {
            return NO_SEED;
        }
        return this.viable ? OK : CANNOT_SURVIVE;
    }

    @Override
    protected boolean isInput(int slot) {
        return slot == SEED_SLOT || slot == WATER_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SEED_SLOT ? genes(stack).isPresent() : slot == WATER_SLOT && isWater(stack);
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.ECOLOGY_SEEDER;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        if (index == STATUS) {
            return this.status();
        }
        if (index == PROGRESS) {
            return (int) Math.min(1000, Math.round(this.progress * 1000 / SEED_TICKS));
        }
        if (index == SEEDED || index == SEEDED + 1) {
            return index == SEEDED ? MachineMenu.low(this.seeded) : MachineMenu.high(this.seeded);
        }
        if (index == WATER_LEFT) {
            return this.irrigating ? (this.waterLeft + 19) / 20 : 0;
        }
        if (index == STATIONS) {
            return this.target != null && this.level instanceof ServerLevel level ? Terraforming.seeders(level.getServer(), this.target) : 0;
        }
        if (index == NATURAL_WATER) {
            return this.naturalWater ? 1 : 0;
        }
        return super.dataValue(index);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
        this.seeded = input.getLongOr("Seeded", 0);
        this.waterLeft = input.getIntOr("WaterLeft", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
        output.putLong("Seeded", this.seeded);
        output.putInt("WaterLeft", this.waterLeft);
    }
}
