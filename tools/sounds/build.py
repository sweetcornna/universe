"""把配方渲染成模组的音效文件（ADR-0029）。

用法（在仓库根目录）：

    uv run --no-project --with numpy --with scipy --with pedalboard --with pyroomacoustics --with pillow \
        python tools/sounds/build.py machines [--only machine/assembler ...] [--list] [--no-write]

- 第一个参数是配方模块（tools/sounds/<模块>.py），可以写 all。
- 每个声音的每个变体写成 src/main/resources/assets/universe/sounds/<路径>_<序号>.ogg（单声道，Vorbis）。
- 频谱图和统计写到 build/sound-preview/<模块>/，另有一张拼好的总览 _sheet.png。
- 整个模块重新生成时，模块 DIRS 里列的目录下不再由配方产出的 .ogg 会删掉。

配方模块的写法：

    from recipe import sound
    import synth as s, models as m, fx

    DIRS = ["machine"]

    @sound("machine/assembler", loop=6.0, level=-22)
    def assembler(rng, i):
        ...
        return 信号（numpy 数组，单声道，采样率 synth.SR）

loop 是循环的长度（秒），配方要多给 crossfade 秒，尾巴叠回开头；不写就是一次性的声音，
自动切掉末尾的静音、两头加极短的淡入淡出。level 是门限 RMS 的目标（dBFS），峰值压到 -1 dBFS。
随机数的种子按 "路径#序号" 算，同一份配方每次生成的文件一样。
"""

from __future__ import annotations

import argparse
import importlib
import os
import shutil
import subprocess
import sys
import tempfile
import wave
import zlib
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import numpy as np  # noqa: E402

import recipe  # noqa: E402
import synth as s  # noqa: E402

ROOT = HERE.parents[1]
SOUNDS = ROOT / "src" / "main" / "resources" / "assets" / "universe" / "sounds"
PREVIEW = ROOT / "build" / "sound-preview"
MODULES = ["common", "machines", "ships", "gear", "hostile", "world"]
# 统一响度时限幅器最多压掉的分贝数
MAX_LIMITING = 4.0
WINGET_FFMPEG = Path(os.environ.get("LOCALAPPDATA", "")) / "Microsoft" / "WinGet" / "Packages"


def ffmpeg() -> str:
    found = os.environ.get("FFMPEG") or shutil.which("ffmpeg")
    if found:
        return found
    for candidate in WINGET_FFMPEG.glob("Gyan.FFmpeg*/*/bin/ffmpeg.exe"):
        return str(candidate)
    raise SystemExit("找不到 ffmpeg：装一个，或者用环境变量 FFMPEG 指过去")


def render(r: recipe.Recipe, i: int) -> np.ndarray:
    rng = np.random.default_rng(zlib.crc32(f"{r.path}#{i}".encode()))
    x = np.asarray(r.fn(rng, i), dtype=float)
    if x.ndim != 1:
        raise ValueError(f"{r.path}: 配方要返回单声道")
    if not np.all(np.isfinite(x)):
        raise ValueError(f"{r.path}: 输出里有 NaN 或无穷大")
    # 25 Hz 以下听不见，留着只会白占动态（录下来的爆炸尾巴拖着 2 Hz 以下的直流似的偏移）
    x = s.highpass(x - np.mean(x), 25, 2)
    if r.loop:
        x = s.loop(x, s.samples(r.loop), r.crossfade, r.power)
    else:
        x = trim(x)
        x = s.fade(x, 0.001, min(0.03, len(x) / s.SR / 4))
    # 按门限 RMS 对齐响度，但限幅器最多压 4 dB：枪声这类峰值比 RMS 高 20 dB 的，宁可整体轻一点也不把起音削平
    gain = min(s.db(r.level - s.rms_db(x)), s.db(-1.0 + MAX_LIMITING - s.peak_db(x)))
    x = x * gain
    x = limit(x, -1.0)
    return x


def trim(x: np.ndarray, floor_db: float = -60) -> np.ndarray:
    threshold = np.max(np.abs(x)) * s.db(floor_db)
    loud = np.nonzero(np.abs(x) > threshold)[0]
    if len(loud) == 0:
        return x
    return x[:min(len(x), loud[-1] + s.samples(0.01))]


def limit(x: np.ndarray, ceiling_db: float) -> np.ndarray:
    """峰值超过上限时用平滑的增益曲线压下来（前看 2 ms，释放 60 ms），不硬削波。"""
    ceiling = s.db(ceiling_db)
    peak = np.abs(x)
    if peak.max() <= ceiling:
        return x
    need = np.minimum(1.0, ceiling / np.maximum(peak, 1e-12))
    look = s.samples(0.002)
    # 前看：每点取后面 look 个采样里最小的增益
    padded = np.concatenate([need, np.ones(look)])
    windows = np.lib.stride_tricks.sliding_window_view(padded, look + 1)
    g = windows.min(axis=1)[:len(x)]
    release = np.exp(-1 / (0.06 * s.SR))
    smooth = np.empty_like(g)
    level = 1.0
    for k in range(len(g)):
        level = g[k] if g[k] < level else g[k] + (level - g[k]) * release
        smooth[k] = level
    return x * smooth


def encode(x: np.ndarray, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "in.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(s.SR)
            w.writeframes(pcm.tobytes())
        subprocess.run([ffmpeg(), "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4",
                        "-ac", "1", "-map_metadata", "-1", str(target)], check=True)


def spectrogram(x: np.ndarray, width: int = 640, height: int = 200):
    """对数频率的频谱图（30 Hz–20 kHz），下面一条波形；返回 PIL 图像。"""
    from PIL import Image
    from scipy import signal as sg

    # 很短的声音（几十毫秒的提示音）窗口跟着缩
    nperseg = int(min(2048, 2 ** np.floor(np.log2(max(64, len(x))))))
    _, _, z = sg.stft(x, fs=s.SR, nperseg=nperseg, noverlap=nperseg - max(16, nperseg // 8))
    mag = 20 * np.log10(np.abs(z) + 1e-9)
    freqs = np.fft.rfftfreq(nperseg, 1 / s.SR)
    rows = np.geomspace(30, 20000, height)[::-1]
    idx = np.clip(np.searchsorted(freqs, rows), 0, len(freqs) - 1)
    img = mag[idx, :]
    cols = np.linspace(0, img.shape[1] - 1, width).astype(int)
    img = img[:, cols]
    top = img.max()
    norm = np.clip((img - (top - 72)) / 72, 0, 1)
    rgb = np.stack([norm ** 0.6, norm ** 1.6, 0.35 + 0.65 * norm ** 3], axis=-1)
    rgb[..., 2] *= norm > 0.02
    spec = (rgb * 255).astype(np.uint8)
    wave_h = 48
    wav = np.zeros((wave_h, width, 3), dtype=np.uint8)
    chunks = np.array_split(np.abs(x), width)
    for c, chunk in enumerate(chunks):
        if len(chunk):
            hpix = int(min(1.0, chunk.max()) * (wave_h - 2))
            r = int(min(1.0, np.sqrt(np.mean(chunk ** 2))) * (wave_h - 2))
            wav[wave_h - 1 - hpix:, c] = (90, 90, 120)
            wav[wave_h - 1 - r:, c] = (200, 210, 255)
    return Image.fromarray(np.concatenate([spec, wav], axis=0))


def sheet(images, target: Path) -> None:
    from PIL import Image, ImageDraw

    if not images:
        return
    cols = 3
    w, h = images[0][1].size
    label = 18
    rows = (len(images) + cols - 1) // cols
    canvas = Image.new("RGB", (cols * (w + 8), rows * (h + label + 8)), (16, 16, 20))
    draw = ImageDraw.Draw(canvas)
    for k, (name, img) in enumerate(images):
        x = (k % cols) * (w + 8)
        y = (k // cols) * (h + label + 8)
        draw.text((x + 4, y + 2), name, fill=(230, 230, 230))
        canvas.paste(img, (x, y + label))
    canvas.save(target)


def build(module: str, only: list[str], write: bool) -> None:
    recipe.REGISTRY.clear()
    mod = importlib.import_module(module)
    recipes = [r for r in recipe.REGISTRY if not only or r.path in only]
    if only and len(recipes) != len(only):
        missing = set(only) - {r.path for r in recipes}
        raise SystemExit(f"{module} 里没有这些配方：{sorted(missing)}")
    preview = PREVIEW / module
    preview.mkdir(parents=True, exist_ok=True)
    written = set()
    images = []
    for r in recipes:
        for i in range(r.variants):
            x = render(r, i)
            name = f"{r.path}_{i + 1}"
            target = SOUNDS / f"{name}.ogg"
            if write:
                encode(x, target)
            written.add(target.resolve())
            img = spectrogram(x)
            img.save(preview / f"{name.replace('/', '__')}.png")
            images.append((f"{name}  {len(x) / s.SR:.2f}s", img))
            seam = ""
            if r.loop:
                # 接缝处的跳变比平时相邻采样的差大多少：超过 1 就是比 99.9% 的地方都陡，多半会咔一声
                steps = np.abs(np.diff(x))
                jump = abs(x[-1] - x[0]) / (np.percentile(steps, 99.9) + 1e-12)
                seam = f"  seam {jump:.2f}"
            size = target.stat().st_size // 1024 if write and target.exists() else 0
            print(f"{name:42s} {len(x) / s.SR:6.2f}s  rms {s.rms_db(x):6.1f}  peak {s.peak_db(x):5.1f}{seam}  {size} KB")
    sheet(images, preview / "_sheet.png")
    if write and not only:
        for folder in getattr(mod, "DIRS", []):
            for old in (SOUNDS / folder).rglob("*.ogg"):
                if old.resolve() not in written:
                    print(f"删掉不再生成的 {old.relative_to(SOUNDS)}")
                    old.unlink()
    print(f"频谱图在 {preview}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("module")
    parser.add_argument("--only", nargs="*", default=[])
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--no-write", action="store_true")
    args = parser.parse_args()
    modules = MODULES if args.module == "all" else [args.module]
    for module in modules:
        if args.list:
            recipe.REGISTRY.clear()
            importlib.import_module(module)
            for r in recipe.REGISTRY:
                print(f"{r.path} ×{r.variants}{' loop ' + str(r.loop) + 's' if r.loop else ''}")
            continue
        build(module, args.only, not args.no_write)


if __name__ == "__main__":
    main()
