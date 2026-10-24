"""世界里的交互：传送门、终端和前哨站、遭遇、任务和奖励（ADR-0029）。

提示类的声音从设备的小喇叭里出来（带通、一点失真），起头一声按键或继电器的咔哒；音高都落在 A 大调上，
连着听不打架。传送门、知识石、巨石是外星的东西，用非谐的模态共鸣和慢慢拍动的两个音，听着不像地球的乐器。
"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["world"]

# A 大调里用到的音（Hz）
A4, B4, CS5, E5, FS5, A5, B5, CS6, E6, A6 = 440.0, 493.9, 554.4, 659.3, 740.0, 880.0, 987.8, 1108.7, 1318.5, 1760.0


def end(x: np.ndarray, seconds: float = 0.06) -> np.ndarray:
    """进混响前把末尾收掉：各层截断的地方不咔。"""
    return s.taper(x, seconds)


def speaker(x: np.ndarray, low: float = 280, high: float = 6500, drive: float = 1.3) -> np.ndarray:
    """设备的小喇叭：低频出不来，高频削掉，推得稍微有点破。"""
    return s.softclip(s.bandpass(x, low, high, 2) * drive, drive)


def ping(f: float, seconds: float, decay: float, ratio: float = 3.0, index: float = 0.9, edge: float = 0.0) -> np.ndarray:
    """调频的短音：起音亮，衰减时调制指数跟着落，尾巴纯；edge 加一点方波的棱角。"""
    n = s.samples(seconds)
    env = s.perc(n, 0.002, decay)
    tone = s.fm(f, ratio, index * env, n)
    if edge:
        tone = tone + edge * s.square(f, n, 7)
    return tone * env


def motif(notes, step: float, decay: float, gain_fall: float = 0.0, ratio: float = 3.0, index: float = 0.9,
          edge: float = 0.0, start: float = 0.0):
    """一串音：notes 是频率，每隔 step 秒一个，后面的可以渐弱。返回 mix 用的片段。"""
    parts = []
    for k, f in enumerate(notes):
        parts.append((ping(f, decay * 1.3 + 0.05, decay, ratio, index, edge), 1 - gain_fall * k, start + k * step))
    return parts


def click(rng, size: float = 0.6, bright: float = 0.5) -> np.ndarray:
    return m.relay(rng, size=size, bright=bright)


def alien(rng, f: float, seconds: float, decay: float, beat: float = 1.7, ratios=m.HOLLOW, brightness: float = 0.5) -> np.ndarray:
    """外星的共鸣：一组非谐的模态被软的激励敲响，最低的模态旁边贴一个差几赫兹的音，慢慢拍动。"""
    n = s.samples(seconds)
    k = s.samples(0.006)
    excite = np.zeros(n)
    excite[:k] = np.hanning(2 * k)[k:]
    excite += s.lowpass(s.white(n, rng), 2000 + 4000 * brightness) * s.perc(n, 0.002, 0.05) * 0.3
    freqs = [f * r * (1 + rng.uniform(-0.004, 0.004)) for r in ratios]
    freqs.append(f + beat)
    decays = [decay / (1 + 0.45 * j) for j in range(len(ratios))] + [decay]
    gains = [1 / (1 + 0.7 * j) for j in range(len(ratios))] + [0.8]
    body = s.modes(excite, freqs, decays, gains)
    return body / (np.max(np.abs(body)) + 1e-9)


def swell(n: int, rng, low: float, high: float, attack: float, release: float) -> np.ndarray:
    """往上涌的能量：粉红噪声过一个从 low 扫到 high 的带通，慢起慢落。"""
    t_total = n / s.SR
    sweep = s.exp_curve([(0, low), (attack, high), (t_total, high * 0.9)], n)
    body = s.resonant(s.pink(n, rng), "bandpass", sweep, 2.0)
    return body * s.envelope([(0, 0), (attack, 1), (max(attack, t_total - release), 0.8), (t_total, 0)], n)


# ---- 传送门

@sound("world/portal_glyph", variants=3, level=-17)
def portal_glyph(rng, i):
    """符文：外星的石头敲一下，一个高一点的泛音拖出来，带一点合唱般的晃动。"""
    base = [A4 / 2, CS5 / 2, E5 / 2][i] * (1 + rng.uniform(-0.01, 0.01))
    body = alien(rng, base, 1.4, 1.1, beat=rng.uniform(1.2, 2.4))
    shimmer = ping(base * 4.02, 1.2, 0.8, ratio=1.41, index=0.6) * 0.25
    x = s.mix((body, 1.0), (shimmer, 1.0, 0.02))
    x = fx.chorus(end(x), rate=0.6, depth=0.2, mix=0.35)
    return fx.space(x, fx.outdoor_ir(rng, 0.5), wet=0.3, tail=0.6)


@sound("world/portal_open", level=-15)
def portal_open(rng, i):
    """传送门打开：低沉的嗡声一点点升起来，能量往上涌，最后"嗡"地一下稳住，留一层发亮的余音。"""
    length = 3.4
    n = s.samples(length)
    drone_f = s.exp_curve([(0, 42), (2.4, 110), (length, 110)], n)
    drone = s.harmonics(drone_f, n, [1.0, 0.5, 0.35, 0.2, 0.12]) * s.envelope([(0, 0), (2.3, 1), (2.5, 0.7), (length, 0)], n)
    rise = swell(n, rng, 150, 2400, 2.3, 0.9)
    thump = s.sine(s.exp_curve([(0, 140), (0.25, 48)], s.samples(0.6)), s.samples(0.6)) * s.perc(s.samples(0.6), 0.003, 0.5)
    bloom = alien(rng, 110, 2.0, 1.6, beat=1.3) * 0.6
    x = s.mix((drone, 0.6), (rise, 0.5), (thump, 1.0, 2.35), (bloom, 1.0, 2.35))
    return fx.space(end(x, 0.3), fx.outdoor_ir(rng, 1.2), wet=0.35, tail=1.4)


@sound("world/portal_close", level=-16)
def portal_close(rng, i):
    """传送门关上：发亮的余音往下掉，能量往里收，最后一下闷响。"""
    length = 2.2
    n = s.samples(length)
    fall = s.harmonics(s.exp_curve([(0, 110), (1.8, 38)], n), n, [1.0, 0.4, 0.25, 0.1]) * s.envelope([(0, 1), (1.6, 0.6), (length, 0)], n)
    suck = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 2400), (1.7, 120)], n), 2.0)
    suck *= s.envelope([(0, 0.3), (1.2, 1), (1.75, 0.2), (length, 0)], n)
    m_n = s.samples(0.5)
    thud = s.sine(s.exp_curve([(0, 90), (0.3, 40)], m_n), m_n) * s.perc(m_n, 0.008, 0.35)
    x = s.mix((fall, 0.7), (suck, 0.6), (thud, 1.0, 1.7))
    return fx.space(end(x, 0.2), fx.outdoor_ir(rng, 1.0), wet=0.3, tail=1.0)


@sound("world/portal_travel", level=-16)
def portal_travel(rng, i):
    """穿过传送门：一阵往身后掠过的气流，中间夹着一声外星的共鸣。"""
    length = 1.6
    n = s.samples(length)
    whoosh = s.resonant(s.pink(n, rng), "bandpass", s.exp_curve([(0, 300), (0.5, 3200), (length, 200)], n), 1.4)
    whoosh *= s.envelope([(0, 0), (0.45, 1), (length, 0)], n)
    tone = alien(rng, CS5 / 2, 1.4, 1.1, beat=2.0) * 0.5
    # 共鸣是涌出来的，不是敲出来的：起头那一下抹掉
    tone *= s.envelope([(0, 0), (0.15, 1), (1.4, 1)], len(tone))
    x = s.mix((whoosh, 1.0), (tone, 1.0, 0.3))
    return fx.space(end(x, 0.2), fx.outdoor_ir(rng, 0.6), wet=0.2, tail=0.5)


@sound("world/portal_deny", level=-17)
def portal_deny(rng, i):
    """拨号不对：外星石头两声低沉、不协和的回应（三全音），往下落。"""
    a = alien(rng, 98, 0.9, 0.6, beat=4.0, brightness=0.2)
    b = alien(rng, 98 * 1.414, 0.9, 0.6, beat=5.0, brightness=0.2)
    x = s.mix((b, 0.8), (a, 1.0, 0.16))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.5), wet=0.25, tail=0.4)


# ---- 终端、前哨站、补给

@sound("world/terminal_access", variants=3, level=-18)
def terminal_access(rng, i):
    """终端访问：按键一声咔，一串很快的数据声，收在一个确认音上。"""
    parts = [(click(rng), 0.6, 0.0)]
    t0 = 0.03
    for k in range(6 + i * 2):
        f = rng.choice([E5, FS5, A5, B5, CS6, E6]) * 2
        tn = s.samples(0.018)
        blip = (s.sine(f, tn) + 0.3 * s.square(f, tn, 5)) * s.adsr(tn, 0.002, 0.004, 0.7, 0.006)
        parts.append((blip, 0.25, t0))
        t0 += 0.022
    parts += motif([A5, E6][:1 + i % 2], 0.08, 0.12, edge=0.15, start=t0 + 0.02)
    x = speaker(s.mix(*parts))
    return fx.space(end(x), "corridor", wet=0.15, tail=0.3)


@sound("world/terminal_confirm", variants=2, level=-17)
def terminal_confirm(rng, i):
    """确认：往上走的两个音，第二个拖长一点。"""
    notes = [[E5, A5], [A5, CS6]][i]
    x = s.mix((click(rng, 0.5, 0.4), 0.4, 0.0), *motif(notes, 0.09, 0.22, edge=0.12, start=0.02))
    return fx.space(end(speaker(x)), "corridor", wet=0.15, tail=0.3)


@sound("world/terminal_deny", variants=2, level=-18)
def terminal_deny(rng, i):
    """拒绝：两下低沉的蜂鸣，方波，略微失真。"""
    f = [196.0, 185.0][i]
    parts = []
    for k in range(2):
        tn = s.samples(0.11)
        buzz = s.square(f * (1 - 0.03 * k), tn, 20) * s.adsr(tn, 0.004, 0.02, 0.8, 0.02)
        parts.append((buzz, 1.0 - 0.15 * k, k * 0.15))
    x = speaker(s.mix((click(rng, 0.8, 0.3), 0.4, 0.0), *parts), low=200, high=4000, drive=1.8)
    return fx.space(end(x), "corridor", wet=0.12, tail=0.25)


@sound("world/door_breach", level=-15)
def door_breach(rng, i):
    """加固门被切开：烧软的门板呻吟着变形，一声脆响断开，里面的气压冲出来，整块门板砸到地上，碎渣蹦几下。"""
    length = 2.6
    n = s.samples(length)
    # 门板受力：慢慢变密的黏滑敲在钢板的模态上
    groan_n = s.samples(0.6)
    stick = s.impulses(groan_n, s.curve([(0, 18), (0.55, 60)], groan_n), jitter=0.4, rng=rng)
    groan = s.modes(s.lowpass(stick, 1500, 1), [130 * r for r in m.PLATE[:8]], [0.25] * 8, [1, 0.8, 0.7, 0.5, 0.4, 0.3, 0.25, 0.2])
    groan *= s.envelope([(0, 0.2), (0.5, 1), (0.6, 0.6)], groan_n) / (np.max(np.abs(groan)) + 1e-9)
    snap = m.impact(rng, freq=780, decay=0.12, ratios=m.BAR[:4], hardness=0.95, noise=0.5)
    gust = m.hiss(n, rng, pressure=s.envelope([(0, 0), (0.55, 0), (0.58, 1), (1.4, 0.2), (length, 0)], n), brightness=0.35)
    gust = s.lowpass(gust, 7000)
    slab = m.impact(rng, freq=58, decay=1.1, ratios=m.PLATE, hardness=0.4, size=2.0, noise=0.35)
    thump_n = s.samples(0.5)
    thump = s.sine(s.exp_curve([(0, 110), (0.2, 45)], thump_n), thump_n) * s.perc(thump_n, 0.004, 0.35)
    parts = [(groan, 0.8, 0.0), (snap, 0.45, 0.55), (gust, 0.5), (slab, 1.0, 0.9), (thump, 0.7, 0.9)]
    for k in range(4):
        bit = m.impact(rng, freq=rng.uniform(600, 1400), decay=0.1, ratios=m.PLATE[:5], hardness=0.8, noise=0.2)
        parts.append((bit, 0.25 * rng.uniform(0.5, 1.0), 1.05 + 0.12 * k + rng.uniform(0, 0.06)))
    x = s.mix(*parts)
    return fx.space(end(x, 0.3), fx.outdoor_ir(rng, 0.8), wet=0.3, tail=0.9)


@sound("world/capsule_open", variants=2, level=-16)
def capsule_open(rng, i):
    """救生舱打开：封条"噗"地一下，里面残存的气体嘶嘶泄掉，舱盖的锁扣弹开。"""
    length = 1.3
    n = s.samples(length)
    pop = s.lowpass(s.white(n, rng), 500) * s.perc(n, 0.001, 0.06) * 1.4
    thump = m.impact(rng, freq=rng.uniform(110, 140), decay=0.35, ratios=m.HOLLOW, hardness=0.3, noise=0.15)
    gas = m.hiss(n, rng, pressure=s.envelope([(0, 0), (0.01, 1), (0.9, 0.05), (length, 0)], n), brightness=0.4 + 0.2 * i)
    latch = click(rng, size=1.2, bright=0.4)
    x = s.mix((pop, 1.0), (s.fit(thump, n), 0.7), (gas, 0.45, 0.02), (latch, 0.6, 0.55 + 0.1 * i))
    return fx.space(end(x, 0.1), fx.outdoor_ir(rng, 0.4), wet=0.2, tail=0.4)


@sound("world/crate_open", variants=2, level=-17)
def crate_open(rng, i):
    """补给箱：两个锁扣先后弹开，箱盖的铰链吱一声，盖子落到位。"""
    length = 1.1
    n = s.samples(length)
    creak_f = s.exp_curve([(0, 180 + 40 * i), (0.6, 140 + 30 * i)], n)
    # 铰链的黏滑：按不规则的间隔一下下打在一个窄带共振上
    stick = s.impulses(n, s.curve([(0, 40), (0.6, 28)], n), jitter=0.35, rng=rng)
    creak = s.resonant(s.lowpass(stick, 3000, 1), "bandpass", creak_f * 6, 6)
    creak = s.modes(creak, [creak_f[0] * 6 * r for r in (1.0, 2.3, 3.9)], [0.05, 0.04, 0.03], [1, 0.5, 0.3])
    creak *= s.envelope([(0, 0), (0.28, 0), (0.32, 1), (0.75, 0.6), (0.8, 0), (length, 0)], n) / (np.max(np.abs(creak)) + 1e-9)
    lid = m.impact(rng, freq=rng.uniform(160, 200), decay=0.3, ratios=m.HOLLOW, hardness=0.3, noise=0.15)
    x = s.mix((click(rng, 1.0, 0.3), 0.5, 0.0), (click(rng, 1.1, 0.3), 0.45, 0.12 + 0.03 * i), (creak, 0.45),
              (lid, 0.7, 0.82))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.4), wet=0.2, tail=0.4)


# ---- 知识石、巨石、守卫机的信标柱

@sound("world/stone_touch", variants=2, level=-19)
def stone_touch(rng, i):
    """摸过的知识石：轻轻的一声石头共鸣。"""
    x = alien(rng, [220.0, 246.9][i], 1.3, 0.9, beat=1.1, brightness=0.3)
    breath = s.resonant(s.pink(s.samples(0.8), rng), "bandpass", 900, 1.2) * s.envelope([(0, 0), (0.1, 1), (0.8, 0)], s.samples(0.8))
    x = s.mix((x, 1.0), (breath, 0.15))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.5), wet=0.25, tail=0.5)


def formant_voice(rng, n: int, f0, vowels, seconds: float) -> np.ndarray:
    """像在说话的外星声音：锯齿的声门源过三个共振峰，共振峰在几个"元音"之间滑。"""
    src = s.saw(f0, n, 40) * (1 + 0.05 * s.sine(5.5, n))
    t_total = n / s.SR
    pts = np.linspace(0, t_total, len(vowels))
    out = np.zeros(n)
    for k, (gain, q) in enumerate([(1.0, 6), (0.6, 8), (0.3, 10)]):
        f = s.curve([(pts[j], vowels[j][k]) for j in range(len(vowels))], n)
        out += gain * s.resonant(src, "bandpass", f, q)
    return out


@sound("world/stone_learn", variants=2, level=-16)
def stone_learn(rng, i):
    """从知识石上学会一个词：石头的共鸣里浮出一段像人声又不像的外星语音。"""
    length = 2.0
    n = s.samples(length)
    vowels = [[(650, 1100, 2600), (300, 2200, 3000), (450, 900, 2500), (550, 1700, 2600)],
              [(400, 800, 2400), (700, 1200, 2700), (320, 2000, 2900), (500, 1000, 2500)]][i]
    f0 = s.exp_curve([(0, 150 + 15 * i), (0.7, 175 + 15 * i), (1.4, 140 + 15 * i), (length, 130)], n)
    voice = formant_voice(rng, n, f0, vowels, length) * s.envelope([(0, 0), (0.25, 0), (0.45, 1), (1.5, 0.8), (1.9, 0), (length, 0)], n)
    stone = alien(rng, [196.0, 220.0][i], 2.0, 1.5, beat=1.4)
    x = s.mix((stone, 1.0), (voice, 0.35))
    x = fx.chorus(end(x, 0.2), rate=0.4, depth=0.25, mix=0.3)
    return fx.space(x, fx.outdoor_ir(rng, 0.7), wet=0.3, tail=0.8)


@sound("world/monolith", level=-15)
def monolith(rng, i):
    """巨石教你一个符文：很低的一个和声（根音加五度）慢慢涌起来，上面一层发亮的泛音。"""
    length = 3.2
    n = s.samples(length)
    env = s.envelope([(0, 0), (0.6, 1), (2.2, 0.8), (length, 0)], n)
    root = s.harmonics(55.0, n, [1.0, 0.5, 0.3, 0.2]) + 0.6 * s.harmonics(82.4, n, [1.0, 0.4, 0.2])
    root = root * env * (1 + 0.15 * s.sine(0.9, n))
    high = (ping(A5, length, 2.6, ratio=2.01, index=0.4) + 0.6 * ping(E6 * 1.003, length, 2.4, ratio=2.01, index=0.3)) * 0.35
    hit = alien(rng, 110, 2.6, 2.0, beat=0.8)
    x = s.mix((root, 0.5), (hit, 0.8), (high, 1.0, 0.25))
    return fx.space(end(x, 0.4), fx.outdoor_ir(rng, 1.3), wet=0.4, tail=1.2)


@sound("world/pillar_found", level=-17)
def pillar_found(rng, i):
    """守卫机的信标柱：冷的电子嗡声，每秒两下地搏动，夹着一声扫频的探测音。"""
    length = 2.2
    n = s.samples(length)
    hum = s.harmonics(73.4, n, [1.0, 0.6, 0.5, 0.3, 0.25, 0.15]) * (0.55 + 0.45 * np.abs(s.sine(1.0, n)))
    hum *= s.envelope([(0, 0), (0.3, 1), (1.6, 1), (length, 0)], n)
    sweep_n = s.samples(0.7)
    sweep = s.sine(s.exp_curve([(0, 600), (0.7, 2400)], sweep_n), sweep_n) * s.adsr(sweep_n, 0.02, 0.1, 0.6, 0.2)
    x = s.mix((s.lowpass(hum, 1800, 2), 0.7), (sweep, 0.25, 0.4))
    return fx.space(end(x, 0.2), fx.outdoor_ir(rng, 0.6), wet=0.3, tail=0.6)


@sound("world/pillar_give", level=-16)
def pillar_give(rng, i):
    """信标柱吐出工具：伺服电机转一下，锁扣弹开，一个确认音。"""
    length = 1.4
    n = s.samples(length)
    rpm = m.servo_moves(n, rng, [(0.02, 0.5, 0.8)], top_rpm=7000)
    motor = s.resonant(m.electric_motor(n, rng, rpm, coils=7, gear_teeth=23, chassis=0.5, brushes=0.2, air=0.1), "lowpass", 4000, 0.7)
    clunk = m.impact(rng, freq=230, decay=0.3, ratios=m.PLATE, hardness=0.6, noise=0.2)
    x = s.mix((motor, 0.6), (clunk, 0.9, 0.52), *motif([E5, B5], 0.08, 0.25, edge=0.1, start=0.7))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.5), wet=0.2, tail=0.5)


# ---- 信号和遭遇

@sound("world/signal", variants=2, level=-18)
def signal(rng, i):
    """找到信号：一声干净的探测"嘀"，后面两下远处的回声，带一点电台的底噪。"""
    f = [A6 * 0.8, CS6][i]
    x = ping(f, 1.2, 0.35, ratio=1.0, index=0.0)
    x = s.delay(x, 0.21, feedback=0.4, mix=0.45, lowpass_hz=2500, tail=0.6)
    hiss = s.bandpass(s.white(len(x), rng), 800, 5000) * 0.03 * s.envelope([(0, 0), (0.05, 1), (len(x) / s.SR, 0)], len(x))
    return end(speaker(x + hiss, drive=1.1), 0.1)


@sound("world/poi_found", level=-17)
def poi_found(rng, i):
    """到了一处地点：往上一个五度的两声，第二声发亮、拖长。"""
    x = s.mix(*motif([E5, B5], 0.13, 0.5, ratio=2.0, index=0.8))
    shimmer = ping(E6 * 1.5, 1.0, 0.6, ratio=1.0, index=0.0) * 0.12
    x = s.mix((x, 1.0), (shimmer, 1.0, 0.13))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.5), wet=0.25, tail=0.6)


@sound("world/distress", level=-17)
def distress(rng, i):
    """求救信号：电台里断断续续的三短三长，夹着静电和信号的起伏。"""
    pattern = [(0.07, 0.0), (0.07, 0.13), (0.07, 0.26), (0.22, 0.45), (0.22, 0.75), (0.22, 1.05)]
    parts = []
    for length, at in pattern:
        tn = s.samples(length)
        parts.append(((s.sine(1050, tn) + 0.3 * s.square(1050, tn, 7)) * s.adsr(tn, 0.004, 0.01, 0.9, 0.01), 1.0, at))
    tones = s.mix(*parts)
    n = len(tones) + s.samples(0.2)
    tones = s.fit(tones, n)
    fading = 0.6 + 0.4 * (0.5 + 0.5 * s.sine(2.3, n))
    static = s.bandpass(s.white(n, rng), 600, 5000) * 0.18 + s.highpass(s.crackle(n, rng, 40, width=0.0002), 1500) * 0.3
    x = speaker(tones * fading + static, low=350, high=4000, drive=1.6)
    return end(fx.space(x, "cockpit", wet=0.15, tail=0.2), 0.08)


@sound("world/freighter_alarm", level=-14)
def freighter_alarm(rng, i):
    """货船的警报：舰船的汽笛，两声往上扫的"呜——"，在大钢壳里回荡。"""
    parts = []
    for k in range(2):
        tn = s.samples(0.9)
        f = s.exp_curve([(0, 280), (0.75, 560), (0.9, 560)], tn)
        horn = s.saw(f, tn, 30) * s.adsr(tn, 0.03, 0.05, 0.9, 0.08)
        parts.append((horn, 1.0, k * 1.0))
    x = s.mix(*parts)
    x = s.softclip(s.bandpass(x, 220, 3500, 2) * 2.2, 2.2)
    return fx.space(end(x, 0.1), "hangar", wet=0.35, tail=1.6)


@sound("world/lock_on", level=-17)
def lock_on(rng, i):
    """被锁定：越来越密的告警"嘀"，最后连成一声长音。"""
    parts = []
    at = 0.0
    gap = 0.2
    for k in range(7):
        tn = s.samples(0.05)
        parts.append(((s.sine(1760, tn) + 0.35 * s.square(1760, tn, 7)) * s.adsr(tn, 0.003, 0.01, 0.8, 0.01), 1.0, at))
        at += gap
        gap *= 0.72
    tn = s.samples(0.5)
    parts.append(((s.sine(1760, tn) + 0.35 * s.square(1760, tn, 7)) * s.adsr(tn, 0.003, 0.01, 0.9, 0.05), 1.0, at + 0.02))
    x = speaker(s.mix(*parts), low=400, high=5000, drive=1.4)
    return fx.space(end(x), "cockpit", wet=0.12, tail=0.2)


@sound("world/conduit_hiss", variants=3, level=-20)
def conduit_hiss(rng, i):
    """漏电的管线：一小阵电火花噼啪，夹着气体的嘶声。"""
    length = 0.45 + 0.1 * i
    n = s.samples(length)
    env = s.envelope([(0, 0), (0.02, 1), (length * 0.6, 0.5), (length, 0)], n)
    sparks = s.bandpass(s.crackle(n, rng, 700, width=0.00008), 1500, 9000) * env
    gas = s.bandpass(s.white(n, rng), 2200 + 400 * i, 5500) * env * 0.25
    # 漏电的线圈跟着工频哼，推得有点破
    buzz = s.softclip(s.harmonics(100, n, [0.5, 0.4, 0.35, 0.25, 0.2, 0.12]) * 2, 2) * env * 0.35
    return end(s.mix((sparks, 1.0), (gas, 1.0), (buzz, 1.0)), 0.05)


@sound("world/conduit_shock", variants=2, level=-15)
def conduit_shock(rng, i):
    """被管线电到：一下很响的放电，后面拖着噼啪。"""
    x = m.zap(rng, start=2600 + 400 * i, end=180, length=0.22, bite=0.9, fm_index=3.0)
    arc = m.arc(s.samples(0.3), rng, intensity=s.envelope([(0, 1), (0.3, 0)], s.samples(0.3)))
    x = s.lowpass(s.mix((x, 1.0), (arc, 0.4, 0.02)), 10000)
    return fx.space(end(x), "corridor", wet=0.15, tail=0.3)


# ---- 奖励和进度

@sound("world/credits", variants=2, level=-18)
def credits(rng, i):
    """信用点到账：计数器飞快地跳几下，收在一声亮的"叮"上。"""
    parts = []
    for k in range(4 + i):
        parts.append((click(rng, 0.4, 0.6), 0.35, k * 0.035))
    parts += motif([A6 * 0.75, A6][::1 if i == 0 else -1], 0.07, 0.25, ratio=3.5, index=1.1, start=0.04 * (4 + i))
    x = speaker(s.mix(*parts), low=350, high=7000, drive=1.1)
    return fx.space(end(x), "cockpit", wet=0.12, tail=0.3)


@sound("world/purchase", level=-17)
def purchase(rng, i):
    """买下：机械的"咔哒"落锁，两声确认。"""
    latch = m.impact(rng, freq=320, decay=0.15, ratios=m.PLATE, hardness=0.7, noise=0.3)
    x = s.mix((click(rng, 0.9, 0.6), 0.7, 0.0), (latch, 0.5, 0.05), *motif([CS6, E6], 0.1, 0.3, edge=0.1, start=0.12))
    return fx.space(end(speaker(x, drive=1.1)), "corridor", wet=0.15, tail=0.4)


@sound("world/reward", level=-16)
def reward(rng, i):
    """打赢、领到报酬：往上的三个音，最后一个和下面的根音合成一个和弦。"""
    parts = motif([A5, CS6, E6], 0.11, 0.6, ratio=2.0, index=0.9)
    parts.append((ping(A4, 1.2, 0.9, ratio=1.0, index=0.3), 0.5, 0.22))
    x = s.mix(*parts)
    return fx.space(end(x), "corridor", wet=0.2, tail=0.6)


@sound("world/achievement", level=-15)
def achievement(rng, i):
    """大的收获（有了自己的货船）：四个音的短号角，最后一个和弦慢慢铺开、发亮。"""
    parts = motif([E5, A5, CS6], 0.13, 0.35, ratio=1.0, index=1.6, edge=0.05)
    n = s.samples(2.0)
    pad = (s.saw(A4, n, 20) + s.saw(CS5 * 1.002, n, 20) + s.saw(E5 * 0.998, n, 20)) / 3
    pad = s.resonant(pad, "lowpass", s.exp_curve([(0, 500), (0.6, 3000), (2.0, 1200)], n), 0.9)
    pad *= s.envelope([(0, 0), (0.15, 1), (1.2, 0.7), (2.0, 0)], n)
    top = ping(A5, 2.0, 1.5, ratio=2.0, index=1.0)
    x = s.mix(*parts, (pad, 0.5, 0.39), (top, 0.8, 0.39))
    x = fx.chorus(end(x, 0.2), rate=0.5, depth=0.2, mix=0.3)
    return fx.space(x, "corridor", wet=0.25, tail=0.8)


@sound("world/mission_accept", level=-18)
def mission_accept(rng, i):
    """接下任务：一小段数据传进来，一声干脆的确认。"""
    parts = [(click(rng), 0.5, 0.0)]
    for k in range(5):
        tn = s.samples(0.02)
        f = [B5, E6, CS6, A6 * 0.9, E6][k]
        parts.append(((s.sine(f, tn) + 0.2 * s.square(f, tn, 5)) * s.adsr(tn, 0.002, 0.004, 0.7, 0.006), 0.25, 0.03 + k * 0.025))
    parts += motif([A5], 0.1, 0.3, edge=0.15, start=0.18)
    return fx.space(end(speaker(s.mix(*parts))), "corridor", wet=0.15, tail=0.3)


@sound("world/mission_update", level=-19)
def mission_update(rng, i):
    """任务有了进展：轻轻的两声，一高一低。"""
    x = s.mix(*motif([E6, B5], 0.1, 0.3, ratio=3.0, index=0.6))
    return fx.space(end(x), "corridor", wet=0.15, tail=0.3)


@sound("world/mission_complete", level=-16)
def mission_complete(rng, i):
    """任务完成：三个往上的音落在主音上，下面垫一个低八度。"""
    parts = motif([E5, FS5, A5], 0.12, 0.45, ratio=2.0, index=0.9)
    parts.append((ping(A4, 1.0, 0.8, ratio=1.0, index=0.4), 0.45, 0.24))
    return fx.space(end(s.mix(*parts)), "corridor", wet=0.2, tail=0.5)


@sound("world/research_done", level=-17)
def research_done(rng, i):
    """研究完成：一阵飞快往上爬的数据声，收在一个亮的和弦上。"""
    parts = []
    for k in range(10):
        tn = s.samples(0.022)
        f = A5 * 2 ** (k / 12 * 1.4)
        parts.append(((s.sine(f, tn) + 0.2 * s.square(f, tn, 5)) * s.adsr(tn, 0.002, 0.004, 0.7, 0.006), 0.2, k * 0.026))
    chord = [(ping(f, 1.2, 0.8, ratio=2.0, index=0.8), 0.5, 0.28) for f in (A5, CS6, E6)]
    x = s.mix(*parts, *chord)
    return fx.space(end(x), "corridor", wet=0.18, tail=0.5)


@sound("world/repair", variants=2, level=-16)
def repair(rng, i):
    """修好：棘轮扳手拧几下，一小段焊接的电弧，最后拧紧的一声。"""
    length = 1.3
    n = s.samples(length)
    parts = []
    clicks = 6 + i
    for k in range(clicks):
        parts.append((m.impact(rng, freq=rng.uniform(1800, 2200), decay=0.04, ratios=m.BAR[:3], hardness=0.7, noise=0.25), 0.3,
                      k * 0.055))
    weld_n = s.samples(0.35)
    weld = m.arc(weld_n, rng, intensity=s.envelope([(0, 0), (0.03, 1), (0.3, 0.9), (0.35, 0)], weld_n))
    weld = s.highpass(weld, 400)
    tight = m.impact(rng, freq=rng.uniform(260, 300), decay=0.35, ratios=m.PLATE, hardness=0.6, noise=0.2)
    x = s.mix(*parts, (weld, 0.7, clicks * 0.055 + 0.08), (tight, 0.7, clicks * 0.055 + 0.5))
    return fx.space(end(s.fit(x, max(n, len(x))), 0.1), "corridor", wet=0.18, tail=0.4)


@sound("world/guild_gift", level=-16)
def guild_gift(rng, i):
    """公会送来礼物：外星的铃一组敲响，后面接一个往上的小动机。"""
    bells = s.mix((alien(rng, A4, 1.6, 1.2, beat=1.5, ratios=m.PLATE[:6], brightness=0.7), 0.7, 0.0),
                  (alien(rng, E5, 1.6, 1.1, beat=2.1, ratios=m.PLATE[:6], brightness=0.7), 0.5, 0.09))
    x = s.mix((bells, 1.0), *motif([CS6, E6, A6], 0.09, 0.35, ratio=2.0, index=0.8, start=0.25, gain_fall=0.1))
    return fx.space(end(x, 0.15), "corridor", wet=0.22, tail=0.7)


@sound("world/dialogue", variants=2, level=-18)
def dialogue(rng, i):
    """对话里说对了、得了东西：一声温和的拨弦般的外星音，带一个往上的泛音。"""
    f = [CS5, E5][i]
    pluck = alien(rng, f, 0.9, 0.5, beat=2.5, ratios=[1.0, 2.01, 3.03, 4.1], brightness=0.6)
    over = ping(f * 2, 0.7, 0.35, ratio=2.0, index=0.5) * 0.3
    x = s.mix((pluck, 1.0), (over, 1.0, 0.08))
    return fx.space(end(x), fx.outdoor_ir(rng, 0.4), wet=0.2, tail=0.4)
