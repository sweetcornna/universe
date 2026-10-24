"""按物理过程搭的声音模型（ADR-0029）。

结构参照 Sound Design Toolkit（SkAT-VG/SDT）的模型划分和 Andy Farnell《Designing Sound》的分析方法，
"卡通化"真实物理：只留下听得出来的部分。代码是自己写的。
"""

from __future__ import annotations

import numpy as np

import synth as s

# 圆形钢板、钢梁的模态频率比（相对基频）：非谐，听着是金属
PLATE = [1.0, 1.59, 2.14, 2.30, 2.65, 2.92, 3.16, 3.50, 3.60, 4.06, 4.15, 4.62]
BAR = [1.0, 2.76, 5.40, 8.93, 13.34]
HOLLOW = [1.0, 1.47, 2.09, 2.56, 2.71, 3.24, 3.87]


def electric_motor(n: int, rng: np.random.Generator, rpm, coils: int = 6, gear_teeth: int = 0, chassis: float = 0.6,
                   harshness: float = 0.2, rotor: float = 1.0, gears: float = 0.6, brushes: float = 0.4,
                   air: float = 0.3, load=0.0) -> np.ndarray:
    """直流电机：转子按每转 coils 次的槽谐波哼，齿轮按 gear_teeth 啮合，电刷在换向时出噪声，转动带起气流，机壳共鸣。

    rpm 可以随时间变（加速、减速就是伺服电机的声音）；load 是负载（0–1），负载大时转速往下沉、换向噪声变粗。
    """
    r = s.curve(rpm, n) * (1 - 0.06 * s.curve(load, n))
    turn = r / 60.0
    slot = turn * coils
    out = np.zeros(n)
    # 转子：槽频率和它的几次谐波，幅度随转速升
    speed = np.clip(r / max(np.max(r), 1), 0, 1)
    out += rotor * speed * s.harmonics(slot, n, [1.0, 0.5, 0.3, 0.18, 0.1, 0.06])
    # 转一圈的不平衡
    out += rotor * 0.25 * speed * s.sine(turn, n)
    # 齿轮：啮合频率加上按转速调出来的边带
    if gear_teeth:
        mesh = turn * gear_teeth
        gear = s.harmonics(mesh, n, [1.0, 0.35, 0.15]) * (1 + 0.4 * s.sine(turn, n))
        out += gears * speed * gear
    # 电刷：每次换向一下噪声
    commutation = s.impulses(n, np.maximum(slot, 1), jitter=0.05, rng=rng)
    spark = s.resonant(s.white(n, rng), "highpass", 2500, 0.7)
    brush_env = s.lowpass(commutation, 4000, 1) * 40
    out += brushes * speed * spark * np.clip(brush_env, 0, 1) * (1 + s.curve(load, n))
    # 气流：转速越快越亮
    wind = s.resonant(s.pink(n, rng), "lowpass", 300 + 2500 * speed, 0.7)
    out += air * speed ** 2 * wind
    if harshness:
        out = s.softclip(out * (1 + 3 * harshness), 1 + 2 * harshness)
    if chassis:
        size = 0.4 + chassis
        freqs = [f / size for f in (310, 480, 760, 1130, 1720, 2400)]
        resonated = s.modes(out, freqs, [0.15, 0.12, 0.1, 0.08, 0.06, 0.05], [1, 0.8, 0.6, 0.5, 0.35, 0.25])
        out = out + chassis * 0.15 * resonated / (np.max(np.abs(resonated)) + 1e-9) * np.max(np.abs(out))
    return out


def servo_moves(n: int, rng: np.random.Generator, moves, top_rpm: float = 9000, idle_rpm: float = 0.0) -> np.ndarray:
    """伺服电机的转速曲线：moves 是 [(开始秒, 持续秒, 力度 0–1), …]，每段快速升速、走一会儿、刹住。"""
    rpm = np.full(n, idle_rpm, dtype=float)
    t = s.times(n)
    for start, length, strength in moves:
        rise = min(0.06, length / 3)
        fall = min(0.08, length / 3)
        shape = np.interp(t, [start, start + rise, start + length - fall, start + length], [0, 1, 1, 0], left=0, right=0)
        target = top_rpm * strength * (1 + rng.uniform(-0.05, 0.05))
        rpm = np.maximum(rpm, idle_rpm + shape * (target - idle_rpm))
    return rpm


def relay(rng: np.random.Generator, size: float = 1.0, bright: float = 1.0) -> np.ndarray:
    """继电器、夹爪、电磁阀的"咔哒"：极短的激励敲一个小金属件。"""
    n = s.samples(0.08 * size + 0.04)
    hit = np.zeros(n)
    hit[0] = 1.0
    hit[s.samples(0.0015 * size)] = -0.6
    base = rng.uniform(1800, 2600) / size
    freqs = [base * k for k in PLATE[:6]]
    out = s.modes(hit, freqs, [0.03 * size] * 6, [1, 0.7, 0.5, 0.4, 0.3, 0.2])
    click = s.highpass(s.white(n, rng), 3000) * s.perc(n, 0.0002, 0.004)
    return out * 0.6 + click * bright


def impact(rng: np.random.Generator, freq: float = 400, decay: float = 0.6, ratios=PLATE, hardness: float = 0.5,
           size: float = 1.0, noise: float = 0.3) -> np.ndarray:
    """模态撞击：一个硬度决定长短的激励，敲一组按 ratios 排的模态。hardness 越大激励越短、越亮。"""
    contact = 0.0002 + 0.004 * (1 - hardness) * size
    n = s.samples(decay * 1.2 + 0.05)
    k = max(2, s.samples(contact))
    excite = np.zeros(n)
    excite[:k] = np.hanning(k * 2)[k:] if k > 1 else 1
    freqs = [freq * r * (1 + rng.uniform(-0.02, 0.02)) for r in ratios]
    decays = [decay / (1 + 0.6 * i) for i in range(len(ratios))]
    gains = [1 / (1 + 0.5 * i) * rng.uniform(0.6, 1.0) for i in range(len(ratios))]
    body = s.modes(excite, freqs, decays, gains)
    body /= np.max(np.abs(body)) + 1e-9
    burst = s.lowpass(s.white(n, rng), 1500 + 9000 * hardness) * s.perc(n, 0.0003, 0.02 + 0.05 * size)
    return body + noise * burst


def bubbles(n: int, rng: np.random.Generator, rate, radius=(0.002, 0.008), rise: float = 0.1) -> np.ndarray:
    """气泡：每个气泡一声往上滑的衰减正弦（Minnaert 频率 ≈ 3.26 / 半径 Hz，半径按米）。"""
    out = np.zeros(n)
    r = s.curve(rate, n)
    t = 0.0
    while True:
        local = r[min(int(t * s.SR), n - 1)]
        t += rng.exponential(1 / max(local, 1e-3))
        if t * s.SR >= n:
            break
        radius_m = np.exp(rng.uniform(np.log(radius[0]), np.log(radius[1])))
        f0 = 3.26 / radius_m
        if f0 > s.NYQUIST * 0.8:
            continue
        damping = 0.13 + 0.0072 * np.sqrt(f0)
        tau = 1 / (np.pi * f0 * damping * 0.08)
        length = s.samples(min(tau * 6, 0.25))
        tt = s.times(length)
        f = f0 * (1 + rise * tt / max(tau, 1e-3))
        grain = np.sin(2 * np.pi * np.cumsum(f) / s.SR) * np.exp(-tt / tau) * np.sqrt(radius_m / radius[1])
        start = int(t * s.SR)
        end = min(n, start + length)
        out[start:end] += grain[:end - start]
    return out


def wind(n: int, rng: np.random.Generator, speed, gust: float = 0.3) -> np.ndarray:
    """吹过物体的湍流：粉红噪声，风越大越响越亮，带阵风起伏。speed 是 0–1 的相对风速。"""
    v = np.clip(s.curve(speed, n) * (1 + gust * s.wander(n, rng, 0.7)), 0, 1.5)
    noise = s.pink(n, rng)
    body = s.resonant(noise, "lowpass", 150 + 2600 * v ** 1.5, 0.6)
    edge = s.resonant(noise, "bandpass", 600 + 2400 * v, 1.5) * 0.3
    return (body + edge) * v ** 2


def karman(n: int, rng: np.random.Generator, speed_mps, diameter_m: float = 0.02, q: float = 25) -> np.ndarray:
    """细杆、天线后面的卡门涡街哨声：f = 0.2 v / d，一条窄带噪声。"""
    v = s.curve(speed_mps, n)
    f = np.clip(0.2 * v / diameter_m * (1 + 0.03 * s.wander(n, rng, 3)), 40, 9000)
    tone = s.resonant(s.white(n, rng), "bandpass", f, q)
    return tone * np.clip(v / (np.max(v) + 1e-9), 0, 1)


def arc(n: int, rng: np.random.Generator, intensity=1.0, mains: float = 50.0) -> np.ndarray:
    """电弧：工频两倍（交流每半周燃一次）调制的宽带噼啪，加上变压器的嗡声。"""
    level = s.curve(intensity, n)
    hum_f = mains * 2
    hum = s.harmonics(hum_f, n, [1.0, 0.6, 0.45, 0.3, 0.22, 0.16, 0.12, 0.08])
    # 每半周燃起来，起燃那一下最响
    cycle = 0.5 + 0.5 * s.sine(hum_f, n)
    burst = s.crackle(n, rng, 1500 * level, width=0.00015) * (0.4 + 0.6 * cycle ** 3)
    burst = s.resonant(burst, "highpass", 900, 0.7)
    roar = s.resonant(s.pink(n, rng), "bandpass", 2200, 0.6) * (0.3 + 0.7 * cycle ** 2)
    return hum * 0.35 * level + burst * level + roar * 0.25 * level


def transformer(n: int, rng: np.random.Generator, mains: float = 50.0, load: float = 0.5) -> np.ndarray:
    """变压器的磁致伸缩嗡声：基频是工频的两倍，负载大时高次谐波多一些。"""
    f = mains * 2 * (1 + 0.001 * s.wander(n, rng, 0.2))
    amps = [1.0, 0.5 + 0.3 * load, 0.25 + 0.3 * load, 0.12 + 0.2 * load, 0.06 + 0.12 * load]
    return s.harmonics(f, n, amps)


def fan(n: int, rng: np.random.Generator, rpm, blades: int = 7, size: float = 1.0) -> np.ndarray:
    """风扇：叶片通过频率的音调加上一层宽带气流。"""
    r = s.curve(rpm, n)
    bpf = r / 60 * blades
    tone = s.harmonics(bpf, n, [1.0, 0.3, 0.12]) * 0.3
    hiss = s.resonant(s.pink(n, rng), "lowpass", 800 + 3000 / size, 0.5)
    return tone + hiss


def turbine(n: int, rng: np.random.Generator, rpm, blades: int = 24, roar: float = 1.0, whine: float = 0.5) -> np.ndarray:
    """燃气或蒸汽涡轮：高频的叶片通过音调，加上燃烧或蒸汽的低频轰鸣。"""
    r = s.curve(rpm, n)
    bpf = r / 60 * blades
    tone = s.harmonics(bpf * (1 + 0.002 * s.wander(n, rng, 2)), n, [1.0, 0.4, 0.2, 0.1])
    body = s.resonant(s.brown(n, rng), "lowpass", 400, 0.6)
    mid = s.resonant(s.pink(n, rng), "bandpass", 900, 0.5)
    return roar * (body + 0.35 * mid) + whine * 0.25 * tone


def rocket(n: int, rng: np.random.Generator, thrust=1.0, crackle: float = 0.5, distance: float = 0.0) -> np.ndarray:
    """火箭发动机：低频很重的宽带轰鸣，喷流里的激波带来不对称的"噼啪"；远了高频先没。"""
    th = s.curve(thrust, n)
    base = s.brown(n, rng) * 1.2 + s.pink(n, rng) * 0.5
    rough = s.asym(base * (1 + 2 * crackle), 0.6 * crackle)
    pops = s.crackle(n, rng, 60 * crackle * th, width=0.0006, skew=0.6) * 0.8
    out = (rough + pops) * th
    cutoff = 9000 / (1 + distance * 4)
    return s.lowpass(out, cutoff, 2)


def explosion(rng: np.random.Generator, size: float = 1.0, distance: float = 0.0, debris: float = 0.5,
              tail: float = 2.5) -> np.ndarray:
    """爆炸：冲击波（Friedlander 波形）带出的低频闷响，近处有碎片噼啪，后面是长长的低频回荡。"""
    n = s.samples(tail * (0.6 + 0.4 * size) + 0.5)
    t = s.times(n)
    positive = 0.01 + 0.03 * size
    friedlander = (1 - t / positive) * np.exp(-1.8 * t / positive)
    friedlander[t > positive * 5] = 0
    boom = s.lowpass(friedlander + 0.3 * s.brown(n, rng) * np.exp(-t / (0.08 + 0.1 * size)), 180 + 300 / size, 2)
    body = s.resonant(s.pink(n, rng), "lowpass", 2500 / (1 + distance), 0.6) * np.exp(-t / (0.15 + 0.2 * size))
    rumble = s.lowpass(s.brown(n, rng), 160, 2) * np.exp(-t / (0.5 + 0.9 * size)) * np.clip(t / 0.05, 0, 1)
    bits = s.crackle(n, rng, [(0, 900 * debris), (0.25, 120 * debris), (0.8, 10 * debris), (tail, 0)], width=0.0003)
    bits = s.highpass(bits, 1200) * np.exp(-t / 0.4)
    out = 1.2 * boom / (np.max(np.abs(boom)) + 1e-9) + 0.7 * body + 0.8 * rumble + 0.35 * bits / (1 + distance * 3)
    return s.taper(s.softclip(out, 1.4), tail * 0.4)


def zap(rng: np.random.Generator, start: float = 3000, end: float = 300, length: float = 0.18, bite: float = 0.5,
        fm_index: float = 2.0) -> np.ndarray:
    """放电的"啪咻"：往下扫的调频音，前面一下宽带的爆裂，尾巴带一点电噼啪。"""
    n = s.samples(length + 0.05)
    t = s.times(n)
    f = s.exp_curve([(0, start), (length, end), (length + 0.05, end * 0.9)], n)
    body = s.fm(f, 1.41, fm_index * np.exp(-t / (length * 0.5)), n) * s.perc(n, 0.001, length)
    snap = s.highpass(s.white(n, rng), 1500) * s.perc(n, 0.0003, 0.015) * bite
    sizzle = s.crackle(n, rng, [(0, 2000), (length, 200)], width=0.0001) * s.perc(n, 0.002, length * 1.2) * 0.4
    return body + snap + s.highpass(sizzle, 2000)


def coil_stages(rng: np.random.Generator, stages: int = 5, spacing: float = 0.018, accel: float = 0.8,
                pitch: float = 220) -> np.ndarray:
    """电磁线圈一级级放电（线圈炮、电磁弹射）：每级一声"嗵"加一下电噼啪，越往后越快、越高。"""
    parts = []
    at = 0.0
    gap = spacing
    for k in range(stages):
        n = s.samples(0.12)
        t = s.times(n)
        f = pitch * (1 + 0.25 * k)
        thump = np.sin(2 * np.pi * f * t * (1 - 0.4 * t / 0.12)) * np.exp(-t / 0.03)
        crack = s.highpass(s.white(n, rng), 3000) * np.exp(-t / 0.004) * 0.6
        parts.append((thump + crack, 0.6 + 0.4 * k / max(1, stages - 1), at))
        at += gap
        gap *= accel
    return s.mix(*parts)


def n_wave(rng: np.random.Generator, length: float = 0.004, distance: float = 0.0) -> np.ndarray:
    """超音速弹丸的激波"啪"：一个 N 形的压力脉冲，远了被空气磨圆。"""
    k = max(4, s.samples(length))
    wave = np.linspace(1, -1, k)
    n = s.samples(0.2)
    out = np.zeros(n)
    out[:k] = wave
    if distance > 0:
        out = s.lowpass(out, 12000 / (1 + distance * 3), 1)
    return out


def computer(n: int, rng: np.random.Generator, rate: float = 9, base: float = 900, spread: float = 2.5,
             fans: float = 0.4, clicks: float = 0.3) -> np.ndarray:
    """计算机的嘀嘀声：采样保持的短音高，夹着磁头和继电器的咔哒，底下一层风扇。"""
    out = np.zeros(n)
    t = 0.0
    while t * s.SR < n:
        length = rng.uniform(0.03, 0.12)
        m = s.samples(length)
        f = base * spread ** rng.uniform(-1, 1)
        beep = s.sine(f, m) * s.adsr(m, 0.002, 0.01, 0.7, 0.01)
        if rng.random() < 0.3:
            beep = beep * 0.6 + 0.4 * s.square(f, m, 8) * s.adsr(m, 0.002, 0.01, 0.6, 0.01)
        i = int(t * s.SR)
        end = min(n, i + m)
        out[i:end] += beep[:end - i] * rng.uniform(0.3, 1.0)
        t += rng.exponential(1 / rate) + length
    tick = s.crackle(n, rng, 25 * clicks, width=0.00008)
    tick = s.resonant(tick, "bandpass", 3500, 2)
    hum = fan(n, rng, 2400, 7, 0.6) * fans
    return out * 0.5 + tick * 0.8 + hum * 0.3


def hiss(n: int, rng: np.random.Generator, pressure=1.0, brightness: float = 0.6) -> np.ndarray:
    """泄压、蒸汽：高通的白噪声，压力越大越亮。"""
    p = s.curve(pressure, n)
    noise = s.white(n, rng)
    return s.resonant(noise, "highpass", 1200 + 3000 * brightness * p, 0.6) * p


def pump(n: int, rng: np.random.Generator, strokes_per_second: float = 1.5, liquid: float = 0.5) -> np.ndarray:
    """往复泵：每个冲程一声低沉的"嗵"和阀门的"咔"，管子里的液体咕噜。"""
    out = np.zeros(n)
    period = 1 / strokes_per_second
    t = 0.0
    while t * s.SR < n:
        thump = impact(rng, freq=rng.uniform(70, 90), decay=0.25, ratios=HOLLOW, hardness=0.2, noise=0.1)
        valve = relay(rng, size=1.4, bright=0.4) * 0.35
        i = int(t * s.SR)
        for sig, gain, offset in ((thump, 0.9, 0.0), (valve, 1.0, period * 0.45)):
            j = i + s.samples(offset)
            if j < n:
                e = min(n, j + len(sig))
                out[j:e] += gain * sig[:e - j]
        t += period * (1 + rng.uniform(-0.01, 0.01))
    gurgle = bubbles(n, rng, 30 * liquid, (0.004, 0.015), 0.2) * 0.3
    return out + gurgle
