"""合成的基础件：振荡器、噪声、包络、滤波器、共振、循环（ADR-0029）。

全部是 numpy 数组进、numpy 数组出，单声道，采样率 SR。参数里的"曲线"可以是一个数、
一个和输出等长的数组，或者一组 (秒, 值) 的折点；随时间变的音高、截止频率都这样给。
"""

from __future__ import annotations

import numpy as np
from scipy import signal

SR = 44100
NYQUIST = SR / 2


# ---- 时间和曲线

def samples(seconds: float) -> int:
    return max(1, int(round(seconds * SR)))


def times(n: int) -> np.ndarray:
    return np.arange(n) / SR


def curve(value, n: int) -> np.ndarray:
    """把一个数、数组或 [(秒, 值), …] 折点变成长度 n 的数组；数组长度不对时按比例拉伸。"""
    if isinstance(value, (list, tuple)) and value and isinstance(value[0], (list, tuple)):
        ts = np.array([p[0] for p in value], dtype=float)
        vs = np.array([p[1] for p in value], dtype=float)
        return np.interp(times(n), ts, vs)
    a = np.asarray(value, dtype=float)
    if a.ndim == 0:
        return np.full(n, float(a))
    if len(a) == n:
        return a
    return np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(a)), a)


def exp_curve(points, n: int) -> np.ndarray:
    """折点之间按对数插值：音高、截止频率这样变听着才均匀。"""
    ts = np.array([p[0] for p in points], dtype=float)
    vs = np.log(np.maximum(np.array([p[1] for p in points], dtype=float), 1e-9))
    return np.exp(np.interp(times(n), ts, vs))


def wander(n: int, rng: np.random.Generator, rate: float, depth: float = 1.0) -> np.ndarray:
    """平滑的随机游走，大约每秒 rate 次拐弯，幅度在 ±depth 附近。"""
    points = max(4, int(n / SR * rate) + 4)
    knots = rng.uniform(-1, 1, points)
    # 右端留一点余量：i + 3 不能等于 points
    x = np.linspace(0, points - 3 - 1e-9, n)
    i = np.floor(x).astype(int)
    f = x - i
    p0, p1, p2, p3 = knots[i], knots[i + 1], knots[i + 2], knots[i + 3]
    # Catmull-Rom
    out = 0.5 * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f ** 2 + (-p0 + 3 * p1 - 3 * p2 + p3) * f ** 3)
    return out * depth


def steps(n: int, rng: np.random.Generator, rate: float, low: float, high: float, glide: float = 0.0) -> np.ndarray:
    """采样保持：每秒 rate 次跳到 [low, high] 里的一个新值；glide 秒内滑过去。"""
    hold = max(1, int(SR / rate))
    count = n // hold + 2
    values = rng.uniform(low, high, count)
    out = np.repeat(values, hold)[:n]
    if glide > 0:
        out = one_pole(out, 1 / glide)
    return out


# ---- 振荡器

def phase(freq, n: int, start: float = 0.0) -> np.ndarray:
    return start + 2 * np.pi * np.cumsum(curve(freq, n)) / SR


def sine(freq, n: int, start: float = 0.0) -> np.ndarray:
    return np.sin(phase(freq, n, start))


def _additive(freq, n: int, partial, max_partials: int, start: float) -> np.ndarray:
    f = curve(freq, n)
    ph = phase(f, n, start)
    out = np.zeros(n)
    for k in range(1, max_partials + 1):
        amp = partial(k)
        if amp == 0:
            continue
        # 接近奈奎斯特时淡掉，不混叠
        fade = np.clip((NYQUIST * 0.9 - k * f) / (NYQUIST * 0.1), 0, 1)
        if not fade.any():
            break
        out += amp * fade * np.sin(k * ph)
    return out


def saw(freq, n: int, partials: int = 64, start: float = 0.0) -> np.ndarray:
    return _additive(freq, n, lambda k: 2 / np.pi * (-1) ** (k + 1) / k, partials, start)


def square(freq, n: int, partials: int = 64, start: float = 0.0) -> np.ndarray:
    return _additive(freq, n, lambda k: 4 / np.pi / k if k % 2 else 0, partials, start)


def triangle(freq, n: int, partials: int = 32, start: float = 0.0) -> np.ndarray:
    return _additive(freq, n, lambda k: 8 / np.pi ** 2 * (-1) ** ((k - 1) // 2) / k ** 2 if k % 2 else 0, partials, start)


def harmonics(freq, n: int, amps, start: float = 0.0, stretch: float = 0.0) -> np.ndarray:
    """指定各次谐波的幅度；stretch > 0 时高次谐波略微拉开（钢琴、钢板那样的非谐性）。"""
    f = curve(freq, n)
    out = np.zeros(n)
    for k, amp in enumerate(amps, start=1):
        if amp == 0:
            continue
        ratio = k * np.sqrt(1 + stretch * k * k)
        fk = f * ratio
        fade = np.clip((NYQUIST * 0.9 - fk) / (NYQUIST * 0.1), 0, 1)
        out += amp * fade * np.sin(phase(fk, n, start * k))
    return out


def fm(carrier, ratio, index, n: int, feedback: float = 0.0) -> np.ndarray:
    """两算子调频：载波 carrier Hz，调制器在 carrier×ratio，调制指数 index（都可以随时间变）。"""
    c = curve(carrier, n)
    mod = np.sin(phase(c * curve(ratio, n), n))
    if feedback:
        mod = np.sin(phase(c * curve(ratio, n), n) + feedback * mod)
    return np.sin(phase(c, n) + curve(index, n) * mod)


# ---- 噪声

def white(n: int, rng: np.random.Generator) -> np.ndarray:
    return rng.standard_normal(n) * 0.5


def _shaped(n: int, rng: np.random.Generator, slope_db_per_octave: float) -> np.ndarray:
    spectrum = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1]
    spectrum *= (f / 1000.0) ** (slope_db_per_octave / 6.0206)
    out = np.fft.irfft(spectrum, n)
    return out / (np.std(out) + 1e-12) * 0.5


def pink(n: int, rng: np.random.Generator) -> np.ndarray:
    return _shaped(n, rng, -3)


def brown(n: int, rng: np.random.Generator) -> np.ndarray:
    return highpass(_shaped(n, rng, -6), 15)


def colored(n: int, rng: np.random.Generator, slope_db_per_octave: float) -> np.ndarray:
    return _shaped(n, rng, slope_db_per_octave)


def crackle(n: int, rng: np.random.Generator, rate, width: float = 0.0004, skew: float = 0.0) -> np.ndarray:
    """泊松分布的尖脉冲：火花、电弧、碎屑。rate 是每秒多少个（可以随时间变）。"""
    r = curve(rate, n)
    hits = rng.random(n) < r / SR
    out = np.zeros(n)
    idx = np.nonzero(hits)[0]
    out[idx] = rng.uniform(0.3, 1.0, len(idx)) * rng.choice([-1, 1], len(idx), p=[0.5 - skew / 2, 0.5 + skew / 2])
    w = max(1, int(width * SR))
    if w > 1:
        out = np.convolve(out, np.hanning(w * 2 + 1), mode="same")
    return out


def impulses(n: int, rate, jitter: float = 0.0, rng: np.random.Generator | None = None, start: float = 0.0) -> np.ndarray:
    """每秒 rate 个单位脉冲的脉冲串（rate 可以随时间变），jitter 是每个间隔的随机偏移比例。"""
    r = curve(rate, n)
    out = np.zeros(n)
    t = start * SR
    while t < n:
        i = int(t)
        out[i] = 1.0
        step = SR / max(r[i], 1e-3)
        if jitter and rng is not None:
            step *= 1 + rng.uniform(-jitter, jitter)
        t += step
    return out


# ---- 包络

def perc(n: int, attack: float, decay: float, curve_power: float = 1.0) -> np.ndarray:
    """打击包络：attack 秒线性起，之后按 decay（衰减 60 dB 的秒数）指数落。"""
    t = times(n)
    a = max(attack, 1e-4)
    env = np.where(t < a, (t / a) ** curve_power, np.exp(-6.9078 * (t - a) / max(decay, 1e-4)))
    return env


def adsr(n: int, attack: float, decay: float, sustain: float, release: float) -> np.ndarray:
    t = times(n)
    total = n / SR
    rel_start = max(attack + decay, total - release)
    env = np.interp(t, [0, attack, attack + decay, rel_start, total], [0, 1, sustain, sustain, 0])
    return env


def envelope(points, n: int) -> np.ndarray:
    return curve(points, n)


def taper(x: np.ndarray, seconds: float) -> np.ndarray:
    """末尾 seconds 秒按余弦收到 0：长尾巴的声音在数组结束处不会突然断掉（断掉就是一声咔）。"""
    x = x.copy()
    k = min(len(x), samples(seconds))
    x[-k:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, k))
    return x


def fade(x: np.ndarray, fade_in: float = 0.002, fade_out: float = 0.01) -> np.ndarray:
    x = x.copy()
    a = min(len(x), samples(fade_in))
    b = min(len(x), samples(fade_out))
    if a > 1:
        x[:a] *= np.linspace(0, 1, a) ** 2
    if b > 1:
        x[-b:] *= np.linspace(1, 0, b) ** 2
    return x


# ---- 滤波器

def _sos(kind: str, cutoff, order: int):
    cutoff = np.clip(np.asarray(cutoff, dtype=float), 5, NYQUIST * 0.98)
    return signal.butter(order, cutoff, btype=kind, fs=SR, output="sos")


def lowpass(x: np.ndarray, cutoff: float, order: int = 2) -> np.ndarray:
    return signal.sosfilt(_sos("lowpass", cutoff, order), x)


def highpass(x: np.ndarray, cutoff: float, order: int = 2) -> np.ndarray:
    return signal.sosfilt(_sos("highpass", cutoff, order), x)


def bandpass(x: np.ndarray, low: float, high: float, order: int = 2) -> np.ndarray:
    return signal.sosfilt(_sos("bandpass", [low, high], order), x)


def bandstop(x: np.ndarray, low: float, high: float, order: int = 2) -> np.ndarray:
    return signal.sosfilt(_sos("bandstop", [low, high], order), x)


def one_pole(x: np.ndarray, cutoff: float) -> np.ndarray:
    """一阶低通，常用来平滑控制曲线。"""
    a = np.exp(-2 * np.pi * min(cutoff, NYQUIST) / SR)
    return signal.lfilter([1 - a], [1, -a], x)


def _biquad(kind: str, f: float, q: float, gain_db: float = 0.0):
    """RBJ 音频食谱里的双二阶系数。"""
    f = min(max(f, 5.0), NYQUIST * 0.98)
    w = 2 * np.pi * f / SR
    cw, sw = np.cos(w), np.sin(w)
    alpha = sw / (2 * q)
    amp = 10 ** (gain_db / 40)
    if kind == "lowpass":
        b = [(1 - cw) / 2, 1 - cw, (1 - cw) / 2]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "highpass":
        b = [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "bandpass":
        b = [alpha, 0, -alpha]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "notch":
        b = [1, -2 * cw, 1]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "peak":
        b = [1 + alpha * amp, -2 * cw, 1 - alpha * amp]
        a = [1 + alpha / amp, -2 * cw, 1 - alpha / amp]
    else:
        raise ValueError(kind)
    b = np.array(b) / a[0]
    a = np.array(a) / a[0]
    return b, a


def resonant(x: np.ndarray, kind: str, freq, q=0.707, gain_db: float = 0.0, block: int = 64) -> np.ndarray:
    """可以随时间变的双二阶滤波器（lowpass/highpass/bandpass/notch/peak），每 block 个采样换一次系数。"""
    n = len(x)
    f = curve(freq, n)
    qs = curve(q, n)
    if np.ptp(f) == 0 and np.ptp(qs) == 0:
        b, a = _biquad(kind, f[0], qs[0], gain_db)
        return signal.lfilter(b, a, x)
    out = np.empty(n)
    zi = np.zeros(2)
    for s in range(0, n, block):
        e = min(n, s + block)
        b, a = _biquad(kind, f[s], qs[s], gain_db)
        out[s:e], zi = signal.lfilter(b, a, x[s:e], zi=zi)
    return out


def comb(x: np.ndarray, delay: float, feedback: float, damping: float = 0.0) -> np.ndarray:
    """反馈梳状滤波：管子、机壳一类的共鸣；damping 让回授里的高频衰减得快一些。"""
    d = max(1, int(delay * SR))
    if damping <= 0:
        a = np.zeros(d + 1)
        a[0] = 1
        a[d] = -feedback
        return signal.lfilter([1], a, x)
    out = np.zeros(len(x) + d)
    lp = 0.0
    buf = np.concatenate([np.zeros(d), x])
    for i in range(d, len(buf)):
        lp = (1 - damping) * out[i - d] + damping * lp
        out[i] = buf[i] + feedback * lp
    return out[d:]


def modes(x: np.ndarray, freqs, decays, gains=None, tail: float = 0.0) -> np.ndarray:
    """模态共振：每个模态是一个按 decay 秒衰减 60 dB 的正弦，用激励去卷它。tail 秒加在输出后面。"""
    longest = max(decays)
    m = samples(min(longest * 1.3, 8.0))
    t = times(m)
    ir = np.zeros(m)
    gains = gains if gains is not None else [1.0] * len(freqs)
    for f, d, g in zip(freqs, decays, gains):
        if f >= NYQUIST * 0.95:
            continue
        ir += g * np.exp(-6.9078 * t / d) * np.sin(2 * np.pi * f * t)
    # 冲激响应的末尾收到 0，截断处不留台阶
    ir = taper(ir, m / SR * 0.2)
    padded = np.concatenate([x, np.zeros(samples(tail))]) if tail > 0 else x
    return signal.fftconvolve(padded, ir)[:len(padded)]


def delay(x: np.ndarray, seconds: float, feedback: float = 0.3, mix: float = 0.3, lowpass_hz: float = 0.0,
          tail: float = 0.0) -> np.ndarray:
    d = max(1, int(seconds * SR))
    padded = np.concatenate([x, np.zeros(samples(tail))]) if tail > 0 else x.copy()
    wet = np.zeros_like(padded)
    tap = padded.copy()
    gain = 1.0
    for _ in range(64):
        gain *= feedback if _ else 1.0
        if gain < 1e-3:
            break
        tap = np.concatenate([np.zeros(d), tap[:-d]])
        if lowpass_hz:
            tap = lowpass(tap, lowpass_hz, 1)
        wet += tap * gain
    return padded + mix * wet


# ---- 失真和动态

def softclip(x: np.ndarray, drive: float = 1.0) -> np.ndarray:
    return np.tanh(x * drive) / np.tanh(drive)


def asym(x: np.ndarray, amount: float = 0.3) -> np.ndarray:
    """不对称的削波：火箭和喷流那种"噼啪"的粗糙感（正半周压得比负半周狠）。"""
    y = np.where(x > 0, np.tanh(x * (1 + amount)) / (1 + amount), np.tanh(x))
    return y - np.mean(y)


def ring(x: np.ndarray, freq, n: int | None = None) -> np.ndarray:
    return x * sine(freq, len(x))


def tremolo(x: np.ndarray, rate, depth: float) -> np.ndarray:
    return x * (1 - depth / 2 + depth / 2 * sine(rate, len(x)))


# ---- 拼接

def mix(*parts) -> np.ndarray:
    """把几段（可以是 (信号, 增益) 或 (信号, 增益, 起点秒)）叠到一起，长度取最长的。"""
    items = []
    for p in parts:
        if isinstance(p, np.ndarray):
            items.append((p, 1.0, 0.0))
        elif len(p) == 2:
            items.append((p[0], p[1], 0.0))
        else:
            items.append(p)
    n = max(int(at * SR) + len(sig) for sig, _, at in items)
    out = np.zeros(n)
    for sig, gain, at in items:
        s = int(at * SR)
        out[s:s + len(sig)] += gain * sig
    return out


def pad(x: np.ndarray, seconds: float) -> np.ndarray:
    return np.concatenate([x, np.zeros(samples(seconds))])


def fit(x: np.ndarray, n: int) -> np.ndarray:
    return x[:n] if len(x) >= n else np.concatenate([x, np.zeros(n - len(x))])


def loop(x: np.ndarray, length: int, crossfade: float = 0.4, power: bool = True) -> np.ndarray:
    """无缝循环：x 至少要比 length 多出 crossfade 秒，尾巴叠回开头。

    power=True 用等功率交叉淡化（噪声类，两段不相关）；有稳定音高的用 False（等幅）。
    """
    xf = samples(crossfade)
    if len(x) < length + xf:
        raise ValueError(f"loop needs {length + xf} samples, got {len(x)}")
    out = x[:length].copy()
    w = np.linspace(0, 1, xf)
    if power:
        a, b = np.sin(w * np.pi / 2), np.cos(w * np.pi / 2)
    else:
        a, b = w, 1 - w
    out[:xf] = x[:xf] * a + x[length:length + xf] * b
    return out


# ---- 电平

def db(value: float) -> float:
    return 10 ** (value / 20)


def rms_db(x: np.ndarray, gate_db: float = -40.0) -> float:
    """门限 RMS：只算比最响的 50 ms 段小不到 gate_db 的部分，尾巴和静音不拉低读数。"""
    block = samples(0.05)
    if len(x) < block:
        return 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)
    blocks = len(x) // block
    power = np.mean(x[:blocks * block].reshape(blocks, block) ** 2, axis=1)
    loudest = power.max()
    keep = power[power > loudest * 10 ** (gate_db / 10)]
    return 10 * np.log10(np.mean(keep) + 1e-20)


def peak_db(x: np.ndarray) -> float:
    return 20 * np.log10(np.max(np.abs(x)) + 1e-12)
