"""工厂、发电、物流、戴森球的设备的声音（ADR-0029）。

循环声都做成严格周期的：噪声在频域按长度 n 生成（反变换回来首尾自然接上），音调的频率取整到
每圈整数个周期，离散的事件（咔哒、气泡、冲击）超出结尾的部分折回开头，混响用循环卷积，
有记忆的滤波器先跑一圈预热再取稳态。这样交叉淡化时两段完全一样（power=False），接缝听不出来。
"""

from __future__ import annotations

import numpy as np

import fx
import models as m
import synth as s
from recipe import sound

DIRS = ["machine"]

XF = 0.4


# ---- 周期信号的工具

def loop_out(x: np.ndarray) -> np.ndarray:
    """周期为 len(x) 的信号后面接上开头 XF 秒，交给 build 做交叉淡化。"""
    return np.concatenate([x, x[:s.samples(XF)]])


def snap(f: float, period: float) -> float:
    """把频率取整到每 period 秒整数个周期，循环的首尾相位对得上。"""
    return max(1.0, round(f * period)) / period


def band(lo: float, hi: float, order: int = 2, tilt: float = 0.0):
    """频域的带通形状（巴特沃斯那样的幅度），tilt 是每倍频程的倾斜（dB）。"""

    def shape(f):
        mag = 1 / np.sqrt(1 + (lo / f) ** (2 * order)) / np.sqrt(1 + (f / hi) ** (2 * order))
        return mag * (f / 1000.0) ** (tilt / 6.0206)

    return shape


def noise(n: int, rng: np.random.Generator, shape) -> np.ndarray:
    """周期为 n 的噪声：频域随机相位、按 shape(f) 定幅度。"""
    f = np.fft.rfftfreq(n, 1 / s.SR)
    spec = (rng.standard_normal(len(f)) + 1j * rng.standard_normal(len(f))) * shape(np.maximum(f, 1e-3))
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / (np.std(x) + 1e-12) * 0.5


def tones(n: int, f: float, amps, period: float, rng: np.random.Generator | None = None) -> np.ndarray:
    """基频 f（取整过）和它的谐波，各自随机初相。"""
    f = snap(f, period)
    t = s.times(n)
    out = np.zeros(n)
    for k, a in enumerate(amps, start=1):
        if a == 0 or k * f >= s.NYQUIST * 0.9:
            continue
        ph = rng.uniform(0, 2 * np.pi) if rng is not None else 0.0
        out += a * np.sin(2 * np.pi * k * f * t + ph)
    return out


def wobble(n: int, rng: np.random.Generator, period: float, rate: float) -> np.ndarray:
    """周期的平滑随机起伏（−1 到 1）：频率是 1/period 的整数倍、到 rate 为止的几个正弦。"""
    t = s.times(n)
    count = max(1, int(rate * period))
    out = np.zeros(n)
    for k in range(1, count + 1):
        out += rng.standard_normal() / np.sqrt(k) * np.sin(2 * np.pi * k * t / period + rng.uniform(0, 2 * np.pi))
    return out / (np.max(np.abs(out)) + 1e-12)


def fold(x: np.ndarray, n: int) -> np.ndarray:
    """超出 n 的部分折回开头叠上去。"""
    out = np.zeros(n)
    for k in range(0, len(x), n):
        seg = x[k:k + n]
        out[:len(seg)] += seg
    return out


def place(n: int, events) -> np.ndarray:
    """把 [(信号, 秒), …] 叠到长度 n 的一圈里，越过结尾的折回开头。"""
    longest = max((int(at * s.SR) % n + len(sig) for sig, at in events), default=n)
    out = np.zeros(max(n, longest))
    for sig, at in events:
        i = int(at * s.SR) % n
        out[i:i + len(sig)] += sig
    return fold(out, n)


def steady(x: np.ndarray, fn) -> np.ndarray:
    """有记忆的处理（滤波器）：信号接三圈跑一遍，取最后一圈，就是稳态的、周期的。"""
    n = len(x)
    return fn(np.tile(x, 3))[2 * n:3 * n]


def room(x: np.ndarray, ir: np.ndarray, wet: float) -> np.ndarray:
    """循环卷积的混响：尾巴绕回开头，跟上一圈接得上。湿声去掉开头 3 ms，按干声的响度配。"""
    n = len(x)
    ir = ir.copy()
    ir[:s.samples(0.003)] = 0
    w = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(fold(ir, n)), n)
    scale = np.sqrt(np.mean(x ** 2) + 1e-20) / (np.sqrt(np.mean(w ** 2)) + 1e-20)
    return x + wet * w * scale


def bubbles(n: int, rng: np.random.Generator, rate: float, radius, rise: float = 0.1) -> np.ndarray:
    return fold(m.bubbles(n + s.samples(0.3), rng, rate, radius, rise), n)


def crackle(n: int, rng: np.random.Generator, rate: float, width: float = 0.0002) -> np.ndarray:
    return fold(np.concatenate([s.crackle(n, rng, rate, width), np.zeros(s.samples(0.01))]), n)


def env_cycle(n: int, cycle: float, points) -> np.ndarray:
    """每 cycle 秒重复一次的包络：points 是一圈里的 (秒, 值)。"""
    t = s.times(n) % cycle
    inside = [p for p in points if p[0] < cycle]
    return np.interp(t, [p[0] for p in inside], [p[1] for p in inside], period=cycle)


def servo(rng: np.random.Generator, length: float, strength: float, top_rpm: float = 7800, coils: int = 7,
          teeth: int = 13) -> np.ndarray:
    """一下伺服动作：升速、走一段、刹住，带上机壳里的回声。"""
    n = s.samples(length + 0.25)
    rpm = m.servo_moves(n, rng, [(0.0, length, strength)], top_rpm=top_rpm)
    motor = m.electric_motor(n, rng, rpm, coils=coils, gear_teeth=teeth, chassis=0.4, harshness=0.15, rotor=1.0,
                             gears=0.45, brushes=0.25, air=0.15)
    return s.taper(s.highpass(motor, 120), 0.05)


def puff(rng: np.random.Generator, length: float = 0.15, center: float = 3200) -> np.ndarray:
    """气动阀放气的"嗤"。"""
    n = s.samples(length + 0.05)
    return s.resonant(s.white(n, rng), "bandpass", center, 0.8) * s.perc(n, 0.004, length)


# ---- 循环：加工

@sound("machine/assembler", loop=6.0, level=-22, power=False)
def assembler(rng, i):
    """装配机：机械臂的伺服电机一伸一转，气动夹爪咔哒一下，放下零件，收回到位。三秒一个周期。"""
    period = 6.0
    n = s.samples(period)
    events = []
    for cycle in range(2):
        base = cycle * 3.0 + rng.uniform(0, 0.04)
        for start, length, strength in ((0.00, 0.42, 0.9), (0.62, 0.50, 1.0), (1.30, 0.38, 0.7), (2.05, 0.30, 0.45)):
            events.append((servo(rng, length, strength * rng.uniform(0.85, 1.05)) * 0.8, base + start))
        grip = base + 0.50
        events.append((puff(rng) * 0.5, grip))
        events.append((m.relay(rng, size=1.6, bright=0.5) * 0.9, grip + 0.06))
        events.append((m.impact(rng, freq=rng.uniform(700, 1000), decay=0.18, hardness=0.75, noise=0.25) * 0.35, base + 1.14))
        events.append((m.relay(rng, size=0.8, bright=0.7) * 0.5, base + 1.72))
        events.append((m.relay(rng, size=0.9, bright=0.6) * 0.4, base + 2.37))
    body = room(place(n, events), fx.room_ir("casing"), 0.25)
    fan = noise(n, rng, band(120, 2200)) * 0.06 + tones(n, 280, [1, 0.3], period, rng) * 0.01
    hum = tones(n, 100, [1, 0.5, 0.25, 0.12], period, rng) * 0.025
    return loop_out(room(body + fan + hum, fx.room_ir("factory"), 0.18))


@sound("machine/arc_smelter", loop=6.0, level=-21, power=False)
def arc_smelter(rng, i):
    """电弧熔炉：交流电弧每半周燃一次，100 Hz 调制的宽带噼啪和轰鸣；变压器嗡声；电弧偶尔断了重燃，嘭一下。"""
    period = 6.0
    n = s.samples(period)
    t = s.times(n)
    cycle = 0.5 + 0.5 * np.sin(2 * np.pi * 100 * t)
    unrest = 0.65 + 0.35 * wobble(n, rng, period, 1.5)
    hum = tones(n, 100, [1, 0.6, 0.45, 0.3, 0.22, 0.16, 0.12, 0.08], period, rng)
    crack = crackle(n, rng, 1400, 0.00015) * (0.35 + 0.65 * cycle ** 3) * unrest
    crack = steady(crack, lambda y: s.resonant(y, "highpass", 900, 0.7))
    roar = noise(n, rng, band(600, 6000)) * (0.3 + 0.7 * cycle ** 2) * unrest
    melt = noise(n, rng, band(35, 250)) * (0.8 + 0.2 * wobble(n, rng, period, 0.8))
    bangs = []
    for at in rng.uniform(0, period, 3):
        k = s.samples(0.35)
        burst = s.lowpass(s.white(k, rng), 3000) * s.perc(k, 0.0005, 0.12)
        thud = m.impact(rng, freq=rng.uniform(110, 150), decay=0.3, ratios=m.HOLLOW, hardness=0.3, noise=0.0)
        bangs.append((s.mix((burst, 0.8), (thud, 0.6)), at))
    x = hum * 0.3 + crack * 0.9 + roar * 0.35 + melt * 0.35 + place(n, bangs) * 0.7
    return loop_out(room(x, fx.room_ir("factory"), 0.22))


@sound("machine/plane_smelter", loop=6.0, level=-22, power=False)
def plane_smelter(rng, i):
    """位面熔炉（感应熔炼）：中频逆变器的尖细啸声带着 100 Hz 的纹波，线圈的嗡声，电磁搅拌下熔池的翻滚和冷却水。"""
    period = 6.0
    n = s.samples(period)
    t = s.times(n)
    ripple = 1 + 0.3 * np.sin(2 * np.pi * 100 * t)
    whine = tones(n, 1180, [1, 0.3, 0.15], period, rng) * ripple
    coil = tones(n, 100, [1, 0.5, 0.3, 0.15], period, rng)
    stir = noise(n, rng, band(40, 300)) * (0.7 + 0.3 * wobble(n, rng, period, 1.0))
    blubs = bubbles(n, rng, 2.5, (0.012, 0.025), 0.05)
    water = noise(n, rng, band(250, 1500))
    shimmer = tones(n, 2400, [1], period, rng) + tones(n, 2400 + 1 / 3, [1], period, rng)
    x = whine * 0.05 + coil * 0.12 + stir * 0.45 + blubs * 0.35 + water * 0.12 + shimmer * 0.012
    x = room(x, fx.room_ir("casing"), 0.12)
    return loop_out(room(x, fx.room_ir("factory"), 0.2))


@sound("machine/chemical", loop=6.0, level=-22, power=False)
def chemical(rng, i):
    """化工厂和精炼厂：反应釜里大大小小的气泡，循环泵，搅拌的低吼，泄压阀隔一阵放一次气。"""
    period = 6.0
    n = s.samples(period)
    glugs = bubbles(n, rng, 3.0, (0.006, 0.018), 0.15)
    fizz = bubbles(n, rng, 35, (0.0012, 0.003), 0.1)
    pump = tones(n, 2950 / 60, [0.6, 1.0, 0.3, 0.2, 0.1], period, rng) * 0.08
    pump += tones(n, snap(2950 / 60, period) * 5, [1, 0.3], period, rng) * 0.03
    flow = noise(n, rng, band(150, 1200)) * 0.18
    stir = noise(n, rng, band(40, 200)) * (0.6 + 0.4 * wobble(n, rng, period, 0.7)) * 0.3
    relief = []
    at = rng.uniform(3.4, 4.2)
    k = s.samples(0.75)
    hiss = s.lowpass(s.resonant(s.white(k, rng), "bandpass", 3800, 0.7), 9000) * s.envelope([(0, 0), (0.03, 1), (0.35, 0.7), (0.7, 0)], k)
    relief.append((hiss * 0.35, at))
    relief.append((m.relay(rng, size=1.8, bright=0.4) * 0.5, at - 0.02))
    x = glugs * 0.6 + fizz * 0.25 + pump + flow + stir + place(n, relief)
    x = room(x, fx.room_ir("casing"), 0.1)
    return loop_out(room(x, fx.room_ir("factory"), 0.2))


@sound("machine/fractionator", loop=6.0, level=-23, power=False)
def fractionator(rng, i):
    """分馏塔（离心分离）：高速转子的呜声，两根转子差半赫兹拍出两秒一次的起伏，气流和液体甩动的声音。"""
    period = 6.0
    n = s.samples(period)
    t = s.times(n)
    rotor = tones(n, 280, [1, 0.45, 0.25, 0.12, 0.08], period, rng) + tones(n, 280.5, [0.8, 0.3, 0.15], period, rng)
    air = noise(n, rng, band(300, 5000)) * (0.8 + 0.2 * wobble(n, rng, period, 2))
    bearing = noise(n, rng, band(5000, 12000))
    swish = noise(n, rng, band(150, 900)) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.5 * t))
    x = rotor * 0.06 + air * 0.18 + bearing * 0.03 + swish * 0.25
    x = room(x, fx.room_ir("casing"), 0.2)
    return loop_out(room(x, fx.room_ir("factory"), 0.18))


@sound("machine/lab", loop=6.0, level=-24, power=False)
def lab(rng, i):
    """矩阵研究站：计算机一串串的嘀嘀声，继电器和磁头的轻响，机柜风扇。"""
    period = 6.0
    n = s.samples(period)
    events = []
    at = 0.0
    while at < period:
        length = rng.uniform(0.03, 0.1)
        k = s.samples(length)
        f = 900 * 2.2 ** rng.uniform(-1, 1)
        beep = s.sine(f, k) * s.adsr(k, 0.002, 0.01, 0.7, 0.01)
        if rng.random() < 0.3:
            beep = beep * 0.6 + 0.4 * s.square(f, k, 8) * s.adsr(k, 0.002, 0.01, 0.6, 0.01)
        events.append((beep * rng.uniform(0.3, 1.0), at))
        # 偶尔连着几声
        at += length + (rng.uniform(0.01, 0.03) if rng.random() < 0.35 else rng.exponential(0.14))
    for _ in range(3):
        k = s.samples(0.06)
        seek = s.resonant(s.white(k, rng), "bandpass", rng.uniform(1800, 2600), 4) * s.adsr(k, 0.002, 0.01, 0.6, 0.02)
        events.append((seek * 0.4, rng.uniform(0, period)))
    chatter = place(n, events)
    ticks = steady(crackle(n, rng, 18, 0.00008), lambda y: s.resonant(y, "bandpass", 3500, 2))
    fans = tones(n, 280, [1, 0.3], period, rng) * 0.02 + noise(n, rng, band(150, 3500)) * 0.1
    hum = tones(n, 100, [1, 0.4], period, rng) * 0.015
    x = chatter * 0.35 + ticks * 0.8 + fans + hum
    x = room(x, fx.room_ir("casing"), 0.1)
    return loop_out(room(x, fx.room_ir("factory"), 0.12))


@sound("machine/miner", loop=6.0, level=-21, power=False)
def miner(rng, i):
    """采矿机：冲击钻头每秒八下砸进岩石，钻杆转着研磨，电机和齿轮的嗡声，碎屑往下落。"""
    period = 6.0
    n = s.samples(period)
    t = s.times(n)
    rot = snap(24, period)
    motor = tones(n, rot * 9, [1, 0.4, 0.2], period, rng) * 0.05 + tones(n, rot * 17, [1, 0.3], period, rng) * 0.025
    motor += tones(n, 100, [1, 0.5, 0.2], period, rng) * 0.03
    hits = []
    for k in range(48):
        hit = m.impact(rng, freq=rng.uniform(170, 220), decay=0.12, ratios=m.BAR, hardness=0.6, noise=0.45)
        hits.append((hit * rng.uniform(0.75, 1.0), k / 8 + rng.uniform(-0.004, 0.004)))
    hammer = place(n, hits)
    grind = noise(n, rng, band(250, 3000)) * (0.7 + 0.3 * np.sin(2 * np.pi * rot * t)) * (0.85 + 0.15 * wobble(n, rng, period, 1))
    debris = steady(crackle(n, rng, 60, 0.0003), lambda y: s.highpass(y, 1500))
    x = motor + hammer * 0.45 + grind * 0.3 + debris * 0.5
    return loop_out(room(x, fx.outdoor_ir(rng, 0.6), 0.3))


# ---- 循环：资源

@sound("machine/water_pump", loop=6.0, level=-23, power=False)
def water_pump(rng, i):
    """抽水站（离心泵）：电机每分钟 2950 转，六片叶轮的通过音，管子里哗哗的水，进水口偶尔咕噜一下。"""
    period = 6.0
    n = s.samples(period)
    rot = snap(2950 / 60, period)
    motor = tones(n, rot, [0.4, 1.0, 0.3, 0.2], period, rng) * 0.08 + tones(n, rot * 6, [1, 0.35, 0.1], period, rng) * 0.04
    hum = tones(n, 100, [1, 0.5, 0.25], period, rng) * 0.03
    flow = noise(n, rng, band(80, 1800)) * (0.8 + 0.2 * wobble(n, rng, period, 0.5)) * 0.35
    gurgle = bubbles(n, rng, 8, (0.003, 0.01), 0.15) * 0.25
    x = room(motor + hum + flow + gurgle, fx.room_ir("casing"), 0.15)
    return loop_out(room(x, fx.outdoor_ir(rng, 0.5), 0.25))


@sound("machine/oil_extractor", loop=8.0, level=-22, power=False)
def oil_extractor(rng, i):
    """抽油机（游梁式）：四秒一个冲程，游梁到顶和到底各吱一声，配重到底沉沉地一顿，上冲程油往上涌，减速箱嗡嗡。"""
    period = 8.0
    n = s.samples(period)
    t = s.times(n)
    load = 0.8 + 0.2 * np.sin(2 * np.pi * t / 4.0)
    drive = (tones(n, 100, [1, 0.5, 0.25], period, rng) * 0.04 + tones(n, 520, [1, 0.3], period, rng) * 0.012) * load
    events = []
    for c in range(2):
        base = c * 4.0
        for at, f in ((1.9, 640), (3.9, 820)):
            k = s.samples(0.3)
            # synth.wander 在短数组上会越界，这里自己插一条抖动
            shake = np.interp(np.linspace(0, 1, k), np.linspace(0, 1, 12), rng.uniform(-1, 1, 12))
            fj = f * rng.uniform(0.95, 1.05) * (1 + 0.04 * shake)
            squeak = s.resonant(s.saw(fj, k, 24), "bandpass", f * 2, 3) * s.adsr(k, 0.04, 0.08, 0.7, 0.12)
            events.append((squeak * 0.12, base + at + rng.uniform(-0.03, 0.03)))
        events.append((m.impact(rng, freq=rng.uniform(65, 75), decay=0.35, ratios=m.HOLLOW, hardness=0.3, noise=0.1) * 0.6,
                       base + 3.95))
        events.append((m.relay(rng, size=2.5, bright=0.3) * 0.35, base + 2.0))
    gush = noise(n, rng, band(150, 900)) * env_cycle(n, 4.0, [(0, 0.15), (0.6, 1.0), (1.8, 0.8), (2.2, 0.15), (4.0, 0.15)])
    x = drive + place(n, events) + gush * 0.25
    return loop_out(room(x, fx.outdoor_ir(rng, 0.6), 0.3))


# ---- 循环：发电

@sound("machine/thermal", loop=6.0, level=-21, power=False)
def thermal(rng, i):
    """火力发电厂：锅炉的燃烧轰鸣，汽轮机每分钟 3000 转的叶片啸声和发电机的嗡声，蒸汽管道的嘶声。"""
    period = 6.0
    n = s.samples(period)
    flicker = 1 + 0.15 * wobble(n, rng, period, 3)
    burner = (noise(n, rng, band(30, 600)) + noise(n, rng, band(400, 2500)) * 0.25) * flicker
    turbine = tones(n, 50 * 36, [1, 0.35, 0.15], period, rng) * 0.02
    generator = tones(n, 100, [1, 0.6, 0.3, 0.15], period, rng) * 0.06
    steam = noise(n, rng, band(3000, 9000)) * 0.08
    valves = place(n, [(m.relay(rng, size=2.0, bright=0.4) * 0.3, rng.uniform(0, period)) for _ in range(2)])
    x = burner * 0.5 + turbine + generator + steam + valves
    return loop_out(room(x, fx.room_ir("factory"), 0.25))


def fusion_core(rng, period: float, depth: float):
    """聚变装置：约束线圈深沉的嗡声，等离子体一秒半一次的脉动，射频加热细细的啸声，冷却剂流过。"""
    n = s.samples(period)
    t = s.times(n)
    pulse = 0.6 + 0.4 * (0.5 - 0.5 * np.cos(2 * np.pi * t / 1.5)) ** 2
    coils = tones(n, 50, [1, 0.7, 0.5, 0.3, 0.2, 0.1], period, rng)
    roar = noise(n, rng, band(80, 600))
    rf = np.sin(2 * np.pi * snap(3700, period) * t + 2.0 * np.sin(2 * np.pi * t / 1.5))
    coolant = noise(n, rng, band(200, 2000))
    sparks = steady(crackle(n, rng, 8, 0.0001), lambda y: s.highpass(y, 2500))
    x = (coils * 0.2 + roar * 0.3 * depth) * pulse + rf * 0.012 + coolant * 0.12 + sparks * 0.25
    return room(x, fx.room_ir("factory"), 0.2)


@sound("machine/fusion", loop=6.0, level=-22, power=False)
def fusion(rng, i):
    return loop_out(fusion_core(rng, 6.0, 1.0))


@sound("machine/geothermal", loop=6.0, level=-22, power=False)
def geothermal(rng, i):
    """地热电站：地下传上来的低沉隆隆声，蒸汽三秒一阵地喷，泥浆咕嘟冒大泡，小涡轮在转。"""
    period = 6.0
    n = s.samples(period)
    rumble = noise(n, rng, band(30, 150)) * (0.8 + 0.2 * wobble(n, rng, period, 0.6))
    steam = noise(n, rng, band(1500, 8000)) * env_cycle(n, 3.0, [(0, 0.3), (0.4, 1.0), (1.6, 0.6), (2.6, 0.3), (3.0, 0.3)])
    mud = bubbles(n, rng, 2, (0.015, 0.04), 0.05)
    turbine = tones(n, 50 * 24, [1, 0.3], period, rng) * 0.015 + tones(n, 100, [1, 0.4], period, rng) * 0.03
    x = rumble * 0.5 + steam * 0.12 + mud * 0.4 + turbine
    return loop_out(room(x, fx.outdoor_ir(rng, 0.7), 0.3))


@sound("machine/wind", loop=6.0, level=-24, power=False)
def wind(rng, i):
    """风力涡轮机：三片叶子扫过塔筒，每 0.75 秒"呼"一下，齿轮箱细细的啸声，四周的风。"""
    period = 6.0
    n = s.samples(period)
    t = s.times(n)
    bpf = 8 / period
    swoosh_env = 0.3 + 0.7 * ((1 + np.cos(2 * np.pi * bpf * t)) / 2) ** 3
    swoosh = noise(n, rng, band(250, 2500, tilt=-2)) * swoosh_env
    gearbox = tones(n, 420, [1, 0.3], period, rng) * 0.02 + tones(n, 100, [1, 0.3], period, rng) * 0.015
    air = noise(n, rng, band(40, 600)) * (0.8 + 0.2 * wobble(n, rng, period, 0.5)) * 0.3
    creak = place(n, [(m.relay(rng, size=3.0, bright=0.2) * 0.2, rng.uniform(0, period))])
    x = swoosh * 0.45 + gearbox + air + creak
    return loop_out(room(x, fx.outdoor_ir(rng, 0.8), 0.3))


@sound("machine/collider", loop=6.0, level=-22, power=False)
def collider(rng, i):
    """微型粒子对撞机：每两秒一次，磁铁升流时的呜声越拉越高，束流打到靶上"啪"地放掉；底下是磁铁和真空泵。"""
    period = 6.0
    n = s.samples(period)
    events = []
    for c in range(3):
        k = s.samples(2.0)
        ramp = s.exp_curve([(0, 260), (1.6, 1100), (2.0, 1100)], k)
        amp = s.envelope([(0, 0.0), (0.2, 0.4), (1.55, 1.0), (1.62, 0.0), (2.0, 0.0)], k)
        whine = s.harmonics(ramp, k, [1, 0.4, 0.2]) * amp
        dump = m.zap(rng, start=2400, end=300, length=0.2, bite=0.6)
        thump = m.impact(rng, freq=90, decay=0.25, ratios=m.HOLLOW, hardness=0.4, noise=0.1) * 0.5
        events.append((whine * 0.3, c * 2.0))
        events.append((dump * 0.45, c * 2.0 + 1.6))
        events.append((thump, c * 2.0 + 1.6))
    magnets = tones(n, 100, [1, 0.5, 0.3, 0.2], period, rng) * 0.05
    rf = tones(n, 2600, [1], period, rng) * 0.008
    vacuum = noise(n, rng, band(60, 900)) * 0.1
    x = place(n, events) + magnets + rf + vacuum
    return loop_out(room(x, fx.room_ir("factory"), 0.2))


@sound("machine/receiver", loop=6.0, level=-25, power=False)
def receiver(rng, i):
    """射线接收站：变压器的低嗡，接收阵列上几条高音慢慢地拍，偶尔一点放电的细响。"""
    period = 6.0
    n = s.samples(period)
    hum = tones(n, 100, [1, 0.5, 0.3], period, rng) * 0.05
    shimmer = np.zeros(n)
    for f in (1320, 1980, 2970):
        shimmer += (tones(n, f, [1], period, rng) + tones(n, f + 1 / 3, [1], period, rng)) * (0.6 + 0.4 * wobble(n, rng, period, 0.5))
    sparks = steady(crackle(n, rng, 15, 0.0001), lambda y: s.highpass(y, 3000))
    air = noise(n, rng, band(500, 4000)) * 0.05
    x = hum + shimmer * 0.012 + sparks * 0.2 + air
    return loop_out(room(x, fx.outdoor_ir(rng, 0.5), 0.2))


@sound("machine/transformer", loop=6.0, level=-26, power=False)
def transformer(rng, i):
    """能量枢纽、蓄电器、电塔：变压器铁芯的磁致伸缩嗡声（工频两倍，100 Hz 和它的谐波），负载大时更毛。"""
    period = 6.0
    n = s.samples(period)
    hum = tones(n, 100, [1, 0.55, 0.35, 0.2, 0.12, 0.06], period, rng)
    hum = s.softclip(hum * 1.5, 1.3)
    cooling = noise(n, rng, band(100, 1500)) * 0.05
    x = room(hum * 0.3 + cooling, fx.room_ir("casing"), 0.2)
    return loop_out(room(x, fx.room_ir("factory"), 0.12))


@sound("machine/bio", loop=6.0, level=-26, power=False)
def bio(rng, i):
    """水培槽、营养处理器、蛋序列仪：细碎的气泡，偶尔大一点的咕嘟，水流，膜片气泵按工频轻轻地嗡。"""
    period = 6.0
    n = s.samples(period)
    small = bubbles(n, rng, 12, (0.0015, 0.004), 0.1)
    glug = bubbles(n, rng, 1.2, (0.004, 0.008), 0.15)
    trickle = noise(n, rng, band(1500, 6000)) * (0.7 + 0.3 * wobble(n, rng, period, 1.5)) * 0.04
    pump = tones(n, 50, [1, 0.5, 0.3, 0.15], period, rng) * 0.03
    x = small * 0.4 + glug * 0.4 + trickle + pump
    return loop_out(room(x, fx.room_ir("factory"), 0.12))


@sound("machine/station", loop=6.0, level=-24, power=False)
def station(rng, i):
    """物流塔：散热风扇、变压器，无人机一架架起飞（旋翼的音调升上去、飞远），对接口沉沉地咔嗒。"""
    period = 6.0
    n = s.samples(period)
    fans = tones(n, 120, [1, 0.3], period, rng) * 0.04 + noise(n, rng, band(80, 2500)) * 0.25
    hum = tones(n, 100, [1, 0.5, 0.25], period, rng) * 0.06
    events = []
    for at in (rng.uniform(0.3, 2.5), rng.uniform(3.3, 5.5)):
        k = s.samples(1.6)
        blade = s.exp_curve([(0, 180), (0.8, 420), (1.6, 380)], k) * rng.uniform(0.95, 1.05)
        amp = s.envelope([(0, 0), (0.15, 0.8), (0.8, 1.0), (1.6, 0)], k)
        rotors = s.harmonics(blade, k, [1, 0.5, 0.3, 0.15]) + s.harmonics(blade * 1.03, k, [0.7, 0.3])
        wash = s.resonant(s.white(k, rng), "bandpass", 1200, 0.6)
        events.append(((rotors * 0.25 + wash * 0.3) * amp * 0.5, at))
    for at in rng.uniform(0, period, 2):
        events.append((m.impact(rng, freq=140, decay=0.25, ratios=m.HOLLOW, hardness=0.4, noise=0.2) * 0.35, at))
        events.append((m.relay(rng, size=1.5, bright=0.4) * 0.3, at + 0.05))
    x = fans + hum + place(n, events)
    return loop_out(room(x, fx.outdoor_ir(rng, 0.6), 0.2))


@sound("machine/shield", loop=6.0, level=-23, power=False)
def shield(rng, i):
    """行星护盾展开着：发生器深沉的嗡声和差半赫兹的拍，场的高频沙沙声，三秒一次缓缓地涨落。"""
    period = 6.0
    n = s.samples(period)
    t = s.times(n)
    swell = 0.75 + 0.25 * np.cos(2 * np.pi * t / 3.0)
    hum = tones(n, 55, [1, 0.6, 0.4, 0.25], period, rng) + tones(n, 55.5, [0.8, 0.4], period, rng)
    pad = np.sin(2 * np.pi * snap(440, period) * t + (1.2 + 0.4 * np.cos(2 * np.pi * t / 3.0))
                 * np.sin(2 * np.pi * snap(660, period) * t))
    fizz = noise(n, rng, band(3000, 9000))
    x = (hum * 0.25 + pad * 0.03 + fizz * 0.04) * swell
    return loop_out(room(x, fx.outdoor_ir(rng, 0.8), 0.25))


@sound("machine/jammer", loop=6.0, level=-24, power=False)
def jammer(rng, i):
    """干扰塔：两路调频的啸声来回摆，夹着采样保持的噪声，像收音机里被压住的电台。"""
    period = 6.0
    n = s.samples(period)
    t = s.times(n)
    # 调相指数 40、30：±120 Hz、±135 Hz 地摆
    a = np.sin(2 * np.pi * snap(700, period) * t + 40 * np.sin(2 * np.pi * 3 * t))
    b = np.sin(2 * np.pi * snap(1050, period) * t + 30 * np.sin(2 * np.pi * 4.5 * t + 1.0))
    hold = np.repeat(rng.uniform(0.1, 1.0, 72), int(np.ceil(n / 72)))[:n]
    # 跳变磨圆 4 毫秒，不然每一跳都是一声咔
    hold = steady(hold, lambda y: s.one_pole(y, 40))
    static = noise(n, rng, band(1000, 5000)) * hold
    hum = tones(n, 100, [1, 0.4], period, rng) * 0.03
    x = a * 0.05 + b * 0.035 + static * 0.12 + hum
    return loop_out(room(x, fx.outdoor_ir(rng, 0.5), 0.15))


# ---- 一次性

@sound("machine/ejector_fire", variants=3, level=-17)
def ejector_fire(rng, i):
    """电磁轨道弹射器：电容充电的啸声，线圈一级级放电把太阳帆推出去，超音速的"啪"，导轨嗡地余振，呼地远去。"""
    u = rng.uniform(0.94, 1.06)
    k = s.samples(0.18)
    charge = s.sine(s.exp_curve([(0, 2000), (0.18, 6000)], k) * u, k) * s.envelope([(0, 0), (0.15, 1), (0.18, 0)], k) * 0.08
    stages = m.coil_stages(rng, stages=6, spacing=0.022, accel=0.75, pitch=180 * u)
    crack = m.n_wave(rng, 0.003) * 0.7
    ring = m.impact(rng, freq=520 * u, decay=0.9, ratios=m.BAR, hardness=0.9, noise=0.05) * 0.3
    k2 = s.samples(0.7)
    away = s.resonant(s.white(k2, rng), "bandpass", s.exp_curve([(0, 3000), (0.7, 800)], k2), 1.2) * s.perc(k2, 0.01, 0.6) * 0.3
    x = s.mix((charge, 1.0), (stages, 0.9, 0.17), (crack, 1.0, 0.27), (ring, 1.0, 0.18), (away, 1.0, 0.28))
    return fx.space(x, fx.outdoor_ir(rng, 0.8), wet=0.4, tail=1.0)


@sound("machine/silo_launch", variants=2, level=-14)
def silo_launch(rng, i):
    """垂直发射井：点火的一声闷响，推力爬上去成了宽带的轰鸣，火箭越飞越远、越来越闷，地面上的回声拖得很长。"""
    dur = 7.0
    n = s.samples(dur)
    thrust = s.envelope([(0, 0), (0.3, 0.6), (1.0, 1.0), (3.0, 1.0), (dur, 0.2)], n)
    roar = m.rocket(n, rng, thrust, crackle=0.6)
    roar = s.resonant(roar, "lowpass", s.exp_curve([(0, 9000), (2.0, 7000), (dur, 1100)], n), 0.6)
    rumble = s.lowpass(s.brown(n, rng), 120, 2) * thrust * 0.9
    k = s.samples(0.6)
    ignition = s.lowpass(s.white(k, rng), 900) * s.perc(k, 0.001, 0.25) * 1.2
    ignition += s.fit(m.impact(rng, freq=60, decay=0.4, ratios=m.HOLLOW, hardness=0.2, noise=0.0), k) * 0.8
    x = s.mix((ignition, 1.0), (roar * s.envelope([(0, 0), (0.15, 1), (dur, 1)], n), 0.9, 0.1), (rumble, 1.0, 0.1))
    return fx.space(s.taper(x, 2.0), fx.outdoor_ir(rng, 1.6), wet=0.5, tail=2.0)


@sound("machine/shield_raise", level=-16)
def shield_raise(rng, i):
    """行星护盾展开：发生器的嗡声从低处爬上来，场的高音越来越亮，最后"嗡"地一下张开。"""
    dur = 3.6
    n = s.samples(dur)
    f = s.exp_curve([(0, 30), (2.4, 55), (dur, 55)], n)
    rise = s.envelope([(0, 0), (2.3, 1), (2.45, 1), (2.8, 0.3), (dur, 0)], n)
    hum = s.harmonics(f, n, [1, 0.6, 0.4, 0.25]) * rise
    shimmer = s.fm(s.exp_curve([(0, 300), (2.4, 660), (dur, 660)], n), 1.5, s.envelope([(0, 0.2), (2.4, 2.5), (dur, 1)], n), n) * rise
    swell = s.resonant(s.white(n, rng), "bandpass", s.exp_curve([(0, 1500), (2.4, 7000), (dur, 6000)], n), 0.8) * rise ** 2
    bloom = m.impact(rng, freq=880, decay=1.2, ratios=m.PLATE, hardness=0.95, noise=0.05) * 0.25
    k = s.samples(0.4)
    thump = s.lowpass(s.white(k, rng), 180) * s.perc(k, 0.002, 0.3)
    x = s.mix((hum, 0.35), (shimmer, 0.06), (swell, 0.12), (bloom, 1.0, 2.4), (thump, 0.8, 2.4))
    return fx.space(x, fx.outdoor_ir(rng, 1.0), wet=0.35, tail=1.0)


@sound("machine/shield_hit", variants=3, level=-17)
def shield_hit(rng, i):
    """护盾挨打：被打的地方亮亮地一震（金属板似的非谐泛音加调频的铃声），一阵电噼啪，底下一声闷响。"""
    u = rng.uniform(0.9, 1.1)
    n = s.samples(1.4)
    ring = m.impact(rng, freq=rng.uniform(600, 900), decay=0.9, ratios=m.PLATE, hardness=0.95, noise=0.1)
    bell = s.fm(1200 * u, 1.41, 3 * s.perc(n, 0.001, 0.4), n) * s.perc(n, 0.001, 0.9)
    k = s.samples(0.3)
    sizzle = s.highpass(s.crackle(k, rng, 3000, 0.0001), 2000) * s.perc(k, 0.002, 0.25)
    k2 = s.samples(0.25)
    thud = s.lowpass(s.white(k2, rng), 200) * s.perc(k2, 0.001, 0.12)
    x = s.mix((ring, 0.5), (bell, 0.25), (sizzle, 0.5), (thud, 0.9))
    return fx.space(x, fx.outdoor_ir(rng, 0.8), wet=0.3, tail=0.8)


@sound("machine/shield_collapse", level=-14)
def shield_collapse(rng, i):
    """护盾崩溃：嗡声一路掉下去，调频的啸声往下扫，一串放电噼啪，像一大片玻璃碎掉的高音，最后低沉的回荡。"""
    dur = 3.4
    n = s.samples(dur)
    fall = s.envelope([(0, 1), (2.5, 0.2), (dur, 0)], n)
    hum = s.harmonics(s.exp_curve([(0, 60), (2.5, 25), (dur, 22)], n), n, [1, 0.6, 0.35, 0.2]) * fall
    sweep = s.fm(s.exp_curve([(0, 900), (1.8, 120), (dur, 100)], n), 1.41, s.exp_curve([(0, 4), (1.8, 1), (dur, 0.5)], n), n)
    sweep *= s.envelope([(0, 1), (1.8, 0.4), (dur, 0)], n)
    sparks = s.highpass(s.crackle(n, rng, [(0, 2500), (0.6, 800), (2.0, 50), (dur, 0)], 0.00012), 1800)
    shards = []
    for k in range(6):
        shard = m.impact(rng, freq=rng.uniform(1500, 2600), decay=rng.uniform(0.8, 1.5), ratios=m.PLATE, hardness=1.0, noise=0.1)
        shards.append((shard, rng.uniform(0.3, 0.8), rng.uniform(0.0, 0.35)))
    rumble = s.lowpass(s.brown(n, rng), 150, 2) * s.envelope([(0, 0), (0.05, 1), (dur, 0)], n)
    x = s.mix((hum, 0.35), (sweep, 0.12), (sparks, 0.35), (rumble, 0.6), *shards)
    return fx.space(s.taper(x, 0.8), fx.outdoor_ir(rng, 1.2), wet=0.45, tail=1.2)


@sound("machine/teleport", variants=2, level=-16)
def teleport(rng, i):
    """传送：一阵往上卷的风声和旋转的高音，最后落地"嗵"一下。"""
    dur = 1.3
    n = s.samples(dur)
    rise = s.envelope([(0, 0), (0.85, 1), (0.95, 0.3), (dur, 0)], n)
    whoosh = s.resonant(s.white(n, rng), "bandpass", s.exp_curve([(0, 300), (0.9, 4000), (dur, 3000)], n), 2.0) * rise
    swirl = s.fm(s.exp_curve([(0, 400), (0.9, 1600), (dur, 1600)], n) * rng.uniform(0.95, 1.05), 1.5, 2.0, n) * rise
    swirl = fx.phaser(swirl, rate=3.0, depth=0.8, feedback=0.4, mix=0.6)
    k = s.samples(0.35)
    t = s.times(k)
    thump = np.sin(2 * np.pi * 70 * t) * np.exp(-t / 0.08) + s.lowpass(s.white(k, rng), 400) * s.perc(k, 0.001, 0.1) * 0.5
    x = s.mix((whoosh, 0.4), (swirl, 0.1), (thump, 0.7, 0.92))
    return fx.space(x, "corridor", wet=0.25, tail=0.6)


@sound("machine/jammer_pulse", variants=2, level=-18)
def jammer_pulse(rng, i):
    """干扰塔放一次干扰：往下扫的调频声，夹着一阵断断续续的噪声。"""
    n = s.samples(0.7)
    chirp = s.fm(s.exp_curve([(0, 2400), (0.5, 500), (0.7, 450)], n) * rng.uniform(0.95, 1.05), 2.01,
                 5 * s.perc(n, 0.001, 0.5), n) * s.perc(n, 0.003, 0.55)
    gate = np.repeat(rng.uniform(0, 1, 30) > 0.4, int(np.ceil(n / 30)))[:n].astype(float)
    static = s.resonant(s.white(n, rng), "bandpass", 2500, 0.7) * gate * s.perc(n, 0.002, 0.5)
    x = s.mix((chirp, 0.5), (static, 0.3))
    return fx.space(x, fx.outdoor_ir(rng, 0.5), wet=0.2, tail=0.5)


@sound("machine/station_load", variants=2, level=-17)
def station_load(rng, i):
    """往物流塔里装无人机、运输船：伺服推进去，卡扣"咔"地锁上。"""
    whir = servo(rng, 0.25, 0.8, top_rpm=6000)
    latch = m.impact(rng, freq=rng.uniform(280, 320), decay=0.2, ratios=m.HOLLOW, hardness=0.6, noise=0.3)
    click = m.relay(rng, size=1.3, bright=0.6)
    x = s.mix((whir, 0.6), (latch, 0.7, 0.28), (click, 0.6, 0.3))
    return fx.space(x, fx.room_ir("casing"), wet=0.2, tail=0.3)


@sound("machine/sequencer_done", level=-20)
def sequencer_done(rng, i):
    """蛋序列仪改完一枚：两声轻轻的提示音，培养舱放气。"""
    n = s.samples(0.9)
    k = s.samples(0.25)
    a = s.sine(1320, k) * s.perc(k, 0.003, 0.22)
    b = s.sine(1760, k) * s.perc(k, 0.003, 0.22)
    vent = s.resonant(s.white(n, rng), "bandpass", 3000, 0.8) * s.envelope([(0, 0), (0.3, 0), (0.35, 1), (0.9, 0)], n)
    x = s.mix((a, 0.3), (b, 0.3, 0.14), (vent, 0.08))
    return fx.space(x, fx.room_ir("factory"), wet=0.15, tail=0.4)
