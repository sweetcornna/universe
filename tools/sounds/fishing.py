"""M19 钓鱼（A）的声音（ADR-0034 §一、ADR-0029）。

钓鱼器是多功能工具上的附件：一节伸缩竿和一只带棘轮的绕线轮。听到的都是实在的东西：竿梢甩出去的风声，
线轮放线的咔咔声，浮标落水的"扑通"，鱼咬钩时浮标被拽下去的一声闷响，钓线绷紧的嗡嗡和吱嘎，断线的脆响，
鱼出水时哗啦的水声。水声按物理来：落水的气泡按 Minnaert 频率（≈3.26/半径）发声，往上滑；溅起的水是一阵
短的高频噪声。只有钓上稀有的鱼时加一段很短的铃声，提示这一条不一般。
"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["fishing"]


def outdoors(x: np.ndarray, rng, size: float, wet: float, tail: float) -> np.ndarray:
    return fx.space(x, fx.outdoor_ir(rng, size), wet=wet, tail=tail)


def plop(radius: float, seconds: float = 0.18, rise: float = 0.9) -> np.ndarray:
    """一个大气泡被水面拍出来的"扑"：Minnaert 频率的正弦，很快往上滑、很快衰减。"""
    n = s.samples(seconds)
    f0 = 3.26 / radius
    f = s.exp_curve([(0, f0), (seconds, f0 * (1 + rise))], n)
    return s.sine(f, n) * s.perc(n, 0.001, seconds * 0.35)


def splash(rng, seconds: float, size: float = 1.0, bright: float = 1.0) -> np.ndarray:
    """溅起来的水：一阵带通的噪声，开头最响，越大的水花越低越长；里面夹着一串小气泡。"""
    n = s.samples(seconds)
    env = s.perc(n, 0.004 * size, seconds * 0.3 * size)
    noise = s.resonant(s.white(n, rng), "bandpass", 2600 * bright / (0.6 + 0.4 * size), 0.7)
    spray = s.highpass(s.white(n, rng), 5000) * s.perc(n, 0.001, 0.05 * size)
    bub = m.bubbles(n, rng, [(0, 260 * size), (seconds * 0.4, 40), (seconds, 2)], radius=(0.0015, 0.006 * size), rise=0.2)
    return noise * env * 0.8 + spray * 0.35 + bub * 0.5


def drips(rng, seconds: float, rate: float) -> np.ndarray:
    """出水以后往下滴的水：稀疏的小气泡"嘀嗒"，越来越疏。"""
    n = s.samples(seconds)
    return m.bubbles(n, rng, [(0, rate), (seconds, rate * 0.15)], radius=(0.0012, 0.0035), rise=0.35)


def pluck(f: float, seconds: float, rng, damping: float = 0.25, feedback: float = 0.985) -> np.ndarray:
    """拨弦（Karplus-Strong）：一小段噪声送进梳状滤波器，钓线绷紧、断线时的"嘣"。"""
    n = s.samples(seconds)
    excite = np.zeros(n)
    k = s.samples(0.003)
    excite[:k] = rng.uniform(-1, 1, k) * np.hanning(k * 2)[k:]
    x = s.comb(excite, 1 / f, feedback, damping)
    return s.taper(x / (np.max(np.abs(x)) + 1e-9), seconds * 0.4)


def ratchet(rng, clicks: int, spacing: float, bright: float = 1.0) -> np.ndarray:
    """线轮的棘轮：一颗颗棘爪打在齿上的"咔"，金属小件的模态撞击。"""
    parts = []
    t = 0.0
    for _ in range(clicks):
        click = m.impact(rng, freq=rng.uniform(2600, 3400) * bright, decay=0.025, ratios=m.PLATE[:5], hardness=0.95,
                         size=0.3, noise=0.4)
        parts.append((click, rng.uniform(0.6, 1.0), t))
        t += spacing * rng.uniform(0.85, 1.15)
    return s.mix(*parts)


def whoosh(rng, seconds: float, top: float, low: float) -> np.ndarray:
    """细竿划过空气：一阵往下扫的带通噪声。"""
    n = s.samples(seconds)
    f = s.exp_curve([(0, top), (seconds * 0.5, (top * low) ** 0.5), (seconds, low)], n)
    env = s.envelope([(0, 0), (seconds * 0.3, 1.0), (seconds, 0)], n)
    return s.resonant(s.pink(n, rng), "bandpass", f, 2.2) * env


def chime(notes, seconds: float, gap: float, decay: float, bright: float = 1.0) -> np.ndarray:
    """几声叠起来的铃（调频），提示音用。"""
    parts = []
    for i, f in enumerate(notes):
        n = s.samples(seconds)
        env = s.perc(n, 0.002, decay)
        tone = s.fm(f, 3.5, 1.1 * bright * env, n) * env + 0.25 * s.sine(f * 2, n) * s.perc(n, 0.002, decay * 0.5)
        parts.append((tone, 1.0 / (1 + 0.15 * i), i * gap))
    return s.mix(*parts)


@sound("fishing/cast", variants=2, level=-21)
def cast(rng, i):
    """甩竿：竿梢划过空气的"嗖"，线轮放线时棘轮越转越快、再慢下来的一串咔咔声。"""
    k = 1 + rng.uniform(-0.06, 0.06)
    sweep = whoosh(rng, 0.28, 2600 * k, 500 * k)
    spacing = 0.016 if i == 0 else 0.013
    clicks = ratchet(rng, 34, spacing, bright=1.15)
    clicks *= s.fit(s.envelope([(0, 0.2), (0.15, 1.0), (0.9, 0.0)], s.samples(0.9)), len(clicks))
    x = s.mix((sweep, 1.0), (clicks, 0.22, 0.12))
    return outdoors(x, rng, 0.4, 0.15, 0.3)


@sound("fishing/land", variants=3, level=-21)
def land(rng, i):
    """浮标落水："扑通"一声，小水花，几个气泡冒上来。"""
    r = rng.uniform(0.005, 0.008)
    x = s.mix((plop(r, 0.2), 0.9), (splash(rng, 0.45, size=0.6, bright=1.1), 0.7),
              (plop(r * 0.6, 0.12, 1.2), 0.35, rng.uniform(0.04, 0.08)))
    return outdoors(x, rng, 0.5, 0.18, 0.35)


@sound("fishing/bite", variants=2, level=-16)
def bite(rng, i):
    """咬钩：浮标被一下拽到水下，"咕咚"一个低沉的大气泡，紧接着一个小水花，线上传来一下轻微的弹动。"""
    r = rng.uniform(0.011, 0.016)
    gulp = plop(r, 0.3, 0.6)
    tug = pluck(rng.uniform(150, 190), 0.25, rng, damping=0.5, feedback=0.95)
    x = s.mix((gulp, 1.0), (splash(rng, 0.35, size=0.5, bright=0.8), 0.45, 0.02), (tug, 0.25, 0.05))
    return outdoors(x, rng, 0.5, 0.15, 0.3)


@sound("fishing/hook", variants=2, level=-16)
def hook(rng, i):
    """钩住：竿一扬，钓线一下绷紧"嘣"地一响，棘轮锁住的"咔哒"，水面翻起一个水花。"""
    f = rng.uniform(210, 260)
    twang = pluck(f, 0.5, rng, damping=0.2, feedback=0.99)
    lock = s.mix((ratchet(rng, 2, 0.03, bright=0.9), 1.0))
    x = s.mix((whoosh(rng, 0.14, 1800, 700), 0.5), (twang, 0.6, 0.05), (lock, 0.5, 0.06),
              (splash(rng, 0.5, size=0.9), 0.75, 0.04))
    return outdoors(x, rng, 0.5, 0.15, 0.35)


@sound("fishing/reel", variants=3, level=-25)
def reel(rng, i):
    """收线：按住时一下一下放，三四颗棘爪的咔咔，带一点齿轮的转动声。音高在游戏里跟着张力走。"""
    clicks = ratchet(rng, 3 + i % 2, 0.022)
    n = s.samples(0.12)
    whir = s.resonant(s.white(n, rng), "bandpass", 1400, 3.0) * s.envelope([(0, 0), (0.02, 1), (0.12, 0)], n)
    return s.mix((clicks, 1.0), (whir, 0.25))


@sound("fishing/strain", variants=2, level=-23)
def strain(rng, i):
    """钓线绷紧：线在导线环上一下一下地粘滑，吱嘎的嗡声，竿身跟着共鸣。"""
    seconds = 0.7
    n = s.samples(seconds)
    f0 = rng.uniform(170, 220)
    f = f0 * (1 + 0.04 * s.wander(n, rng, 6)) * s.exp_curve([(0, 0.92), (seconds, 1.06)], n)
    stick = s.saw(f, n, 24) * (0.7 + 0.3 * np.abs(s.wander(n, rng, 18)))
    body = s.resonant(stick, "bandpass", 900, 2.5) + 0.4 * s.resonant(stick, "bandpass", 2300, 4.0)
    env = s.envelope([(0, 0), (0.08, 1.0), (0.55, 0.8), (seconds, 0)], n)
    return fx.space(s.lowpass(body * env, 6000), "casing", wet=0.08, tail=0.1)


@sound("fishing/dash", variants=3, level=-18)
def dash(rng, i):
    """鱼猛冲：水面上连着几下乱拍的水花，线被拖着发出一声短促的嘶。"""
    parts = []
    t = 0.0
    for _ in range(3 + i):
        parts.append((splash(rng, 0.35, size=rng.uniform(0.7, 1.2), bright=rng.uniform(0.8, 1.1)), rng.uniform(0.5, 1.0), t))
        t += rng.uniform(0.07, 0.14)
    n = s.samples(0.4)
    zing = s.resonant(s.white(n, rng), "bandpass", s.exp_curve([(0, 3800), (0.4, 2400)], n), 6.0) * s.perc(n, 0.01, 0.15)
    parts.append((zing, 0.3, 0.02))
    return outdoors(s.mix(*parts), rng, 0.5, 0.18, 0.35)


@sound("fishing/snap", variants=2, level=-15)
def snap(rng, i):
    """断线：一声脆响，松掉的线"咻"地甩回来，线轮空转一小串咔咔。"""
    n = s.samples(0.05)
    crack = s.highpass(s.white(n, rng), 2500) * s.perc(n, 0.0002, 0.01)
    ping = pluck(rng.uniform(700, 900), 0.35, rng, damping=0.1, feedback=0.97)
    lash = whoosh(rng, 0.3, 3200, 900)
    spin = ratchet(rng, 10, 0.018, bright=1.2)
    spin *= s.fit(s.envelope([(0, 1.0), (1, 0.0)], s.samples(1)), len(spin))
    x = s.mix((crack, 1.0), (ping, 0.45), (lash, 0.5, 0.03), (spin, 0.2, 0.08))
    return outdoors(x, rng, 0.4, 0.15, 0.3)


@sound("fishing/escape", variants=2, level=-21)
def escape(rng, i):
    """脱钩：最后一个水花，然后几个气泡越冒越远；线松下来，轻轻一声风。"""
    x = s.mix((splash(rng, 0.5, size=0.9), 1.0), (drips(rng, 0.8, 18), 0.45, 0.15), (whoosh(rng, 0.35, 1400, 500), 0.25, 0.05))
    return outdoors(s.lowpass(x, 7000), rng, 0.6, 0.2, 0.4)


@sound("fishing/catch", variants=2, level=-17)
def catch(rng, i):
    """钓上来：鱼哗啦一下被拽出水面，身上的水往下淌、往下滴；收尾两声轻轻的拨弦，鱼进了包。"""
    out = splash(rng, 0.6, size=1.1, bright=1.0)
    pour = s.resonant(s.white(s.samples(0.5), rng), "bandpass", 1800, 0.8) * s.perc(s.samples(0.5), 0.02, 0.18)
    tune = [392.0, 587.3] if i == 0 else [440.0, 659.3]
    notes = s.mix((pluck(tune[0], 0.5, rng, damping=0.3), 1.0), (pluck(tune[1], 0.6, rng, damping=0.3), 0.9, 0.09))
    x = s.mix((out, 1.0), (pour, 0.4, 0.08), (drips(rng, 0.7, 22), 0.4, 0.25), (notes, 0.22, 0.32))
    return outdoors(x, rng, 0.5, 0.15, 0.35)


@sound("fishing/trophy", level=-15)
def trophy(rng, i):
    """钓上稀有的鱼：一个大水花，接一段往上走的三声铃，尾巴带一点亮闪闪的余音。"""
    out = splash(rng, 0.8, size=1.4, bright=0.9)
    bells = chime([523.3, 659.3, 784.0, 1046.5], 1.4, 0.11, 0.9)
    n = s.samples(1.4)
    shimmer = s.resonant(s.white(n, rng), "bandpass", 7000, 2.0) * s.envelope([(0, 0), (0.4, 0.4), (1.4, 0)], n)
    x = s.mix((out, 0.9), (bells, 0.45, 0.18), (shimmer, 0.06, 0.25))
    return outdoors(x, rng, 0.7, 0.22, 0.6)


@sound("fishing/release", variants=2, level=-21)
def release(rng, i):
    """放生：鱼滑回水里，轻轻的一声水响，尾巴一摆带起一串气泡。"""
    slip = splash(rng, 0.4, size=0.7, bright=0.7)
    flick = plop(rng.uniform(0.008, 0.012), 0.2, 0.5)
    x = s.mix((slip, 1.0), (flick, 0.5, 0.06), (drips(rng, 0.6, 30), 0.3, 0.1))
    return outdoors(s.lowpass(x, 6500), rng, 0.5, 0.18, 0.4)


@sound("fishing/trap", variants=2, level=-22)
def trap(rng, i):
    """捕鱼笼进鱼：水下闷闷的一声，笼门的铁丝"叮"地合上，几个气泡。"""
    n = s.samples(0.3)
    thud = s.lowpass(s.sine(s.exp_curve([(0, 160), (0.3, 70)], n), n) * s.perc(n, 0.002, 0.12), 600)
    clink = m.impact(rng, freq=rng.uniform(1400, 1900), decay=0.25, ratios=m.PLATE[:7], hardness=0.8, size=0.5, noise=0.1)
    bub = m.bubbles(s.samples(0.5), rng, [(0, 40), (0.5, 4)], radius=(0.002, 0.007), rise=0.25)
    x = s.mix((thud, 0.8), (s.lowpass(clink, 3500), 0.5, 0.03), (bub, 0.4, 0.02))
    return fx.space(x, "casing", wet=0.15, tail=0.15)
