"""深空和引力抓取器的声音（ADR-0034 §四、§七）。

残骸里的声音都是顺着结构传过来的：真空里没有空气，听到的是船体、宇航服和脚下金属的振动，
所以混响用大的金属空腔（机库、走廊），高频比在空气里少。
- 警报是航天器的主警报那种两音的"呜—哇"，从一只坏了一半的扬声器里出来，带点破音。
- 金属呻吟是结构受力时的粘滑摩擦：一串不规则的微小滑动去激一组低频的板模态，音高慢慢往上挤。
- 熔毁是堆芯炸开：一下很重的低频冲击，紧跟一声电弧的爆裂，然后是整条船身的长长的金属回响和碎片噼啪。
- 堆芯的嗡鸣是冷却泵和电源的工频嗡声加一层细碎的电噼啪，两头淡入淡出，隔一会儿放一次。
- 碎片碎裂：一下金属撞击，几块小碎片接着叮叮当当散开。
- 牵引光束开、关：电源起来、落下的扫频，加一声继电器；收进货舱是一下吸气似的"呼"，接着货舱门闷闷一合。
- 舱门：锁扣咔哒，泄压的嘶声慢慢弱下去，最后门沉沉地落位。抛货是一下气动弹射的猛嘶加一声闷响。
- 引力抓取器：抓住是往上走的"嗡"，抓着是两条差一点点的音拍出来的低嗡（循环），放下是往下掉的"嗡"；
  扔出去是线圈一级级放电加一声闷推；推开是一声很沉的"轰"往外扩；扔出去的东西撞上是一下实打实的闷击。
"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["deepspace"]

# 循环开头先多算这么久再扔掉：混响和滤波器的起振不留在循环里
WARMUP = 0.4


def norm(x: np.ndarray) -> np.ndarray:
    return x / (np.max(np.abs(x)) + 1e-9)


def per(f: float, length: float) -> float:
    """把频率取整到循环长度里整数个周期：有音高的成分首尾相位接得上。"""
    return max(1.0, round(f * length)) / length


def hull(x: np.ndarray, wet: float = 0.3, tail: float = 0.8) -> np.ndarray:
    """顺着船体传过来：高频少一些，大金属空腔的混响。"""
    return fx.space(s.lowpass(x, 7000, 2), "hangar", wet=wet, tail=tail)


def suit(x: np.ndarray, wet: float = 0.12, tail: float = 0.15) -> np.ndarray:
    """手里的设备：宇航服里听，空间很小。"""
    return fx.space(x, "cockpit", wet=wet, tail=tail)


def sweep(length: float, points, amps=(1.0, 0.5, 0.3, 0.15), attack: float = 0.01, release: float = 0.1) -> np.ndarray:
    """带几个谐波的扫频音。"""
    n = s.samples(length)
    f = s.exp_curve(points, n)
    return s.harmonics(f, n, list(amps)) * s.adsr(n, attack, 0.0, 1.0, release)


def thump(f0: float, f1: float, seconds: float, decay: float) -> np.ndarray:
    n = s.samples(seconds)
    return s.sine(s.exp_curve([(0, f0), (seconds * 0.6, f1), (seconds, f1)], n), n) * s.perc(n, 0.001, decay)


# ---------------------------------------------------------------- 残骸

@sound("deepspace/siren", level=-18)
def siren(rng, i):
    """主警报："呜—哇"两下，方波从 560 Hz 扫到 880 Hz 再落回来，扬声器坏了一半：窄带、破音、嗡嗡的底噪。"""
    length = 1.9
    n = s.samples(length)
    t = s.times(n)
    cycle = 0.9
    ph = (t % cycle) / cycle
    f = np.where(ph < 0.55, 560 * (880 / 560) ** (ph / 0.55), 880 * (560 / 880) ** ((ph - 0.55) / 0.45))
    tone = s.square(f, n, 15) * 0.7 + s.saw(f * 1.005, n, 15) * 0.3
    gate = np.clip(np.minimum(ph / 0.02, (1 - ph) / 0.04), 0, 1)
    gate *= np.interp(t, [0, length - 0.15, length], [1, 1, 0])
    speaker = s.resonant(s.bandpass(tone * gate, 380, 3600, 2), "peak", 1400, 1.2, 6)
    speaker = s.softclip(speaker * 2.2, 1.6)
    buzz = s.harmonics(100.0, n, [0.4, 0.3, 0.2, 0.1]) * 0.05 * gate
    return hull(speaker + buzz, wet=0.35, tail=1.0)


@sound("deepspace/groan", variants=3, level=-20)
def groan(rng, i):
    """金属呻吟：结构受力，一串不规则的微小滑动激起几块大板的低频共鸣，音高慢慢往上挤，最后"嘎"地松一下。"""
    length = 2.4 + 0.3 * i
    n = s.samples(length)
    t = s.times(n)
    rate = s.curve([(0, 15), (length * 0.4, 70 + 20 * i), (length * 0.85, 110), (length, 20)], n)
    slips = s.crackle(n, rng, rate, width=0.0015, skew=0.4)
    k = rng.uniform(0.85, 1.2)
    body = s.modes(slips, [58 * k, 91 * k, 133 * k, 187 * k, 262 * k, 341 * k], [1.4, 1.2, 1.0, 0.8, 0.6, 0.45])
    centre = s.exp_curve([(0, 85 * k), (length * 0.8, 150 * k), (length, 130 * k)], n)
    bow = s.resonant(s.brown(n, rng), "bandpass", centre, 9) * np.interp(t, [0, 0.3, length * 0.8, length], [0, 1, 1, 0.2])
    snap = m.impact(rng, freq=rng.uniform(110, 160), decay=0.9, ratios=m.PLATE, hardness=0.35, size=2.0, noise=0.2)
    x = s.mix((norm(body), 1.0), (norm(bow), 0.6), (s.lowpass(snap, 1500, 2), 0.5, length * 0.82))
    return hull(s.taper(x, 0.3), wet=0.4, tail=1.2)


@sound("deepspace/meltdown", level=-12)
def meltdown(rng, i):
    """熔毁：一下很重的低频冲击，紧跟电弧爆裂的"啪"，整条船身长长地嗡着回响，碎片噼里啪啦往外飞。"""
    blast = m.explosion(rng, size=3.5, distance=0.3, debris=1.0, tail=3.5)
    zap = m.zap(rng, start=5000, end=200, length=0.5, bite=0.9)
    n = s.samples(5.0)
    t = s.times(n)
    ring_out = s.modes(s.white(s.samples(0.02), rng), [47, 71, 103, 139, 188, 241, 319], [4.0, 3.6, 3.0, 2.6, 2.0, 1.6, 1.2])
    ring_out = s.fit(ring_out, n) * np.interp(t, [0, 0.1, 5.0], [0, 1, 0.3])
    debris = s.highpass(s.crackle(n, rng, [(0, 400), (1.0, 120), (3.5, 15), (5.0, 0)], width=0.0003), 1800)
    x = s.mix((norm(blast), 1.0), (norm(zap), 0.45, 0.02), (norm(ring_out), 0.4, 0.05), (debris, 0.3, 0.1))
    return hull(s.taper(x, 1.0), wet=0.35, tail=1.8)


@sound("deepspace/reactor_hum", variants=2, level=-24)
def reactor_hum(rng, i):
    """堆芯嗡鸣：冷却泵和电源 120 Hz 的嗡声，一层细碎的电噼啪，两头淡入淡出（隔一会儿放一次，叠起来是连着的）。"""
    length = 2.8
    n = s.samples(length)
    t = s.times(n)
    hum = s.harmonics(120.0 + 2 * i, n, [1.0, 0.6, 0.45, 0.3, 0.2, 0.12, 0.08]) * (1 + 0.15 * s.wander(n, rng, 2))
    pump = s.lowpass(s.brown(n, rng), 140, 2) * (0.7 + 0.3 * np.sin(2 * np.pi * 1.6 * t))
    crackle = s.resonant(s.crackle(n, rng, 60, width=0.0002), "bandpass", 4200, 0.9)
    x = (0.5 * norm(hum) + 0.4 * norm(pump) + 0.12 * norm(crackle)) * np.hanning(n)
    return hull(x, wet=0.25, tail=0.4)


@sound("deepspace/debris_break", variants=3, level=-16)
def debris_break(rng, i):
    """碎片碎裂：一下金属撞击，几块小碎片接着叮叮当当散开，底下一点断裂的嘎吱。"""
    hit = m.impact(rng, freq=rng.uniform(260, 420), decay=0.6, ratios=m.PLATE, hardness=0.7, size=1.2, noise=0.5)
    parts = [(norm(hit), 1.0)]
    for k in range(5 + i):
        bit = m.impact(rng, freq=rng.uniform(900, 2600), decay=rng.uniform(0.12, 0.3), ratios=m.PLATE, hardness=0.85,
                       size=0.3, noise=0.2)
        parts.append((norm(bit), rng.uniform(0.15, 0.35), rng.uniform(0.04, 0.7)))
    n = s.samples(0.25)
    crunch = s.bandpass(s.crackle(n, rng, 900, width=0.0004), 600, 5000) * s.perc(n, 0.002, 0.2)
    parts.append((norm(crunch), 0.4))
    return hull(s.taper(s.mix(*parts), 0.1), wet=0.3, tail=0.6)


# ---------------------------------------------------------------- 牵引光束、舱门、抛货

@sound("deepspace/tractor_on", level=-20)
def tractor_on(rng, i):
    """牵引光束开：继电器"咔"一声，电源从低往高扫上去，一层微光似的嘶声跟着亮起来，落到一个稳定的嗡声。"""
    click = m.relay(rng, size=1.2)
    rise = sweep(1.3, [(0, 140), (0.7, 620), (1.3, 560)], attack=0.02, release=0.35)
    n = s.samples(1.3)
    shimmer = s.resonant(s.white(n, rng), "bandpass", s.exp_curve([(0, 1500), (0.8, 6000), (1.3, 5000)], n), 3)
    shimmer *= s.envelope([(0, 0), (0.5, 1), (1.3, 0)], n)
    x = s.mix((norm(click), 0.6), (norm(rise), 0.8, 0.03), (norm(shimmer), 0.25, 0.05))
    return suit(s.taper(x, 0.2), wet=0.2, tail=0.3)


@sound("deepspace/tractor_off", level=-20)
def tractor_off(rng, i):
    """牵引光束关：电源往下掉，嘶声先没，最后继电器"咔"一声断开。"""
    fall = sweep(0.9, [(0, 560), (0.9, 110)], attack=0.005, release=0.4)
    click = m.relay(rng, size=1.0)
    x = s.mix((norm(fall), 0.8), (norm(click), 0.6, 0.75))
    return suit(s.taper(x, 0.15), wet=0.2, tail=0.3)


@sound("deepspace/tractor_stow", variants=3, level=-20)
def tractor_stow(rng, i):
    """收进货舱：一下吸进去的"呼"（噪声往下扫），接着货舱门闷闷地一合，锁扣轻轻一响。"""
    n = s.samples(0.35)
    suck = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 3200 + 300 * i), (0.35, 500)], n), 2.0)
    suck *= s.envelope([(0, 0), (0.25, 1), (0.35, 0)], n)
    clunk = m.impact(rng, freq=rng.uniform(95, 130), decay=0.35, ratios=m.HOLLOW, hardness=0.3, size=1.5, noise=0.2)
    latch = m.relay(rng, size=0.6, bright=1.2)
    x = s.mix((norm(suck), 0.5), (s.lowpass(norm(clunk), 1800, 2), 1.0, 0.3), (norm(latch), 0.3, 0.42))
    return hull(s.taper(x, 0.1), wet=0.2, tail=0.4)


@sound("deepspace/hatch", variants=2, level=-18)
def hatch(rng, i):
    """舱门：锁扣"咔哒"两下，泄压的嘶声一冲而出、慢慢弱下去，最后门沉沉地落位。"""
    latch1 = m.relay(rng, size=1.6)
    latch2 = m.relay(rng, size=1.4)
    n = s.samples(1.1)
    vent = m.hiss(n, rng, pressure=[(0, 0), (0.03, 1.0), (1.1, 0.0)], brightness=0.5 - 0.1 * i)
    seat = m.impact(rng, freq=rng.uniform(70, 95), decay=0.6, ratios=m.HOLLOW, hardness=0.25, size=2.0, noise=0.3)
    x = s.mix((norm(latch1), 0.6), (norm(latch2), 0.5, 0.09), (norm(vent), 0.6, 0.15), (s.lowpass(norm(seat), 1200, 2), 1.0, 1.15))
    return fx.space(s.taper(x, 0.2), "corridor", wet=0.3, tail=0.6)


@sound("deepspace/jettison", variants=2, level=-16)
def jettison(rng, i):
    """抛货：气动弹射一下猛嘶，货舱门"嗵"地一开，东西擦着舱口出去。"""
    n = s.samples(0.6)
    blast = m.hiss(n, rng, pressure=[(0, 1.0), (0.6, 0.0)], brightness=0.7) * s.perc(n, 0.003, 0.5)
    boom = thump(140, 55, 0.4, 0.3)
    scrape = s.bandpass(s.crackle(s.samples(0.3), rng, 500, width=0.0005), 900, 4000) * s.perc(s.samples(0.3), 0.01, 0.25)
    x = s.mix((norm(boom), 1.0), (norm(blast), 0.6, 0.01), (norm(scrape), 0.25, 0.08))
    return hull(s.taper(x, 0.1), wet=0.25, tail=0.5)


# ---------------------------------------------------------------- 引力抓取器

@sound("deepspace/gravity_grab", variants=2, level=-18)
def gravity_grab(rng, i):
    """抓住：一下往上走的"嗡"（调频音从 140 Hz 扫到 330 Hz），底下一声软的闷推，夹一点电噼啪。"""
    length = 0.5
    n = s.samples(length)
    f = s.exp_curve([(0, 140 + 15 * i), (0.25, 330 + 20 * i), (length, 300 + 20 * i)], n)
    env = s.adsr(n, 0.02, 0.1, 0.6, 0.2)
    tone = s.fm(f, 2.0, 1.5 * env, n) * env
    crackle = s.highpass(s.crackle(n, rng, 150, width=0.0002), 3000) * env * 0.3
    x = s.mix((norm(tone), 0.8), (norm(thump(110, 60, 0.25, 0.2)), 0.5), (crackle, 1.0))
    return suit(s.taper(x, 0.08))


@sound("deepspace/gravity_hold", loop=3.0, level=-24, power=False)
def gravity_hold(rng, i):
    """抓着：两条差半赫兹的低音拍出来一起一伏的嗡，上面一层很轻的、慢慢起伏的高频微光（3 秒循环）。"""
    length = 3.0
    n = s.samples(length + 0.4 + WARMUP)
    t = s.times(n) - WARMUP
    a = s.harmonics(per(110, length), n, [1.0, 0.5, 0.35, 0.2, 0.1])
    b = s.harmonics(per(110, length) + 1 / length, n, [0.8, 0.4, 0.25])
    wobble = 0.8 + 0.2 * np.sin(2 * np.pi * per(1.3, length) * t)
    shimmer = s.resonant(s.white(n, rng), "bandpass", 3600, 4) * (0.5 + 0.5 * np.sin(2 * np.pi * per(0.67, length) * t))
    x = (0.55 * norm(a) + 0.45 * norm(b)) * wobble + 0.04 * norm(shimmer)
    return suit(x, wet=0.1, tail=0.0)[s.samples(WARMUP):]


@sound("deepspace/gravity_drop", variants=2, level=-20)
def gravity_drop(rng, i):
    """放下：往下掉的"嗡"，越来越暗，最后很轻地松开。"""
    length = 0.55
    n = s.samples(length)
    f = s.exp_curve([(0, 320 - 20 * i), (length, 90)], n)
    env = s.adsr(n, 0.005, 0.1, 0.5, 0.35)
    tone = s.lowpass(s.fm(f, 2.0, 1.0 * env, n) * env, 2500, 2)
    return suit(s.taper(tone, 0.08))


@sound("deepspace/gravity_throw", variants=2, level=-14)
def gravity_throw(rng, i):
    """扔出去：线圈一级级放电"嗵嗵嗵"越来越快，接着一声闷推，东西带着一阵往上扫的风声飞出去。"""
    coils = m.coil_stages(rng, stages=4, spacing=0.03, accel=0.7, pitch=180 + 30 * i)
    push = thump(160, 45, 0.45, 0.35)
    n = s.samples(0.6)
    whoosh = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 600), (0.35, 3500), (0.6, 2000)], n), 1.5)
    whoosh *= s.envelope([(0, 0), (0.12, 1), (0.6, 0)], n)
    x = s.mix((norm(coils), 0.6), (norm(push), 1.0, 0.08), (norm(whoosh), 0.45, 0.1))
    return suit(s.taper(x, 0.1), wet=0.15, tail=0.3)


@sound("deepspace/gravity_push", variants=2, level=-14)
def gravity_push(rng, i):
    """推开：一声很沉的"轰"从 120 Hz 掉到 35 Hz，宽带的气浪往外扩，尾巴一层细碎的电噼啪。"""
    boom = thump(120 + 10 * i, 35, 0.8, 0.55)
    n = s.samples(0.8)
    wave = s.resonant(s.pink(n, rng), "lowpass", s.exp_curve([(0, 5000), (0.8, 300)], n), 0.9) * s.perc(n, 0.004, 0.6)
    crackle = s.highpass(s.crackle(n, rng, [(0, 300), (0.8, 0)], width=0.0002), 2500) * 0.25
    x = s.mix((norm(boom), 1.0), (norm(wave), 0.6), (crackle, 1.0, 0.05))
    return suit(s.taper(x, 0.15), wet=0.2, tail=0.4)


@sound("deepspace/gravity_impact", variants=3, level=-14)
def gravity_impact(rng, i):
    """撞上：一下实打实的闷击（低频的身体撞击加一点金属的棱），碎屑擦一下。"""
    hit = m.impact(rng, freq=rng.uniform(150, 240), decay=0.35, ratios=m.HOLLOW, hardness=0.45, size=1.6, noise=0.6)
    body = thump(95, 45, 0.3, 0.2)
    clank = m.impact(rng, freq=rng.uniform(700, 1100), decay=0.25, ratios=m.PLATE, hardness=0.8, size=0.4, noise=0.1)
    x = s.mix((s.lowpass(norm(hit), 2500, 2), 1.0), (norm(body), 0.7), (norm(clank), 0.2 + 0.1 * i))
    return fx.space(s.taper(x, 0.08), fx.outdoor_ir(rng, 0.5), wet=0.15, tail=0.3)
