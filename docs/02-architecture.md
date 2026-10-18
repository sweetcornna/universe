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
│   ├── terrain/               地形模型、群系
│   ├── sky/                   天体位置、天空配色
│   ├── weather/               天气轮换
│   ├── ecology/               植物、树、动物的物种和外观
│   ├── ship/                  飞船部件、性能、飞控、组装时的连通性
│   ├── space/                 太空维度里的天体布局
│   ├── factory/               传送带线路、分拣器、配方执行
│   ├── power/                 电网的连通和结算
│   ├── survival/              生命维持、危害防护、喷气背包的数值，重力模型，激光热量，扫描时矿脉的合并
│   ├── dimension/             运行时维度的空闲计时
│   ├── logistics/             物流站供需匹配与运输排程
│   └── dyson/                 戴森云与球壳的结构、建造进度、发电
├── registry/                  各类 DeferredRegister
├── block/                     方块类（星球方块、植物、树、飞船部件）
├── entity/                    实体（异星动物、飞船）和刷怪器
├── world/                     维度管理（星球、太空）、星球区块生成器、群系、地物
├── survival/                  外骨骼、危害、生命维持、多功能工具
├── machine/                   设备方块与方块实体（只做壳，逻辑交给 core）
├── research/                  科技树数据与研究进度
├── network/                   网络包的定义与处理
├── data/                      SavedData、数据附件、配置
├── compat/                    JEI、Jade 等可选兼容，对方模组存在时才加载
├── datagen/                   数据生成
└── client/                    渲染、HUD、界面
```

目前建了入口类、`core` 下的 `procgen`、`galaxy`、`terrain`、`sky`、`weather`、`ecology`、`ship`、`space`、`factory`、`power`、`survival`、`dimension`，以及 `registry`、`block`、`entity`、`world`、`survival`、`data`、`network`、`datagen`、`client`、`mixin`，其余的包在用到时再建。

## 3. 关键方案

### 3.1 宇宙数据与种子派生

- 种子逐级派生：世界种子 → 星团 → 第 i 个恒星系 → 第 j 颗行星 → 第 k 个物种，统一用 `core.procgen.Seeds`（已实现）。
- 宇宙描述（恒星的位置和类型、行星参数）是种子的纯函数，按需计算并缓存，不写进存档。
- 存档只保存玩家造成的变化：已创建的星球维度、玩家起的名字、戴森球建造进度、物流站、研究进度等。
- 客户端拿到星团种子后自己算出宇宙描述，网络上只同步变化。
- 派生算法属于存档格式，改动时必须附带存档迁移。`SeedsTest` 固定了几个派生结果，防止误改。

### 3.2 星球维度

详见 [ADR-0004](adr/0004-planets-as-runtime-dimensions.md)。M1 原型 S1 已验证可行（2026-10-16），实现在 `world.RuntimeDimensions`。

- 玩家第一次前往某颗星球时，在运行时创建它的维度：按环境模板生成 `LevelStem`，直接构造 `ServerLevel` 放进服务器的维度表，再用自定义网络包通知客户端。不需要注册进 `LEVEL_STEM` 注册表（26.3 只在启动时读它）。
- 已创建的星球列表存进 SavedData（`data/universe/runtime_dimensions.dat`），记录每颗星球当前是否加载；服务器启动时只重建加载着的那些，其余的等有人前往时再加载。
- 没有玩家、也没有强制加载区块的星球维度，空闲一段时间后卸载，以节省内存。卸载时照原版关服的顺序：送走玩家、停用票据、等区块任务清空，再保存、关闭。S1 只做了手动卸载，空闲自动卸载在 M2 实现。
- 自己实现，不依赖 Infiniverse（它没有 26.3 版本，依赖的 NeoForge 方法已删除）。需要用访问转换器开放 `MinecraftServer.levels` 和 `storageSource`。
- 维度 tick 期间不能创建或卸载维度（`levels` 正在被遍历）。由方块、实体触发的创建要排进队列，在服务器 tick 末尾处理。
- 维度类型、群系、时间线都属于登录时同步的注册表，运行时不能新增。所以预置一组"环境模板"（见 §3.4）供星球挑选，星球之间的细节差异通过参数化生成和客户端渲染体现。
- 一个空闲维度约占 0.4 MB 堆内存，内存主要花在已加载的区块上。

### 3.3 星球地形

- 以原版的噪声地形生成器为基础，在代码里按星球参数组装噪声设置和密度函数（用直接 Holder，不注册），控制地形起伏、海平面、洞穴和地表方块。
- 群系：在数据包里预置一批"星球群系原型"，比如 30–40 个（陨坑原野、冰川、酸沼、晶簇林……），每颗星球从中选一部分。同一个原型在不同星球上的颜色由星球参数决定。
- 地物：矿脉节点、植物、遗迹结构由代码注册的地物按星球参数放置。
- 性能目标：星球区块的生成速度不低于原版主世界。
- 生态：植物、树、动物都按星球种子生成，固定的几种方块和两种实体按物种序号显示不同的外观，动物用自己的刷怪器按生境刷新，详见 [ADR-0007](adr/0007-planet-ecology.md)。

### 3.4 天空与环境

- 从 1.21.11 到 26.x，原版把天空颜色、雾色、环境光、昼夜变化等改成了数据驱动，对应环境属性（environment attributes）、时间线（timelines）和世界时钟（world clocks）。这些定义挂在维度类型、群系、时间线这些同步注册表上。
- 方案：预置 8–16 套"环境模板"，区分昼夜长度、大气色调、是否潮汐锁定，每颗星球选一套。具体差异（天空色调、恒星大小和颜色、其他行星的位置）由客户端按星球参数计算后覆盖。
- 自定义天空渲染：星空、恒星、同一恒星系的其他行星和卫星、戴森云和戴森球。
- 接入点（已在 26.3 源码中确认）：原版的 `DimensionSpecialEffects` 已经不存在。NeoForge 提供 `CustomSkyboxRenderer`，在 `RegisterCustomEnvironmentEffectRendererEvent` 里注册，再通过环境属性 `NeoForgeEnvironmentAttributes.CUSTOM_SKYBOX` 指定给维度类型或群系；渲染要用的数据在 `ExtractLevelRenderStateEvent` 里提取。同一事件还能注册自定义的云和天气渲染器。所有环境模板都指向同一个天空渲染器，由它按当前星球的参数绘制。
- 进出星球的过场：NeoForge 的 `RegisterDimensionTransitionScreenEvent` 按维度键注册切换画面，但只在客户端启动时触发一次，那时运行时维度还不存在。S1 保留事件对象，客户端得知新维度时再补注册，实测有效（`client.ClientDimensions`）。这依赖 NeoForge 的内部实现，长期方案（向 NeoForge 提 PR 或用 mixin）在 S4 做降落画面时定，见 ADR-0004。
- 渲染代码集中在 `client` 包，方便随版本调整。
- 光影兼容：M1 用 Iris 实测（26.3 已有 NeoForge 版），必要时提供简化渲染。
- S3 的实现：天体位置和配色在 `core.sky` 算，渲染器 `client.sky.PlanetSkyRenderer` 按参数画；每颗星球的天色、雾、云、日落、水色靠 mixin 往环境属性系统里插两层，不写数据包；天气按维度单独轮换。详见 [ADR-0006](adr/0006-planet-sky-and-weather.md)。

### 3.5 太空与飞船

- 飞船用方块拼装：驾驶台、推进器、燃料舱、脉冲引擎、座椅、船体板，加上任意别的方块。在驾驶台上组装后，方块变成一个实体 `universe:ship`，带着方块状态和方块实体数据飞；着陆后按住 Shift 停船，方块放回世界。
- 飞控、性能、组装连通性在 `core.ship`，服务端按驾驶员的输入算位置，客户端只插值。船身网格用原版方块模型烘焙，方块实体走原版渲染器。
- 每个恒星系一个太空维度（空区块、黑色天空，区块不存盘），天体按 `core.space.SpaceLayout` 摆在黄道面上。飞出星球维度顶部进入太空，飞进星球的大气球就降落到那颗星球，飞船连同乘客一起传送。
- 高速飞行的区块加载：S4 选了方案 A（真实坐标，飞船在太空维度里真实移动）。开脉冲引擎以 40 格/刻飞行时服务端平均 5.4 ms/刻，单人够用；方案 B（浮动原点）等多人时再评估。
- 详见 [ADR-0008](adr/0008-block-built-ships.md)。

### 3.6 工厂模拟

- 方块实体只是"壳"：保存位置和配置，具体逻辑交给 core 里的模拟对象。
- 传送带：相连的传送带合并成一条"线路"（`core.factory.BeltLine`），线路上的物品用紧凑的环形数组记录位置。每 tick 按线路批量推进，下游先算，而不是每段传送带各自 tick。
- 分拣器和设备按网络批量结算；渲染时按线路批量提交物品模型。
- 增删方块时只重建受影响的那一段线路（`split`、`concat`）。
- 性能预算：1 万段传送带、4 万个物品的推进在 2 ms/tick 以内。S5 的基准测试是 0.018 ms，详见 [ADR-0009](adr/0009-belts-and-power-core.md)。

### 3.7 电网

- 电力感应塔的覆盖范围决定设备属于哪个电网；连接距离内的塔合并成同一个网络（`core.power.PowerSystem`）。加塔时合并碰到的电网，拆塔时只在原来的电网里重新找连通部分，塔和设备按格子建空间索引。
- 每个电网每 tick 结算一次：发电、耗电、蓄电器充放电、满足率。200 台设备 0.005 ms，详见 [ADR-0009](adr/0009-belts-and-power-core.md)。
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

- 网络包用 NeoForge 的 `CustomPacketPayload` 加 `StreamCodec`，在 `RegisterPayloadHandlersEvent` 里注册。发往客户端的包在这里只登记编解码器，处理器在客户端的 `RegisterClientPayloadHandlersEvent` 里注册（见 §4）。
- 按恒星系订阅：客户端只接收所在恒星系的状态（戴森球、物流站等）。
- 世界级数据用 SavedData，玩家数据（外骨骼能量、发现记录）用数据附件（data attachments）。
- 每份存档数据都带版本号，加载时逐级迁移。注意 DFU 的 `optionalFieldOf(名称, 默认值)` 编码时会省略等于默认值的字段，版本号要用 `optionalFieldOf(名称)` 总是写出。

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
| `DimensionSpecialEffects` 已移除；自定义天空用 NeoForge 的 `CustomSkyboxRenderer` 加环境属性 `CUSTOM_SKYBOX`（见 §3.4） | 已确认，S3 在用 |
| 渲染管线 `RenderPipeline` 在 `com.mojang.renderpearl.api.pipeline`，不在旧的 blaze3d 包里；`RenderSystem`、`BufferBuilder`、`PoseStack` 仍在 `com.mojang.blaze3d` | 已确认，S3 在用 |
| 物品、流体、能量传输接口：`net.neoforged.neoforge.transfer`（`ResourceHandler`、`energy.EnergyHandler` 等） | 已确认，用法待核实 |
| 物品模型定义（`ClientItem`，对应 `assets/<命名空间>/items/`） | 已确认类存在 |
| `MinecraftServer` 没有公开的增删维度方法；NeoForge 以前提供的 `forgeGetWorldMap()`、`markWorldsDirty()` 在 26.3 已删除（26.1 还有）。`levels`（`LinkedHashMap`）在 tick 时被直接遍历，要用访问转换器开放（见 §3.2） | 已确认，S1 在用 |
| `ServerLevel` 构造器参数：`server, executor, levelStorage, levelData, dimension, levelStem, isDebug, biomeZoomSeed, customSpawners, tickTime`；executor 用 `Util.backgroundExecutor()`，主世界以外的维度用 `DerivedLevelData` | 已确认，S1 在用 |
| 区块票据随维度存盘：关服时 `ServerChunkCache#deactivateTicketsOnClosing()` 停用，启动时 `TicketStorage#activateAllDeactivatedTickets()` 和 NeoForge 的 `ForcedChunkManager.activateAllDeactivatedTickets(...)` 重新激活；运行时加载的维度要自己调用 | 已确认，S1 在用 |
| 玩家登录时由配置任务 `PrepareSpawnTask` 选出生维度：存档里的维度没加载时，退回世界出生点所在的维度，但坐标不变 | 已确认 |
| 服务器没人时按 `pause-when-empty-seconds`（默认 60 秒）暂停，暂停期间不触发 `ServerTickEvent` | 已确认 |
| `SavedDataType(Identifier, Supplier, Codec)`，用 `server.getDataStorage().computeIfAbsent(type)` 读写，文件在 `<世界>/data/<命名空间>/<路径>.dat`；维度目录是 `<世界>/dimensions/<命名空间>/<路径>/` | 已确认，S1 在用 |
| 发往客户端的包：`RegisterPayloadHandlersEvent` 里用 `playToClient(type, codec)` 只登记编解码器，客户端在 `RegisterClientPayloadHandlersEvent` 里注册处理器 | 已确认，S1 在用 |
| `RegisterDimensionTransitionScreenEvent` 只在客户端启动时触发一次，按维度键精确匹配（见 §3.4） | 已确认 |
| 传送用 `Entity#teleport(TeleportTransition)`；`TeleportTransition.PLACE_PORTAL_TICKET` 在目标点加 15 秒的传送门票据 | 已确认，S1 在用 |
| 命令权限写法：`.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` | 已确认，S1 在用 |
| 密度函数改为编译式：`compileSampler(CompileContext)` 返回 `DensitySampler`（`sampleVolume`/`sampleValue`），另有 `rewriteChildren`、`range()`（`net.minecraft.util.Interval`）、`domainAxes()`；二维函数按列填 `DensityBuffer` | 已确认，S2 在用 |
| `NoiseRouter` 只剩 8 个字段，含水层参数移到 `NoiseGeneratorSettings` 的 `Optional<Aquifer.Config>`；原 `SurfaceRules` 改名为 `levelgen.material`（`MaterialRules`、`MaterialCondition`，注册表 `MATERIAL_CONDITION_TYPE`） | 已确认，S2 在用 |
| `LevelStem(Holder<DimensionType>, ChunkGenerator, OptionalLong seedOverride)`：可以给单个维度换种子，原版噪声和洞穴随之改变 | 已确认，S2 在用 |
| 维度类型带时间线（`HolderSet<Timeline>`），同一时钟上的时间标记名必须唯一；不带周期、只有一个关键帧的时间线就是常量；床的规则是环境属性 `gameplay/bed_rule` | 已确认，S2 在用 |
| 群系的生物刷新改为环境属性；配置地物的键是 `ResourceKey<Feature>`；同一群系源里各群系的地物列表顺序要一致；原版矿石认 `stone_ore_replaceables` 标签 | 已确认，S2 在用 |
| "阻挡移动"改由方块标签决定：`blocks_motion_no_leaves` → `blocks_motion` → 高度图 `MOTION_BLOCKING`、窒息、传送落点、挡流体等。模组的实心方块不进这个标签，高度图会穿过整片地形 | 已确认，S2 踩过 |
| `CustomSkyboxRenderer#renderSky(LevelRenderState, SkyRenderState, Matrix4fc, GpuBufferSlice)` 返回 true 跳过原版天空；每帧数据在 `ExtractLevelRenderStateEvent` 里用 `state.setRenderData(ContextKey, ...)` 挂上，先判断 `state.customSkyboxRenderer` 是不是自己。`RenderSystem` 在渲染线程以外调用会报错，缓冲区要懒加载 | 已确认，S3 在用 |
| 自定义管线：`RenderPipeline.builder(snippets)`…`withVertexShader/withFragmentShader(Identifier)`、`withColorTargetState(new ColorTargetState(BlendFunction.X))`、`withVertexBinding(0, format)`，深度状态默认是不测深度；在 `RegisterRenderPipelinesEvent` 里登记。画的时候 `createCommandEncoder().createRenderPass(...)` 开渲染通道，`RenderSystem.bindDefaultUniforms(pass)`；每次绘制的参数用 `RenderSystem.getDynamicUniforms().writeTransform(modelView, colorModulator, modelOffset, textureMatrix)`，着色器里 `TextureMat[i]` 是矩阵的第 i 列 | 已确认，S3 在用 |
| 环境属性按层求值：维度、群系（`addStaticLayers`），时间线、天气（`addDynamicLayers`），客户端再加闪电。NeoForge 没有给这个构建过程开事件，要加层只能 mixin。时间线入夜时把天色、雾色、云色乘向黑色；`CLOUD_COLOR` 透明度为 0 就没有云；`FOG_END_DISTANCE` 默认 1024 | 已确认，S3 在用 |
| 雾色在 `FogRenderer` 里还会按夜视、水下视野把最亮的通道拉到 1：测试时玩家身上有夜视，雾会亮得不正常 | 已确认，S3 踩过 |
| 流体染色走 `ClientLevel#calculateBlockTint(BlockPos, ColorResolver)`，返回值必须是不透明的 ARGB（透明度 0 时水看不见） | 已确认，S3 在用 |
| 天气数据（`WeatherData`）全服一份，但每个能下雨的维度每刻都调用一次私有的 `ServerLevel#advanceWeatherCycle`；`Level` 的 `rainLevel`、`thunderLevel` 等字段是公开的，变化要自己用 `ClientboundGameEventPacket` 广播给维度里的玩家 | 已确认，S3 在用 |
| 降水类型由群系温度和 `has_precipitation` 决定；维度自己的存档数据用 `level.getDataStorage().computeIfAbsent(new SavedDataType<>(id, level -> ..., level -> codec))` | 已确认，S3 在用 |
| Mixin：在 `neoforge.mods.toml` 里用 `[[mixins]] config = "..."` 登记；运行时就是官方名，不需要 refmap | 已确认，S3 在用 |
| 地物是接口，实现写成 record，`MapCodec.unit(...)` 作编解码器；类型注册在 `Registries.FEATURE_TYPE`，配置地物和放置地物是数据包注册表（`FEATURE`、`PLACED_FEATURE`），放置地物要带 `BiomeFilter.biome()`，否则数据生成报错 | 已确认，植物在用 |
| 方块走哪个渲染层由贴图的透明度自动决定（`ChunkSectionLayer.byTransparency`），模型里不用再写 `render_type` | 已确认 |
| 方块不再需要 `codec()`；`VegetationBlock#mayPlaceOn`、`LeavesBlock(声音, 属性)`；`PushReaction` 里"被推就掉落"叫 `POPPED`；`isViewBlocking` 的谓词有 4 个参数 | 已确认 |
| 实体渲染改为提交式：`LivingEntityRenderer#submit` 调 `submitModel(model, state, ...)`，模型的 `setupAnim` 在真正绘制时才按状态执行；所以在 `super.submit` 前换掉 `model` 就能每个实体用不同的模型。`CameraRenderState` 在 `client.renderer.state.level` | 已确认，异星动物在用 |
| 自发光层照 `EyesLayer`：`collector.order(1).submitModel(..., RenderTypes.eyes(贴图), ...)`；`EntityModel(root)` 默认 `RenderTypes::entityCutout`（不剔除背面） | 已确认 |
| 运行时贴图：`new DynamicTexture(Supplier<String>, NativeImage)` 注册到 `TextureManager#register(Identifier, ...)`，用完 `release`；`NativeImage#setPixel` 收的是 ARGB | 已确认 |
| 长方体的贴图展开没变：顶面 (u+d, v)、底面 (u+d+w, v)，第二行依次是 -x 侧、正面（-z）、+x 侧、背面，宽各为 d、w、d、w | 已确认 |
| 实体类型用 `DeferredRegister.Entities#registerEntityType(名字, 工厂, 分类, builder -> ...)`；原版常量在 `EntityTypes`；`Entity#moveTo` 改名 `snapTo`；生成原因 `EntitySpawnReason`；存档读写用 `ValueOutput`/`ValueInput` | 已确认 |
| 体型可以直接用 `Attributes.SCALE`：碰撞箱（`getDimensions` 乘 `getScale()`）、渲染（`state.scale`）、影子都跟着缩放 | 已确认，异星动物在用 |
| 自定义刷怪器 `CustomSpawner#tick(ServerLevel, boolean)` 只在游戏规则 `spawn_mobs` 打开时调用；NeoForge 的 `ModifyCustomSpawnersEvent` 在 `ServerLevel` 构造器末尾触发，运行时创建的维度也会触发 | 已确认，`FaunaSpawner` 在用 |
| 游戏规则改名：`spawn_mobs`（原 doMobSpawning）、`advance_time`（原 doDaylightCycle）、`advance_weather` 等 | 已确认 |
| 配方也是数据包注册表的条目：数据生成用 `GatherDataRegistryEntriesEvent#recipe(RecipeProvider 的构造器)`；`RecipeProvider#simpleCookingRecipe` 会把配方存进 minecraft 命名空间，模组要自己指定 ID | 已确认 |
| 盔甲：`Item.Properties#humanoidArmor(ArmorMaterial, ArmorType)`；`ArmorMaterial` 是记录（耐久倍数、各部位护甲值、附魔能力、穿戴声音、韧性、击退抗性、修补材料标签、外观键）。外观键是 `EquipmentAssets.ROOT_ID` 下的 `ResourceKey`，对应 `assets/<命名空间>/equipment/<名字>.json`，贴图在 `textures/entity/equipment/humanoid/`、`humanoid_leggings/` | 已确认，外骨骼在用 |
| 数据附件同步：`AttachmentType.builder(...).sync((holder, player) -> ..., StreamCodec)`，之后 `setData` 自动发给对应的玩家；可变对象改完调 `syncData` | 已确认，外骨骼 HUD 在用 |
| 伤害类型是数据包注册表的条目：`GatherDataRegistryEntriesEvent#add(Registries.DAMAGE_TYPE, ...)`，标签继承 `DamageTypeTagsProvider(输出, 查找, 模组 ID)`（数据生成的查找里已经有本模组的条目）；死亡消息键是 `death.attack.<message_id>`；造成伤害用 `hurtServer(ServerLevel, DamageSource, float)`，来源 `level.damageSources().source(键)` | 已确认 |
| 重力、起跳速度、安全摔落高度、摔落伤害倍数都是会同步的属性（`Attributes.GRAVITY` 等）；`AttributeInstance#addTransientModifier` 加不存档的修饰。`EntityJoinLevelEvent` 在换维度、登录、重生、读档时都会触发 | 已确认，星球重力在用 |
| 玩家移动由客户端决定：要改本地玩家的速度，在客户端的 `PlayerTickEvent.Pre` 里 `setDeltaMovement`（这时还没执行 `aiStep`）；按键状态在 `LocalPlayer#input.keyPresses`（`Input` 记录），服务端能看到的是 `ServerPlayer#getLastClientInput()` | 已确认，喷气背包在用 |
| 原版 `minecraft:ores` 标签已经在 `blocks_motion_no_leaves` 里，矿石进 `ores` 就不用再单独加；`BlockTags` 里没有煤矿标签，要用 `BlockItemTags.COAL_ORES.block()`。矿石方块是 `DropExperienceBlock(IntProvider, 属性)`，掉落表照原版的 `createOreDrop` 或"精准采集分支 + 数量 + 时运加成 + 爆炸损耗" | 已确认，星球矿石在用 |
| 战利品表的数值提供器改为 `ContextIntProviders`、`ContextFloatProviders`；刷怪蛋是 `SpawnEggItem` 加 `Item.Properties#spawnEgg(type)`，每种刷怪蛋有自己的贴图；实体类型先于物品注册，物品属性里可以直接取实体类型 | 已确认 |
| 方块能不能整体搬走看 `BlockState#getRelocability(level, pos)`：`BlockRelocability.No` 不能搬，`BlockRelocability.Multiblock#isRelocatable(positions)` 判断床、门这类结构是否整个都在 | 已确认，飞船组装在用 |
| 不触发副作用地搬方块：移走时 `UPDATE_CLIENTS \| UPDATE_SKIP_ALL_SIDEEFFECTS`，放回时 `UPDATE_CLIENTS \| UPDATE_KNOWN_SHAPE` 再 `updateNeighborsAt`；方块实体用 `saveWithFullMetadata(registries)` 存、`BlockEntity.loadStatic(pos, state, tag, registries)` 或 `loadWithComponents(TagValueInput.create(reporter, registries, tag))` 读 | 已确认，飞船在用 |
| 服务端拿玩家的移动输入：`ServerPlayer#getLastClientInput()` 返回 `Input`（前后左右、跳跃、潜行、疾跑）；骑乘时按潜行下马的判断在 `Player#wantsToStopRiding` | 已确认，飞船在用 |
| 客户端输入换成了 SDL3：按键码是 SDL 扫描码（潜行默认 225 = 左 Shift），`InputConstants.isKeyDown` 读 `SDL_GetKeyboardState` | 已确认 |
| 客户端位置插值：实体覆写 `createInterpolationHandler()`，返回 `LinearInterpolationHandler.create(this, 刻数)`；`Entity#commonTick` 在 `tick` 之前插值 | 已确认，飞船在用 |
| 生成时带额外数据的实体实现 NeoForge 的 `IEntityWithComplexSpawn`（`writeSpawnData`/`readSpawnData`，参数是 `RegistryFriendlyByteBuf`） | 已确认，飞船在用 |
| `Entity#teleport(TeleportTransition)` 跨维度时带着乘客一起走；客户端收到乘坐数据包会显示原版的"按 Shift 下马"，同一刻发的动作栏提示会被盖掉 | 已确认，飞船在用 |
| 方块模型烘焙：`new ModelBlockRenderer(环境光遮蔽, 剔面, blockColors).tesselateBlock(BlockQuadOutput, x, y, z, BlockAndTintGetter, pos, state, model, seed)`；实体里画方块用 `collector.submitCustomGeometry(pose, RenderTypes.solidMovingBlock(), ...)` 和 `VertexConsumer#putBakedQuad(pose, quad, QuadInstance)` | 已确认，飞船在用 |
| 方块实体渲染也是提取加提交：`renderer.createRenderState()`、`extractRenderState(be, state, partialTicks, cameraPos, crumbling)`，再 `BlockEntityRenderDispatcher#submit(state, poseStack, collector, camera)`；光照在状态的 `lightCoords` 里 | 已确认，飞船在用 |
| 数据附件：`AttachmentType.builder(Supplier).serialize(MapCodec).copyOnDeath().build()`，注册表键 `NeoForgeRegistries.Keys.ATTACHMENT_TYPES` | 已确认，飞船在用 |
| `Tags.Blocks` 的公共标签在 26.3 标为待删除，改用 `Tags.BlockItems.X.block()` | 已确认 |
| F1 隐藏界面：`minecraft.gui.hud.isHidden()`（没有 `options.hideGui` 了），模组的 HUD 层要自己判断 | 已确认 |
| 区块存盘入口 `ChunkMap#save(ChunkAccess)`，不想存时 `chunk.tryMarkSaved()` 后返回 false；实体存在单独的 `entities/` 文件里，和区块存盘无关 | 已确认，太空维度在用 |
| 实体低于维度底部 64 格时 `checkBelowWorld` → `onBelowWorld`：普通实体直接删除，生物受伤 | 已确认 |
| `/tick query` 给出平均每刻耗时和 P50/P95/P99，测性能很方便 | 已确认 |
| 工具的挖掘规则是数据组件 `DataComponents.TOOL`：`new Tool(List<Tool.Rule>, 默认速度, 每块耗损, 创造模式能否破坏)`，规则用 `Tool.Rule.deniesDrops(HolderSet)`、`minesAndDrops(HolderSet, 速度)`；物品注册时拿方块标签用 `BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK)` | 已确认，多功能工具在用 |
| 代替玩家挖掉方块：`ServerPlayer#gameMode.destroyBlock(pos)` 走完整的破坏流程（事件、掉落、经验、工具耗损）；裂纹用 `level.destroyBlockProgress(编号, pos, 0–9)`，-1 清除，编号和玩家自己挖的分开 | 已确认，采矿激光在用 |
| 持续使用的物品：`Item#use` 里 `player.startUsingItem(hand)` 返回 `InteractionResult.CONSUME`，之后每刻调 `onUseTick(Level, LivingEntity, ItemStack, 剩余刻数)`；时长 `getUseDuration(ItemStack, LivingEntity)`，动作 `getUseAnimation` 返回 `ItemUseAnimation.NONE` 就不举手 | 已确认，采矿激光在用 |
| 服务端发粒子：`ServerLevel#sendParticles(选项, x, y, z, 数量, dx, dy, dz, 速度)`；彩色尘埃 `new DustParticleOptions(RGB, 大小)` | 已确认 |
| HUD 上把世界坐标投到屏幕：`gameRenderer.mainCamera()` 的 `position()`、`yRot()`、`xRot()`，再按 `options.fov()` 算焦距；HUD 层在 `RegisterGuiLayersEvent` 里 `registerBelow/registerAbove(VanillaGuiLayers.CROSSHAIR, ...)` | 已确认，扫描标记在用 |
| 遍历区块里的方块：`LevelChunkSection#hasOnlyAir()`、`maybeHas(谓词)` 先按调色板跳过整段；`getChunkSource().getChunkNow(x, z)` 只取已加载的区块 | 已确认，扫描在用 |
| 成书：`DataComponents.WRITTEN_BOOK_CONTENT` 是 `new WrittenBookContent(Filterable<String> 标题, 作者, 代数, List<Filterable<Component>> 页, resolved)`，页面可以是可翻译文字，客户端按自己的语言显示；`Filterable.passThrough(...)` | 已确认，探测器日志在用 |
| 世界出生点：`level.getRespawnData().globalPos().pos()`；信号篝火是 `CampfireBlock.SIGNAL_FIRE` | 已确认，坠毁点在用 |
| 方块标签 `minecraft:dirt` 只剩泥土、砂土、缠根泥土，不含草方块；草方块、灰化土、菌丝在 `grass_blocks`，几样合起来是 `substrate_overworld`。锁链叫 `Blocks.IRON_CHAIN`。没加载的区块 `Level#getHeight` 返回世界底部，要先 `getChunk` | 已确认，坠毁点、矿脉露头都踩过 |
| 登录的配置任务：NeoForge 的 `RegisterConfigurationTasksEvent`（模组总线）登记的任务排在原版 `PrepareSpawnTask` 之前；`ConfigurationTask#start` 可能在网络线程上调用，`tick()` 在服务器处理连接时调用，返回 true 算完成。玩家存档用 `PlayerList#loadPlayerData(NameAndId)` 读，维度在 `ServerPlayer.SavedPosition.MAP_CODEC` 里 | 已确认，运行时维度在用 |
| 重生：`PlayerList#respawn` 先算 `findRespawnPositionAndUseSpawnBlock`，维度不存在就退回世界出生点；之后触发 NeoForge 的 `PlayerRespawnPositionEvent`，可以换掉 `TeleportTransition` | 已确认，运行时维度在用 |
| 强制加载的区块：`ServerLevel#getForceLoadedChunks()` 只算原版 `/forceload` 的票据；NeoForge 的方块、实体加载器在 `TicketStorage#getBlockForcedChunks()`、`getEntityForcedChunks()`（`TicketTracker#isEmpty()`），`TicketStorage` 从 `level.getDataStorage().get(TicketStorage.TYPE)` 取 | 已确认 |

原则：拿不准的 API，先查源码再动手写，核实后把结果补进这张表。源码位置：

- Minecraft（含 NeoForge 补丁）：`build/moddev/artifacts/minecraft-patched-26.3.0.86-sources.jar`，IDE 里可以直接跳转。
- NeoForge 自己的类：Gradle 缓存里的 `neoforge-26.3.0.86-sources.jar`（`~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.3.0.86/`）。
| 燃料改成物品组件 `DataComponents.COOKING_FUEL`（`CookingFuel(ResolvableInt burnTime, ResolvableFloat speedMultiplier)`），NeoForge 的 `furnace_fuels` 数据映射没有了；读燃烧时间要 `ResolvableInt.getFromItem(stack, COOKING_FUEL, CookingFuel::burnTime, lootContext, 0)`，常量用 `new ResolvableInt.Constant(n)` | 已确认，火力发电厂在用 |
| 容器数据（`ContainerData`、`DataSlot`）同步时按 16 位有符号数传，大的数要拆成两格 | 已确认，设备界面在用 |
| 容器方块实体 `BaseContainerBlockEntity` 实现了 `Container` 时，拆掉方块会在 `preRemoveSideEffects` 里自动掉出物品；对外的物品能力用 `event.registerBlockEntity(Capabilities.Item.BLOCK, type, WorldlyContainerWrapper::new)` | 已确认，设备在用 |
| 菜单：`IMenuTypeExtension.create((id, inventory, buf) -> ...)`，打开时 `player.openMenu(provider, pos)` 把方块位置写进附加数据；界面在 `RegisterMenuScreensEvent` 里登记，背景画在 `extractBackground`，文字画在 `extractLabels`（坐标相对界面左上角），格子背景要自己画 | 已确认，设备界面在用 |
| `PoseStack` 转动用 `rotateDegrees(Axis.YP, 角度)`，没有 `mulPose(Quaternionf)` 了；方块实体渲染器分 `extractRenderState` 和 `submit` 两步，单独加载的模型在 `ModelEvent.RegisterStandalone` 里用 `SimpleUnbakedStandaloneModel.simpleModelWrapper(id)` 登记，`getModelManager().getStandaloneModel(key)` 取出后 `collector.submitBlockModel(...)` 画 | 已确认，风力涡轮机在用 |
| 活塞推不动的是 `PushReaction.IMMOVEABLE`；块元素模型的坐标范围是 -16 到 32，两格高的物品模型可以把上半截叠在 16 以上，但每个面都要写明 UV | 已确认 |
| 配方不再默认同步到客户端：`OnDatapackSyncEvent#sendRecipes(类型...)` 登记要发的类型，客户端在 `RecipesReceivedEvent` 里收；配方结果是 `ItemStackTemplate`，序列化器是 `RecipeSerializer(MapCodec, StreamCodec)` 记录 | 已确认 |
| 配方书类别是注册表 `Registries.RECIPE_BOOK_CATEGORY` 的条目（`new RecipeBookCategory()`），每个配方都要给一个；`isSpecial()` 为真的配方不进配方书，`/recipe give` 也给不了；`ServerPlayer.awardRecipes(配方)` 把配方加进配方书 | 已确认，设备配方和研究配方在用 |
| 输入换成 SDL 后，`MouseButtonEvent.button()` 的左键是 1、中键 2、右键 3（`InputConstants.MOUSE_BUTTON_LEFT` 等），不再是 GLFW 的 0、2、1；界面的 `mouseClicked(MouseButtonEvent, boolean)` 里按常量判断 | 已确认，配方设备界面在用 |
| 菜单按钮：客户端 `gameMode.handleInventoryButtonClick(containerId, 按钮号)` 发包，服务端调 `AbstractContainerMenu.clickMenuButton(player, 按钮号)`，返回 true 时同步菜单 | 已确认，选配方在用 |
| 界面上要盖在物品图标上面的半透明色块，先调 `GuiGraphicsExtractor.nextStratum()` 再画（之后画的东西在新的一层，盖在之前的物品上） | 已确认，配方设备界面在用 |
| `Inventory.placeItemBackInInventory` 多了预测参数，服务端用 `Prediction.SERVER_ONLY` | 已确认 |
| 地物分两层：`Registries.FEATURE_TYPE` 登记 `MapCodec`（代码），`Registries.FEATURE` 是数据包注册表，条目是实现 `Feature` 接口的记录。往原版生物群系加地物用 NeoForge 的 `BiomeModifiers.AddFeaturesBiomeModifier(HolderSet<Biome>, HolderSet<PlacedFeature>, GenerationStep.Decoration)`，注册表键 `NeoForgeRegistries.Keys.BIOME_MODIFIERS`，在数据生成里写出 | 已确认，主世界矿脉在用 |
| NeoForge 的物品传输：物品能力是 `ResourceHandler<ItemResource>`，相邻方块用 `BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, 位置, 面)` 缓存；塞进去用 `ResourceHandlerUtil.insertStacking(handler, ItemResource.of(stack), 数量, null)`，事务传 null 直接生效 | 已确认，设备出料在用 |
| 区块附件：`chunk.setData(类型, 值)` 会把区块标成要存盘，原地改了附件里的集合要自己 `chunk.markUnsaved()`；`getExistingDataOrNull` 不会新建 | 已确认，矿脉储量在用 |
| `/setblock` 末尾加 `strict` 不触发形状更新和邻居更新，测试时能分别放下两格高方块的上下半截 | 已确认 |
| 方块的 `useItemOn` 空手时也会调用（物品是空的）；要走到 `useWithoutItem` 得返回 `InteractionResult.TRY_WITH_EMPTY_HAND`，返回 `PASS` 就到此为止 | 已确认，传送带、分拣器踩过 |
| 物品传输的事务：`Transaction.openRoot()`、`Transaction.open(父事务)`，`commit()` 才生效，不提交就回滚；自己写 `ResourceHandler` 时用 `SnapshotJournal<T>`（`createSnapshot`、`revertToSnapshot`、`onRootCommit`），改动前调 `updateSnapshots(事务)` | 已确认，传送带的插入在用 |
| 方块实体：`onLoad`、`onChunkUnloaded`、`setRemoved` 管登记和退出；拆掉方块时掉东西放在 `preRemoveSideEffects(pos, state)`；同步用 `getUpdateTag` 返回 `saveCustomOnly(registries)`，`getUpdatePacket` 返回 `ClientboundBlockEntityDataPacket.create(this)`。存档的 `ValueOutput#store(名字, codec, 值)`、`ValueInput#read(名字, codec)` 返回 `Optional` | 已确认，传送带、分拣器在用 |
| 方块实体渲染器里画物品：`context.itemModelResolver()` 的 `updateForTopItem(ItemStackRenderState, 物品, ItemDisplayContext.FIXED, level, null, 种子)`，再 `renderState.submit(pose, collector, 光照, OverlayTexture.NO_OVERLAY, 0)` | 已确认，传送带、分拣器在用 |
| 按键：`KeyMapping.Category` 是记录 `new KeyMapping.Category(Identifier)`，语言键 `key.category.<命名空间>.<路径>`，在 `RegisterKeyMappingsEvent` 里 `registerCategory` 再 `register(按键)`；键盘类型叫 `InputConstants.Type.KEYBOARD`，键码用 `InputConstants.KEY_J` 这类常量（SDL 的值，不是 GLFW 的） | 已确认，科技树按键在用 |
| 当前界面挪到了 `Minecraft#gui`：`minecraft.gui.screen()`、`minecraft.gui.setScreen(界面)` | 已确认 |
| 普通界面 `Screen`：画在 `extractRenderState(GuiGraphicsExtractor, 鼠标x, 鼠标y, a)`，背景在 `extractBackground`；裁剪用 `enableScissor/disableScissor`，`nextStratum()` 之后要重新开裁剪；鼠标事件是记录 `MouseButtonEvent(x, y, buttonInfo)`，`button()` 按 SDL 编号 | 已确认，科技树在用 |
| 块元素模型的元素可以写 `"shade": false`（不按朝向变暗）和 `"light_emission"`，单独加载的模型转起来时用得上 | 已确认，研究站核心在用 |
| 数据包的 `pack.mcmeta` 写 `"min_format"`、`"max_format"`（26.3 的数据包版本是 121）；放进存档的 `datapacks` 目录后 `/reload` 就会启用，测试时用函数一次摆好整条产线 | 已确认 |
| 界面里画任意方向的线、浮点位置的贴图：`graphics.pose()` 是 JOML 的 `Matrix3x2fStack`，`translate`、`rotate`、`scale` 之后 `fill(0, 0, 1, 1, 颜色)` 就是一条斜线；`blit(RenderPipelines.GUI_TEXTURED, 贴图, …, 颜色)` 的最后一个参数给白色贴图上色。`textures/gui/` 下的贴图按路径直接用，不进图集 | 已确认，星图在用 |
| 动作栏消息是 `Player#sendOverlayMessage(Component)`，`displayClientMessage(…, true)` 没有了；`ChunkPos` 是记录，`ChunkPos.pack(BlockPos)`、`unpack(long)`；看得见某个区块的玩家 `chunkMap.getPlayers(ChunkPos, false)` | 已确认 |

## 5. 性能预算（目标）

- 服务端：中等规模的基地（5,000 段传送带、500 台设备、同时加载 3 颗星球）保持 20 TPS，本模组每 tick 耗时低于 10 ms。
- 客户端：完整显示戴森球时，中端显卡在 1080p 下帧率下降不超过 10%。
- 首次前往一颗星球（含创建维度）的等待时间低于 5 秒。

## 6. 测试

- core：JUnit 6 单元测试，随 `gradlew build` 运行，CI 上也跑。
- 数据：`SurvivalProgressionTest` 读 `src/generated/resources`，检查科技树和配方在生存里走得通（ADR-0011）；改了数据生成要先跑 `runData`。
- 游戏内行为：NeoForge GameTest（`gameTestServer` 运行配置），覆盖设备、传送带、维度切换等。
- 性能：core 层做基准测试（M1 定方案），游戏内用 Spark 剖析。
- 回归：保留几个固定种子的测试世界，每次升级 MC 版本后手动走一遍。

## 7. 版本跟进

见 [ADR-0001](adr/0001-target-version.md)。
