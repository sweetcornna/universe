# 发布页文字

上传的文件是 `./gradlew build` 生成的 `build/libs/universe-26.3-0.7.0-alpha.jar`；游戏版本 26.3，加载器 NeoForge，客户端和服务端都要装，JEI、Jade 填成可选依赖。

Modrinth 和 CurseForge 用同一份。上传时把"简介"填进 Summary，正文按平台分别贴英文或中英两段（Modrinth 支持 Markdown，CurseForge 选 Markdown 编辑器）。图片放在平台的图库里，正文不内嵌，免得图挂了。

## 简介（Summary）

English (≤ 256 characters):

> Fly a block-built starship across a procedurally generated star cluster, survive alien planets, automate factories with belts and research, and wrap a Dyson sphere around a star.

中文：

> 驾驶自己拼的飞船探索程序生成的星团，在外星球上活下来，用传送带和科研把工厂自动化，最后给恒星套上戴森球。

## 正文（English）

**Universe** is a space exploration and factory mod for NeoForge. It takes the loop of *No Man's Sky* (land, scan, mine, fly on) and the loop of *Dyson Sphere Program* (belts, power, research, logistics, Dyson sphere) and puts both into survival Minecraft.

This is an **alpha**. The whole main line is in, from the crashed probe near spawn to the last research. The story text is a placeholder and will be rewritten. Worlds may not carry over to later versions.

### Explore

- Every world has a cluster of 64 star systems and a few hundred planets you can land on, all generated from the world seed. The Overworld is your home planet, and the moon in its night sky is your first stop.
- Eight planet types with their own terrain, sky, weather, plants and animals. Heat, cold, toxicity and radiation. Gravity changes from planet to planet.
- An exosuit for oxygen and hazard protection, and a multi-tool that mines, scans and fights. Points of interest, ruins, sentinels.
- Build a ship from blocks, take off, fly through space, land, warp to other systems. Weapons, shields, pirates, asteroid mining. Every system has a space station with trading, missions and repairs.

### Build

- Conveyor belts, sorters, splitters and storage. A power grid with wind, thermal, solar and fusion. Matrix labs and a tech tree with infinite upgrades at the end.
- Five machine recipe types and around a thousand items, from iron ingots to antimatter. Planetary and interstellar logistics with drones and freighters. Production statistics and blueprints.
- Plan orbits and shells in the Dyson sphere editor, launch solar sails and rockets, and draw the power back down with ray receivers. The sphere shows up in the sky as you build it.

### Requirements

- Minecraft 26.3, NeoForge 26.3.0.95 or newer, Java 25.
- Install on both the client and the server.
- Optional: JEI (recipe categories for every machine) and Jade (machine status on hover).
- Sodium: use 0.9.3-beta.1 or newer. 0.9.2 crashes on current NeoForge 26.3 builds on its own. Iris 1.11.7 works with 0.9.3-beta.1. With a shader pack on (tested with Complementary Reimagined), the Dyson sphere is not drawn and space shows an Overworld sky.

### Links

- Changelog: see the Changelog tab.
- Source code and issues: (fill in the repository link before publishing)

Inspired by *No Man's Sky* and *Dyson Sphere Program*. Not affiliated with Hello Games or Youthcat Studio, and no assets from either game are used.

## 正文（中文）

**寰宇（Universe）**是一个 NeoForge 上的太空探索加工厂模组。它把《无人深空》的循环（降落、扫描、采矿、再起飞）和《戴森球计划》的循环（传送带、电网、研究、物流、戴森球）一起搬进了生存模式。

现在是**测试版**。主线已经完整，从出生点附近坠毁的探测器一直到最后一项研究。剧情文字是占位稿，之后会重写。之后的版本不保证能读这一版的存档。

### 探索

- 每个世界一个星团，64 个恒星系，几百颗能降落的星球，都由世界种子生成。主世界就是母星，夜空里的月亮是第一个目的地。
- 8 类星球，地形、天空、天气、植物、动物各不相同。高温、严寒、毒性、辐射，重力也各不一样。
- 外骨骼管氧气和防护，多功能工具能采矿、扫描、战斗。兴趣点、遗迹、守卫机。
- 用方块拼一艘船，起飞、太空飞行、降落、跃迁到别的恒星系。武器、护盾、海盗、小行星采矿。每个恒星系有一座空间站，能交易、接任务、修船。

### 建设

- 传送带、分拣器、分流器、储物仓。风力、火力、太阳能、聚变发电。矩阵研究站和科技树，最后是无限升级。
- 五种设备配方，从铁锭到反物质一千来样东西。行星内和星际物流，无人机和运输船。生产统计，蓝图。
- 在戴森球编辑器里排轨道和球壳，发射太阳帆和火箭，用射线接收站把能量收回地面。建到哪一步，天上就挂着哪一步。

### 需要

- Minecraft 26.3，NeoForge 26.3.0.95 或更新，Java 25。
- 客户端和服务器都要装。
- 可选：JEI（每种设备配方一个分类）、Jade（指着设备看状态）。
- Sodium 要用 0.9.3-beta.1 或更新的版本，0.9.2 本身在现在的 NeoForge 26.3 上启动就崩溃。Iris 1.11.7 配它能用。开光影包（试的是 Complementary Reimagined）时戴森球看不见，太空里会变成主世界的天空。

玩法参考了《无人深空》和《戴森球计划》。本模组和 Hello Games、柚子猫工作室（Youthcat Studio）没有关系，没有用这两款游戏的美术和音效。

## 图库

`screenshots/` 里的图按编号上传，第一张设为特色图。标题和说明：

| 文件 | 标题 | 说明 |
|---|---|---|
| 01_dyson_sunrise.jpg | Dyson sphere at sunrise · 日出 | A half-built Dyson sphere rising behind a factory in the Overworld. 主世界的工厂后面，建到一半的戴森球随太阳升起。 |
| 02_exotic_planet.jpg | Exotic planet · 奇异星球 | Floating islands and alien flora. 浮空岛和异星植物。 |
| 03_dyson_sphere.jpg | Dyson sphere · 戴森球 | Two shells and a swarm of solar sails, seen from space. 从太空看两层球壳和一圈太阳帆。 |
| 04_habitable_planet.jpg | Habitable planet · 宜居星球 | Terraced hills and alien trees. 梯田一样的山坡和异星的树。 |
| 05_factory.jpg | Factory · 工厂 | Belts, sorters, assemblers and wireless power towers. 传送带、分拣器、制造台和电力感应塔。 |
| 06_toxic_planet.jpg | Toxic planet · 毒雾星球 | A banded rock spire in a green haze. 绿色毒雾里一座带条纹的石峰。 |
| 07_frozen_planet.jpg | Frozen planet · 冰冻星球 | A snowy valley under a teal sky. 青色天空下的雪谷。 |
| 08_star_map.jpg | Star map · 星图 | 64 star systems; pick one and warp. 64 个恒星系，选一个跃迁过去。 |
| 09_tech_tree.jpg | Tech tree · 科技树 | Matrix research and infinite upgrades. 矩阵研究和无限升级。 |

## 版本说明（上传时的 Changelog 栏）

中文用 `CHANGELOG.md` 里 0.7.0-alpha 那一节。英文：

> First public alpha. Requires Minecraft 26.3, NeoForge 26.3.0.95 or newer and Java 25.
>
> **In this version**
>
> - The full main line, from the crashed probe to the Dyson sphere and the last research.
> - Built-in profiling (`/universe perf`) and a reference factory (`/universe bench`). With 5,000 belts and 500 machines plus factories on three planets, the mod uses 1.16 ms per tick.
> - The Dyson sphere in the sky is rebuilt once per tick instead of every frame. New client setting for its detail: full, simple or off (Mods screen, Config button).
> - JEI: a category for every machine recipe type, with the research each recipe needs. Click the progress arrow in a machine screen to open it.
> - Jade: machine power, current recipe and what is missing; miner output and reserves; ray receiver efficiency; logistics station slots.
> - Multiplayer: players who join late still get the story advancements the world has reached, without a chat announcement for each one.
>
> **Known issues**
>
> - Story text is a placeholder and will be rewritten.
> - Sodium needs 0.9.3-beta.1 or newer; 0.9.2 crashes on current NeoForge 26.3 builds by itself. Iris 1.11.7 works with it. With a shader pack, the Dyson sphere is not drawn and space shows an Overworld sky.
> - Worlds are not guaranteed to load in later versions before 1.0.
> - Every stage was tested in game, but nobody has played the whole thing start to finish yet.
> - Feedback from the `/universe` debug commands is Chinese only.
