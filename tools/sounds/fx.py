"""效果和空间感（ADR-0029）：pedalboard 的效果器，pyroomacoustics 按真实尺寸算的房间冲激响应。

两个库只在生成时由 uv 临时装（见 build.py 的用法），不进模组。冲激响应算一次缓存在
build/sound-cache 下。
"""

from __future__ import annotations

import hashlib
import os
from pathlib import Path

import numpy as np
from scipy import signal

import synth as s

CACHE = Path(__file__).resolve().parents[2] / "build" / "sound-cache"

# 房间：尺寸（米）、墙面的能量吸收系数、散射系数、声源和听者的位置
ROOMS = {
    # 工厂车间：混凝土地面、钢结构屋顶
    "factory": dict(size=(36, 24, 10), absorption=0.12, scattering=0.3, source=(14, 10, 1.5), mic=(22, 13, 1.7)),
    # 飞船驾驶舱：面板和舷窗，很小
    "cockpit": dict(size=(3.2, 2.6, 2.0), absorption=0.25, scattering=0.2, source=(1.0, 1.2, 0.6), mic=(2.0, 1.3, 1.2)),
    # 货船机库：大钢壳
    "hangar": dict(size=(60, 36, 18), absorption=0.06, scattering=0.25, source=(24, 15, 2), mic=(36, 20, 1.7)),
    # 空间站的走廊
    "corridor": dict(size=(20, 4, 3.5), absorption=0.15, scattering=0.2, source=(4, 2, 1.5), mic=(12, 2.2, 1.6)),
    # 山洞：粗糙的岩石
    "cave": dict(size=(24, 16, 9), absorption=0.05, scattering=0.6, source=(8, 6, 1.5), mic=(15, 9, 1.7)),
    # 机器的外壳内部：金属箱子，给电机加机壳共鸣
    "casing": dict(size=(1.4, 1.1, 0.9), absorption=0.08, scattering=0.3, source=(0.4, 0.3, 0.3), mic=(1.0, 0.7, 0.6)),
}


def room_ir(name: str) -> np.ndarray:
    spec = ROOMS[name]
    key = hashlib.sha1(repr(sorted(spec.items())).encode()).hexdigest()[:10]
    path = CACHE / f"ir_{name}_{key}.npy"
    if path.exists():
        return np.load(path)
    import pyroomacoustics as pra

    material = pra.Material(energy_absorption=spec["absorption"], scattering=spec["scattering"])
    room = pra.ShoeBox(spec["size"], fs=s.SR, materials=material, max_order=3, air_absorption=True, ray_tracing=False,
                       use_rand_ism=False)
    room.set_ray_tracing(receiver_radius=0.5, n_rays=20000, energy_thres=1e-6)
    room.add_source(list(spec["source"]))
    room.add_microphone(list(spec["mic"]))
    room.compute_rir()
    ir = np.asarray(room.rir[0][0], dtype=float)
    # 去掉直达声之前的空白，归一到直达声
    start = max(0, int(np.argmax(np.abs(ir) > np.max(np.abs(ir)) * 0.05)) - 8)
    ir = ir[start:]
    ir /= np.max(np.abs(ir))
    CACHE.mkdir(parents=True, exist_ok=True)
    # 先写临时文件再改名：几个模块同时生成时不会读到写了一半的缓存
    tmp = path.with_suffix(f".{os.getpid()}.tmp.npy")
    np.save(tmp, ir)
    os.replace(tmp, path)
    return ir


def outdoor_ir(rng: np.random.Generator, size: float = 1.0) -> np.ndarray:
    """空旷的地面：一次地面反射，几处远处地形的回声，一层只剩低频的漫射尾巴。size 越大回声越远、尾巴越长。"""
    length = s.samples(1.2 + 2.0 * size)
    ir = np.zeros(length)
    ir[0] = 1.0
    ground = s.samples(0.004)
    ir[ground] = 0.55
    for _ in range(int(4 + 4 * size)):
        at = s.samples(rng.uniform(0.15, 0.9) * (0.6 + size))
        if at < length:
            ir[at] += rng.uniform(0.05, 0.2) / (1 + size * 0.5)
    t = s.times(length)
    tail = s.lowpass(rng.standard_normal(length), 900, 2) * np.exp(-6.9 * t / (0.8 + 1.6 * size)) * 0.08
    ir += tail * np.clip(t / 0.05, 0, 1)
    return ir / np.max(np.abs(ir))


def space(x: np.ndarray, room: str | np.ndarray, wet: float = 0.25, dry: float = 1.0, tail: float = 0.0,
          predelay: float = 0.0) -> np.ndarray:
    """卷积混响。room 是 ROOMS 的名字或者一段冲激响应；tail 秒加在后面让尾巴响完。"""
    ir = room_ir(room) if isinstance(room, str) else room
    padded = s.pad(x, tail) if tail > 0 else x
    wet_sig = signal.fftconvolve(padded, ir)[:len(padded)]
    # 冲激响应的直达声已经在里面，湿声去掉开头几毫秒，不和干声叠出梳状
    cut = s.samples(0.003)
    wet_sig = np.concatenate([np.zeros(cut), wet_sig[cut:]])
    if predelay > 0:
        wet_sig = np.concatenate([np.zeros(s.samples(predelay)), wet_sig])[:len(padded)]
    scale = np.sqrt(np.mean(padded ** 2) + 1e-20) / (np.sqrt(np.mean(wet_sig ** 2)) + 1e-20)
    out = dry * padded + wet * wet_sig * scale
    return s.taper(out, min(tail, 0.3)) if tail > 0 else out


# ---- pedalboard 的效果器

def _board(x: np.ndarray, *plugins) -> np.ndarray:
    from pedalboard import Pedalboard

    out = Pedalboard(list(plugins))(x.astype(np.float32), s.SR)
    return np.asarray(out, dtype=float).reshape(-1)[:len(x)] if out.ndim == 1 else np.asarray(out[0], dtype=float)


def reverb(x: np.ndarray, room_size: float = 0.5, damping: float = 0.5, wet: float = 0.3, tail: float = 0.0) -> np.ndarray:
    from pedalboard import Reverb

    padded = s.pad(x, tail) if tail > 0 else x
    return _board(padded, Reverb(room_size=room_size, damping=damping, wet_level=wet, dry_level=1.0, width=0.0))


def compress(x: np.ndarray, threshold_db: float = -18, ratio: float = 3, attack_ms: float = 5,
             release_ms: float = 120) -> np.ndarray:
    from pedalboard import Compressor

    return _board(x, Compressor(threshold_db=threshold_db, ratio=ratio, attack_ms=attack_ms, release_ms=release_ms))


def limit(x: np.ndarray, threshold_db: float = -1.0, release_ms: float = 80) -> np.ndarray:
    from pedalboard import Limiter

    return _board(x, Limiter(threshold_db=threshold_db, release_ms=release_ms))


def chorus(x: np.ndarray, rate: float = 0.8, depth: float = 0.25, mix: float = 0.4, delay_ms: float = 7) -> np.ndarray:
    from pedalboard import Chorus

    return _board(x, Chorus(rate_hz=rate, depth=depth, centre_delay_ms=delay_ms, feedback=0.0, mix=mix))


def phaser(x: np.ndarray, rate: float = 0.5, depth: float = 0.6, feedback: float = 0.3, mix: float = 0.5) -> np.ndarray:
    from pedalboard import Phaser

    return _board(x, Phaser(rate_hz=rate, depth=depth, centre_frequency_hz=1200, feedback=feedback, mix=mix))


def distort(x: np.ndarray, drive_db: float = 12) -> np.ndarray:
    from pedalboard import Distortion

    return _board(x, Distortion(drive_db=drive_db))


def ladder(x: np.ndarray, cutoff: float, resonance: float = 0.2, drive: float = 1.0, mode: str = "LPF24") -> np.ndarray:
    """Moog 式的梯形滤波器（参数固定；要随时间扫就用 synth.resonant）。"""
    from pedalboard import LadderFilter

    m = getattr(LadderFilter.Mode, mode)
    return _board(x, LadderFilter(mode=m, cutoff_hz=cutoff, resonance=resonance, drive=drive))
