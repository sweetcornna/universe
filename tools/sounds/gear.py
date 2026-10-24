"""玩家的装备：多功能工具、武器、宇航服、分析镜（ADR-0029）。

光束本身不出声，听到的都是设备：电源的呜声、冷却风扇、放电的爆裂、高温气体、打在石头上的灼烧。
宇航服的声音在头盔里，很干；武器在户外，带一点地形的回声。
"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["tool", "weapon", "suit", "visor"]

# 循环开头先多算这么久再扔掉：混响和滤波器的起振不留在循环里
WARMUP = 0.4


def per(f: float, length: float) -> float:
    """把频率取整到循环长度里整数个周期：有音高的成分首尾相位接得上，交叉淡化不打架。"""
    return max(1.0, round(f * length)) / length


def lfo(n: int, length: float, rate: float, start: float = 0.0) -> np.ndarray:
    """周期能整除循环长度的正弦（时间从 -WARMUP 算起，扔掉开头以后还是整周期）。"""
    t = s.times(n) - WARMUP
    return np.sin(2 * np.pi * per(rate, length) * t + start)


def drift(n: int, rng, rate: float, depth: float = 1.0) -> np.ndarray:
    """平滑的随机起伏（synth.wander 在最后一个采样上越界，先用余弦插值的这个）。"""
    points = max(4, int(n / s.SR * rate) + 4)
    knots = rng.uniform(-1, 1, points)
    x = np.linspace(0, points - 1, n)
    i = np.minimum(np.floor(x).astype(int), points - 2)
    w = (1 - np.cos(np.pi * (x - i))) / 2
    return (knots[i] * (1 - w) + knots[i + 1] * w) * depth


def warm(n_loop: float, crossfade: float = 0.4) -> int:
    return s.samples(n_loop + crossfade + WARMUP)


def unwarm(x: np.ndarray) -> np.ndarray:
    return x[s.samples(WARMUP):]


def short(x: np.ndarray, seconds: float, tail: float = 0.12) -> np.ndarray:
    """开火的声音截到 seconds 秒以内，末尾收干净。"""
    return s.taper(s.fit(x, s.samples(seconds)), tail) if len(x) > s.samples(seconds) else x


def outdoors(x: np.ndarray, rng, size: float, wet: float, tail: float) -> np.ndarray:
    return fx.space(x, fx.outdoor_ir(rng, size), wet=wet, tail=tail)


def helmet(x: np.ndarray, wet: float = 0.1, tail: float = 0.12) -> np.ndarray:
    """头盔里：驾驶舱那么大的空间都嫌大，湿声给得很少。"""
    return fx.space(x, "cockpit", wet=wet, tail=tail)


def thump(f0: float, f1: float, seconds: float, decay: float) -> np.ndarray:
    n = s.samples(seconds)
    return s.sine(s.exp_curve([(0, f0), (seconds * 0.6, f1), (seconds, f1)], n), n) * s.perc(n, 0.001, decay)


def beep(f: float, seconds: float, edge: float = 0.25) -> np.ndarray:
    """电子提示音：正弦带一点方波的棱角，低通压掉刺耳的高次。"""
    n = s.samples(seconds)
    tone = s.sine(f, n) + edge * s.square(f, n, 9)
    return s.lowpass(tone * s.adsr(n, 0.004, 0.02, 0.8, 0.015), 4200, 2)


def bell(f: float, seconds: float, decay: float, ratio: float = 3.5, index: float = 1.2) -> np.ndarray:
    """调频的铃声：调制指数随着衰减往下掉，起音亮、尾巴纯。"""
    n = s.samples(seconds)
    env = s.perc(n, 0.002, decay)
    return s.fm(f, ratio, index * env, n) * env


# ---- 多功能工具

@sound("tool/mining_beam", loop=6.0, level=-24, power=False)
def mining_beam(rng, i):
    """采矿激光：电源逆变器的呜声和边带，一层能量的低嗡，冷却风扇，打在石头上的灼烧和崩裂。"""
    length = 6.0
    n = warm(length)
    hum_f = per(98, length)
    hum = s.harmonics(hum_f, n, [1, 0.55, 0.4, 0.22, 0.12, 0.06]) * (1 + 0.12 * lfo(n, length, 4))
    whine_f = per(2400, length) * (1 + 0.002 * lfo(n, length, 5.5))
    whine = s.sine(whine_f, n) + 0.3 * s.sine(whine_f * 2, n) + 0.2 * s.sine(whine_f - per(98, length) * 2, n)
    fan = s.lowpass(m.fan(n, rng, 4200, blades=7, size=0.5), 3500, 2)
    burn_level = 0.75 + 0.25 * drift(n, rng, 3)
    crack = s.resonant(s.crackle(n, rng, 220, width=0.00012), "bandpass", 3200, 0.8)
    sizzle = s.resonant(s.white(n, rng), "bandpass", 5200, 1.2)
    burn = (crack * 0.6 + sizzle * 0.25) * burn_level
    x = hum * 0.35 + whine * 0.05 + fan * 0.35 + burn * 0.35
    x = fx.space(s.lowpass(x, 9000, 2), "casing", wet=0.08)
    return unwarm(x)


@sound("tool/overheat", variants=2, level=-18)
def overheat(rng, i):
    """过热：泄压阀一下猛地放气，越放越弱越暗；电源断开的呜声往下掉；金属降温时几声细小的咔哒。"""
    n = s.samples(1.7)
    press = s.exp_curve([(0, 1.0), (0.06, 1.0), (0.9, 0.08), (1.7, 0.005)], n)
    hiss = s.resonant(s.white(n, rng), "highpass", 900, 0.6)
    hiss = s.resonant(hiss, "lowpass", 2500 + 6000 * press, 0.7) * press * s.adsr(n, 0.004, 0.0, 1.0, 0.05)
    k = 1 + rng.uniform(-0.05, 0.05)
    whine = s.sine(s.exp_curve([(0, 2400 * k), (0.45, 480), (1.7, 400)], n), n) * s.perc(n, 0.002, 0.5)
    parts = [(hiss, 1.0), (whine, 0.12)]
    for _ in range(4 + i):
        ping = m.impact(rng, freq=rng.uniform(2500, 4200), decay=0.08, ratios=m.PLATE[:5], hardness=0.9, noise=0.2)
        parts.append((ping, rng.uniform(0.08, 0.2), rng.uniform(0.4, 1.4)))
    return fx.space(s.mix(*parts), "casing", wet=0.1, tail=0.1)


@sound("tool/mode", variants=2, level=-20)
def mode(rng, i):
    """换模式："咔"的一下解锁，伺服电机转一小段，再"嗒"地锁上，最后一声很轻的确认音。"""
    n = s.samples(0.45)
    rpm = m.servo_moves(n, rng, [(0.03, 0.16, 1.0)], top_rpm=12000)
    motor = m.electric_motor(n, rng, rpm, coils=5, gear_teeth=14, chassis=0.4, brushes=0.3, air=0.1)
    motor /= np.max(np.abs(motor)) + 1e-9
    blip = beep(1760 if i == 0 else 1320, 0.04, edge=0.1)
    return s.mix((m.relay(rng, size=0.8, bright=0.6), 0.9), (motor, 0.35), (m.relay(rng, size=0.6, bright=0.8), 0.8, 0.21),
                 (blip, 0.12, 0.23))


@sound("tool/scan", level=-16)
def scan(rng, i):
    """扫描：一道往外扩散的上扫频，到头一声声呐式的"叮"，后面是一串越来越远、越来越闷的回声。"""
    n = s.samples(0.7)
    sweep_f = s.exp_curve([(0, 280), (0.55, 2800), (0.7, 2800)], n)
    sweep = s.fm(sweep_f, 2.0, 1.2, n) * s.envelope([(0, 0), (0.03, 0.5), (0.5, 1.0), (0.62, 0.0), (0.7, 0.0)], n)
    sweep = fx.chorus(sweep, rate=1.3, depth=0.3, mix=0.5)
    m2 = s.samples(1.6)
    ping = (s.sine(1400, m2) + 0.3 * s.sine(2803, m2)) * s.perc(m2, 0.002, 1.2)
    ping = s.delay(ping, 0.28, feedback=0.45, mix=0.6, lowpass_hz=2500, tail=1.0)
    x = s.mix((sweep, 0.7), (ping, 0.8, 0.55), (thump(120, 70, 0.2, 0.15), 0.4))
    return outdoors(x, rng, 0.8, 0.3, 0.6)


# ---- 武器

@sound("weapon/blaster", variants=4, level=-19)
def blaster(rng, i):
    """爆能枪：电容一下放空的"啪咻"，枪身的一点低频后坐，户外很短的回声。"""
    k = 1 + rng.uniform(-0.05, 0.05)
    z = m.zap(rng, start=2600 * k, end=380 * k, length=rng.uniform(0.10, 0.14), bite=0.7, fm_index=2.5)
    x = s.mix((z, 1.0), (thump(140 * k, 60, 0.15, 0.08), 0.6))
    return short(outdoors(x, rng, 0.3, 0.22, 0.35), 0.6)


@sound("weapon/scatter", variants=3, level=-18)
def scatter(rng, i):
    """散射爆能枪：七八团放电几乎同时出去，音高错开，听着粗、宽；更重的后坐和爆裂。"""
    parts = []
    for _ in range(7):
        k = rng.uniform(0.85, 1.15)
        z = m.zap(rng, start=1700 * k, end=260 * k, length=rng.uniform(0.08, 0.14), bite=0.4, fm_index=1.8)
        parts.append((z, rng.uniform(0.35, 0.6), rng.uniform(0, 0.018)))
    n = s.samples(0.2)
    boom = s.lowpass(s.brown(n, rng), 400, 2) * s.perc(n, 0.001, 0.12)
    snap = s.highpass(s.white(n, rng), 2000) * s.perc(n, 0.0003, 0.008)
    x = s.mix(*parts, (boom, 1.0), (thump(95, 45, 0.2, 0.12), 0.9), (snap, 0.8))
    x = s.softclip(x / (np.max(np.abs(x)) + 1e-9), 1.3)
    return short(outdoors(x, rng, 0.4, 0.28, 0.4), 0.7)


@sound("weapon/pulse", variants=4, level=-21)
def pulse(rng, i):
    """脉冲喷射器：轻、快的小放电。"""
    k = 1 + rng.uniform(-0.06, 0.06)
    z = m.zap(rng, start=3600 * k, end=1100 * k, length=rng.uniform(0.05, 0.07), bite=0.5, fm_index=1.5)
    x = s.mix((z, 1.0), (thump(220 * k, 120, 0.06, 0.03), 0.4))
    return short(outdoors(x, rng, 0.2, 0.15, 0.2), 0.35)


@sound("weapon/javelin_charge", loop=4.0, level=-22, power=False)
def javelin_charge(rng, i):
    """烈焰标枪蓄力：电容充电的嗡声和高频呜声，一点脉动和电噼啪。播放时音高随蓄力从 0.7 升到 1.4。"""
    length = 4.0
    n = warm(length)
    base = per(165, length)
    hum = s.harmonics(base, n, [1, 0.7, 0.5, 0.35, 0.25, 0.18, 0.12, 0.08])
    fifth = s.harmonics(per(base * 1.5, length), n, [1, 0.4, 0.2])
    whine = s.sine(per(1980, length) * (1 + 0.003 * lfo(n, length, 7)), n)
    beat = 1 + 0.25 * lfo(n, length, 6)
    crack = s.highpass(s.crackle(n, rng, 60, width=0.0001), 2500)
    x = (hum * 0.5 + fifth * 0.35 + whine * 0.12) * beat + crack * 0.3
    x = fx.space(s.lowpass(x, 7000, 2), "casing", wet=0.1)
    return unwarm(x)


@sound("weapon/javelin_ready", level=-18)
def javelin_ready(rng, i):
    """蓄满：两个亮的铃音叠在一起，带几点电火花。"""
    n = s.samples(0.9)
    ring = s.fit(bell(1760, 0.9, 0.6, ratio=1.0, index=1.5), n) + 0.4 * s.fit(bell(2640, 0.9, 0.4, ratio=2.0, index=0.6), n)
    sparks = s.highpass(s.crackle(n, rng, [(0, 400), (0.4, 0)], width=0.00008), 3000) * 0.15
    return fx.space(ring + sparks, "casing", wet=0.15, tail=0.1)


@sound("weapon/javelin_fire", variants=2, level=-15)
def javelin_fire(rng, i):
    """标枪发射：一道重的光束放电，开头空气被烧穿的"啪"，电弧的噼啪，低频的轰，户外的回声。"""
    k = 1 + rng.uniform(-0.04, 0.04)
    crack = m.n_wave(rng, 0.003)
    z = m.zap(rng, start=1900 * k, end=110 * k, length=0.32, bite=1.0, fm_index=4.0)
    n = s.samples(0.3)
    boom = thump(90 * k, 38, 0.3, 0.3) + s.lowpass(s.brown(n, rng), 300, 2) * s.perc(n, 0.004, 0.2) * 0.5
    a = s.samples(0.25)
    arc = m.arc(a, rng, intensity=[(0, 1.0), (0.25, 0.0)], mains=60)
    tail = m.hiss(s.samples(0.6), rng, pressure=[(0, 1.0), (0.6, 0.0)], brightness=0.4)
    x = s.mix((crack, 0.9), (z, 1.0), (boom, 0.9), (arc, 0.35, 0.01), (tail, 0.2, 0.05))
    x = s.softclip(x / (np.max(np.abs(x)) + 1e-9), 1.4)
    return short(outdoors(x, rng, 0.9, 0.35, 0.5), 0.8, 0.2)


@sound("weapon/launcher", variants=2, level=-18)
def launcher(rng, i):
    """等离子发射器：电磁抛射的低沉"嘭"，机构的一声咔，榴弹飞出去的风声往下掉。"""
    k = 1 + rng.uniform(-0.05, 0.05)
    n = s.samples(0.35)
    puff = s.lowpass(s.white(n, rng), 500, 2) * s.perc(n, 0.001, 0.06)
    whoosh = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 1600 * k), (0.3, 380)], n), 1.2)
    whoosh *= s.envelope([(0, 0), (0.02, 1), (0.3, 0), (0.35, 0)], n)
    x = s.mix((thump(150 * k, 55, 0.2, 0.12), 1.0), (puff, 0.6), (m.relay(rng, size=1.6, bright=0.5), 0.5), (whoosh, 0.5, 0.02))
    return short(outdoors(x, rng, 0.4, 0.25, 0.35), 0.6)


@sound("weapon/grenade_bounce", variants=3, level=-20)
def grenade_bounce(rng, i):
    """榴弹磕在地上：空心的金属壳。"""
    x = m.impact(rng, freq=rng.uniform(850, 1250), decay=0.18, ratios=m.HOLLOW, hardness=0.75, noise=0.25)
    x = s.mix((x, 1.0), (thump(180, 120, 0.06, 0.04), 0.3))
    return outdoors(x, rng, 0.2, 0.15, 0.15)


@sound("weapon/bolt_fizzle", variants=2, level=-22)
def bolt_fizzle(rng, i):
    """等离子弹掉进水里：一下蒸汽的嘶声，一串细小的气泡。"""
    n = s.samples(0.6)
    hiss = s.resonant(s.white(n, rng), "bandpass", s.exp_curve([(0, 6000), (0.5, 2500), (0.6, 2500)], n), 0.8)
    hiss *= s.envelope([(0, 0), (0.01, 1), (0.5, 0), (0.6, 0)], n)
    bub = s.taper(m.bubbles(n, rng, [(0, 400), (0.25, 40), (0.4, 0)], radius=(0.0008, 0.003), rise=0.2), 0.2)
    pop = s.lowpass(s.white(n, rng), 800, 2) * s.perc(n, 0.0005, 0.03)
    return hiss + 0.4 * bub / (np.max(np.abs(bub)) + 1e-9) + 0.5 * pop


# ---- 宇航服

@sound("suit/life_support", loop=6.0, level=-26, power=False)
def life_support(rng, i):
    """生命维持：头盔后面的小风扇，隔膜泵一下一下地推冷却液，供气的气流跟着呼吸似的慢慢起伏。"""
    length = 6.0
    n = warm(length)
    blades = 5
    fan = s.lowpass(m.fan(n, rng, per(3000 * blades / 60, length) * 60 / blades, blades=blades, size=0.5), 2500, 2)
    pump = np.zeros(n)
    rate = per(1.5, length)
    strokes = int(round(rate * length))
    params = [(rng.uniform(62, 76), rng.uniform(0.7, 1.0), rng.uniform(0.15, 0.3)) for _ in range(strokes)]
    t = 0.0
    j = 0
    while True:
        at = s.samples(t)
        if at >= n:
            break
        f, gain, valve = params[j % strokes]
        hit = thump(f, f * 0.8, 0.2, 0.12) * gain
        tick = m.relay(np.random.default_rng(j % strokes), size=2.0, bright=0.2) * valve
        for sig, offset in ((hit, 0), (tick, s.samples(0.28))):
            a = at + offset
            if a < n:
                e = min(n, a + len(sig))
                pump[a:e] += sig[:e - a]
        t += 1 / rate
        j += 1
    air = s.lowpass(s.pink(n, rng), 1200, 2) * (0.8 + 0.2 * lfo(n, length, 0.33))
    x = fan * 0.5 + pump * 0.45 + air * 0.4
    return unwarm(helmet(s.lowpass(x, 4000, 2), wet=0.15))


@sound("suit/jetpack", loop=4.0, level=-20)
def jetpack(rng, i):
    """喷气背包：两个小推进器的喷流，高压气体的嘶声，背上的一点低频震动。"""
    length = 4.0
    n = warm(length)
    roar = s.highpass(m.rocket(n, rng, thrust=1.0, crackle=0.35), 70, 2)
    roar /= np.std(roar) + 1e-9
    gas = s.resonant(s.white(n, rng), "bandpass", 3500, 0.7) * (1 + 0.1 * drift(n, rng, 2))
    gas /= np.std(gas) + 1e-9
    rumble = s.lowpass(s.brown(n, rng), 180, 2)
    rumble /= np.std(rumble) + 1e-9
    x = roar * 0.6 + gas * 0.15 + rumble * 0.4
    return unwarm(s.lowpass(x, 8000, 2))


@sound("suit/warning_oxygen", level=-20)
def warning_oxygen(rng, i):
    """氧气告急：两声往下走的提示音。"""
    x = s.mix((beep(1320, 0.09), 1.0), (beep(990, 0.09), 1.0, 0.13))
    return helmet(x)


@sound("suit/warning_hazard", level=-20)
def warning_hazard(rng, i):
    """防护告急：三声又短又急的同音，棱角更硬。"""
    x = s.mix(*[(beep(1480, 0.05, edge=0.45), 1.0, 0.08 * k) for k in range(3)])
    return helmet(x)


@sound("suit/equip", variants=2, level=-18)
def equip(rng, i):
    """穿上外骨骼：甲片磕在一起的空响，伺服电机收紧，卡扣锁上，接着头盔密封的一下嘶声。"""
    clunk = m.impact(rng, freq=rng.uniform(260, 320), decay=0.25, ratios=m.HOLLOW, hardness=0.35, noise=0.3)
    n = s.samples(0.45)
    rpm = m.servo_moves(n, rng, [(0.05, 0.25, 0.8)], top_rpm=10000)
    motor = m.electric_motor(n, rng, rpm, coils=5, gear_teeth=18, chassis=0.5)
    motor /= np.max(np.abs(motor)) + 1e-9
    seal = m.hiss(s.samples(0.3), rng, pressure=[(0, 1.0), (0.3, 0.0)], brightness=0.5)
    x = s.mix((clunk, 0.9), (motor, 0.35, 0.04), (m.relay(rng, size=1.0, bright=0.5), 0.6, 0.33), (seal, 0.25, 0.36))
    return helmet(x, wet=0.12, tail=0.15)


@sound("suit/refill_oxygen", level=-20)
def refill_oxygen(rng, i):
    """补氧气：阀门一开，气体往罐里灌，罐子越满嘶声越尖，阀门关上。"""
    n = s.samples(0.9)
    flow = s.envelope([(0, 0), (0.06, 1.0), (0.6, 0.7), (0.85, 0), (0.9, 0)], n)
    gas = s.resonant(s.white(n, rng), "bandpass", s.exp_curve([(0, 1800), (0.85, 3200), (0.9, 3200)], n), 0.9) * flow
    x = s.mix((gas, 1.0), (m.relay(rng, size=1.2, bright=0.4), 0.5), (m.relay(rng, size=1.2, bright=0.4), 0.5, 0.84))
    return helmet(x)


@sound("suit/refill", level=-20)
def refill(rng, i):
    """给宇航服充能：一道往上走的充电音，到头一声确认。"""
    n = s.samples(0.45)
    sweep = s.sine(s.exp_curve([(0, 420), (0.35, 1680), (0.45, 1680)], n), n)
    sweep *= s.envelope([(0, 0), (0.02, 0.5), (0.35, 0.8), (0.42, 0), (0.45, 0)], n)
    chime = s.fit(bell(1680, 0.4, 0.25, ratio=1.5, index=0.4), s.samples(0.4))
    x = s.mix((m.relay(rng, size=0.7, bright=0.4), 0.4), (sweep, 0.6), (chime, 0.5, 0.36))
    return helmet(x)


@sound("suit/shield_hit", variants=3, level=-17)
def shield_hit(rng, i):
    """个人护盾挡下一击：能量场被敲响的"铮"，一点电的爆裂和闷响。"""
    k = 1 + rng.uniform(-0.06, 0.06)
    n = s.samples(0.6)
    ring = s.fm(820 * k, 1.414, s.envelope([(0, 3.5), (0.4, 0.3), (0.6, 0.3)], n), n) * s.perc(n, 0.001, 0.45)
    zing = s.sine(s.exp_curve([(0, 3200 * k), (0.3, 2200 * k), (0.6, 2200 * k)], n), n) * s.perc(n, 0.001, 0.25)
    crack = s.highpass(s.crackle(n, rng, [(0, 3000), (0.2, 0), (0.6, 0)], width=0.0001), 3000) * s.perc(n, 0.001, 0.2)
    thud = s.lowpass(s.white(n, rng), 300, 2) * s.perc(n, 0.001, 0.05)
    x = ring + 0.25 * zing + 0.4 * crack + 0.6 * thud
    return outdoors(x, rng, 0.2, 0.2, 0.2)


@sound("suit/shield_break", level=-15)
def shield_break(rng, i):
    """个人护盾被打穿：能量场往下塌的扫频，像玻璃碎了一地的一阵细响，一下闷击。"""
    n = s.samples(1.2)
    down = s.fm(s.exp_curve([(0, 1900), (0.55, 140), (1.2, 140)], n), 1.5, s.envelope([(0, 4), (0.55, 0.5), (1.2, 0.5)], n), n)
    down *= s.envelope([(0, 0), (0.005, 1), (0.6, 0), (1.2, 0)], n)
    parts = [(down, 0.8), (thump(70, 40, 0.3, 0.2), 0.8)]
    for _ in range(25):
        shard = m.impact(rng, freq=rng.uniform(2500, 7000), decay=0.15, ratios=m.PLATE[:6], hardness=0.95, noise=0.1)
        at = rng.exponential(0.1)
        if at < 0.4:
            parts.append((shard, 0.25 * np.exp(-at / 0.15) * rng.uniform(0.4, 1.0), at))
    return outdoors(s.mix(*parts), rng, 0.5, 0.3, 0.4)


# ---- 分析镜

@sound("visor/open", level=-22)
def visor_open(rng, i):
    """分析镜打开：镜片就位的轻咔，一道往上走的启动音，尾巴一点亮光。"""
    n = s.samples(0.25)
    blip = s.sine(s.exp_curve([(0, 600), (0.12, 1400), (0.25, 1400)], n), n)
    blip *= s.envelope([(0, 0), (0.01, 0.8), (0.12, 0.6), (0.2, 0), (0.25, 0)], n)
    x = s.mix((m.relay(rng, size=0.5, bright=0.6), 0.4), (blip, 0.7), (bell(2800, 0.2, 0.15, ratio=1.0, index=0.2), 0.15, 0.11))
    return helmet(x)


@sound("visor/close", level=-22)
def visor_close(rng, i):
    """分析镜关上：往下走的关机音，最后一声咔。"""
    n = s.samples(0.2)
    blip = s.sine(s.exp_curve([(0, 1400), (0.15, 550), (0.2, 550)], n), n)
    blip *= s.envelope([(0, 0), (0.01, 0.7), (0.15, 0.4), (0.2, 0)], n)
    x = s.mix((blip, 0.7), (m.relay(rng, size=0.5, bright=0.6), 0.4, 0.16))
    return helmet(x)


@sound("visor/tick", level=-24)
def visor_tick(rng, i):
    """分析中：很短的一声高音加一下轻咔，放的时候音高随进度往上走。"""
    n = s.samples(0.08)
    tone = s.sine(2200, n) * s.perc(n, 0.001, 0.03)
    return helmet(s.mix((tone, 1.0), (m.relay(rng, size=0.4, bright=0.3), 0.3)), wet=0.12, tail=0.1)


@sound("visor/complete", level=-18)
def visor_complete(rng, i):
    """分析完：往上的两个音，调频的铃声，很干。"""
    x = s.mix((bell(1046.5, 0.35, 0.18, ratio=2.0, index=0.5), 0.8), (bell(1568, 0.45, 0.3, ratio=2.0, index=0.5), 1.0, 0.1))
    return helmet(x)


@sound("visor/discovery", level=-16)
def visor_discovery(rng, i):
    """新发现：四个音往上走的琶音，铃声带着金属的泛音，底下一层慢慢起来的和声，带混响。"""
    notes = [523.25, 783.99, 1046.5, 1318.5]
    parts = [(bell(f, 1.2, 0.9, ratio=3.5, index=1.0), 0.7, 0.09 * k) for k, f in enumerate(notes)]
    n = s.samples(1.4)
    pad = (s.sine(523.25, n) + 0.6 * s.sine(783.99, n)) * s.envelope([(0, 0), (0.4, 0.3), (1.0, 0.2), (1.4, 0)], n)
    parts.append((pad, 0.4))
    return fx.reverb(s.mix(*parts), room_size=0.6, damping=0.5, wet=0.3, tail=0.6)
