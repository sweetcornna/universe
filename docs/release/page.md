# 发布页文字

上传的文件是 `./gradlew build` 生成的 `build/libs/universe-26.3-<版本>.jar`（版本号是 `gradle.properties` 里的 `mod_version`，发布时由作者定）；游戏版本 26.3，加载器 NeoForge，客户端和服务端都要装，JEI、Jade 填成可选依赖。

Modrinth 和 CurseForge 用同一份。上传时把"简介"填进 Summary，正文按平台分别贴英文或中英两段（Modrinth 支持 Markdown，CurseForge 选 Markdown 编辑器）。图片放在平台的图库里，正文不内嵌，免得图挂了。

## 简介（Summary）

English (≤ 256 characters):

> Fly a block-built starship across a procedurally generated star cluster, survive alien planets, meet alien races, fight the Dark Fog, automate factories and wrap a Dyson sphere around a star.

中文：

> 驾驶自己拼的飞船探索程序生成的星团，在外星球上活下来，结识外星种族，打退黑雾，把工厂自动化，最后给恒星套上戴森球。

## 正文（English）

**Universe** is a space exploration and factory mod for NeoForge. It takes the loop of *No Man's Sky* (land, scan, mine, fly on) and the loop of *Dyson Sphere Program* (belts, power, research, logistics, Dyson sphere) and puts both into survival Minecraft. Planets and space are drawn from NASA photos and real physics; the blocks you place still look like Minecraft.

This is still a test version. The story text is a placeholder and will be rewritten. Worlds may not carry over to later versions.

### Explore

- Every world has a star cluster of 64 systems (32 to 256 if you change it when creating the world) and a few hundred planets you can land on, all generated from the seed. The Overworld is your home planet.
- Eight planet types with their own terrain, sky, weather, plants and animals. Toxic planets have acid seas. Exotic planets come in six forms taken from real places: basalt columns, hoodoos, giant crystals, concretions, polygon ground and stone arches.
- An exosuit for oxygen and hazards, with a jetpack, an energy core and flight upgrades. Multi-tools in five classes and four grades, with weapons you slot in. Resource plants, cooking, shelters.
- Oceans with fish, eels, jellyfish and squid, kelp that gives oxygen, pearls, wrecks and ruins. A submarine, hover car, heavy rover and an exo-mech.
- Sentinels with five wanted levels, quads and walkers, and a sentinel pillar on every planet. Landmarks: trading posts, manufacturing facilities, observatories.
- Tame animals, ride the giant ones, and raise companions from eggs.

### Fly

- Build a ship from blocks. It is graded as a fighter, hauler, explorer or shuttle, C to S, with tech slots. Buy ships at stations, repair crashed ones, summon your ship, use autopilot.
- Space is to scale: the home planet is 1,200 blocks across in space, and a trip between neighbouring planets takes half a minute on the pulse engine. Black holes, three exotic drives, and a portal on every planet.
- A freighter of your own with a deck you can build on, and up to twelve frigates to send on expeditions.
- Every system has a space station: trading, missions, guilds, a multi-tool cabinet and a ship dealer.

### Meet

- Three alien races, each with its own language of 64 words. Learn words from conversations and knowledge stones, give gifts, and earn standing with the race and its three guilds. Settlements on planets with air.

### Build

- Conveyor belts, sorters, splitters, piling and proliferators. A power grid with wind, thermal, solar, geothermal, fusion and artificial stars. Matrix labs, seven matrices and a tech tree with infinite upgrades.
- Planetary and interstellar logistics with drones and vessels. Blueprints, construction drones and logistics bots.
- Plan orbits and shells in the Dyson sphere editor, or place nodes, frames and shells yourself and colour them. Share a layer as a blueprint string. Launch solar sails and rockets, and draw the power back down with ray receivers.
- A grid interface passes power to and from other mods (1 J = 1 FE). Storage tanks, water pumps and oil extractors connect to other mods' fluid pipes.

### Defend

- The Dark Fog builds bases on planets and hives in space. It grows when you hit it, raids your factories when its threat fills up, and sends relays and seeds to new places.
- Gauss, laser, implosion, missile and plasma turrets, signal and jammer towers, planetary shields, and a battlefield analysis base that repairs and rebuilds. Dark Fog drops make the seventh matrix.
- The Dark Fog, sentinel and resource settings are game rules you can set when creating the world.

### Sound

- All sounds for machines, ships, weapons, gear and enemies are original, made for this mod. Machines blend into one soundscape per factory. Sound is muffled in thin air and vacuum.

### Requirements

- Minecraft 26.3, NeoForge 26.3.0.95 or newer, Java 25.
- Install on both the client and the server.
- Optional: JEI (recipe categories for every machine) and Jade (machine status on hover).
- Sodium: use 0.9.3-beta.1 or newer. 0.9.2 crashes on current NeoForge 26.3 builds on its own. Iris 1.11.7 works with 0.9.3-beta.1. With a shader pack on (tested with Complementary Reimagined), planets and space use the pack's Overworld sky, so space has a blue sky.
- Sound Physics Remastered: when it is installed, this mod leaves sound filtering to it.

### Links

- Changelog: see the Changelog tab.
- Source code and issues: (fill in the repository link before publishing)

Inspired by *No Man's Sky* and *Dyson Sphere Program*. Not affiliated with Hello Games or Youthcat Studio, and no assets from either game are used.

## 正文（中文）

**寰宇（Universe）**是一个 NeoForge 上的太空探索加工厂模组。它把《无人深空》的循环（降落、扫描、采矿、再起飞）和《戴森球计划》的循环（传送带、电网、研究、物流、戴森球）一起搬进了生存模式。星球和太空照 NASA 的照片和真实的物理做，玩家放的方块还是我的世界的样子。

现在还是测试版。剧情文字是占位稿，之后会重写。之后的版本不保证能读这一版的存档。

### 探索

- 每个世界一个星团，64 个恒星系（建世界时能改成 32 到 256），几百颗能降落的星球，都由世界种子生成。主世界就是母星。
- 8 类星球，地形、天空、天气、植物、动物各不相同。毒雾星球的海是酸液。奇异星球有六种，照现实里的地貌：柱状玄武岩、石林、巨晶、结核球、多边形地面、天然石拱。
- 外骨骼管氧气和防护，带喷气背包、能量核心和飞行升级。多功能工具分五种、四档，武器装进格子里。资源植物、做菜、庇护所。
- 海里有鱼、鳗、水母、乌贼，补氧气的海带，珍珠，沉船和遗迹。潜艇、悬浮车、重型车、机甲。
- 守卫机五级通缉，有四足机和步行机，每颗星球一座守卫塔。地标：交易站、制造设施、观测站。
- 驯服动物，骑巨兽，用卵孵出伙伴。

### 飞行

- 用方块拼一艘船，按部件评成战斗机、运输船、探索船、穿梭机，C 到 S 档，带技术槽。空间站能买船，坠毁的船能修好开走，能召唤，有自动驾驶。
- 太空是按比例的：母星在太空里直径 1200 格，开脉冲引擎到相邻的行星要半分钟。黑洞、三种特殊驱动，每颗星球一座传送门。
- 自己的货船，甲板上随便建；最多 12 艘护卫舰，派出去远征。
- 每个恒星系一座空间站：交易、任务、公会、多功能工具柜、飞船交易。

### 外星文明

- 三个外星种族，各有一种 64 个词的语言。在对话和知识石上学词，送礼，攒种族和三个公会的声望。有大气的星球上有外星人的聚落。

### 建设

- 传送带、分拣器、分流器、集装、增产剂。风力、火力、太阳能、地热、聚变、人造恒星发电。矩阵研究站，七种矩阵，科技树和无限升级。
- 行星内和星际物流，无人机和运输船。蓝图、建造无人机、配送机器人。
- 在戴森球编辑器里排轨道和球壳，也能自己摆节点、连框架、铺壳面、上色，一层能导出成蓝图串分享。发射太阳帆和火箭，用射线接收站把能量收回地面。
- 电网接口和别的模组互通电力（1 J = 1 FE）；储液罐、抽水站、原油萃取站能接别的模组的流体管道。

### 防御

- 黑雾在星球上建基地，在太空里建巢穴。挨打会升级，威胁满了来打你的工厂，还会派中继站和种子去别处扎根。
- 高斯、激光、聚爆、导弹、等离子炮塔，信号塔、干扰塔，行星护盾，会修会重建的战场分析基地。黑雾的掉落做第七种矩阵。
- 黑雾、守卫机、资源的设置都是游戏规则，建世界时就能调。

### 声音

- 设备、飞船、武器、装备、敌人的声音都是为本模组原创的。一片工厂的设备混成一片环境声。稀薄的大气里和真空里声音发闷。

### 需要

- Minecraft 26.3，NeoForge 26.3.0.95 或更新，Java 25。
- 客户端和服务器都要装。
- 可选：JEI（每种设备配方一个分类）、Jade（指着设备看状态）。
- Sodium 要用 0.9.3-beta.1 或更新的版本，0.9.2 本身在现在的 NeoForge 26.3 上启动就崩溃。Iris 1.11.7 配它能用。开光影包（试的是 Complementary Reimagined）时，本模组的星球和太空用的是光影包的主世界天空，太空里是蓝天。
- 装了 Sound Physics Remastered 时，声音的滤波交给它。

玩法参考了《无人深空》和《戴森球计划》。本模组和 Hello Games、柚子猫工作室（Youthcat Studio）没有关系，没有用这两款游戏的美术和音效。

## 图库

`screenshots/` 里的图按编号上传，第一张设为特色图。标题和说明：

现在的图是 M10 之前截的，内容对不上了（02 写着浮空岛，奇异星球早改成了六种真实地貌；08 写着 64 个恒星系，现在建世界时能选 32–256）。发布前按下面这张表重截，1584×861 或更大，存成 jpg。

| 文件 | 标题 | 说明 |
|---|---|---|
| 01_dyson_sunrise.jpg | Dyson sphere at sunrise · 日出 | A half-built Dyson sphere rising behind a factory in the Overworld. 主世界的工厂后面，建到一半的戴森球随太阳升起。 |
| 02_dyson_editor.jpg | Dyson editor · 戴森球编辑器 | Place nodes, link frames, fill and paint shells; export a layer as a blueprint. 自己摆节点、连框架、铺壳面上色，一层能导出成蓝图。 |
| 03_dyson_sphere.jpg | Dyson sphere · 戴森球 | Shells and a swarm of solar sails, seen from space. 从太空看球壳和一圈太阳帆。 |
| 04_exotic_planet.jpg | Exotic planet · 奇异星球 | Columnar basalt, hoodoos, crystal fields, concretions, polygonal ground or natural arches. 柱状玄武岩、风蚀石林、晶体原野、球形结核、多边形冻土或天然石拱。 |
| 05_factory.jpg | Factory · 工厂 | Belts, sorters, assemblers and wireless power towers. 传送带、分拣器、制造台和电力感应塔。 |
| 06_acid_sea.jpg | Acid sea · 酸液海 | A toxic planet's sea is acid, not water; acid rain falls there too. 毒雾星球的海是酸液，还下酸雨。 |
| 07_dark_fog.jpg | Dark Fog · 黑雾 | A ground base under fire from Gauss and laser turrets. 高斯炮塔和激光炮塔在打黑雾的地面基地。 |
| 08_star_map.jpg | Star map · 星图 | Pick a star system and warp; 32 to 256 of them, set when you create the world. 选一个恒星系跃迁过去；建世界时定 32–256 个。 |
| 09_tech_tree.jpg | Tech tree · 科技树 | Matrix research and infinite upgrades. 矩阵研究和无限升级。 |
| 10_settlement.jpg | Alien settlement · 外星人聚落 | Learn words, trade and build reputation with three species. 学词、交易，攒三个种族的声望。 |

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
> - Sodium needs 0.9.3-beta.1 or newer; 0.9.2 crashes on current NeoForge 26.3 builds by itself. Iris 1.11.7 works with it. With a shader pack, planets and space use the pack's Overworld sky.
> - Worlds are not guaranteed to load in later versions before 1.0.
> - Every stage was tested in game, but nobody has played the whole thing start to finish yet.
> - Feedback from the `/universe` debug commands is Chinese only.
