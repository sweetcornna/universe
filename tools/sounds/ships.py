"""飞船、载具、货船、海盗的声音（ADR-0029）。"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["ship", "vehicle", "freighter", "pirate"]

# 循环前面多生成这么多秒再切掉：混响和滤波器先进入稳态，接缝两边才一样
PRE = 1.0
XF = 0.4


def norm(x: np.ndarray) -> np.ndarray:
    return x / (np.max(np.abs(x)) + 1e-9)


def match(x: np.ndarray, ref: np.ndarray) -> np.ndarray:
    """把 x 的有效值调到和 ref 一样。"""
    return x * np.sqrt(np.mean(ref ** 2) / (np.mean(x ** 2) + 1e-20))


def warm(x: np.ndarray) -> np.ndarray:
    return x[s.samples(PRE):]


def end(x: np.ndarray) -> np.ndarray:
    """一次性的声音在进混响前把末尾 80 ms 收掉：各层被截断的地方不会咔一声。"""
    return s.taper(x, 0.08)


def hann(x: np.ndarray) -> np.ndarray:
    """服务端隔一段时间放一次的持续声（光束）：两头按汉宁窗收，每隔半段长度放一次正好接成平的。"""
    return x * np.hanning(len(x))


def burst(rng, length: float, low: float, high: float, attack: float, decay: float) -> np.ndarray:
    n = s.samples(length)
    return s.bandpass(s.white(n, rng), low, high) * s.perc(n, attack, decay)


def sub_drop(length: float, start: float, end: float, decay: float, attack: float = 0.003) -> np.ndarray:
    n = s.samples(length)
    return s.sine(s.exp_curve([(0, start), (length, end)], n), n) * s.perc(n, attack, decay)


# ---- 驾驶时跟着玩家的三层

@sound("ship/cabin", loop=6.0, level=-21)
def cabin(rng, i):
    """舱内：推进器和涡轮泵的振动顺着船体传进来，低频为主；加上生命维持的风扇。音高在代码里随推力变。"""
    n = s.samples(6.0 + XF + PRE)
    rumble = s.lowpass(s.brown(n, rng), 160, 2)
    # 船体几块大板子的共鸣
    hull = match(s.modes(rumble, [43, 67, 98, 131, 177], [0.5, 0.45, 0.4, 0.3, 0.25]), rumble)
    throb = 1 + 0.2 * s.wander(n, rng, 1.5)
    # 涡轮泵：140 Hz 和它的谐波（6 秒正好整数个周期，接缝两边相位一致）
    pump = s.harmonics(140.0, n, [0.6, 0.3, 0.18, 0.1, 0.05]) * (1 + 0.2 * s.wander(n, rng, 3))
    fan = m.fan(n, rng, 1500, 7, 0.8)
    x = norm(0.7 * rumble + 0.6 * hull) * throb + 0.1 * norm(pump) + 0.07 * norm(fan)
    return warm(fx.space(x, "cockpit", wet=0.3))


@sound("ship/air", loop=6.0, level=-20)
def air(rng, i):
    """外面的气流：贴着船体的湍流、低频的抖振，天线后面的卡门涡街哨声，隔着舱壁听高频少一些。"""
    n = s.samples(6.0 + XF + PRE)
    rush = m.wind(n, rng, 0.95, gust=0.35)
    buffet = s.lowpass(s.brown(n, rng), 90, 2) * (1 + 0.6 * s.wander(n, rng, 4))
    speed = 110 * (1 + 0.05 * s.wander(n, rng, 0.8))
    whistle = m.karman(n, rng, speed, 0.012, q=30) + 0.6 * m.karman(n, rng, speed, 0.035, q=20)
    x = norm(rush) + 0.45 * norm(buffet) + 0.05 * norm(whistle)
    x = s.lowpass(x, 7000, 2)
    return warm(fx.space(x, "cockpit", wet=0.15))


@sound("ship/pulse", loop=4.0, level=-22, power=False)
def pulse(rng, i):
    """脉冲引擎：功率单元的高频嗡声，6 Hz 一下一下地推；各个频率在 4 秒里都是整数个周期，接缝两边相位一致。"""
    n = s.samples(4.0 + XF + PRE)
    drone = s.harmonics(110.0, n, [1.0, 0.7, 0.5, 0.45, 0.3, 0.25, 0.2, 0.15, 0.12, 0.1])
    beat = s.harmonics(110.25, n, [0.6, 0.4, 0.3])
    whine = 0.08 * s.sine(1760.0, n) + 0.04 * s.sine(2640.0, n)
    throb = 0.7 + 0.3 * s.sine(6.0, n)
    buzz = s.resonant(s.white(n, rng), "bandpass", 3200, 1.2) * 0.12
    x = (0.7 * norm(drone) + 0.3 * norm(beat)) * throb + whine + buzz * throb
    return warm(fx.space(x, "cockpit", wet=0.2))


@sound("ship/exterior", loop=5.0, level=-18)
def exterior(rng, i):
    """别人的船飞过：推进器的轰鸣加涡轮泵的尖音，从外面听。"""
    n = s.samples(5.0 + XF + PRE)
    roar = m.rocket(n, rng, 0.75 * (1 + 0.1 * s.wander(n, rng, 2)), crackle=0.35)
    whine = s.harmonics(2400.0, n, [1.0, 0.3]) * (1 + 0.3 * s.wander(n, rng, 1))
    x = norm(roar) + 0.05 * norm(whine)
    return warm(fx.space(x, fx.outdoor_ir(rng, 0.8), wet=0.3))


# ---- 一次性

@sound("ship/takeoff", variants=2, level=-14)
def takeoff(rng, i):
    """起飞：点火器噼啪几下，推进器的轰鸣涌上来，地面把低频反射回来、扬起尘土；飞远了高频先没。"""
    length = 3.6
    n = s.samples(length)
    thrust = [(0, 0), (0.12, 0.35), (0.5, 0.8), (1.2, 1.0), (2.4, 0.85), (length, 0.0)]
    roar = m.rocket(n, rng, thrust, crackle=0.5)
    cutoff = s.exp_curve([(0, 2500), (0.4, 9000), (1.5, 6000), (length, 1200)], n)
    roar = s.resonant(roar, "lowpass", cutoff, 0.7)
    blast = s.lowpass(s.brown(n, rng), 120, 2) * s.envelope([(0, 0), (0.1, 1), (0.8, 0.6), (2.0, 0), (length, 0)], n)
    dust = m.hiss(n, rng, [(0, 0), (0.15, 1), (1.5, 0), (length, 0)], 0.4)
    pops = []
    for t0 in (0.0, 0.05 + rng.uniform(0, 0.03), 0.12 + rng.uniform(0, 0.03)):
        pop = burst(rng, 0.12, 800, 9000, 0.0005, 0.03) + 0.8 * s.lowpass(s.white(s.samples(0.12), rng), 300) * s.perc(s.samples(0.12), 0.001, 0.08)
        pops.append((norm(pop), 0.6, t0))
    x = s.mix((norm(roar), 1.0), (norm(blast), 0.5), (norm(dust), 0.15), *pops)
    return fx.space(end(x), fx.outdoor_ir(rng, 1.0), wet=0.4, tail=1.0)


@sound("ship/land", variants=2, level=-16)
def land(rng, i):
    """着陆：起落架砸在地上的一声闷响，支柱的金属咔嗒，液压泄压，推进器收声。"""
    k = 1 + rng.uniform(-0.05, 0.05)
    n = s.samples(1.8)
    thud = m.impact(rng, freq=55 * k, decay=0.6, ratios=m.HOLLOW, hardness=0.3, size=1.5, noise=0.5)
    gear1 = m.impact(rng, freq=280 * k, decay=0.35, ratios=m.PLATE, hardness=0.7, noise=0.2)
    gear2 = m.impact(rng, freq=330 * k, decay=0.3, ratios=m.PLATE, hardness=0.7, noise=0.2)
    hiss = m.hiss(n, rng, [(0, 0), (0.18, 0), (0.22, 1), (1.2, 0), (1.8, 0)], 0.5)
    spool = s.lowpass(m.rocket(n, rng, [(0, 0.5), (0.9, 0), (1.8, 0)], crackle=0.2), 2500, 2)
    x = s.mix((norm(thud), 1.0, 0.0), (norm(gear1), 0.35, 0.01), (norm(gear2), 0.3, 0.05 + rng.uniform(0, 0.03)),
              (norm(hiss), 0.15), (norm(spool), 0.35))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.5), wet=0.3, tail=0.6)


@sound("ship/warp_charge", level=-16)
def warp_charge(rng, i):
    """跃迁充能（3.5 秒）：低沉的嗡声和涡轮的尖啸一起往上爬，脉动越来越快，电噼啪越来越密；最后一声撕开。"""
    length = 4.4
    n = s.samples(length)
    rise = s.envelope([(0, 0.08), (3.4, 1.0), (3.55, 1.0), (3.75, 0.15), (length, 0)], n)
    hum = s.harmonics(s.exp_curve([(0, 38), (3.5, 95), (length, 95)], n), n, [1, 0.6, 0.4, 0.25, 0.15])
    whine = s.fm(s.exp_curve([(0, 180), (3.5, 1400), (length, 1400)], n), 2.0, s.envelope([(0, 0.5), (3.5, 2.5), (length, 2.5)], n), n)
    throb = 0.6 + 0.4 * s.sine(s.exp_curve([(0, 2), (3.5, 18), (length, 18)], n), n)
    crack = s.highpass(s.crackle(n, rng, [(0, 5), (3.5, 500), (3.6, 0), (length, 0)], width=0.0001), 2000)
    tear_n = s.samples(0.9)
    tear = s.resonant(s.pink(tear_n, rng), "bandpass", s.exp_curve([(0, 5000), (0.9, 150)], tear_n), 0.8) * s.perc(tear_n, 0.02, 0.8)
    drop = sub_drop(0.9, 70, 32, 0.8)
    body = (0.8 * norm(hum) + 0.35 * norm(whine)) * throb * rise + 0.25 * crack * rise
    x = s.mix((body, 1.0), (norm(tear), 0.8, 3.45), (drop, 0.7, 3.45))
    return fx.space(end(x), "cockpit", wet=0.25, tail=0.4)


@sound("ship/warp", level=-15)
def warp(rng, i):
    """旁人听到的跃迁：空气被往里吸的倒卷声，一声爆裂和闷响，留下一圈金属似的余音。"""
    n = s.samples(0.62)
    t = s.times(n)
    swell = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 300), (0.62, 3000)], n), 0.9) * (t / 0.62) ** 3
    crack = m.n_wave(rng, 0.004)
    flash = burst(rng, 0.2, 1500, 12000, 0.0003, 0.05)
    boom = sub_drop(1.6, 62, 32, 1.2)
    ring_n = s.samples(1.8)
    ring = s.comb(s.white(ring_n, rng) * s.perc(ring_n, 0.002, 0.08), 1 / 220, 0.9, damping=0.3) * s.perc(ring_n, 0.01, 1.6)
    x = s.mix((norm(swell), 0.8, 0.0), (crack, 0.6, 0.6), (norm(flash), 0.5, 0.6), (boom, 1.0, 0.6), (norm(ring), 0.25, 0.6))
    return fx.space(end(x), fx.outdoor_ir(rng, 1.2), wet=0.35, tail=0.8)


@sound("ship/warp_exit", variants=2, level=-15)
def warp_exit(rng, i):
    """跃出：一声爆裂，冲出来的呼啸迅速慢下来，留下一层闪烁的余音。"""
    k = 1 + rng.uniform(-0.04, 0.04)
    length = 3.0
    n = s.samples(length)
    crack = s.mix((m.n_wave(rng, 0.005), 0.7), (burst(rng, 0.1, 1500, 12000, 0.0003, 0.05), 0.5))
    boom = sub_drop(1.4, 58 * k, 30, 1.0, 0.002)
    whoosh = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 3500 * k), (1.4, 250 * k), (length, 200)], n), 1.0)
    whoosh *= s.envelope([(0, 0), (0.05, 1), (1.4, 0.1), (2.2, 0), (length, 0)], n)
    shimmer = s.harmonics(s.exp_curve([(0, 880 * k), (2.0, 440 * k), (length, 440 * k)], n), n, [1, 0.5, 0.3]) * s.perc(n, 0.01, 1.8)
    shimmer = fx.chorus(shimmer, rate=1.3, depth=0.4, mix=0.6)
    x = s.mix((crack, 1.0), (boom, 0.9), (norm(whoosh), 0.7), (norm(shimmer), 0.12))
    return fx.space(end(x), "cockpit", wet=0.2, tail=0.4)


@sound("ship/hull_hit", variants=3, level=-17)
def hull_hit(rng, i):
    """船体挨打：大块钢板的低沉共鸣，一声高一点的金属咔嚓，碎片哗啦一下。"""
    n = s.samples(1.4)
    panel = m.impact(rng, freq=rng.uniform(110, 170), decay=1.1, ratios=m.PLATE, hardness=0.65, size=1.4, noise=0.45)
    clank = m.impact(rng, freq=rng.uniform(520, 700), decay=0.3, ratios=m.BAR, hardness=0.8, noise=0.2)
    rattle = s.highpass(s.crackle(n, rng, [(0, 300), (0.3, 40), (0.6, 0), (1.4, 0)], width=0.0002), 1500)
    thud = s.lowpass(s.white(n, rng), 250) * s.perc(n, 0.001, 0.15)
    x = s.mix((norm(panel), 1.0), (norm(clank), 0.4, 0.005), (norm(rattle), 0.15, 0.01), (norm(thud), 0.6))
    return fx.space(end(x), "cockpit", wet=0.25, tail=0.4)


@sound("ship/bump", variants=2, level=-18)
def bump(rng, i):
    """擦碰：低沉的一声闷撞，加一点刮擦。"""
    n = s.samples(0.7)
    thud = s.lowpass(s.brown(n, rng), 180, 2) * s.perc(n, 0.002, 0.2)
    body = m.impact(rng, freq=rng.uniform(70, 90), decay=0.3, ratios=m.HOLLOW, hardness=0.2, size=1.4, noise=0.3)
    scrape = s.resonant(s.white(n, rng), "bandpass", rng.uniform(1200, 1800), 1.5) * s.envelope([(0, 0), (0.02, 1), (0.35, 0), (0.7, 0)], n)
    x = s.mix((norm(thud), 1.0), (norm(body), 0.7), (norm(scrape), 0.12, 0.01))
    return fx.space(end(x), "cockpit", wet=0.2, tail=0.3)


@sound("ship/shield_hit", variants=3, level=-19)
def shield_hit(rng, i):
    """护盾挡下一击：一声亮的放电，护盾发生器的几个频率嗡地一颤，余下细碎的嘶声。"""
    k = 1 + rng.uniform(-0.05, 0.05)
    n = s.samples(0.9)
    t = s.times(n)
    zap = m.zap(rng, start=4200 * k, end=1400 * k, length=0.07, bite=0.7, fm_index=1.5)
    ring = np.zeros(n)
    for f, tau in ((523, 0.35), (811, 0.25), (1247, 0.18), (1690, 0.12)):
        ring += np.sin(2 * np.pi * f * k * t) * np.exp(-t / tau)
    ring *= 0.75 + 0.25 * s.sine(14.0, n)
    fizz = s.highpass(s.white(n, rng), 3000) * s.perc(n, 0.001, 0.35)
    x = s.mix((norm(zap), 0.9), (norm(ring), 0.6), (norm(fizz), 0.2))
    x = fx.phaser(x, rate=3.0, depth=0.7, feedback=0.4, mix=0.5)
    return fx.space(end(x), fx.outdoor_ir(rng, 0.3), wet=0.15, tail=0.3)


@sound("ship/photon", variants=3, level=-19)
def photon(rng, i):
    """光子炮：电容放电的一声"啪咻"，炮座后坐的一下闷响。"""
    k = 1 + rng.uniform(-0.05, 0.05)
    zap = m.zap(rng, start=2300 * k, end=380 * k, length=0.14, bite=0.6, fm_index=2.4)
    thump = sub_drop(0.12, 110 * k, 55, 0.08, 0.001)
    crack = burst(rng, 0.02, 2500, 14000, 0.0002, 0.006)
    x = s.mix((norm(zap), 1.0), (thump, 0.6), (norm(crack), 0.35))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.3), wet=0.2, tail=0.25)


@sound("ship/beam", variants=2, level=-20)
def beam(rng, i):
    """采矿光束：每 8 刻（0.4 秒）放一段 0.8 秒，汉宁窗叠起来是连续的嗡声。音高取 0.4 秒内整数个周期，前后两段相位对得上。"""
    n = s.samples(0.8)
    hum = s.harmonics(195.0, n, [1, 0.8, 0.6, 0.5, 0.4, 0.3, 0.25, 0.2]) * (1 + 0.1 * s.wander(n, rng, 6))
    sizzle = s.resonant(s.white(n, rng), "bandpass", 3800, 1.5) * (0.6 + 0.4 * s.wander(n, rng, 10))
    crack = s.highpass(s.crackle(n, rng, 120, width=0.0001), 2500)
    return hann(0.6 * norm(hum) + 0.35 * norm(sizzle) + 0.2 * crack)


@sound("ship/summon", level=-16)
def summon(rng, i):
    """召唤来的飞船到了：推进器的声音从远处压过来，落地一声闷响、泄压。"""
    length = 2.8
    n = s.samples(length)
    roar = m.rocket(n, rng, [(0, 0.2), (1.2, 0.9), (1.6, 0.5), (2.2, 0), (length, 0)], crackle=0.4)
    roar = s.resonant(roar, "lowpass", s.exp_curve([(0, 800), (1.4, 8000), (length, 3000)], n), 0.7)
    thud = m.impact(rng, freq=60, decay=0.6, ratios=m.HOLLOW, hardness=0.3, size=1.5, noise=0.5)
    clank = m.impact(rng, freq=300, decay=0.3, ratios=m.PLATE, hardness=0.7, noise=0.2)
    hiss = m.hiss(s.samples(1.0), rng, [(0, 0), (0.05, 1), (1.0, 0)], 0.5)
    x = s.mix((norm(roar), 1.0), (norm(thud), 0.9, 1.5), (norm(clank), 0.3, 1.52), (norm(hiss), 0.15, 1.6))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.8), wet=0.35, tail=0.8)


@sound("ship/power_up", level=-18)
def power_up(rng, i):
    """飞船组装好、通电：继电器一个个吸合，变压器的嗡声和电机的尖音升起来，最后两声提示音。"""
    length = 1.9
    n = s.samples(length)
    relays = [(m.relay(rng, size=rng.uniform(0.8, 1.3), bright=0.6), 0.5, t0) for t0 in (0.0, 0.12, 0.26)]
    hum = m.transformer(n, rng, 50, 0.6) * s.envelope([(0, 0), (0.3, 0), (1.0, 1), (length, 0.8)], n)
    whine = s.sine(s.exp_curve([(0, 400), (1.2, 1200), (length, 1200)], n), n) * s.envelope([(0, 0), (0.3, 0), (1.0, 0.6), (length, 0)], n)
    beeps = []
    for t0, f in ((1.25, 880), (1.42, 1320)):
        bn = s.samples(0.12)
        beeps.append((s.sine(f, bn) * s.adsr(bn, 0.004, 0.02, 0.7, 0.04), 0.3, t0))
    x = s.mix((norm(hum), 0.5), (norm(whine), 0.12), *relays, *beeps)
    return fx.space(end(x), "cockpit", wet=0.25, tail=0.4)


@sound("ship/power_down", level=-18)
def power_down(rng, i):
    """停船拆回方块：电机的尖音落下去，嗡声散掉，最后继电器一声断开。"""
    length = 1.5
    n = s.samples(length)
    hum = m.transformer(n, rng, 50, 0.6) * s.envelope([(0, 0.8), (1.1, 0), (length, 0)], n)
    whine = s.sine(s.exp_curve([(0, 1200), (1.2, 180), (length, 180)], n), n) * s.envelope([(0, 0.6), (1.2, 0), (length, 0)], n)
    clack = m.relay(rng, size=1.4, bright=0.6)
    x = s.mix((norm(hum), 0.5), (norm(whine), 0.15), (clack, 0.6, 1.15))
    return fx.space(end(x), "cockpit", wet=0.25, tail=0.3)


# ---- 载具

@sound("vehicle/rover", loop=4.0, level=-21)
def rover(rng, i):
    """探索车：轮毂电机的转子哼声和减速齿轮的啮合声，轮子压过地面的低频和碎石。音高在代码里随车速变。"""
    n = s.samples(4.0 + XF + PRE)
    motor = m.electric_motor(n, rng, 4800, coils=8, gear_teeth=17, chassis=0.6, harshness=0.15, rotor=0.8, gears=0.5,
                             brushes=0.25, air=0.3)
    road = s.lowpass(s.brown(n, rng), 110, 2) * (1 + 0.5 * s.wander(n, rng, 3))
    gravel = s.highpass(s.crackle(n, rng, 60, width=0.0003), 1200) * (0.5 + 0.5 * s.wander(n, rng, 2))
    bumps = np.zeros(n)
    t = 0.3
    while t < n / s.SR - 0.5:
        hit = m.impact(rng, freq=rng.uniform(60, 85), decay=0.25, ratios=m.HOLLOW, hardness=0.25, noise=0.4)
        j = s.samples(t)
        e = min(n, j + len(hit))
        bumps[j:e] += norm(hit)[:e - j] * rng.uniform(0.4, 1.0)
        t += rng.uniform(0.6, 1.4)
    x = 0.5 * norm(motor) + 0.6 * norm(road) + 0.12 * norm(gravel) + 0.25 * bumps
    return warm(fx.space(x, "cockpit", wet=0.2))


@sound("vehicle/mech_jet", loop=3.0, level=-20)
def mech_jet(rng, i):
    """机甲的喷气背包：两个小喷口的喷流，比飞船的推进器亮、轻。"""
    n = s.samples(3.0 + XF + PRE)
    roar = m.rocket(n, rng, 0.85 * (1 + 0.08 * s.wander(n, rng, 3)), crackle=0.3)
    roar = s.highpass(roar, 150, 2)
    jet = m.hiss(n, rng, 0.8, 0.5)
    x = norm(roar) + 0.25 * norm(jet)
    return warm(fx.space(x, fx.outdoor_ir(rng, 0.4), wet=0.2))


@sound("vehicle/mech_step", variants=3, level=-16)
def mech_step(rng, i):
    """机甲的脚步：几吨重的脚砸下去的闷响，脚掌钢板的一声咔，腿上的伺服电机抬脚时嗡一下。"""
    thud = m.impact(rng, freq=rng.uniform(60, 75), decay=0.35, ratios=m.HOLLOW, hardness=0.25, size=1.6, noise=0.6)
    ground_n = s.samples(0.4)
    ground = s.lowpass(s.brown(ground_n, rng), 200, 2) * s.perc(ground_n, 0.002, 0.18)
    clank = m.impact(rng, freq=rng.uniform(380, 460), decay=0.22, ratios=m.PLATE, hardness=0.75, noise=0.15)
    servo_n = s.samples(0.4)
    rpm = m.servo_moves(servo_n, rng, [(0.0, 0.28, 0.8)], top_rpm=7000)
    whir = m.electric_motor(servo_n, rng, rpm, coils=6, gear_teeth=11, chassis=0.4, harshness=0.1, brushes=0.2, air=0.1)
    x = s.mix((norm(thud), 1.0), (norm(ground), 0.7), (norm(clank), 0.3, 0.015), (norm(whir), 0.15, 0.12))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.5), wet=0.25, tail=0.3)


@sound("vehicle/cannon", variants=3, level=-17)
def cannon(rng, i):
    """机甲的爆能炮：比光子炮粗的一声放电，炮管后坐。"""
    k = 1 + rng.uniform(-0.05, 0.05)
    zap = m.zap(rng, start=1700 * k, end=260 * k, length=0.2, bite=0.8, fm_index=3.0)
    thump = sub_drop(0.2, 90 * k, 45, 0.1, 0.001)
    sizzle = s.highpass(s.crackle(s.samples(0.3), rng, [(0, 1500), (0.3, 100)], width=0.0001), 2500)
    x = s.mix((s.softclip(norm(zap) * 1.5, 1.5), 1.0), (thump, 0.7), (norm(sizzle), 0.15, 0.01))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.4), wet=0.25, tail=0.3)


@sound("vehicle/laser", variants=2, level=-21)
def laser(rng, i):
    """探索车的采矿激光：每 10 刻（0.5 秒）放一段 1 秒；音高取 0.5 秒内整数个周期。"""
    n = s.samples(1.0)
    hum = s.harmonics(262.0, n, [1, 0.6, 0.5, 0.35, 0.25, 0.2]) * (1 + 0.1 * s.wander(n, rng, 6))
    sizzle = s.resonant(s.white(n, rng), "bandpass", 4500, 1.8) * (0.6 + 0.4 * s.wander(n, rng, 10))
    crack = s.highpass(s.crackle(n, rng, 90, width=0.0001), 3000)
    return hann(0.6 * norm(hum) + 0.3 * norm(sizzle) + 0.15 * crack)


@sound("vehicle/scan", level=-18)
def scan(rng, i):
    """车载扫描：雷达扫一圈的上扫音，一声带回声的"嘀"。"""
    n = s.samples(2.2)
    sweep_n = s.samples(0.5)
    sweep = s.sine(s.exp_curve([(0, 300), (0.5, 2400)], sweep_n), sweep_n) * s.envelope([(0, 0), (0.1, 1), (0.45, 1), (0.5, 0)], sweep_n)
    ping_n = s.samples(1.6)
    ping = (s.sine(1800.0, ping_n) + 0.2 * s.sine(3600.0, ping_n)) * s.perc(ping_n, 0.003, 0.5)
    ping = s.delay(ping, 0.22, feedback=0.45, mix=0.5, lowpass_hz=3000)
    whoosh = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 400), (0.5, 3000), (2.2, 3000)], n), 1.0)
    whoosh *= s.envelope([(0, 0), (0.4, 1), (0.6, 0), (2.2, 0)], n)
    x = s.mix((norm(sweep), 0.35), (norm(ping), 0.8, 0.5), (norm(whoosh), 0.15))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.5), wet=0.2, tail=0.4)


@sound("vehicle/submarine", loop=4.0, level=-21)
def submarine(rng, i):
    """潜艇：螺旋桨每秒 25 下打水的低沉"突突"，电机隔着艇壳的哼声，水流和偶尔的气泡；水里高频传不远。"""
    n = s.samples(4.0 + XF + PRE)
    blade = s.harmonics(25.0, n, [1, 0.7, 0.45, 0.3, 0.2, 0.12]) * (1 + 0.15 * s.wander(n, rng, 2))
    motor = s.lowpass(m.electric_motor(n, rng, 3600, coils=6, chassis=0.5, harshness=0.1, brushes=0.15, air=0.0), 1200, 2)
    flow = s.lowpass(s.pink(n, rng), 500, 2) * (1 + 0.3 * s.wander(n, rng, 1))
    bub = m.bubbles(n, rng, 8, (0.003, 0.012), 0.15)
    x = 0.6 * norm(blade) + 0.25 * norm(motor) + 0.5 * norm(flow) + 0.15 * norm(bub)
    x = s.lowpass(x, 2500, 2)
    return warm(fx.space(x, "cockpit", wet=0.3))


@sound("vehicle/sonar", variants=2, level=-16)
def sonar(rng, i):
    """声呐：一声干净的"嘀——"，在水里一遍遍回来，越来越闷。"""
    f0 = 1500.0 if i == 0 else 1250.0
    n = s.samples(3.6)
    ping = (s.sine(f0, n) + 0.15 * s.sine(2 * f0, n)) * s.perc(n, 0.004, 0.45)
    echo = s.delay(ping, 0.42, feedback=0.5, mix=0.55, lowpass_hz=1600)
    x = fx.reverb(echo, room_size=0.92, damping=0.75, wet=0.35)
    return s.lowpass(x, 4000, 2)


@sound("vehicle/torpedo", variants=2, level=-15)
def torpedo(rng, i):
    """鱼雷发射：压缩空气把它推出管子的一声"噗"、一团气泡，推进电机转起来、跑远。"""
    length = 1.5
    n = s.samples(length)
    whump = s.lowpass(s.brown(n, rng), 150, 2) * s.perc(n, 0.003, 0.25)
    air_burst = m.bubbles(n, rng, [(0, 900), (0.15, 300), (0.6, 20), (length, 0)], (0.002, 0.012), 0.25)
    rpm = s.envelope([(0, 0), (0.6, 9000), (length, 9000)], n)
    motor = m.electric_motor(n, rng, rpm, coils=4, chassis=0.2, harshness=0.1, brushes=0.1, air=0.2)
    motor = s.resonant(motor, "lowpass", s.exp_curve([(0, 3000), (length, 400)], n), 0.7) * s.envelope([(0, 0), (0.2, 1), (length, 0)], n)
    x = s.mix((norm(whump), 1.0), (norm(air_burst), 0.5), (norm(motor), 0.3))
    x = s.lowpass(x, 3500, 2)
    return fx.reverb(x, room_size=0.7, damping=0.8, wet=0.2, tail=0.5)


@sound("vehicle/summon", variants=2, level=-17)
def vehicle_summon(rng, i):
    """载具就位：一组和弦颤着升起来，噪声往上扫，最后机械一声落定、泄压。"""
    k = 1 + rng.uniform(-0.04, 0.04)
    length = 1.7
    n = s.samples(length)
    chord = sum(s.sine(f * k, n) for f in (330.0, 495.0, 742.5)) * (0.7 + 0.3 * s.sine(12.0, n))
    chord *= s.envelope([(0, 0), (0.9, 1), (1.05, 0.3), (length, 0)], n)
    riser = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 400), (1.0, 5000), (length, 5000)], n), 1.2)
    riser *= s.envelope([(0, 0), (1.0, 1), (1.05, 0), (length, 0)], n)
    clunk = m.impact(rng, freq=190 * k, decay=0.5, ratios=m.PLATE, hardness=0.6, noise=0.3)
    hiss = m.hiss(s.samples(0.6), rng, [(0, 1), (0.6, 0)], 0.5)
    x = s.mix((norm(chord), 0.35), (norm(riser), 0.25), (norm(clunk), 0.9, 1.0), (norm(hiss), 0.12, 1.02))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.4), wet=0.25, tail=0.4)


# ---- 货船

@sound("freighter/warp_in", level=-13)
def warp_in(rng, i):
    """货船跃入：空间被撕开的呼啸，一声巨大的爆裂和长长的低频，几百米长的船体受力呻吟。"""
    length = 7.0
    n = s.samples(length)
    tear_n = s.samples(0.5)
    tear = s.resonant(s.pink(tear_n, rng), "bandpass", s.exp_curve([(0, 200), (0.5, 4000)], tear_n), 0.9) * (s.times(tear_n) / 0.5) ** 2
    crack = s.mix((m.n_wave(rng, 0.006), 0.7), (burst(rng, 0.15, 1200, 12000, 0.0003, 0.08), 0.5))
    boom = sub_drop(3.0, 48, 31, 2.5, 0.005)
    blast = m.explosion(rng, size=2.5, debris=0.1, tail=4.0)
    groan_n = s.samples(5.5)
    groan = s.resonant(s.brown(groan_n, rng), "bandpass", s.exp_curve([(0, 90), (3.0, 55), (5.5, 50)], groan_n), 4.0)
    groan = match(s.modes(groan, [40 * r for r in m.HOLLOW], [2.0, 1.6, 1.3, 1.0, 0.9, 0.7, 0.6]), groan)
    groan *= s.envelope([(0, 0), (0.5, 1), (3.5, 0.6), (5.5, 0)], groan_n)
    x = s.mix((norm(tear), 0.6, 0.0), (crack, 0.8, 0.5), (boom, 1.0, 0.5), (norm(blast), 0.8, 0.5), (norm(groan), 0.35, 1.2))
    x = s.fit(x, n)
    return fx.space(end(x), fx.outdoor_ir(rng, 1.8), wet=0.45, tail=1.5)


@sound("freighter/terminal", variants=3, level=-20)
def terminal(rng, i):
    """船上终端确认：按键一声轻咔，两三声往上走的提示音，从小喇叭里出来。"""
    seq = [(880.0, 1175.0), (660.0, 880.0, 1320.0), (1047.0, 1397.0)][i]
    parts = [(m.relay(rng, size=0.7, bright=0.6), 0.3, 0.0)]
    t0 = 0.03
    for f in seq:
        tn = s.samples(0.08)
        tone = (s.sine(f, tn) + 0.2 * s.square(f, tn, 8)) * s.adsr(tn, 0.003, 0.02, 0.7, 0.025)
        parts.append((tone, 0.5, t0))
        t0 += 0.1
    x = s.bandpass(s.mix(*parts), 300, 6000, 2)
    return fx.space(end(x), "corridor", wet=0.15, tail=0.3)


# ---- 海盗

@sound("pirate/alarm", level=-15)
def alarm(rng, i):
    """海盗来袭：驾驶舱的两音警报，从小喇叭里出来，有点破。"""
    parts = []
    for j in range(6):
        f = 640.0 if j % 2 == 0 else 820.0
        tn = s.samples(0.26)
        tone = s.square(f, tn, 10) * s.adsr(tn, 0.01, 0.02, 0.85, 0.03)
        parts.append((tone, 1.0, j * 0.28))
    x = s.mix(*parts)
    x = s.softclip(s.bandpass(x, 350, 4000, 2) * 2, 2)
    return fx.space(end(x), "cockpit", wet=0.25, tail=0.4)


@sound("pirate/scan", level=-18)
def pirate_scan(rng, i):
    """海盗在扫描货舱：一道调频的扫描音来回扫，两声回波，夹着数据的嘀嘀声。"""
    length = 2.0
    n = s.samples(length)
    carrier = s.envelope([(0, 900), (1.0, 1600), (2.0, 900)], n)
    index = 1.5 + 2.0 * np.abs(s.wander(n, rng, 3))
    sweep = s.fm(carrier, 0.5, index, n) * s.envelope([(0, 0), (0.1, 1), (1.8, 1), (2.0, 0)], n)
    pings = []
    for t0 in (0.3, 1.1):
        pn = s.samples(0.3)
        pings.append((s.sine(2100.0, pn) * s.perc(pn, 0.002, 0.25), 0.5, t0))
    chatter = m.computer(n, rng, rate=14, base=1400, spread=1.8, fans=0, clicks=0.2) * s.envelope([(0, 0), (0.3, 1), (1.7, 1), (2.0, 0)], n)
    x = s.mix((norm(sweep), 0.35), (norm(chatter), 0.3), *pings)
    return fx.space(end(x), "cockpit", wet=0.25, tail=0.3)


@sound("pirate/laser", variants=3, level=-16)
def pirate_laser(rng, i):
    """海盗战机开火：比光子炮糙的一声放电，带着失真的毛刺。"""
    k = 1 + rng.uniform(-0.05, 0.05)
    zap = m.zap(rng, start=1900 * k, end=480 * k, length=0.12, bite=0.9, fm_index=4.0)
    grit = s.softclip(norm(zap) * 3, 2)
    thump = sub_drop(0.1, 140 * k, 70, 0.06, 0.001)
    x = s.mix((grit, 1.0), (thump, 0.5))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.3), wet=0.2, tail=0.25)
