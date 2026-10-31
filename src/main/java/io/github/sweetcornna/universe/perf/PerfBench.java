package io.github.sweetcornna.universe.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * 在游戏内测试服务端里量参照基地（ADR-0038）：{@code ./gradlew runGameTestServer -PperfBench=5000,500,100,1200} 时，测试服务端
 * 只跑这一项，按参数搭一座 {@link BenchCommand} 的参照基地，区块都更新起来以后热身 {@link #WARMUP} 刻，再按分项计时量给定的刻数，
 * 报告写进日志（{@code Perf:} 开头，和 {@code /universe perf report} 一样）。不用开客户端，也不用同意 EULA。
 */
public final class PerfBench {
    /** 系统属性：{@code 传送带,设备,化工设备,量几刻}，后两项可以省（0 台、1200 刻）。 */
    public static final String PROPERTY = "universe.perfBench";
    /** 区块都更新起来以后先跑这么多刻再开始计时：传送带排满、设备开工、电网稳定。 */
    public static final int WARMUP = 400;

    public record Settings(int belts, int machines, int chemistry, int ticks) {
        /** 读系统属性；没设时是 null。 */
        public static @Nullable Settings fromSystem() {
            String value = System.getProperty(PROPERTY);
            return value == null || value.isBlank() ? null : parse(value);
        }

        /** 空着的一项按默认；数不对（不是整数、超出 {@code /universe bench} 的范围）直接报错，说清是哪一项。 */
        static Settings parse(String value) {
            String[] parts = value.split(",", -1);
            int[] numbers = {0, 0, 0, 1200};
            int[] max = {50_000, 5_000, 5_000, 72_000};
            if (parts.length > numbers.length) {
                throw new IllegalArgumentException(PROPERTY + " 最多四项（传送带,设备,化工设备,刻数）：" + value);
            }
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) {
                    continue;
                }
                try {
                    numbers[i] = Integer.parseInt(part);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(PROPERTY + " 第 " + (i + 1) + " 项不是整数：" + value, e);
                }
                int min = i == 3 ? 1 : 0;
                if (numbers[i] < min || numbers[i] > max[i]) {
                    throw new IllegalArgumentException(PROPERTY + " 第 " + (i + 1) + " 项要在 " + min + "–" + max[i] + " 之间：" + value);
                }
            }
            return new Settings(numbers[0], numbers[1], numbers[2], numbers[3]);
        }
    }

    private final BenchCommand.Bench bench;

    private PerfBench(BenchCommand.Bench bench) {
        this.bench = bench;
    }

    /** 以 {@code origin}（平台上面一格的西北角）搭一座参照基地，开始补给。 */
    public static PerfBench build(ServerLevel level, BlockPos origin, Settings settings) {
        return new PerfBench(BenchCommand.build(level, origin, settings.belts(), settings.machines(), settings.chemistry()));
    }

    /** 参照基地的区块是不是都在更新方块实体了。 */
    public boolean ticking() {
        for (ChunkPos chunk : this.bench.chunks) {
            if (!this.bench.level.shouldTickBlocksAt(chunk.pack())) {
                return false;
            }
        }
        return true;
    }

    public void startTiming() {
        ServerPerf.start(this.bench.level.getServer());
    }

    /** 停止计时，报告写进日志，停掉参照基地的补给、取消强制加载。返回报告的各行。 */
    public List<String> finish() {
        ServerPerf.stop();
        List<String> lines = new ArrayList<>(PerfCommand.report(ServerPerf.stats()));
        int total = this.bench.machines.size() + this.bench.chemistry.size();
        lines.add(String.format(Locale.ROOT, "参照基地：传送带 %d 格，制造台 %d 台，化工设备 %d 台，%d/%d 台在做，已做出 %d 个", this.bench.belts,
                this.bench.machines.size(), this.bench.chemistry.size(), BenchCommand.working(this.bench), total, this.bench.produced));
        lines.forEach(line -> Universe.LOGGER.info("Perf: {}", line));
        BenchCommand.stop(this.bench);
        return lines;
    }
}
