"""局部危害的声音（ADR-0034 §六）：间歇泉、火山喷发、熔岩弹、孢子云、尘卷风。

都是野外的声音，混响用空旷地面的回声（fx.outdoor_ir），越大的事越远越闷。
间歇泉照真的录音想象：喷之前地下咕嘟咕嘟、一阵阵往上涌，喷的时候先"嘭"一声，接着是水汽冲出来的长啸，
夹着水落下来的哗哗声。火山是脚下的低频震动和大块岩石崩裂，喷发是一声闷雷似的爆炸接着不停的轰鸣。
熔岩弹飞过来是一阵嘶嘶的风声，砸到地上一声闷响、碎石溅开、冒一阵烟。孢子云是很轻的、像呼吸一样起伏的沙沙声；
尘卷风是转着圈一阵阵的风，里面夹着沙粒打在东西上的细碎声。
"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["hazard"]


def outdoors(x: np.ndarray, rng: np.random.Generator, size: float, wet: float, tail: float) -> np.ndarray:
    return fx.space(x, fx.outdoor_ir(rng, size), wet=wet, tail=tail)


@sound("hazard/geyser_rumble", variants=2, level=-20)
def geyser_rumble(rng, i):
    """喷之前的三秒：地下低沉的翻滚一阵阵往上涌，气泡越来越密、越来越大，最后几下沉闷的"咕咚"。"""
    n = s.samples(3.2)
    t = s.times(n)
    swell = np.clip(0.35 + 0.65 * t / 3.0 + 0.25 * s.wander(n, rng, 2.5), 0, 1.2)
    deep = s.lowpass(s.brown(n, rng), 110, 2) * swell
    bubbles = m.bubbles(n, rng, [(0, 6), (1.5, 18), (3.2, 40)], radius=(0.006, 0.03), rise=0.25)
    bubbles = s.lowpass(bubbles, 2200, 2) * np.clip(t / 0.6, 0, 1)
    glugs = []
    for k in range(3 + i):
        at = rng.uniform(1.0, 2.9)
        hit = m.impact(rng, freq=rng.uniform(55, 85), decay=0.5, ratios=m.HOLLOW, hardness=0.1, size=2.0, noise=0.1)
        glugs.append((s.lowpass(hit, 400, 2), 0.35, at))
    out = s.mix((deep, 1.0), (bubbles, 0.45), *glugs)
    return outdoors(s.taper(out, 0.4), rng, 0.4, 0.2, 0.6)


@sound("hazard/geyser_erupt", variants=2, level=-16)
def geyser_erupt(rng, i):
    """喷发：先"嘭"的一声闷响，水汽柱冲出来的长啸（宽带的嘶声，开头最亮），底下是持续的轰鸣，
    水和冰屑落下来哗哗的细碎声跟在后面，五秒后慢慢收住。"""
    n = s.samples(5.5)
    t = s.times(n)
    env = np.interp(t, [0, 0.08, 0.6, 3.8, 5.5], [0, 1.0, 0.85, 0.55, 0])
    thump = m.explosion(rng, size=0.5, distance=0.2, debris=0.1, tail=0.8)
    jet = m.hiss(n, rng, pressure=[(0, 1.0), (1.0, 0.8), (5.5, 0.4)], brightness=0.5) * env
    roar = s.resonant(s.pink(n, rng), "lowpass", 900, 0.6) * env * (1 + 0.15 * s.wander(n, rng, 6))
    fall = s.highpass(s.crackle(n, rng, [(0, 0), (0.8, 400), (4.5, 300), (5.5, 0)], width=0.0003), 1500)
    fall = s.lowpass(fall, 7000, 2) * np.interp(t, [0, 0.8, 1.6, 5.5], [0, 0, 1, 0.3])
    out = s.mix((thump, 0.8), (jet, 0.7), (roar, 0.9), (fall, 0.25))
    return outdoors(s.taper(out, 0.5), rng, 0.6, 0.25, 0.8)


@sound("hazard/eruption_rumble", variants=2, level=-18)
def eruption_rumble(rng, i):
    """喷发前的震动：脚下极低的轰隆声慢慢起来，一阵紧一阵松，中间夹着远处岩石崩裂的几声闷响。"""
    n = s.samples(4.0)
    t = s.times(n)
    swell = np.interp(t, [0, 1.0, 3.0, 4.0], [0, 0.8, 1.0, 0.6]) * (1 + 0.35 * s.wander(n, rng, 1.8))
    rumble = s.lowpass(s.brown(n, rng), 70, 4) * swell
    grind = s.bandpass(s.brown(n, rng), 60, 260, 2) * swell * 0.35
    cracks = []
    for k in range(4):
        at = rng.uniform(0.6, 3.4)
        hit = m.impact(rng, freq=rng.uniform(70, 140), decay=0.8, ratios=m.PLATE, hardness=0.35, size=2.5, noise=0.5)
        cracks.append((s.lowpass(hit, 600, 2), 0.25 * rng.uniform(0.6, 1.0), at))
    out = s.mix((rumble, 1.0), (grind, 1.0), *cracks)
    return outdoors(s.taper(out, 0.5), rng, 1.6, 0.35, 1.2)


@sound("hazard/eruption_blast", variants=2, level=-14)
def eruption_blast(rng, i):
    """火山喷发：一声闷雷似的大爆炸，碎石往外崩，后面是不停的、像大火箭一样的低沉轰鸣，几秒后变远变闷。"""
    blast = m.explosion(rng, size=3.0, distance=0.6, debris=0.8, tail=4.0)
    n = s.samples(7.0)
    t = s.times(n)
    roar = m.rocket(n, rng, thrust=[(0, 0), (0.3, 1.0), (4.0, 0.8), (7.0, 0.3)], crackle=0.7, distance=0.8)
    roar = s.taper(s.lowpass(roar, 1800, 2) * (1 + 0.2 * s.wander(n, rng, 1.5)), 2.5)
    out = s.mix((blast, 1.0), (roar, 0.6, 0.15))
    return outdoors(s.taper(out, 1.2), rng, 2.0, 0.4, 1.6)


@sound("hazard/lava_bomb_fly", variants=3, level=-22)
def lava_bomb_fly(rng, i):
    """熔岩弹飞过：一阵由低到高再落下去的呼啸（带通的噪声扫过去），表面滋滋地冒着小火星。"""
    n = s.samples(0.9)
    t = s.times(n)
    env = np.interp(t, [0, 0.35, 0.9], [0, 1, 0])
    centre = s.exp_curve([(0, 380 + 60 * i), (0.4, 900 + 120 * i), (0.9, 420)], n)
    whoosh = s.resonant(s.pink(n, rng), "bandpass", centre, 2.2) * env
    sizzle = s.highpass(s.crackle(n, rng, 260, width=0.0002), 3000) * env * 0.3
    return s.mix((whoosh, 1.0), (sizzle, 1.0))


@sound("hazard/lava_bomb_impact", variants=3, level=-16)
def lava_bomb_impact(rng, i):
    """熔岩弹落地：一声沉闷的撞击，碎石和熔渣溅开噼啪响，接着一阵烧着东西的嘶嘶声慢慢散掉。"""
    hit = m.impact(rng, freq=rng.uniform(70, 110), decay=0.7, ratios=m.PLATE, hardness=0.45, size=2.0, noise=0.7)
    hit = s.lowpass(hit, 1800, 2)
    boom = m.explosion(rng, size=0.6, distance=0.1, debris=0.9, tail=1.0)
    n = s.samples(1.8)
    t = s.times(n)
    steam = m.hiss(n, rng, pressure=[(0, 0), (0.05, 1.0), (1.8, 0)], brightness=0.4) * np.exp(-t / 0.7)
    crack = s.highpass(s.crackle(n, rng, [(0, 300), (0.6, 40), (1.8, 0)], width=0.0003), 1500) * 0.6
    out = s.mix((hit, 0.9), (boom, 0.8), (steam, 0.35, 0.03), (crack, 0.4))
    return outdoors(s.taper(out, 0.4), rng, 0.6, 0.2, 0.6)


@sound("hazard/spore_cloud", variants=2, level=-26)
def spore_cloud(rng, i):
    """孢子云：很轻的、像呼吸一样慢慢起伏的沙沙声，高处一点细碎的簌簌声，底下一丝低低的嗡鸣。"""
    n = s.samples(4.0)
    t = s.times(n)
    breath = 0.55 + 0.45 * np.sin(2 * np.pi * (0.35 + 0.05 * i) * t - np.pi / 2) ** 2
    breath = breath * np.interp(t, [0, 0.6, 3.4, 4.0], [0, 1, 1, 0])
    air = s.resonant(s.pink(n, rng), "bandpass", 1400 + 500 * s.wander(n, rng, 0.8), 0.9) * breath
    rustle = s.highpass(s.crackle(n, rng, 90, width=0.0006), 3500) * breath * 0.25
    hum = s.sine(s.curve(92 + 6 * i, n), n) * 0.015 * breath
    return s.mix((air, 1.0), (rustle, 1.0), (hum, 1.0))


@sound("hazard/dust_devil", variants=2, level=-20)
def dust_devil(rng, i):
    """尘卷风：转着圈的风，一阵阵地扑过来（每秒一两次），里面夹着沙粒噼噼啪啪打在东西上的细碎声。"""
    n = s.samples(3.0)
    t = s.times(n)
    whirl = 0.65 + 0.35 * np.sin(2 * np.pi * (1.3 + 0.3 * i) * t + rng.uniform(0, 6)) + 0.1 * s.wander(n, rng, 3)
    env = np.interp(t, [0, 0.4, 2.6, 3.0], [0, 1, 1, 0])
    air = m.wind(n, rng, speed=0.75 * whirl, gust=0.2) * env
    grit = s.highpass(s.crackle(n, rng, 500 * whirl, width=0.0002), 2500) * env * whirl * 0.35
    return s.mix((air, 1.0), (grit, 1.0))
