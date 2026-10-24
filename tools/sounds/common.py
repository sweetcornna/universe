"""几类共用的声音：小爆炸、大爆炸、能量弹打中东西（ADR-0029）。"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["explosion", "impact"]


@sound("explosion/small", variants=3, level=-14)
def explosion_small(rng, i):
    """手雷、炮弹、导弹：几十米外也听得见的一声闷响，碎片噼啪，户外的回声。"""
    size = 0.5 + 0.15 * i
    x = m.explosion(rng, size=size, debris=0.7, tail=1.6)
    crack = m.n_wave(rng, 0.0025) * 0.5
    x = s.mix((x, 1.0), (s.fit(crack, len(x)), 1.0))
    return fx.space(x, fx.outdoor_ir(rng, 0.6), wet=0.35, tail=0.8)


@sound("explosion/large", variants=2, level=-13)
def explosion_large(rng, i):
    """建筑、飞船、巢穴：更长的低频和回荡，碎片落一阵。"""
    x = m.explosion(rng, size=1.6 + 0.3 * i, debris=1.0, tail=3.5)
    second = m.explosion(rng, size=0.8, debris=0.6, tail=1.5) * 0.45
    x = s.mix((x, 1.0), (second, 1.0, 0.18 + 0.05 * i))
    return fx.space(x, fx.outdoor_ir(rng, 1.4), wet=0.45, tail=1.5)


@sound("impact/energy", variants=4, level=-20)
def impact_energy(rng, i):
    """等离子弹、能量弹打中：高温气体炸开的"噗嗤"，一点电噼啪和金属的余响。"""
    n = s.samples(0.45)
    t = s.times(n)
    burst = s.resonant(s.white(n, rng), "bandpass", s.exp_curve([(0, 4500), (0.3, 900)], n), 0.9)
    burst *= s.perc(n, 0.001, 0.25)
    pop = s.lowpass(s.white(n, rng), 600) * s.perc(n, 0.0005, 0.05) * 1.5
    sizzle = s.highpass(s.crackle(n, rng, [(0, 3000), (0.35, 50)], width=0.00008), 2500) * s.perc(n, 0.002, 0.35)
    ring = m.impact(rng, freq=rng.uniform(900, 1300), decay=0.25, hardness=0.8, noise=0.0) * 0.15
    x = s.mix((burst, 1.0), (pop, 0.8), (sizzle, 0.5), (s.fit(ring, n), 1.0))
    return fx.space(x, "factory", wet=0.12, tail=0.3)
