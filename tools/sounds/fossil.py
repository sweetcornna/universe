"""M19 化石（C）的声音（ADR-0034 §三）。

骨头是干的、中空的：放下、拿起是短促发空的"笃"，带一点沙粒的摩擦；封装、拆开是一把骨头碰在一起再裹上布，
完整的骨架拼好时一串木琴似的骨音往上走。收藏家那边是纸和两声清脆的成交音。

石哨兵是活的岩石：醒来、受伤、碎掉都用岩石的非谐模态加碎石的噼啪；平时是低沉的研磨声和绕着它转的碎石互相碰；
扔石块前先蓄力磨一下再"呼"地甩出去；点着了以后是越来越急的嘶嘶声和火的低吼，最后在爆炸里收掉（爆炸用原版的）。
"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["fossil"]

# 岩石的模态频率比：比金属板密、更不谐和
ROCK = [1.0, 1.38, 1.83, 2.41, 2.97, 3.62, 4.40, 5.21]
# A 大调五声音阶里的几个音（Hz），和 world.py 的提示音一个调
A3, CS4, E4, FS4, A4, B4, CS5, E5 = 220.0, 277.2, 329.6, 370.0, 440.0, 493.9, 554.4, 659.3


def bone_tap(rng, f: float, decay: float = 0.09, hardness: float = 0.75) -> np.ndarray:
    """骨头碰到硬的东西：中空的模态，很短，前面一下亮的接触声。"""
    x = m.impact(rng, freq=f * rng.uniform(0.95, 1.05), decay=decay, ratios=m.HOLLOW, hardness=hardness, noise=0.3)
    return s.highpass(x, 180)


def grit(n: int, rng, rate, bright: float = 0.6) -> np.ndarray:
    """沙粒、碎屑的细碎摩擦。"""
    x = s.crackle(n, rng, rate, width=0.00015)
    return s.bandpass(x, 1200 + 1500 * bright, 6000 + 4000 * bright)


def rustle(n: int, rng, rate: float = 14, bright: float = 0.5) -> np.ndarray:
    """布、纸的窸窣：粉红噪声过带通，幅度一阵一阵的。"""
    noise = s.bandpass(s.pink(n, rng), 900 + 800 * bright, 5500 + 3000 * bright)
    bursts = np.clip(s.wander(n, rng, rate, 1.0) + 0.2, 0, None) ** 1.5
    return noise * bursts


def stone_hit(rng, f: float, decay: float = 0.18, hardness: float = 0.85, noise: float = 0.6) -> np.ndarray:
    """石块的一下撞击或裂开。"""
    x = m.impact(rng, freq=f * rng.uniform(0.93, 1.07), decay=decay, ratios=ROCK, hardness=hardness, noise=noise)
    return s.lowpass(x, 7000)


def grind(n: int, rng, cutoff: float = 380, rate: float = 4.0) -> np.ndarray:
    """石头磨石头：低频的布朗噪声，起伏不平，夹着一点碎粒。"""
    body = s.resonant(s.brown(n, rng), "lowpass", cutoff, 1.2)
    body = body / (np.max(np.abs(body)) + 1e-9)
    shake = np.clip(0.6 + s.wander(n, rng, rate, 0.5), 0.05, None)
    bits = s.lowpass(grit(n, rng, 120, 0.2), 3500) * 0.5
    return body * shake + bits


def ember_roar(n: int, rng, intensity) -> np.ndarray:
    """火的低吼和噼啪：粉红噪声过一个随强度升高的低通，噼啪越来越密。"""
    k = s.curve(intensity, n)
    roar = s.resonant(s.pink(n, rng), "lowpass", 300 + 1800 * k, 0.9) * (0.2 + 0.8 * k)
    pops = s.highpass(s.crackle(n, rng, 20 + 220 * k, width=0.0002), 1500) * (0.3 + 0.7 * k)
    return roar + 0.5 * pops


def chime(f: float, seconds: float, decay: float, bright: float = 0.6) -> np.ndarray:
    """骨琴一样的音：木琴的模态比，起音带一点亮。"""
    n = s.samples(seconds)
    excite = np.zeros(n)
    excite[0] = 1.0
    x = s.modes(excite, [f * r for r in (1.0, 3.93, 9.0)], [decay, decay * 0.35, decay * 0.15], [1.0, 0.35 * bright, 0.12 * bright])
    return x / (np.max(np.abs(x)) + 1e-9)


def end(x: np.ndarray, seconds: float = 0.06) -> np.ndarray:
    return s.taper(x, seconds)


# ---- 骨头、展台

@sound("fossil/bone_place", variants=3, level=-19)
def bone_place(rng, i):
    """骨头放到底座、展台上：一声发空的"笃"，跟着一下轻的回弹，底下的沙沙一声。"""
    f = [980.0, 1150.0, 860.0][i]
    n = s.samples(0.45)
    tap = bone_tap(rng, f, 0.11)
    bounce = bone_tap(rng, f * 1.08, 0.05, hardness=0.6)
    sand = grit(n, rng, s.envelope([(0, 600), (0.08, 120), (0.3, 0)], n)) * s.perc(n, 0.001, 0.25)
    x = s.mix((tap, 1.0), (bounce, 0.35, 0.05 + 0.01 * i), (sand, 0.25))
    return fx.space(end(x), "casing", wet=0.12, tail=0.15)


@sound("fossil/bone_take", variants=2, level=-20)
def bone_take(rng, i):
    """从底座、展台上拿起骨头：骨头在石面上蹭一下，离开时轻轻磕到边。"""
    n = s.samples(0.4)
    scrape = grit(n, rng, s.envelope([(0, 0), (0.02, 900), (0.16, 300), (0.2, 0)], n), 0.4)
    scrape += 0.4 * s.bandpass(s.pink(n, rng), 1500, 5000) * s.envelope([(0, 0), (0.02, 1), (0.18, 0.4), (0.22, 0)], n)
    tick = bone_tap(rng, 1300 + 180 * i, 0.05, hardness=0.8)
    x = s.mix((scrape, 0.7), (tick, 0.6, 0.2 + 0.03 * i))
    return fx.space(end(x), "casing", wet=0.1, tail=0.1)


@sound("fossil/pack", level=-17)
def pack(rng, i):
    """封装：一把骨头碰在一起收拢，裹上布，最后箱盖落下。"""
    n = s.samples(1.3)
    parts = []
    for k in range(6):
        parts.append((bone_tap(rng, rng.uniform(800, 1400), 0.06), 0.6 * rng.uniform(0.6, 1.0), 0.02 + 0.06 * k + rng.uniform(0, 0.03)))
    wrap = rustle(n, rng, 18, 0.4) * s.envelope([(0, 0), (0.35, 0), (0.45, 1), (0.85, 0.7), (1.0, 0), (1.3, 0)], n)
    lid = stone_hit(rng, 190, 0.22, hardness=0.4, noise=0.2)
    x = s.mix(*parts, (wrap, 0.5), (lid, 0.8, 1.0))
    return fx.space(end(x, 0.15), "casing", wet=0.15, tail=0.25)


@sound("fossil/unpack", level=-17)
def unpack(rng, i):
    """拆开展品摆回展台：布掀开，五块骨头依次落到位，越摆越高。"""
    n = s.samples(1.4)
    unwrap = rustle(n, rng, 16, 0.5) * s.envelope([(0, 0), (0.05, 1), (0.35, 0.6), (0.45, 0), (1.4, 0)], n)
    parts = [(unwrap, 0.5)]
    for k, f in enumerate((760, 860, 980, 1100, 1240)):
        parts.append((bone_tap(rng, f, 0.09), 0.75, 0.5 + 0.15 * k))
    x = s.mix(*parts)
    return fx.space(end(x, 0.15), "casing", wet=0.15, tail=0.25)


@sound("fossil/skeleton_complete", level=-16)
def skeleton_complete(rng, i):
    """骨架拼齐了：最后一块"笃"地落下，接着一串骨琴的音往上走，停在一个大三和弦上。"""
    tap = bone_tap(rng, 1000, 0.1)
    notes = [(A4, 0.12), (CS5, 0.22), (E5, 0.32)]
    parts = [(tap, 0.9)]
    for f, at in notes:
        parts.append((chime(f, 1.4, 0.9), 0.45, at))
    parts.append((chime(A3, 1.6, 1.2, 0.3), 0.3, 0.32))
    x = s.mix(*parts)
    return fx.space(end(x, 0.4), fx.outdoor_ir(rng, 0.5), wet=0.25, tail=0.6)


# ---- 收藏家

@sound("fossil/sell", level=-17)
def sell(rng, i):
    """卖给收藏家：展品被接过去，单据撕下来一张，两声清脆的成交音。"""
    n = s.samples(0.9)
    paper = rustle(n, rng, 30, 0.8) * s.envelope([(0, 0), (0.02, 1), (0.18, 0.3), (0.25, 0), (0.9, 0)], n)
    tear = s.highpass(s.crackle(n, rng, s.envelope([(0, 0), (0.12, 0), (0.13, 3000), (0.24, 2000), (0.25, 0)], n), 0.0001), 2500)
    x = s.mix((paper, 0.6), (tear, 0.35), (chime(E5, 0.8, 0.5), 0.45, 0.3), (chime(A4 * 2, 0.9, 0.6), 0.4, 0.42))
    return fx.space(end(x, 0.2), "casing", wet=0.12, tail=0.3)


@sound("fossil/swap", level=-18)
def swap(rng, i):
    """跟收藏家换骨头：三块骨头推过去碰在一起，换回来的那块放到面前。"""
    parts = []
    for k in range(3):
        parts.append((bone_tap(rng, rng.uniform(900, 1200), 0.06), 0.6, 0.02 + 0.07 * k))
    parts.append((bone_tap(rng, 760, 0.12), 0.9, 0.42))
    parts.append((chime(CS5, 0.6, 0.4), 0.25, 0.44))
    x = s.mix(*parts)
    return fx.space(end(x, 0.15), "casing", wet=0.12, tail=0.2)


# ---- 石哨兵

@sound("fossil/unrest", variants=2, level=-18)
def unrest(rng, i):
    """沉睡的石哨兵被吵到：石头深处闷闷地磨一下，碎石从身上簌簌落下。"""
    length = 1.2
    n = s.samples(length)
    groan = grind(n, rng, 160 + 40 * i, 3.0) * s.envelope([(0, 0), (0.15, 1), (0.7, 0.6), (1.1, 0)], n)
    trickle = grit(n, rng, s.envelope([(0, 0), (0.3, 0), (0.4, 180), (1.0, 20), (1.2, 0)], n), 0.3)
    knock = stone_hit(rng, 140 + 30 * i, 0.3, hardness=0.3, noise=0.2)
    x = s.mix((groan, 0.9), (trickle, 0.35), (knock, 0.5, 0.05))
    return fx.space(end(x, 0.2), fx.outdoor_ir(rng, 0.6), wet=0.25, tail=0.5)


@sound("fossil/sentry_wake", level=-14)
def sentry_wake(rng, i):
    """石哨兵醒来：先是几声裂开，接着整块石头一边磨一边往上抬，碎石哗地落下，最后眼缝里的火"呼"地亮起来。"""
    length = 2.6
    n = s.samples(length)
    t = s.times(n)
    parts = []
    for k, at in enumerate((0.0, 0.18, 0.31)):
        parts.append((stone_hit(rng, 420 - 60 * k, 0.25, hardness=0.95), 0.8, at))
    rise = grind(n, rng, s.exp_curve([(0, 120), (1.6, 420), (length, 300)], n), 5.0)
    rise *= s.envelope([(0, 0), (0.3, 0.2), (0.6, 1), (1.8, 0.8), (2.3, 0), (length, 0)], n)
    groan = s.resonant(s.saw(s.exp_curve([(0, 48), (1.6, 72), (length, 64)], n), n, 24), "bandpass",
                       s.exp_curve([(0, 220), (1.6, 520), (length, 400)], n), 3)
    groan *= s.envelope([(0, 0), (0.5, 0), (0.9, 0.6), (1.7, 0.4), (2.2, 0), (length, 0)], n)
    cascade = grit(n, rng, s.envelope([(0, 0), (0.5, 0), (0.7, 600), (1.6, 150), (2.4, 0)], n), 0.3)
    cascade = cascade + 0.5 * s.lowpass(s.crackle(n, rng, s.envelope([(0, 0), (0.6, 0), (0.8, 60), (2.0, 5), (2.4, 0)], n), 0.002), 900)
    flare = ember_roar(n, rng, np.clip((t - 1.7) / 0.25, 0, 1) * np.exp(-np.clip(t - 1.95, 0, None) / 0.4))
    x = s.mix(*parts, (rise, 1.0), (groan, 0.35), (cascade, 0.4), (flare, 0.5))
    return fx.space(end(x, 0.3), fx.outdoor_ir(rng, 1.0), wet=0.3, tail=0.9)


@sound("fossil/sentry_ambient", variants=3, level=-21)
def sentry_ambient(rng, i):
    """醒着的石哨兵：身子里低沉地磨着，绕着它转的碎石时不时互相磕一下，眼缝里的火轻轻噼啪。"""
    length = 1.8
    n = s.samples(length)
    hum = grind(n, rng, 140 + 25 * i, 2.0) * s.envelope([(0, 0), (0.3, 0.8), (1.4, 0.7), (length, 0)], n)
    parts = [(hum, 0.8)]
    for k in range(2 + i % 2):
        parts.append((stone_hit(rng, rng.uniform(900, 1500), 0.08, hardness=0.9, noise=0.4), 0.3, 0.2 + 0.5 * k + rng.uniform(0, 0.2)))
    flicker = s.highpass(s.crackle(n, rng, 40, width=0.0002), 1800) * s.envelope([(0, 0), (0.2, 1), (1.6, 1), (length, 0)], n)
    parts.append((flicker, 0.2))
    x = s.mix(*parts)
    return fx.space(end(x, 0.3), fx.outdoor_ir(rng, 0.6), wet=0.25, tail=0.4)


@sound("fossil/sentry_hurt", variants=3, level=-16)
def sentry_hurt(rng, i):
    """石哨兵受伤：石头被敲裂的脆响，崩下几块碎屑，身子里闷哼一下。"""
    n = s.samples(0.8)
    crack = stone_hit(rng, [620.0, 700.0, 540.0][i], 0.2, hardness=0.98, noise=0.8)
    chips = s.highpass(s.crackle(n, rng, s.envelope([(0, 0), (0.02, 900), (0.2, 40), (0.4, 0)], n), 0.0002), 1500)
    grunt = grind(n, rng, 180, 6.0) * s.envelope([(0, 0), (0.04, 1), (0.4, 0.2), (0.6, 0)], n)
    x = s.mix((crack, 1.0), (chips, 0.4), (grunt, 0.6))
    return fx.space(end(x, 0.15), fx.outdoor_ir(rng, 0.6), wet=0.22, tail=0.35)


@sound("fossil/sentry_death", level=-14)
def sentry_death(rng, i):
    """石哨兵碎了：一连串裂开的脆响，大块的石头一块接一块砸到地上，尘土落定，火光熄掉。"""
    length = 2.4
    n = s.samples(length)
    t = s.times(n)
    parts = []
    for k, at in enumerate((0.0, 0.1, 0.17, 0.26)):
        parts.append((stone_hit(rng, 560 - 50 * k, 0.2, hardness=0.98, noise=0.8), 0.7, at))
    for k, at in enumerate((0.45, 0.62, 0.74, 0.95, 1.2)):
        parts.append((stone_hit(rng, 150 - 12 * k, 0.4, hardness=0.35, noise=0.4), 0.9 - 0.1 * k, at))
    dust = s.lowpass(s.brown(n, rng), 300) * s.envelope([(0, 0), (0.45, 0), (0.6, 1), (2.0, 0.2), (length, 0)], n)
    dust /= np.max(np.abs(dust)) + 1e-9
    gravel = grit(n, rng, s.envelope([(0, 0), (0.4, 0), (0.6, 500), (1.6, 60), (2.2, 0)], n), 0.3)
    fade_out = ember_roar(n, rng, 0.6 * np.exp(-t / 0.5))
    x = s.mix(*parts, (dust, 0.5), (gravel, 0.35), (fade_out, 0.3))
    return fx.space(end(x, 0.3), fx.outdoor_ir(rng, 1.0), wet=0.3, tail=0.9)


@sound("fossil/sentry_throw", variants=2, level=-16)
def sentry_throw(rng, i):
    """扔石块：拳石抬起来时磨一下，甩出去"呼"的一声风。"""
    length = 0.9
    n = s.samples(length)
    windup = grind(n, rng, 260 + 40 * i, 8.0) * s.envelope([(0, 0), (0.05, 0.8), (0.35, 0.5), (0.42, 0)], n)
    sweep = s.exp_curve([(0, 300), (0.45, 300), (0.55, 2200 + 300 * i), (length, 500)], n)
    whoosh = s.resonant(s.pink(n, rng), "bandpass", sweep, 1.4) * s.envelope([(0, 0), (0.4, 0), (0.55, 1), (length, 0)], n)
    x = s.mix((windup, 0.7), (whoosh, 0.9))
    return fx.space(end(x, 0.1), fx.outdoor_ir(rng, 0.6), wet=0.2, tail=0.3)


@sound("fossil/rock_impact", variants=3, level=-15)
def rock_impact(rng, i):
    """石块落地：一声闷的砸，接着碎成几块往外蹦。"""
    n = s.samples(0.9)
    thud = stone_hit(rng, [130.0, 150.0, 115.0][i], 0.35, hardness=0.4, noise=0.5)
    shatter = s.highpass(s.crackle(n, rng, s.envelope([(0, 0), (0.01, 1500), (0.15, 200), (0.5, 0)], n), 0.0003), 900)
    bits = [(stone_hit(rng, rng.uniform(800, 1400), 0.06, hardness=0.9, noise=0.3), 0.3, 0.12 + 0.09 * k + rng.uniform(0, 0.05))
            for k in range(3)]
    x = s.mix((thud, 1.0), (shatter, 0.4), *bits)
    return fx.space(end(x, 0.15), fx.outdoor_ir(rng, 0.7), wet=0.25, tail=0.45)


@sound("fossil/sentry_fuse", level=-14)
def sentry_fuse(rng, i):
    """石哨兵点着了（两秒后自爆）：裂缝里嘶嘶地喷火，越来越急，音高一路往上逼。"""
    length = 2.0
    n = s.samples(length)
    k = s.envelope([(0, 0.15), (1.6, 0.85), (length, 1.0)], n)
    roar = ember_roar(n, rng, k)
    hiss = m.hiss(n, rng, pressure=k, brightness=0.7)
    whine = s.sine(s.exp_curve([(0, 240), (length, 980)], n), n) * (0.15 + 0.35 * k)
    whine = s.softclip(whine * 1.5, 1.5)
    rattle = s.lowpass(s.crackle(n, rng, 8 + 40 * k, width=0.001), 1200)
    x = s.mix((roar, 0.8), (hiss, 0.4), (whine, 0.35), (rattle, 0.4))
    return fx.space(s.fade(x, 0.02, 0.02), fx.outdoor_ir(rng, 0.6), wet=0.2, tail=0.2)
