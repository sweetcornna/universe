"""把贴图配方画成模组的贴图（ADR-0031）。

用法（在仓库根目录）：

    uv run --no-project --with pillow python tools/textures/build.py <模块|all> [--only 路径 ...] [--no-write] [--list]
    uv run --no-project --with pillow python tools/textures/build.py all --coverage

- 模块是 tools/textures/ 下登记了配方的 .py（build、recipe、parts 这些工具文件除外）；all 是全部。
- --coverage 列出贴图目录里还没有配方的图（还在用旧脚本画的）。
- 每个配方写到 src/main/resources/assets/universe/textures/<路径>.png；标了 glow 的再写一张 <路径>_glow.png。
- 总览图写到 build/texture-preview/<模块>.png：每行是提交里的旧图、新图、新图平铺，最下面是原版参照。
  调色板写到 build/texture-preview/_palette.png。
- 每张图跑一遍自动检查（tools/pixel/lint.py），有问题就列出来，不拦着写文件。尺寸和提交里的旧图
  不一样也会提示：实体贴图的 UV、动画的帧数都靠尺寸对上。
- 随机数的种子按路径算，同一份配方每次画出来一样。
"""
from __future__ import annotations

import argparse
import importlib
import io
import random
import subprocess
import sys
import zipfile
import zlib
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "pixel"))

from PIL import Image  # noqa: E402

import recipe  # noqa: E402
from lint import lint  # noqa: E402
from preview import palette_sheet, sheet  # noqa: E402

TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "universe" / "textures"
PREVIEW = ROOT / "build" / "texture-preview"
# 不是配方的文件
TOOLS = {"build", "recipe", "parts"}


def modules() -> list[str]:
    return sorted(f.stem for f in HERE.glob("*.py") if f.stem not in TOOLS and not f.stem.startswith("_"))


def vanilla_jar() -> Path | None:
    jars = sorted((ROOT / "build" / "moddev" / "artifacts").glob("minecraft-patched-*-merged.jar"))
    return jars[-1] if jars else None


def vanilla(path: str) -> Image.Image | None:
    jar = vanilla_jar()
    if not jar:
        return None
    try:
        with zipfile.ZipFile(jar) as z:
            return Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")
    except KeyError:
        return None


def committed(path: str) -> Image.Image | None:
    rel = f"src/main/resources/assets/universe/textures/{path}.png"
    r = subprocess.run(["git", "show", f"HEAD:{rel}"], cwd=ROOT, capture_output=True)
    if r.returncode != 0:
        return None
    return Image.open(io.BytesIO(r.stdout)).convert("RGBA")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("module")
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--no-write", action="store_true")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--coverage", action="store_true")
    args = ap.parse_args()
    sys.stdout.reconfigure(newline="\n")  # Windows 上也输出 LF，好拿名单去比
    mods = modules() if args.module == "all" else [args.module]
    for m in mods:
        importlib.import_module(m)
    owners: dict[str, str] = {}
    for t in recipe.REGISTRY:
        if t.path in owners and owners[t.path] != t.module:
            sys.exit(f"{t.path} 同时由 {owners[t.path]} 和 {t.module} 画")
        owners[t.path] = t.module
    if args.coverage:
        coverage(owners)
        return
    palette_sheet(PREVIEW / "_palette.png")
    total, flagged = 0, 0
    for m in mods:
        rows, refs, seen = [], [], set()
        for t in recipe.REGISTRY:
            if t.module != m or (args.only and t.path not in args.only):
                continue
            if args.list:
                print(t.path)
                continue
            rng = random.Random(zlib.crc32(t.path.encode()))
            c = t.fn(rng)
            img = c.image()
            problems = lint(c, t.kind, t.rough, t.tinted, t.frames)
            old = committed(t.path)
            if old is not None and old.size != img.size:
                problems.append(f"尺寸 {img.size[0]}×{img.size[1]}，原来是 {old.size[0]}×{old.size[1]}")
            if t.frames > 1 and img.size[1] % t.frames:
                problems.append(f"高度 {img.size[1]} 不是 {t.frames} 帧的整数倍")
            total += 1
            if problems:
                flagged += 1
                print(f"  {t.path}: " + "；".join(problems))
            if not args.no_write:
                out = TEXTURES / f"{t.path}.png"
                out.parent.mkdir(parents=True, exist_ok=True)
                img.save(out)
                if t.glow:
                    g = c.emissive_image()
                    if g is not None:
                        g.save(TEXTURES / f"{t.path}_glow.png")
            rows.append((t.path.split("/", 1)[1], old, img, t.tile, t.frames))
            for r in t.refs:
                if r not in seen:
                    seen.add(r)
                    v = vanilla(r)
                    if v is not None:
                        refs.append(v)
        if rows:
            sheet(rows, PREVIEW / f"{m}.png", refs)
            print(f"{m}: {len(rows)} 张，总览 {PREVIEW / (m + '.png')}")
    if not args.list:
        print(f"共 {total} 张，{flagged} 张有提示")


def coverage(owners: dict[str, str]):
    """贴图目录里还没有配方的图：按旧脚本还在画的、或者别处生成的。"""
    glows = {f"{t.path}_glow" for t in recipe.REGISTRY if t.glow}
    left: dict[str, list[str]] = {}
    total = 0
    for f in sorted(TEXTURES.rglob("*.png")):
        rel = f.relative_to(TEXTURES).as_posix()[:-4]
        total += 1
        if rel in owners or rel in glows:
            continue
        left.setdefault(rel.split("/", 1)[0], []).append(rel)
    n = sum(len(v) for v in left.values())
    print(f"共 {total} 张，有配方的 {total - n} 张，还没有的 {n} 张")
    for k, v in left.items():
        print(f"## {k}（{len(v)}）")
        print(" ".join(v))


if __name__ == "__main__":
    main()
