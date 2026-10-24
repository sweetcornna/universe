"""M19 聚落（B）的声音（ADR-0034 §二）：接管、施工、开张、每天的决定、海盗来袭、聚落的炮台。

施工是木头和铁器的敲打，离得近；开张、守住、失守是户外的铜管和铃，音高落在 D 大调上，和世界里提示的 A 大调
隔一个五度，同时响也不打架。警报是聚落塔楼上的手摇汽笛，往上扫、往下落，传得远。
"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["settlement"]

# D 大调里用到的音（Hz）
D4, F4, FS4, A4, B4, D5, E5, FS5, A5, D6 = 293.7, 349.2, 370.0, 440.0, 493.9, 587.3, 659.3, 740.0, 880.0, 1174.7
# 木头的模态比：比钢板稀，衰减快
WOOD = [1.0, 2.32, 4.25, 6.63]


def end(x: np.ndarray, seconds: float = 0.06) -> np.ndarray:
    return s.taper(x, seconds)


def brass(f: float, seconds: float, attack: float = 0.04, bright: float = 0.6) -> np.ndarray:
    """铜管的一个音：锯齿波起音时滤波器打开，泛音跟着出来，尾巴收暗；带一点颤音。"""
    n = s.samples(seconds)
    vib = 1 + 0.004 * s.sine(5.2, n) * s.envelope([(0, 0), (min(0.2, seconds), 0), (seconds, 1)], n)
    tone = s.saw(f * vib, n, 40)
    env = s.adsr(n, attack, 0.08, 0.75, 0.12)
    cutoff = s.exp_curve([(0, 600), (attack * 2, 1200 + 3000 * bright), (seconds, 900)], n)
    return s.resonant(tone, "lowpass", cutoff, 0.9) * env


def bell(rng, f: float, decay: float = 1.4, bright: float = 0.6) -> np.ndarray:
    """手铃：钢板的模态敲一下，亮的那几阶衰减快。"""
    return m.impact(rng, freq=f, decay=decay, ratios=m.PLATE[:7], hardness=bright, size=0.6, noise=0.12)


def hammer(rng, wood: bool) -> np.ndarray:
    """一锤子：木头闷一点，铁器亮一点，带一下工具弹起来的轻响。"""
    if wood:
        hit = m.impact(rng, freq=rng.uniform(180, 260), decay=0.12, ratios=WOOD, hardness=0.55, size=1.2, noise=0.45)
    else:
        hit = m.impact(rng, freq=rng.uniform(900, 1300), decay=0.25, ratios=m.BAR[:4], hardness=0.85, noise=0.25)
    bounce = m.impact(rng, freq=rng.uniform(1400, 1900), decay=0.05, ratios=m.BAR[:3], hardness=0.8, noise=0.2)
    return s.mix((hit, 1.0, 0.0), (bounce, 0.18, rng.uniform(0.07, 0.11)))


def crowd(n: int, rng, swell: float = 0.4) -> np.ndarray:
    """远处一群人叫好：粉红噪声过几个慢慢游动的元音共振峰。"""
    noise = s.pink(n, rng)
    out = np.zeros(n)
    for f0, q in ((520, 4.0), (1100, 5.0), (2300, 6.0)):
        sweep = f0 * (1 + 0.15 * s.wander(n, rng, 2.5))
        out += s.resonant(noise, "bandpass", sweep, q)
    t = n / s.SR
    return out * s.envelope([(0, 0), (swell, 1), (t * 0.6, 0.8), (t, 0)], n)


# ---- 接管、施工

@sound("settlement/claim", level=-15)
def claim(rng, i):
    """接管：一声铃，后面铜管吹一个往上的三音动机，最后那个音拖长。"""
    notes = [(D5, 0.0, 0.32), (FS5, 0.22, 0.32), (A5, 0.44, 0.9)]
    parts = [(bell(rng, D6, 1.6, 0.7), 0.35, 0.0)]
    for f, at, length in notes:
        parts.append((brass(f, length, bright=0.55), 0.8, 0.12 + at))
        parts.append((brass(f / 2, length, bright=0.3), 0.35, 0.12 + at))
    x = s.mix(*parts)
    return fx.space(end(x, 0.2), fx.outdoor_ir(rng, 1.0), wet=0.3, tail=1.2)


@sound("settlement/build", variants=4, level=-19)
def build(rng, i):
    """施工的动静：几锤子敲在木头或铁上，间隔不匀，偶尔一下锯。"""
    parts = []
    at = 0.0
    hits = 2 + i % 3
    for k in range(hits):
        parts.append((hammer(rng, wood=(i + k) % 3 != 0), rng.uniform(0.7, 1.0), at))
        at += rng.uniform(0.16, 0.3)
    if i == 3:
        n = s.samples(0.45)
        saw = s.bandpass(s.white(n, rng), 1800, 6000, 2) * (0.6 + 0.4 * s.sine(7.5, n) ** 2) * s.envelope([(0, 0), (0.05, 1), (0.4, 0.8), (0.45, 0)], n)
        parts.append((saw, 0.35, at))
    x = s.mix(*parts)
    return fx.space(end(x), fx.outdoor_ir(rng, 0.5), wet=0.2, tail=0.4)


@sound("settlement/deliver", variants=2, level=-18)
def deliver(rng, i):
    """交材料：一摞木料、铁料放下去，咚一声，跟着几下金属的碰响。"""
    thud = m.impact(rng, freq=rng.uniform(110, 140), decay=0.18, ratios=WOOD, hardness=0.4, size=1.6, noise=0.6)
    clinks = [(m.impact(rng, freq=rng.uniform(1500, 2400), decay=0.08, ratios=m.BAR[:3], hardness=0.8, noise=0.1), 0.25,
               0.05 + k * rng.uniform(0.05, 0.09)) for k in range(2 + i)]
    x = s.mix((thud, 1.0, 0.0), *clinks)
    return fx.space(end(x), fx.outdoor_ir(rng, 0.4), wet=0.15, tail=0.3)


@sound("settlement/stage", variants=2, level=-17)
def stage(rng, i):
    """一段完工：最后一锤，停一下，两声往上的铃。"""
    first = [(D5, A5), (FS5, D6)][i]
    x = s.mix((hammer(rng, wood=False), 0.8, 0.0), (bell(rng, first[0], 0.9, 0.6), 0.45, 0.3), (bell(rng, first[1], 1.3, 0.65), 0.5, 0.48))
    return fx.space(end(x, 0.15), fx.outdoor_ir(rng, 0.7), wet=0.25, tail=0.8)


@sound("settlement/open", level=-14)
def open_(rng, i):
    """开张：铜管吹一段 D 大调的分解和弦，铃跟着敲，远处一群人叫好，最后一声长和弦。"""
    phrase = [(D5, 0.0, 0.2), (FS5, 0.18, 0.2), (A5, 0.36, 0.2), (D6, 0.54, 1.3)]
    parts = []
    for f, at, length in phrase:
        parts.append((brass(f, length, bright=0.65), 0.7, at))
    for f in (D4, FS4, A4):
        parts.append((brass(f, 1.3, attack=0.06, bright=0.35), 0.35, 0.54))
    parts.append((bell(rng, D6, 1.8, 0.75), 0.3, 0.54))
    parts.append((bell(rng, A5, 1.6, 0.7), 0.25, 0.7))
    parts.append((crowd(s.samples(2.2), rng), 0.12, 0.3))
    x = s.mix(*parts)
    return fx.space(end(x, 0.3), fx.outdoor_ir(rng, 1.3), wet=0.32, tail=1.6)


@sound("settlement/decision", variants=2, level=-18)
def decision(rng, i):
    """办公桌上的铃：叮一声，纸页翻过去一张。"""
    ding = bell(rng, [A5, B4 * 2][i], 0.9, 0.8)
    n = s.samples(0.25)
    paper = s.bandpass(s.white(n, rng), 1500, 7000, 2) * s.envelope([(0, 0), (0.03, 1), (0.12, 0.4), (0.25, 0)], n)
    x = s.mix((ding, 0.8, 0.0), (paper, 0.25, 0.35))
    return fx.space(end(x, 0.1), "corridor", wet=0.12, tail=0.4)


# ---- 海盗来袭

@sound("settlement/raid_alarm", level=-13)
def raid_alarm(rng, i):
    """聚落的手摇汽笛：转起来往上扫，到顶停一会儿，再慢慢落下来，扫两遍。"""
    length = 5.0
    n = s.samples(length)
    f = s.exp_curve([(0, 180), (1.0, 620), (1.6, 640), (2.5, 300), (3.1, 620), (3.8, 640), (length, 160)], n)
    rough = 1 + 0.006 * s.wander(n, rng, 9)
    tone = s.saw(f * rough, n, 24) * 0.6 + s.square(f * 2 * rough, n, 12) * 0.25
    tone = s.resonant(tone, "bandpass", f * 2.2, 1.4)
    env = s.envelope([(0, 0), (0.4, 1), (length - 0.8, 0.95), (length, 0)], n)
    x = s.softclip(tone * env * 1.8, 1.8)
    return fx.space(end(x, 0.2), fx.outdoor_ir(rng, 2.0), wet=0.4, tail=2.0)


@sound("settlement/raid_won", level=-15)
def raid_won(rng, i):
    """守住了：铜管从低到高一句，铃一齐响。"""
    phrase = [(A4, 0.0, 0.18), (D5, 0.16, 0.18), (FS5, 0.32, 0.18), (A5, 0.48, 0.18), (D6, 0.64, 1.2)]
    parts = [(brass(f, length, bright=0.6), 0.7, at) for f, at, length in phrase]
    parts += [(bell(rng, f, 1.6, 0.7), 0.25, 0.64 + k * 0.05) for k, f in enumerate((D6, A5, FS5))]
    parts.append((crowd(s.samples(2.0), rng, 0.3), 0.14, 0.6))
    x = s.mix(*parts)
    return fx.space(end(x, 0.3), fx.outdoor_ir(rng, 1.3), wet=0.3, tail=1.5)


@sound("settlement/raid_lost", level=-15)
def raid_lost(rng, i):
    """失守：一阵碎石塌下来，铜管往下落的小调三个音，最后一声闷鼓。"""
    n = s.samples(1.2)
    rubble = s.lowpass(s.crackle(n, rng, 45, width=0.002), 3000) * s.envelope([(0, 1), (1.2, 0)], n)
    rubble += s.lowpass(s.brown(n, rng), 400) * s.perc(n, 0.01, 0.6)
    phrase = [(A4, 0.5, 0.35), (F4, 0.82, 0.35), (D4, 1.14, 1.1)]
    parts = [(rubble, 0.5, 0.0)]
    parts += [(brass(f, length, bright=0.35), 0.6, at) for f, at, length in phrase]
    dn = s.samples(0.8)
    drum = s.sine(s.exp_curve([(0, 90), (0.4, 50)], dn), dn) * s.perc(dn, 0.005, 0.5)
    parts.append((drum, 0.8, 1.14))
    x = s.mix(*parts)
    return fx.space(end(x, 0.3), fx.outdoor_ir(rng, 1.2), wet=0.3, tail=1.4)


@sound("settlement/turret_fire", variants=3, level=-15)
def turret_fire(rng, i):
    """聚落炮台开火：线圈充能一下"嗡"，等离子弹"嘭"地打出去，炮管的余响往下落。"""
    zap = m.zap(rng, start=rng.uniform(2600, 3200), end=rng.uniform(220, 300), length=0.22, bite=0.6)
    tn = s.samples(0.5)
    thump = s.sine(s.exp_curve([(0, 160), (0.2, 55)], tn), tn) * s.perc(tn, 0.002, 0.3)
    ring = m.impact(rng, freq=rng.uniform(380, 460), decay=0.5, ratios=m.PLATE[:6], hardness=0.6, noise=0.15)
    x = s.mix((zap, 0.8, 0.0), (thump, 1.0, 0.01), (ring, 0.2, 0.02))
    return fx.space(end(x, 0.1), fx.outdoor_ir(rng, 1.0), wet=0.3, tail=0.8)
