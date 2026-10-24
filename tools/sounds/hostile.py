"""敌人和炮塔：玩家的炮塔、黑雾、守卫机（ADR-0029）。

- 炮塔照它的原理响：高斯炮塔是线圈一级级放电加弹丸的激波，激光炮塔响的是电容放电和散热（光本身不响），
  聚爆炮、导弹、等离子炮各有各的发射药、尾焰、等离子。
- 黑雾是失控的自我复制机器（ADR-0028）：金属、电弧、调频音的指数按采样保持乱跳（像数字信号出错），
  冷，不像生物。
- 守卫机是会巡逻、会喊人的无人机：悬浮的嗡声、扫频的扫描音、两音的警报、短促的调频"叽"声。
"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["turret", "darkfog", "sentinel"]


def _wander(n: int, rng: np.random.Generator, rate: float, depth: float = 1.0) -> np.ndarray:
    points = max(4, int(n / s.SR * rate) + 4)
    knots = rng.uniform(-1, 1, points)
    x = np.linspace(0, points - 3, n)
    i = np.minimum(np.floor(x).astype(int), points - 4)
    f = x - i
    p0, p1, p2, p3 = knots[i], knots[i + 1], knots[i + 2], knots[i + 3]
    out = 0.5 * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f ** 2 + (-p0 + 3 * p1 - 3 * p2 + p3) * f ** 3)
    return out * depth


# synth.wander 的最后一个采样会越界（下标取到 points−3 时 i+3 出界）；基础件修好以前先换成这个
try:
    s.wander(8, np.random.default_rng(0), 1.0)
except IndexError:
    s.wander = _wander


# ---- 共用的小件

def jitter(rng, amount: float = 0.05) -> float:
    return 1 + rng.uniform(-amount, amount)


def thump(f0: float, f1: float, length: float, attack: float = 0.002) -> np.ndarray:
    """音高往下掉的正弦：炮口、线圈、落地的那一下低频。"""
    n = s.samples(length)
    f = s.exp_curve([(0, f0), (length, f1)], n)
    return s.sine(f, n) * s.perc(n, attack, length)


def snap(rng, length: float = 0.012, cutoff: float = 2500) -> np.ndarray:
    """极短的宽带爆裂：放电、击发的起音。"""
    n = s.samples(length + 0.02)
    return s.highpass(s.white(n, rng), cutoff) * s.perc(n, 0.0003, length)


def burst(rng, length: float, cutoff: float, attack: float = 0.001) -> np.ndarray:
    """低通的噪声冲击：发射药、气浪。"""
    n = s.samples(length)
    return s.lowpass(s.white(n, rng), cutoff, 2) * s.perc(n, attack, length)


def sparks(rng, length: float, rate: float = 900, cutoff: float = 2500) -> np.ndarray:
    n = s.samples(length)
    c = s.crackle(n, rng, [(0, rate), (length, rate * 0.05)], width=0.0001)
    return s.taper(s.highpass(c, cutoff), length * 0.4)


def glitch(rng, length: float, f0: float, f1: float, rate: float = 40, ratio: float = 2.76,
           low: float = 0.5, high: float = 4.0) -> np.ndarray:
    """黑雾的"嗓音"：非谐比的调频音，指数按采样保持乱跳，听着像坏掉的数字信号。"""
    n = s.samples(length)
    f = s.exp_curve([(0, f0), (length, f1)], n)
    index = s.steps(n, rng, rate, low, high, glide=0.002)
    return s.fm(f, ratio, index, n) * s.adsr(n, 0.004, 0.03, 0.7, length * 0.4)


def chirp(rng, length: float, f0: float, f1: float, index: float = 1.2) -> np.ndarray:
    """守卫机的"叽"：一声带滑音的调频短音。"""
    n = s.samples(length)
    f = s.exp_curve([(0, f0), (length, f1)], n)
    return s.fm(f, 2.0, index, n) * s.adsr(n, 0.004, 0.02, 0.8, length * 0.3)


def outdoors(x: np.ndarray, rng, size: float = 0.6, wet: float = 0.3, tail: float = 0.4) -> np.ndarray:
    return fx.space(x, fx.outdoor_ir(rng, size), wet=wet, tail=tail)


def clip_to(x: np.ndarray, seconds: float, taper: float = 0.08) -> np.ndarray:
    """频繁的声音压在这个长度以内，末尾收干净。"""
    return s.taper(s.fit(x, s.samples(seconds)), taper)


def debris(rng, length: float, count: int, freq=(300, 900), start: float = 0.15) -> np.ndarray:
    """碎片落地：几块金属先后敲在地上。"""
    parts = []
    for _ in range(count):
        hit = m.impact(rng, freq=rng.uniform(*freq), decay=rng.uniform(0.12, 0.35), ratios=m.PLATE,
                       hardness=rng.uniform(0.5, 0.9), noise=0.25)
        parts.append((hit, rng.uniform(0.2, 0.6), rng.uniform(start, length * 0.8)))
    return s.fit(s.mix(*parts), s.samples(length))


# ---- 炮塔

@sound("turret/gauss", variants=4, level=-15)
def gauss(rng, i):
    """线圈一级级放电把弹丸推出去（越往后越快），出膛就超音速，一声激波的"啪"；炮架后坐撞一下。"""
    j = jitter(rng)
    coils = m.coil_stages(rng, stages=5, spacing=0.011 * jitter(rng, 0.1), accel=0.72, pitch=170 * j)
    crack = m.n_wave(rng, 0.0022 * jitter(rng, 0.2))
    report = burst(rng, 0.14, 220) * 1.4
    recoil = m.impact(rng, freq=190 * j, decay=0.16, ratios=m.HOLLOW, hardness=0.4, noise=0.15)
    at = 0.042 + rng.uniform(-0.004, 0.004)
    x = s.mix((coils, 0.55), (crack, 1.0, at), (report, 0.9, at), (recoil, 0.35, at + 0.01),
              (sparks(rng, 0.08, 1500), 0.15))
    return clip_to(outdoors(x, rng, 0.5, wet=0.22, tail=0.35), 0.6)


@sound("turret/laser", variants=3, level=-17)
def laser(rng, i):
    """光打出去不响：响的是电容组放电的"啪"、变压器往下掉的啸声、散热风扇和泄压。"""
    j = jitter(rng)
    n = s.samples(0.55)
    whine = s.fm(s.exp_curve([(0, 3400 * j), (0.25, 1700 * j), (0.55, 1500 * j)], n), 0.5, 0.8, n) * s.perc(n, 0.002, 0.3)
    hum = s.harmonics(100, n, [1.0, 0.5, 0.3, 0.15]) * s.perc(n, 0.005, 0.35)
    vent = s.lowpass(m.hiss(n, rng, s.envelope([(0, 0), (0.06, 1.0), (0.55, 0.0)], n), brightness=0.25), 6000)
    body = thump(260 * j, 120, 0.09)
    x = s.mix((snap(rng, 0.008, 3000), 1.0), (whine, 0.35), (hum, 0.25), (vent, 0.14), (body, 0.5),
              (sparks(rng, 0.12, 2500, 3500), 0.2))
    return clip_to(outdoors(x, rng, 0.4, wet=0.18, tail=0.25), 0.6)


@sound("turret/implosion", variants=3, level=-14)
def implosion(rng, i):
    """重炮：发射药的闷响和气浪，炮身后坐、退回，炮闩咔一声，远处的回声。"""
    j = jitter(rng, 0.04)
    boom = m.explosion(rng, size=0.55, debris=0.0, tail=0.9)
    blast = burst(rng, 0.06, 1800) * 0.8
    recoil = m.impact(rng, freq=120 * j, decay=0.45, ratios=m.HOLLOW, hardness=0.35, noise=0.1)
    breech = m.relay(rng, size=2.2, bright=0.5)
    x = s.mix((boom, 1.0), (blast, 1.0), (thump(70 * j, 38, 0.35), 0.9), (recoil, 0.35, 0.01), (breech, 0.3, 0.42 + rng.uniform(0, 0.05)))
    return clip_to(outdoors(x, rng, 1.0, wet=0.35, tail=0.6), 1.6, 0.25)


@sound("turret/missile", variants=3, level=-15)
def missile(rng, i):
    """导弹出筒：点火的"噗"，固体火箭的轰鸣，越飞越远、越闷。"""
    length = 1.3
    n = s.samples(length)
    thrust = s.envelope([(0, 0.0), (0.04, 1.0), (0.45, 0.9), (length, 0.0)], n)
    roar = m.rocket(n, rng, thrust, crackle=0.6)
    roar = s.resonant(roar, "lowpass", s.exp_curve([(0, 9000), (0.3, 5000), (length, 900)], n), 0.7)
    whoosh = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 2500), (0.5, 1100), (length, 500)], n), 2.0)
    whoosh *= s.envelope([(0, 0), (0.08, 1.0), (0.6, 0.4), (length, 0)], n)
    ignite = burst(rng, 0.05, 2500) * 1.2
    clunk = m.relay(rng, size=1.8, bright=0.3)
    x = s.mix((roar, 0.8), (whoosh, 0.5), (ignite, 0.8), (clunk, 0.25), (thump(110, 60, 0.15), 0.4))
    return clip_to(outdoors(x, rng, 0.9, wet=0.3, tail=0.5), 1.8, 0.3)


def _plasma(rng, base: float, hum: tuple, length: float, sizzle: float) -> np.ndarray:
    """等离子炮：线圈先把等离子团约束住（一小段往上走的嗡声），放出去时一声低沉的"砰"和高温气体的嘶声。"""
    j = jitter(rng, 0.04)
    pre = 0.11
    n = s.samples(pre)
    charge = s.harmonics(s.exp_curve([(0, hum[0] * j), (pre, hum[1] * j)], n), n, [1.0, 0.6, 0.4, 0.25, 0.15])
    charge *= s.envelope([(0, 0), (pre * 0.7, 1.0), (pre, 0.6)], n)
    body = thump(base * j, base * 0.55, length * 0.5)
    m_ = s.samples(length)
    hissing = s.resonant(s.white(m_, rng), "bandpass", s.exp_curve([(0, 3200), (length, 1100)], m_), 0.9)
    hissing *= s.perc(m_, 0.002, length * 0.8)
    x = s.mix((charge, 0.35), (body, 1.0, pre), (burst(rng, 0.09, 900), 0.8, pre), (hissing, sizzle, pre),
              (sparks(rng, length * 0.6, 1200), 0.2, pre), (snap(rng, 0.01, 2000), 0.5, pre))
    return x


@sound("turret/plasma", variants=3, level=-15)
def plasma(rng, i):
    x = _plasma(rng, 58, (70, 120), 0.75, 0.45)
    return clip_to(outdoors(x, rng, 0.8, wet=0.3, tail=0.5), 1.3, 0.25)


@sound("turret/sr_plasma", variants=3, level=-16)
def sr_plasma(rng, i):
    x = _plasma(rng, 95, (140, 230), 0.4, 0.5)
    return clip_to(outdoors(x, rng, 0.5, wet=0.2, tail=0.3), 0.7)


@sound("turret/plasma_burst", variants=3, level=-14)
def plasma_burst(rng, i):
    """等离子团炸开：气浪的闷响，长长的嘶声，噼啪的电火花。"""
    j = jitter(rng)
    length = 1.1
    n = s.samples(length)
    whump = burst(rng, 0.25, 350) * 1.5
    hissing = s.resonant(s.white(n, rng), "bandpass", s.exp_curve([(0, 5000 * j), (length, 1500)], n), 0.8) * s.perc(n, 0.004, length)
    x = s.mix((whump, 1.0), (thump(48 * j, 30, 0.4), 1.0), (hissing, 0.5), (sparks(rng, 0.7, 1800), 0.3),
              (snap(rng, 0.015, 1500), 0.6))
    return clip_to(outdoors(x, rng, 0.9, wet=0.35, tail=0.6), 1.7, 0.3)


# ---- 黑雾的地面单位

@sound("darkfog/raider_zap", variants=4, level=-16)
def raider_zap(rng, i):
    """突袭者的近战电弧：一小段交流电弧的噼啪和嗡声，打中时一声放电。"""
    j = jitter(rng)
    length = 0.32
    n = s.samples(length)
    arc = m.arc(n, rng, s.envelope([(0, 1.0), (0.18, 0.7), (length, 0.0)], n), mains=50 * j)
    z = m.zap(rng, start=2600 * j, end=480, length=0.14, bite=0.8, fm_index=2.5)
    arc = s.highpass(arc, 250, 2)
    x = s.mix((arc, 0.35), (z, 0.9), (glitch(rng, 0.12, 900 * j, 500, rate=60), 0.25))
    return clip_to(x, 0.45)


@sound("darkfog/ranger_shot", variants=4, level=-17)
def ranger_shot(rng, i):
    """游骑兵的激光：电容放电的"咻"，一点低频和数字信号似的毛刺。"""
    j = jitter(rng)
    z = m.zap(rng, start=4200 * j, end=950 * j, length=0.11, bite=0.6, fm_index=3.0)
    x = s.mix((z, 1.0), (thump(150 * j, 90, 0.06), 0.5), (glitch(rng, 0.09, 1800 * j, 700, rate=80), 0.2, 0.02))
    return clip_to(outdoors(x, rng, 0.4, wet=0.15, tail=0.2), 0.4)


@sound("darkfog/guardian_shot", variants=3, level=-16)
def guardian_shot(rng, i):
    """守卫者：更重的一发，低频的推力和电噼啪。"""
    j = jitter(rng)
    z = m.zap(rng, start=2400 * j, end=260 * j, length=0.24, bite=0.7, fm_index=2.2)
    x = s.mix((z, 1.0), (thump(80 * j, 45, 0.2), 0.8), (burst(rng, 0.07, 700), 0.5), (sparks(rng, 0.25, 900), 0.25))
    return clip_to(outdoors(x, rng, 0.5, wet=0.2, tail=0.3), 0.6)


@sound("darkfog/unit_ambient", variants=4, level=-22)
def unit_ambient(rng, i):
    """机器的"呼吸"：冷却液泵一吸一呼带起的共鸣，底下一层伺服电机的嗡声，偶尔继电器咔一下。"""
    length = 1.9
    n = s.samples(length)
    # 吸一口、停一下、再呼出去
    breath = s.envelope([(0, 0), (0.55, 1.0), (0.8, 0.15), (1.25, 0.85), (length, 0)], n)
    air = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 280), (0.7, 850 * jitter(rng)), (length, 260)], n), 3.0)
    rpm = 1800 * (1 + 0.15 * s.wander(n, rng, 2))
    motor = m.electric_motor(n, rng, rpm, coils=5, chassis=0.4, harshness=0.1, brushes=0.15, air=0.1)
    x = s.mix((air * breath, 1.0), (motor * s.envelope([(0, 0), (0.3, 1), (length - 0.3, 1), (length, 0)], n), 0.035),
              (glitch(rng, 0.25, 220, 180, rate=12, low=0.2, high=1.5), 0.08, rng.uniform(0.4, 1.2)))
    for _ in range(rng.integers(1, 3)):
        x = s.mix((x, 1.0), (m.relay(rng, size=1.2, bright=0.3), 0.15, rng.uniform(0.2, 1.6)))
    return s.taper(x, 0.3)


@sound("darkfog/unit_hurt", variants=4, level=-16)
def unit_hurt(rng, i):
    """挨打：金属壳被敲响，电路短路的火花，一声变调的毛刺。"""
    j = jitter(rng, 0.08)
    shell = m.impact(rng, freq=rng.uniform(560, 900), decay=0.22, ratios=m.PLATE, hardness=0.75, noise=0.35)
    x = s.mix((shell, 0.8), (sparks(rng, 0.15, 2000), 0.3), (glitch(rng, 0.14, 950 * j, 380 * j, rate=70), 0.35, 0.01),
              (thump(180 * j, 110, 0.06), 0.4))
    return clip_to(x, 0.4)


@sound("darkfog/unit_death", variants=3, level=-15)
def unit_death(rng, i):
    """毁坏：断电的啸声往下沉，短路的火花，壳体炸开，碎片落地。"""
    j = jitter(rng)
    length = 1.3
    n = s.samples(0.75)
    fall = s.fm(s.exp_curve([(0, 900 * j), (0.75, 70)], n), 2.76, s.steps(n, rng, 25, 0.3, 3.5, 0.008), n)
    fall *= s.envelope([(0, 0), (0.02, 1.0), (0.5, 0.6), (0.75, 0)], n)
    pop = s.mix((burst(rng, 0.18, 900), 1.2), (thump(90 * j, 40, 0.3), 1.0))
    x = s.mix((fall, 0.4), (sparks(rng, 0.6, 1600), 0.3), (pop, 1.0, 0.25), (debris(rng, length, 3, (400, 1100), 0.4), 0.7))
    return clip_to(outdoors(x, rng, 0.6, wet=0.25, tail=0.4), 1.6, 0.3)


@sound("darkfog/tower_laser", variants=3, level=-17)
def tower_laser(rng, i):
    """高能激光塔：烧穿东西的一秒，电源的嗡声带着蜂鸣，目标处的嘶嘶声。"""
    j = jitter(rng, 0.04)
    length = 0.85
    n = s.samples(length)
    env = s.envelope([(0, 0), (0.03, 1.0), (0.6, 0.85), (length, 0)], n)
    buzz = s.fm(180 * j * (1 + 0.01 * s.wander(n, rng, 8)), 2.76, 1.8 + 0.6 * s.wander(n, rng, 6), n)
    buzz = s.softclip(buzz * 1.5, 1.5) * env
    burn = s.resonant(s.white(n, rng), "bandpass", 3200 * j, 1.2) * env * (0.7 + 0.3 * s.wander(n, rng, 20))
    x = s.mix((snap(rng, 0.01, 2500), 0.7), (buzz, 0.45), (burn, 0.4), (sparks(rng, length, 600), 0.15))
    return clip_to(outdoors(x, rng, 0.5, wet=0.2, tail=0.3), 1.1)


@sound("darkfog/sentry_fire", variants=3, level=-15)
def sentry_fire(rng, i):
    """等离子哨塔把一团等离子抛出去：低沉的"嗵"，尾巴拖着嘶声。"""
    j = jitter(rng)
    length = 0.8
    n = s.samples(length)
    trail = s.resonant(s.white(n, rng), "bandpass", s.exp_curve([(0, 2200 * j), (length, 900)], n), 1.2) * s.perc(n, 0.01, length)
    x = s.mix((thump(140 * j, 55, 0.22), 1.0), (burst(rng, 0.12, 600), 0.9), (trail, 0.35),
              (glitch(rng, 0.1, 400 * j, 250, rate=30), 0.15))
    return clip_to(outdoors(x, rng, 0.7, wet=0.3, tail=0.4), 1.1)


@sound("darkfog/building_hum", variants=3, level=-22)
def building_hum(rng, i):
    """黑雾的建筑：变压器似的低频嗡声，两个差一点的音拍出起伏，上面飘着一丝乱跳的高音。"""
    j = jitter(rng, 0.04)
    length = 2.6
    n = s.samples(length)
    base = 58 * j
    drone = s.harmonics(base, n, [1.0, 0.25, 0.55, 0.12, 0.3, 0.08, 0.15]) + 0.8 * s.harmonics(base + 0.7, n, [1.0, 0.2, 0.5])
    swell = s.envelope([(0, 0), (0.7, 1.0), (1.9, 0.9), (length, 0)], n)
    high = s.sine(s.steps(n, rng, 6, 3200, 4800, glide=0.03), n) * 0.04
    x = (drone * 0.5 + high) * swell + 0.1 * s.resonant(s.pink(n, rng), "lowpass", 400, 0.7) * swell
    return x


@sound("darkfog/building_hurt", variants=4, level=-16)
def building_hurt(rng, i):
    """建筑挨打：大块空心的金属壳被打响，火花和毛刺。"""
    j = jitter(rng, 0.08)
    shell = m.impact(rng, freq=rng.uniform(220, 380), decay=0.5, ratios=m.HOLLOW, hardness=0.6, noise=0.4)
    x = s.mix((shell, 0.9), (sparks(rng, 0.2, 1500), 0.3), (glitch(rng, 0.18, 520 * j, 260, rate=45), 0.3, 0.02),
              (thump(110 * j, 60, 0.12), 0.5))
    return clip_to(x, 0.6)


@sound("darkfog/collapse", variants=2, level=-13)
def collapse(rng, i):
    """建筑垮掉：钢结构呻吟着变形，里面的储能炸开，碎片砸了一地，电火花慢慢灭。"""
    j = jitter(rng, 0.04)
    length = 3.8
    groan_len = 1.3
    n = s.samples(groan_len)
    groan = s.fm(s.exp_curve([(0, 95 * j), (groan_len, 48)], n), 1.41, 3 + 2 * s.wander(n, rng, 4), n)
    groan = s.softclip(groan * 2, 2) * s.envelope([(0, 0), (0.15, 1.0), (1.0, 0.8), (groan_len, 0)], n)
    groan = s.resonant(groan, "lowpass", 900, 0.8)
    boom = m.explosion(rng, size=1.2, debris=0.9, tail=2.2)
    x = s.mix((groan, 0.6), (boom, 1.0, 0.35), (debris(rng, length, 7, (180, 700), 0.8), 0.8),
              (sparks(rng, length * 0.8, 500), 0.2, 0.4))
    return outdoors(x, rng, 1.3, wet=0.4, tail=1.2)


# ---- 太空的黑雾和巢穴

@sound("darkfog/lancer_shot", variants=3, level=-17)
def lancer_shot(rng, i):
    """枪骑的速射：短促的放电，非谐的金属音色。"""
    j = jitter(rng)
    z = m.zap(rng, start=3600 * j, end=700 * j, length=0.1, bite=0.5, fm_index=3.5)
    tone = glitch(rng, 0.08, 1400 * j, 900 * j, rate=90, ratio=2.76)
    x = s.mix((z, 1.0), (tone, 0.3), (thump(170 * j, 100, 0.05), 0.4))
    return clip_to(outdoors(x, rng, 0.5, wet=0.15, tail=0.25), 0.45)


@sound("darkfog/bomb_drop", variants=2, level=-16)
def bomb_drop(rng, i):
    """枪骑投弹：挂架松开的"咔嗒"，炸弹的尾翼一路往下掉的呼啸。"""
    j = jitter(rng, 0.06)
    length = 2.6
    n = s.samples(length)
    f = s.exp_curve([(0, 1500 * j), (length, 480 * j)], n) * (1 + 0.006 * s.sine(5.5, n))
    tone = s.sine(f, n)
    breath = s.resonant(s.resonant(s.white(n, rng), "bandpass", f, 9), "bandpass", f, 9) * 0.6
    swell = s.envelope([(0, 0), (0.25, 0.4), (length - 0.15, 1.0), (length, 0)], n)
    release = s.mix((m.relay(rng, size=2.4, bright=0.6), 1.0), (m.impact(rng, freq=420, decay=0.2, ratios=m.PLATE, hardness=0.7), 0.4))
    x = s.mix((release, 0.8), ((tone * 0.35 + breath) * swell, 0.7, 0.12))
    return fx.space(x, fx.outdoor_ir(rng, 0.8), wet=0.2, tail=0.3)


@sound("darkfog/humpback_shot", variants=3, level=-14)
def humpback_shot(rng, i):
    """座头鲸的重炮：很低的推力，放电往下扫，一长串电噼啪。"""
    j = jitter(rng, 0.04)
    z = m.zap(rng, start=1500 * j, end=120, length=0.38, bite=0.8, fm_index=2.0)
    x = s.mix((z, 0.9), (thump(55 * j, 30, 0.5), 1.2), (burst(rng, 0.2, 500), 0.9), (sparks(rng, 0.5, 900), 0.25),
              (glitch(rng, 0.3, 300 * j, 120, rate=20), 0.2))
    return clip_to(outdoors(x, rng, 1.0, wet=0.3, tail=0.5), 1.3, 0.25)


@sound("darkfog/hive_plasma", variants=3, level=-14)
def hive_plasma(rng, i):
    """巢穴的等离子塔：比等离子炮塔更大的一团，先有一段低沉的蓄能。"""
    x = _plasma(rng, 42, (45, 85), 1.0, 0.4)
    x = s.mix((x, 1.0), (glitch(rng, 0.2, 240, 140, rate=18), 0.15))
    return clip_to(outdoors(x, rng, 1.2, wet=0.3, tail=0.6), 1.6, 0.3)


@sound("darkfog/hive_laser", variants=3, level=-17)
def hive_laser(rng, i):
    """相位激光塔：一声尖锐往下扫的调频，带着移相的"嗖嗖"感。"""
    j = jitter(rng)
    length = 0.45
    n = s.samples(length)
    sweep = s.fm(s.exp_curve([(0, 2600 * j), (length, 700 * j)], n), 1.5, s.envelope([(0, 4), (length, 0.8)], n), n)
    sweep *= s.perc(n, 0.002, length)
    x = s.mix((sweep, 0.8), (snap(rng, 0.008, 3000), 0.8), (thump(200 * j, 120, 0.06), 0.4))
    x = fx.phaser(x, rate=6.0, depth=0.8, feedback=0.4, mix=0.6)
    return clip_to(outdoors(x, rng, 0.6, wet=0.2, tail=0.3), 0.75)


@sound("darkfog/hive_core", variants=2, level=-20)
def hive_core(rng, i):
    """巢穴核心：极低的嗡声，几个失谐的泛音慢慢拍，隔一阵一下机械的"脉搏"。"""
    j = jitter(rng, 0.03)
    length = 4.2
    n = s.samples(length)
    base = 36 * j
    drone = sum(s.harmonics(base * r, n, [1.0, 0.4, 0.2]) * g for r, g in ((1.0, 1.0), (1.503, 0.35), (2.01, 0.3), (2.76, 0.12)))
    drone *= 1 + 0.25 * s.wander(n, rng, 0.8)
    choir = fx.chorus(s.saw(base * 4.02, n, 24), rate=0.3, depth=0.4, mix=0.6)
    choir = s.resonant(choir, "lowpass", 700, 0.8) * 0.12
    pulses = []
    for k in range(3):
        pulses.append((thump(75 * j, 40, 0.35), 0.6, 0.4 + k * 1.3))
    swell = s.envelope([(0, 0), (0.8, 1.0), (length - 0.8, 1.0), (length, 0)], n)
    x = s.mix(((drone * 0.6 + choir) * swell, 1.0), *pulses)
    return s.taper(x, 0.5)


@sound("darkfog/craft_hum", variants=2, level=-21)
def craft_hum(rng, i):
    """中继站和种子飞过：涡轮似的嗡声，一跳一跳的推进脉冲，上面一层闪烁的调频。"""
    j = jitter(rng, 0.04)
    length = 3.0
    n = s.samples(length)
    engine = m.turbine(n, rng, 2600 * j * (1 + 0.02 * s.wander(n, rng, 1)), blades=11, roar=0.6, whine=0.7)
    engine = s.tremolo(engine, 3.2 * j, 0.5)
    shimmer = s.fm(330 * j, 2.76, 1.5 + s.wander(n, rng, 3), n) * 0.06
    swell = s.envelope([(0, 0), (0.7, 1.0), (length - 0.7, 1.0), (length, 0)], n)
    return (engine + shimmer) * swell


@sound("darkfog/relay_land", variants=2, level=-14)
def relay_land(rng, i):
    """中继站落地：反推的轰鸣越来越近，一声沉重的着地，支脚的伺服电机展开、锁死，泄压。"""
    j = jitter(rng, 0.04)
    length = 3.6
    n = s.samples(length)
    touch = 1.05
    thrust = s.envelope([(0, 0.0), (0.9, 1.0), (touch + 0.1, 0.6), (touch + 0.6, 0.0), (length, 0.0)], n)
    retro = m.rocket(n, rng, thrust, crackle=0.4)
    retro = s.resonant(retro, "lowpass", s.exp_curve([(0, 1200), (touch, 4500), (length, 1500)], n), 0.7)
    landing = s.mix((m.explosion(rng, size=0.8, debris=0.0, tail=1.2), 0.8),
                    (m.impact(rng, freq=88 * j, decay=1.0, ratios=m.HOLLOW, hardness=0.3, noise=0.2), 0.6))
    nm = s.samples(1.3)
    rpm = m.servo_moves(nm, rng, [(0.0, 0.35, 0.8), (0.45, 0.3, 0.6), (0.85, 0.35, 0.9)], top_rpm=7000)
    servo = m.electric_motor(nm, rng, rpm, coils=7, gear_teeth=19, chassis=0.7, harshness=0.2)
    locks = s.mix(*[(m.relay(rng, size=2.0, bright=0.5), 0.6, t) for t in (0.36, 0.77, 1.22)])
    vent = m.hiss(s.samples(1.0), rng, s.envelope([(0, 0), (0.05, 1.0), (1.0, 0)], s.samples(1.0)), brightness=0.5)
    x = s.mix((retro, 0.7), (landing, 1.0, touch), (servo, 0.25, touch + 0.5), (locks, 0.6, touch + 0.5), (vent, 0.25, touch + 1.6))
    return outdoors(x, rng, 1.2, wet=0.35, tail=0.9)


# ---- 守卫机

@sound("sentinel/scan", variants=2, level=-18)
def sentinel_scan(rng, i):
    """扫描：一道往上扫的探测音，带着雷达似的细碎回声。"""
    j = jitter(rng, 0.03)
    length = 1.4
    n = s.samples(length)
    f = s.exp_curve([(0, 420 * j), (1.2, 1700 * j), (length, 1750 * j)], n)
    tone = s.sine(f, n) * (1 - 0.35 * (0.5 + 0.5 * s.sine(32, n)))
    tone *= s.envelope([(0, 0), (0.08, 0.7), (1.15, 1.0), (length, 0)], n)
    ticks = s.resonant(s.impulses(n, 14, 0.05, rng), "bandpass", 3800, 6) * 1.5
    x = s.mix((tone, 0.6), (ticks, 0.25), (s.resonant(s.pink(n, rng), "bandpass", f, 4) * 0.2, 1.0))
    return s.taper(fx.space(x, "corridor", wet=0.15, tail=0.3), 0.3)


@sound("sentinel/alert", variants=2, level=-16)
def sentinel_alert(rng, i):
    """警报：高低两个音交替响三下，底下一声低沉的提示。"""
    j = jitter(rng, 0.02)
    hi, lo = 880 * j, 622 * j
    parts = []
    for k in range(4):
        f = hi if k % 2 == 0 else lo
        n = s.samples(0.17)
        tone = s.square(f, n, 12) * 0.5 + s.sine(f, n) * 0.5
        tone = s.resonant(tone, "lowpass", 3500, 0.7) * s.adsr(n, 0.005, 0.02, 0.85, 0.04)
        parts.append((tone, 0.7, k * 0.2))
    parts.append((thump(110, 80, 0.35), 0.6, 0.0))
    x = s.mix(*parts)
    return outdoors(x, rng, 0.6, wet=0.25, tail=0.4)


@sound("sentinel/stand_down", variants=1, level=-18)
def sentinel_stand_down(rng, i):
    """解除警戒：一声往下滑的音，电源的嗡声慢慢退下去。"""
    length = 1.5
    n = s.samples(length)
    glide = s.fm(s.exp_curve([(0, 1200), (length, 280)], n), 2.0, s.envelope([(0, 1.2), (length, 0.2)], n), n)
    glide *= s.envelope([(0, 0), (0.05, 1.0), (length, 0)], n)
    hum = s.harmonics(s.exp_curve([(0, 220), (length, 140)], n), n, [1.0, 0.3, 0.15]) * s.envelope([(0, 0.5), (length, 0)], n)
    x = s.mix((glide, 0.6), (hum, 0.3))
    return outdoors(x, rng, 0.5, wet=0.2, tail=0.3)


@sound("sentinel/warp", variants=3, level=-16)
def sentinel_warp(rng, i):
    """跃迁：往上扫的嘶声和闪烁的调频，最后"噗"地合上。"""
    j = jitter(rng)
    length = 0.75
    n = s.samples(length)
    whoosh = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 300), (length, 4500 * j)], n), 2.5)
    whoosh *= s.envelope([(0, 0), (0.55, 1.0), (length, 0.1)], n)
    shimmer = s.fm(s.exp_curve([(0, 300 * j), (length, 1500 * j)], n), 3.01, 2.0, n) * s.envelope([(0, 0), (0.5, 0.8), (length, 0)], n)
    pop = s.mix((thump(320 * j, 90, 0.08), 1.0), (snap(rng, 0.01, 1800), 0.6))
    x = s.mix((whoosh, 0.6), (shimmer, 0.2), (pop, 0.8, length - 0.05))
    return clip_to(outdoors(x, rng, 0.4, wet=0.2, tail=0.3), 1.1)


@sound("sentinel/shot", variants=4, level=-17)
def sentinel_shot(rng, i):
    """守卫机的点射：短促的等离子"咻"。"""
    j = jitter(rng)
    z = m.zap(rng, start=2900 * j, end=620 * j, length=0.085, bite=0.6, fm_index=2.0)
    x = s.mix((z, 1.0), (thump(170 * j, 100, 0.05), 0.5), (chirp(rng, 0.04, 1900 * j, 1500 * j), 0.15))
    return clip_to(outdoors(x, rng, 0.4, wet=0.15, tail=0.2), 0.35)


@sound("sentinel/heavy_shot", variants=3, level=-15)
def sentinel_heavy_shot(rng, i):
    """重型守卫机、步行守卫机：更粗的一发，低频的后坐和电噼啪。"""
    j = jitter(rng)
    z = m.zap(rng, start=1900 * j, end=210 * j, length=0.2, bite=0.8, fm_index=2.5)
    x = s.mix((z, 1.0), (thump(85 * j, 45, 0.22), 0.9), (burst(rng, 0.08, 800), 0.6), (sparks(rng, 0.3, 900), 0.2))
    return clip_to(outdoors(x, rng, 0.7, wet=0.25, tail=0.35), 0.75)


@sound("sentinel/ambient", variants=3, level=-22)
def sentinel_ambient(rng, i):
    """悬浮的嗡声：两个差几赫兹的音拍着，中间夹一串"叽叽"的自言自语。"""
    j = jitter(rng, 0.04)
    length = 2.2
    n = s.samples(length)
    f = 218 * j * (1 + 0.004 * s.wander(n, rng, 1.5))
    hover = s.harmonics(f, n, [1.0, 0.35, 0.2, 0.08]) + s.harmonics(f * 1.013, n, [0.8, 0.25])
    hover *= s.envelope([(0, 0), (0.4, 1.0), (length - 0.5, 1.0), (length, 0)], n)
    talk = []
    at = rng.uniform(0.5, 0.9)
    for _ in range(int(rng.integers(3, 6))):
        d = rng.uniform(0.04, 0.09)
        f0 = rng.uniform(1100, 2200)
        talk.append((chirp(rng, d, f0, f0 * rng.uniform(0.7, 1.4)), rng.uniform(0.25, 0.45), at))
        at += d + rng.uniform(0.02, 0.07)
    x = s.mix((hover, 0.3), *talk)
    return s.taper(x, 0.3)


@sound("sentinel/hurt", variants=4, level=-16)
def sentinel_hurt(rng, i):
    """挨打：外壳"当"一声，火花，一声慌乱往下掉的"叽"。"""
    j = jitter(rng, 0.08)
    shell = m.impact(rng, freq=rng.uniform(1000, 1450), decay=0.18, ratios=m.PLATE, hardness=0.9, noise=0.3)
    x = s.mix((shell, 0.7), (sparks(rng, 0.12, 2200), 0.3), (chirp(rng, 0.13, 1800 * j, 700 * j, 2.5), 0.35, 0.015))
    return clip_to(x, 0.35)


@sound("sentinel/death", variants=2, level=-15)
def sentinel_death(rng, i):
    """坠毁：一声往下沉的"叽——"，短路，炸开，零件落地。"""
    j = jitter(rng)
    length = 1.5
    fall = chirp(rng, 0.6, 1600 * j, 180, 3.0)
    pop = s.mix((burst(rng, 0.2, 1100), 1.2), (thump(120 * j, 45, 0.3), 0.9), (snap(rng, 0.015, 1500), 0.6))
    x = s.mix((fall, 0.4), (sparks(rng, 0.5, 1800), 0.3), (pop, 1.0, 0.5), (debris(rng, length, 4, (600, 1500), 0.75), 0.6))
    return clip_to(outdoors(x, rng, 0.6, wet=0.25, tail=0.4), 1.8, 0.3)


@sound("sentinel/vent_hit", variants=2, level=-15)
def sentinel_vent_hit(rng, i):
    """打中散热口：尖锐的金属"叮"，一团电火花，冷却气体泄出来。"""
    j = jitter(rng, 0.05)
    ping = m.impact(rng, freq=2100 * j, decay=0.4, ratios=m.BAR, hardness=1.0, noise=0.2)
    n = s.samples(0.45)
    gas = m.hiss(n, rng, s.envelope([(0, 0), (0.02, 1.0), (0.45, 0)], n), brightness=0.6)
    x = s.mix((ping, 0.7), (sparks(rng, 0.2, 3000), 0.4), (gas, 0.2, 0.01), (snap(rng, 0.01), 0.5))
    return clip_to(x, 0.55)


@sound("sentinel/step", variants=4, level=-20)
def sentinel_step(rng, i):
    """机械腿迈一步：伺服电机转一下，金属脚掌落地，液压的一小声。"""
    j = jitter(rng, 0.08)
    n = s.samples(0.14)
    servo = m.electric_motor(n, rng, m.servo_moves(n, rng, [(0, 0.12, 0.8)], top_rpm=9000 * j), coils=6, chassis=0.3)
    foot = m.impact(rng, freq=rng.uniform(140, 200), decay=0.14, ratios=m.HOLLOW, hardness=0.4, noise=0.4)
    k = s.samples(0.12)
    hyd = m.hiss(k, rng, s.envelope([(0, 0), (0.01, 1.0), (0.12, 0)], k), brightness=0.3)
    x = s.mix((servo, 0.25), (foot, 1.0, 0.1), (hyd, 0.12, 0.11), (burst(rng, 0.05, 300), 0.4, 0.1))
    return clip_to(x, 0.32)


@sound("sentinel/pounce", variants=2, level=-16)
def sentinel_pounce(rng, i):
    """四足守卫机扑出去：液压杆一推，气阀泄压，伺服电机往上尖啸。"""
    j = jitter(rng)
    n = s.samples(0.45)
    rpm = s.exp_curve([(0, 3000), (0.3, 14000 * j), (0.45, 12000 * j)], n)
    servo = m.electric_motor(n, rng, rpm, coils=6, gear_teeth=13, chassis=0.5, harshness=0.2) * s.envelope([(0, 0), (0.05, 1), (0.45, 0)], n)
    piston = m.impact(rng, freq=160 * j, decay=0.2, ratios=m.HOLLOW, hardness=0.5, noise=0.3)
    k = s.samples(0.3)
    gas = m.hiss(k, rng, s.envelope([(0, 0), (0.01, 1.0), (0.3, 0)], k), brightness=0.45)
    x = s.mix((piston, 1.0), (gas, 0.45), (servo, 0.25, 0.02), (burst(rng, 0.06, 500), 0.5))
    return clip_to(x, 0.55)


@sound("sentinel/strike", variants=2, level=-15)
def sentinel_strike(rng, i):
    """扑中：一团金属重重撞上来。"""
    j = jitter(rng)
    clang = m.impact(rng, freq=340 * j, decay=0.3, ratios=m.PLATE, hardness=0.7, noise=0.5)
    x = s.mix((clang, 0.8), (burst(rng, 0.08, 400), 1.0), (thump(120 * j, 60, 0.1), 0.6))
    return clip_to(x, 0.45)


@sound("sentinel/charge", variants=1, level=-18)
def sentinel_charge(rng, i):
    """四足守卫机蓄能：往上爬的调频啸声，电火花越来越密。"""
    length = 1.0
    n = s.samples(length)
    f = s.exp_curve([(0, 180), (length, 1700)], n)
    whine = s.fm(f, 1.5, s.envelope([(0, 0.5), (length, 3.0)], n), n) * s.envelope([(0, 0), (0.1, 0.5), (length, 1.0)], n)
    crackle = s.highpass(s.crackle(n, rng, [(0, 50), (length, 1500)], width=0.0001), 2500)
    x = s.mix((whine, 0.5), (crackle, 0.3), (s.harmonics(100, n, [1, 0.5, 0.3]) * s.envelope([(0, 0), (length, 1)], n), 0.12))
    return s.taper(x, 0.03)


@sound("sentinel/laser", variants=2, level=-15)
def sentinel_laser(rng, i):
    """四足守卫机的激光：蓄满的能量一下放掉，粗的蜂鸣，目标处炸开。"""
    j = jitter(rng)
    length = 0.6
    n = s.samples(length)
    buzz = s.softclip(s.saw(300 * j * (1 + 0.02 * s.wander(n, rng, 10)), n, 30) * 1.4, 1.5) * s.perc(n, 0.002, length)
    burn = s.resonant(s.white(n, rng), "bandpass", 2600, 1.0) * s.perc(n, 0.002, length * 0.8)
    x = s.mix((snap(rng, 0.012, 2000), 1.0), (buzz, 0.35), (burn, 0.35), (burst(rng, 0.15, 900), 0.8, 0.02),
              (thump(150 * j, 60, 0.15), 0.6))
    return clip_to(outdoors(x, rng, 0.6, wet=0.25, tail=0.35), 0.95)


@sound("sentinel/roar", variants=2, level=-14)
def sentinel_roar(rng, i):
    """步行守卫机的轰鸣：伺服机构和低音号角似的共鸣一起起来，压着失真。"""
    j = jitter(rng, 0.04)
    length = 2.0
    n = s.samples(length)
    f = s.exp_curve([(0, 58 * j), (0.35, 74 * j), (1.4, 70 * j), (length, 52 * j)], n) * (1 + 0.01 * s.wander(n, rng, 7))
    horn = s.saw(f, n, 60)
    formants = sum(s.resonant(horn, "bandpass", s.exp_curve([(0, fc * 0.8), (0.4, fc), (length, fc * 0.85)], n), 4) * g
                   for fc, g in ((420, 1.0), (1150, 0.6), (2600, 0.25)))
    formants = s.softclip(formants * 2.5, 2.5)
    env = s.envelope([(0, 0), (0.3, 1.0), (1.4, 0.9), (length, 0)], n)
    servo = m.electric_motor(n, rng, s.exp_curve([(0, 4000), (0.4, 11000), (length, 6000)], n), coils=6, gear_teeth=17,
                             chassis=0.8, harshness=0.4) * env
    x = s.mix((formants * env, 0.8), (servo, 0.2), (s.lowpass(horn, 200) * env, 0.5))
    return outdoors(x, rng, 1.0, wet=0.35, tail=0.7)


@sound("sentinel/stomp", variants=2, level=-13)
def sentinel_stomp(rng, i):
    """践踏：几吨重的机械腿砸在地上，地面的闷响和碎石，金属腿的回音。"""
    j = jitter(rng, 0.04)
    boom = m.explosion(rng, size=0.9, debris=0.2, tail=1.4)
    leg = m.impact(rng, freq=70 * j, decay=0.8, ratios=m.HOLLOW, hardness=0.3, noise=0.2)
    n = s.samples(1.2)
    rubble = s.resonant(s.crackle(n, rng, [(0, 600), (0.3, 120), (1.2, 0)], width=0.0006), "bandpass", 900, 0.8)
    x = s.mix((boom, 1.0), (leg, 0.6), (thump(55 * j, 30, 0.4), 0.8), (rubble, 0.5, 0.03))
    return outdoors(x, rng, 1.1, wet=0.35, tail=0.8)


@sound("sentinel/repair", variants=2, level=-20)
def sentinel_repair(rng, i):
    """维修守卫机的焊接束：小电弧的嗡嗡噼啪，熔化的嘶声。"""
    j = jitter(rng, 0.05)
    length = 1.3
    n = s.samples(length)
    env = s.envelope([(0, 0), (0.08, 1.0), (length - 0.25, 0.9), (length, 0)], n)
    arc = m.arc(n, rng, 0.45 * (1 + 0.3 * s.wander(n, rng, 5)), mains=60 * j)
    fizz = s.resonant(s.white(n, rng), "bandpass", 4200, 1.5) * (0.6 + 0.4 * s.wander(n, rng, 15))
    x = (arc * 0.6 + fizz * 0.2) * env
    return s.taper(x, 0.05)


@sound("sentinel/summon_charge", variants=1, level=-18)
def sentinel_summon_charge(rng, i):
    """召唤者蓄能：几层失谐的锯齿波一起往上爬，滤波器慢慢打开。"""
    length = 1.5
    n = s.samples(length)
    f = s.exp_curve([(0, 90), (length, 260)], n)
    layers = sum(s.saw(f * d, n, 40) for d in (1.0, 1.007, 0.993, 2.003))
    swept = s.resonant(layers, "lowpass", s.exp_curve([(0, 250), (length, 3800)], n), 4.0)
    crackle = s.highpass(s.crackle(n, rng, [(0, 20), (length, 700)], width=0.0001), 2500)
    x = s.mix((swept * s.envelope([(0, 0), (0.2, 0.6), (length, 1.0)], n), 0.35), (crackle, 0.2))
    return s.taper(x, 0.04)


@sound("sentinel/summon", variants=2, level=-16)
def sentinel_summon(rng, i):
    """召唤：能量一下子放出去，往下绽开的调频和气浪。"""
    j = jitter(rng)
    length = 1.0
    n = s.samples(length)
    bloom = s.fm(s.exp_curve([(0, 1400 * j), (length, 160 * j)], n), 1.41, s.envelope([(0, 5), (length, 0.5)], n), n)
    bloom *= s.perc(n, 0.003, length)
    whoosh = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 3500), (length, 400)], n), 1.5) * s.perc(n, 0.01, length)
    x = s.mix((bloom, 0.5), (whoosh, 0.5), (thump(130 * j, 50, 0.25), 0.8), (snap(rng, 0.015, 1500), 0.5))
    return clip_to(outdoors(x, rng, 0.7, wet=0.3, tail=0.5), 1.5, 0.3)
