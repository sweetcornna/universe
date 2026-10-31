package io.github.sweetcornna.universe.gametest;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;

/**
 * 等后台线程按真实时间做完的事：生成强制加载的区块、光照线程算到新放的方块。测试服务端两刻之间不等
 * （{@code GameTestServer.waitUntilNextTick} 只跑完排着的任务），空闲时一秒几千刻，按刻数限时，CI 的机器一忙就不够。
 * 所以按真实时间限时。
 *
 * <p>等的时候主线程每刻歇一毫秒：不空转抢后台线程的 CPU，等不到时也不会一秒刷出几千行进度条；等满 {@link #SECONDS} 秒最多过 {@link #TICKS} 刻。
 * 用它的测试登记最长刻数时加上 {@link #TICKS}。
 */
final class RealTime {
    static final int SECONDS = 60;
    static final int TICKS = SECONDS * 1000;

    private RealTime() {
    }

    /** 从测试开头等到 {@code ready}，最多 {@link #SECONDS} 秒，过了还没好就失败；返回的序列接着往下写。 */
    static GameTestSequence await(GameTestHelper helper, BooleanSupplier ready, String waiting) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SECONDS);
        return helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!ready.getAsBoolean() && System.nanoTime() < deadline) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                        helper.fail(waiting);
                    }
                })
                .thenExecute(() -> helper.assertTrue(ready.getAsBoolean(), waiting + "，等了 " + SECONDS + " 秒"));
    }
}
