# 技术架构（草案 v0）

> 2026-10-16 · 标注"待 M1 验证"的部分在技术验证后定稿

## 1. 分层

| 层 | 内容 | 可以依赖 | 加载位置 |
|---|---|---|---|
| client | 渲染（天空、星球、戴森球、飞船）、HUD、界面 | 适配层、core | 只在客户端 |
| 适配层 | 方块、物品、实体、维度、网络包、存档、事件 | core、MC、NeoForge | 客户端和服务端 |
| core | 程序生成、轨道、工厂与电网模拟、物流排程、戴森球计算 | 只有 JDK | 客户端和服务端 |

- 依赖只能自上而下。core 不得引用 `net.minecraft`、`com.mojang`、`net.neoforged`，由 `CoreIsolationTest` 在每次构建时检查（[ADR-0003](adr/0003-core-decoupling.md)）。
- 服务端权威：玩法状态都在服务端计算，客户端只负责展示和输入。

## 2. 包结构

```
io.github.sweetcornna.universe
├── Universe, UniverseClient   入口
├── core/                      纯 Java 核心
│   ├── procgen/               种子派生、噪声、命名
│   ├── galaxy/                星团、恒星、行星参数与轨道
│   ├── factory/               传送带、分拣器、配方执行
│   ├── power/                 电网结算
│   ├── logistics/             物流站供需匹配与运输排程
│   └── dyson/                 戴森云与球壳的结构、建造进度、发电
├── registry/                  各类 DeferredRegister
├── world/                     维度管理、星球区块生成器、群系、地物
├── space/                     太空维度、飞船实体、跃迁
├── survival/                  外骨骼、危害、生命维持、多功能工具
├── machine/                   设备方块与方块实体（只做壳，逻辑交给 core）
├── research/                  科技树数据与研究进度
├── network/                   网络包的定义与处理
├── data/                      SavedData、数据附件、配置
├── compat/                    JEI、Jade 等可选兼容，对方模组存在时才加载
├── datagen/                   数据生成
└── client/                    渲染、HUD、界面
```

目前只建了入口类和 `core/procgen`，其余的包在用到时再建。

## 3. 关键方案

### 3.1 宇宙数据与种子派生

- 种子逐级派生：世界种子 → 星团 → 第 i 个恒星系 → 第 j 颗行星 → 第 k 个物种，统一用 `core.procgen.Seeds`（已实现）。
- 宇宙描述（恒星的位置和类型、行星参数）是种子的纯函数，按需计算并缓存，不写进存档。
- 存档只保存玩家造成的变化：已创建的星球维度、玩家起的名字、戴森球建造进度、物流站、研究进度等。
- 客户端拿到星团种子后自己算出宇宙描述，网络上只同步变化。
- 派生算法属于存档格式，改动时必须附带存档迁移。`SeedsTest` 固定了几个派生结果，防止误改。

### 3.2 星球维度（待 M1 验证）

详见 [ADR-0004](adr/0004-planets-as-runtime-dimensions.md)。

- 玩家第一次前往某颗星球时，在运行时创建它的维度（`LevelStem` + `ServerLevel`），注册到服务器，并通知客户端。
- 已创建的星球列表存进 SavedData，服务器启动时重新创建。
- 没有玩家、也没有强制加载区块的星球维度，空闲一段时间后卸载，以节省内存。
- 参考 Infiniverse（Commoble，MIT 许可）的实现；它支持 26.3 后也可以直接依赖。
- 维度类型、群系、时间线都属于登录时同步的注册表，运行时不能新增。所以预置一组"环境模板"（见 §3.4）供星球挑选，星球之间的细节差异通过参数化生成和客户端渲染体现。
- 退路：服务器启动时预建固定数量的维度，组成维度池，按需分配。

### 3.3 星球地形

- 以原版的噪声地形生成器为基础，在代码里按星球参数组装噪声设置和密度函数（用直接 Holder，不注册），控制地形起伏、海平面、洞穴和地表方块。
- 群系：在数据包里预置一批"星球群系原型"，比如 30–40 个（陨坑原野、冰川、酸沼、晶簇林……），每颗星球从中选一部分。同一个原型在不同星球上的颜色由星球参数决定。
- 地物：矿脉节点、植物、遗迹结构由代码注册的地物按星球参数放置。
- 性能目标：星球区块的生成速度不低于原版主世界。

### 3.4 天空与环境

- 从 1.21.11 到 26.x，原版把天空颜色、雾色、环境光、昼夜变化等改成了数据驱动，对应环境属性（environment attributes）、时间线（timelines）和世界时钟（world clocks）。这些定义挂在维度类型、群系、时间线这些同步注册表上。
- 方案：预置 8–16 套"环境模板"，区分昼夜长度、大气色调、是否潮汐锁定，每颗星球选一套。具体差异（天空色调、恒星大小和颜色、其他行星的位置）由客户端按星球参数计算后覆盖。
- 自定义天空渲染：星空、恒星、同一恒星系的其他行星和卫星、戴森云和戴森球。
- 接入点（已在 26.3 源码中确认）：原版的 `DimensionSpecialEffects` 已经不存在。NeoForge 提供 `CustomSkyboxRenderer`，在 `RegisterCustomEnvironmentEffectRendererEvent` 里注册，再通过环境属性 `NeoForgeEnvironmentAttributes.CUSTOM_SKYBOX` 指定给维度类型或群系；渲染要用的数据在 `ExtractLevelRenderStateEvent` 里提取。同一事件还能注册自定义的云和天气渲染器。所有环境模板都指向同一个天空渲染器，由它按当前星球的参数绘制。
- 进出星球的过场：NeoForge 的 `RegisterDimensionTransitionScreenEvent` 可以按维度注册切换画面。它按维度键注册，运行时创建的星球维度怎么接入，在 M1 的 S1、S4 里一起验证。
- 渲染代码集中在 `client` 包，方便随版本调整。
- 光影兼容：M1 用 Iris 实测（26.3 已有 NeoForge 版），必要时提供简化渲染。

### 3.5 太空与飞船

- 每个恒星系一个太空维度，使用空的区块生成器。
- 飞船是实体，用 GeckoLib 做模型和动画，玩家作为乘客驾驶。跨维度时先下船，飞船和玩家分别传送，再重新上船。
- 高速飞行会不断触发区块加载，M1 比较两种方案：
  - **A 真实坐标**：飞船在太空维度里真实移动，靠空区块降低加载成本。实现简单。
  - **B 浮动原点**：飞船始终在原点附近，行星按"虚拟坐标"绘制。完全不加载区块，但多人时其他飞船的位置要自己同步。
- 飞控、HUD、相机在 `client` 包，飞行状态由服务端校验。

### 3.6 工厂模拟

- 方块实体只是"壳"：保存位置和配置，具体逻辑交给 core 里的模拟对象。
- 传送带：相连的传送带合并成一条"线路"，线路上的物品用紧凑数组记录位置。每 tick 按线路批量推进，而不是每段传送带各自 tick。
- 分拣器和设备按网络批量结算；渲染时按线路批量提交物品模型。
- 增删方块时只重建受影响的那一段线路。
- 性能预算（M1 用基准测试确认）：1 万段传送带、4 万个物品的推进在 2 ms/tick 以内。

### 3.7 电网

- 电力感应塔的覆盖范围决定设备属于哪个电网；连接距离内的塔合并成同一个网络（用并查集维护）。
- 每个电网每 tick 结算一次：发电、耗电、蓄电器充放电、满足率。
- 单位：内部用焦耳和瓦特；和 FE 互通时按 1 FE = 1 J 换算（M3 定稿）。

### 3.8 物流

- 物流站的库存和供需设置存在全局 SavedData 里，运输由服务端统一排程，按距离和速度算出到达时间。
- 无人机和运输船只负责表现，它们丢失或未加载都不影响结算。
- 站点所在区块未加载时，站点之间的运输照常结算，但站点和周围设备之间的物品交换要等区块加载（M5 细化）。

### 3.9 戴森球

- 数据模型在 `core.dyson`：每颗恒星一个戴森球，包括戴森云轨道（半径、倾角、太阳帆数量与寿命）和球壳层（半径、朝向、节点、框架、壳面、建造进度）。
- 服务端每秒结算一次太阳帆消散、建造进度和发电量。
- 状态存进 SavedData。客户端订阅自己所在恒星系的戴森球，按增量同步。
- 渲染：太阳帆用实例化的点精灵，框架用线，壳面用半透明网格，按距离分级。

### 3.10 网络与存档

- 网络包用 NeoForge 的 `CustomPacketPayload` 加 `StreamCodec`，在 `RegisterPayloadHandlersEvent` 里注册。
- 按恒星系订阅：客户端只接收所在恒星系的状态（戴森球、物流站等）。
- 世界级数据用 SavedData，玩家数据（外骨骼能量、发现记录）用数据附件（data attachments）。
- 每份存档数据都带版本号，加载时逐级迁移。

### 3.11 与其他模组互通

- 设备通过 NeoForge 21.9 起的新传输接口（Transfer Rework）对外提供物品、流体和能量。
- 可选兼容放在 `compat` 包，只在对方模组存在时加载：JEI 显示配方，Jade 显示设备状态。
- 可选依赖在 26.3 上的情况（2026-10-16 查 Modrinth）：GeckoLib 5.5.7、JEI 31.14、Jade 26.3.5、Curios 17.0 beta、Sodium 0.9.3 beta、Iris 1.11.7 都有 26.3 的 NeoForge 版；EMI 还没有 26.x 版本。

## 4. 26.x 开发注意事项

网上的教程和 AI 助手的训练资料大多基于 1.21.1 及更早的版本。下表列出写代码时要特别留意的变化。"已确认"表示在官方 MDK、NeoForge 公告或 26.3 的源码里见到过（2026-10-16）。

| 变化 | 状态 |
|---|---|
| Mojang 取消了混淆，直接使用官方名称，不再需要 Parchment | 已确认 |
| `ResourceLocation` 改名为 `Identifier`（`net.minecraft.resources`） | 已确认，模组代码已在用 |
| 数据文件里的物品改用 `ItemStackTemplate` 表示，流体同理（26.1） | 已确认 |
| `GuiGraphics` 改为 `GuiGraphicsExtractor`，`Screen#render` 改为 `extractRenderState` 等 | 已确认 |
| `new ChunkPos(blockPos)` 改为 `ChunkPos.containing(...)` 等 | 已确认（NeoForge 26.1 公告） |
| 环境属性、时间线、世界时钟：`net.minecraft.world.attribute`、`world.timeline`、`world.clock` | 已确认，字段细节待核实 |
| `DimensionSpecialEffects` 已移除；自定义天空用 NeoForge 的 `CustomSkyboxRenderer` 加环境属性 `CUSTOM_SKYBOX`（见 §3.4） | 已确认 |
| 渲染管线 `RenderPipeline` 在 `com.mojang.renderpearl.api.pipeline`，不在旧的 blaze3d 包里 | 已确认，用法待核实 |
| 物品、流体、能量传输接口：`net.neoforged.neoforge.transfer`（`ResourceHandler`、`energy.EnergyHandler` 等） | 已确认，用法待核实 |
| 物品模型定义（`ClientItem`，对应 `assets/<命名空间>/items/`） | 已确认类存在 |

原则：拿不准的 API，先查源码再动手写，核实后把结果补进这张表。源码位置：

- Minecraft（含 NeoForge 补丁）：`build/moddev/artifacts/minecraft-patched-26.3.0.86-sources.jar`，IDE 里可以直接跳转。
- NeoForge 自己的类：Gradle 缓存里的 `neoforge-26.3.0.86-sources.jar`（`~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.3.0.86/`）。

## 5. 性能预算（目标）

- 服务端：中等规模的基地（5,000 段传送带、500 台设备、同时加载 3 颗星球）保持 20 TPS，本模组每 tick 耗时低于 10 ms。
- 客户端：完整显示戴森球时，中端显卡在 1080p 下帧率下降不超过 10%。
- 首次前往一颗星球（含创建维度）的等待时间低于 5 秒。

## 6. 测试

- core：JUnit 6 单元测试，随 `gradlew build` 运行，CI 上也跑。
- 游戏内行为：NeoForge GameTest（`gameTestServer` 运行配置），覆盖设备、传送带、维度切换等。
- 性能：core 层做基准测试（M1 定方案），游戏内用 Spark 剖析。
- 回归：保留几个固定种子的测试世界，每次升级 MC 版本后手动走一遍。

## 7. 版本跟进

见 [ADR-0001](adr/0001-target-version.md)。
