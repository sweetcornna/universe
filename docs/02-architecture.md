# 技术架构（草案 v0）

> 2026-10-16 · 标注"待 M1 验证"的部分在技术验证后定稿

## 1. 分层

| 层 | 内容 | 可以依赖 | 加载位置 |
|---|---|---|---|
| client | 渲染（天空、星球、戴森球、飞船）、HUD、界面 | 适配层、core | 只在客户端 |
| 适配层 | 方块、物品、实体、维度、网络包、存档、事件 | core、MC、NeoForge | 客户端和服务端 |
| core | 程序生成、轨道、工厂与电网模拟、物流排程、戴森球计算 | 只有 JDK | 客户端和服务端 |

- 依赖只能自上而下。core 不得引用 `net.minecraft`、`com.mojang`、`net.neoforged`，由 `CoreIsolationTest` 在每次构建时检查（[ADR-0003](adr/0003-core-decoupling.md)）。
- 玩法状态都在服务端计算（服务端权威），客户端只负责展示和输入。

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

## 3. 方案

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
- 数据包里预置一批"星球群系原型"，比如 30–40 个（陨坑原野、冰川、酸沼、晶簇林……），每颗星球从中选一部分。同一个原型在不同星球上的颜色由星球参数决定。
- 矿脉节点、植物、遗迹结构用代码注册的地物，按星球参数放置。
- 性能目标是星球区块的生成速度不低于原版主世界。
- 植物、树、动物都按星球种子生成，固定的几种方块和两种实体按物种序号显示不同的外观，动物用自己的刷怪器按生境刷新，详见 [ADR-0007](adr/0007-planet-ecology.md)。

### 3.4 天空与环境

- 从 1.21.11 到 26.x，原版把天空颜色、雾色、环境光、昼夜变化等改成了数据驱动，对应环境属性（environment attributes）、时间线（timelines）和世界时钟（world clocks）。这些定义挂在维度类型、群系、时间线这些同步注册表上。
- 预置 8–16 套"环境模板"，区分昼夜长度、大气色调、是否潮汐锁定，每颗星球选一套。具体差异（天空色调、恒星大小和颜色、其他行星的位置）由客户端按星球参数计算后覆盖。
- 天空由模组自己渲染，要画星空、恒星、同一恒星系的其他行星和卫星、戴森云和戴森球。
- 接入点按 26.3 源码确认过：原版的 `DimensionSpecialEffects` 已经不存在，NeoForge 提供 `CustomSkyboxRenderer`，在 `RegisterCustomEnvironmentEffectRendererEvent` 里注册，再通过环境属性 `NeoForgeEnvironmentAttributes.CUSTOM_SKYBOX` 指定给维度类型或群系；渲染要用的数据在 `ExtractLevelRenderStateEvent` 里提取。同一事件还能注册自定义的云和天气渲染器。所有环境模板都指向同一个天空渲染器，由它按当前星球的参数绘制。
- 进出星球的过场画面用 NeoForge 的 `RegisterDimensionTransitionScreenEvent` 按维度键注册，但这个事件只在客户端启动时触发一次，那时运行时维度还不存在。S1 保留事件对象，客户端得知新维度时再补注册，实测有效（`client.ClientDimensions`）。这依赖 NeoForge 的内部实现，长期方案（向 NeoForge 提 PR 或用 mixin）在 S4 做降落画面时定，见 ADR-0004。
- 渲染代码集中在 `client` 包，方便随版本调整。
- 光影兼容在 M1 用 Iris 实测（26.3 已有 NeoForge 版），必要时提供简化渲染。M9 实测：Iris 1.11.7 加 Sodium 0.9.3-beta.1、不装光影包时，星球天空和戴森球都正常。装 Complementary Reimagined 时戴森球也看得见（画在天空自己的 RenderPass 里），但本模组的维度被光影包画成主世界的天空（ADR-0019）。
- S3 里天体位置和配色在 `core.sky` 算，渲染器 `client.sky.PlanetSkyRenderer` 按参数画；每颗星球的天色、雾、云、日落、水色靠 mixin 往环境属性系统里插两层，不写数据包；天气按维度单独轮换。详见 [ADR-0006](adr/0006-planet-sky-and-weather.md)。

### 3.5 太空与飞船

- 飞船用方块拼装：驾驶台、推进器、燃料舱、脉冲引擎、座椅、船体板，加上任意别的方块。在驾驶台上组装后，方块变成一个实体 `universe:ship`，带着方块状态和方块实体数据飞；着陆后按住 Shift 停船，方块放回世界。
- 飞控、性能、组装连通性在 `core.ship`，服务端按驾驶员的输入算位置，客户端只插值。船身网格用原版方块模型烘焙，方块实体走原版渲染器。
- 每个恒星系一个太空维度（空区块、黑色天空，区块不存盘），天体按 `core.space.SpaceLayout` 摆在黄道面上。飞出星球维度顶部进入太空，飞进星球的大气球就降落到那颗星球，飞船连同乘客一起传送。
- 高速飞行时的区块加载，S4 选了方案 A（真实坐标，飞船在太空维度里真实移动）。开脉冲引擎以 40 格/刻飞行时服务端平均 5.4 ms/刻，单人够用；方案 B（浮动原点）等多人时再评估。
- 详见 [ADR-0008](adr/0008-block-built-ships.md)。

### 3.6 工厂模拟

- 方块实体只是"壳"：保存位置和配置，具体逻辑交给 core 里的模拟对象。
- 相连的传送带合并成一条"线路"（`core.factory.BeltLine`），线路上的物品用紧凑的环形数组记录位置。每 tick 按线路批量推进，下游先算；单段传送带没有自己的 tick。
- 分拣器和设备按网络批量结算；渲染时按线路批量提交物品模型。
- 增删方块时只重建受影响的那一段线路（`split`、`concat`）。
- 性能预算是 1 万段传送带、4 万个物品的推进在 2 ms/tick 以内，S5 的基准测试是 0.018 ms，详见 [ADR-0009](adr/0009-belts-and-power-core.md)。

### 3.7 电网

- 电力感应塔的覆盖范围决定设备属于哪个电网；连接距离内的塔合并成同一个网络（`core.power.PowerSystem`）。加塔时合并碰到的电网，拆塔时只在原来的电网里重新找连通部分，塔和设备按格子建空间索引。
- 每个电网每 tick 结算一次：发电、耗电、蓄电器充放电、满足率。200 台设备 0.005 ms，详见 [ADR-0009](adr/0009-belts-and-power-core.md)。
- 内部单位用焦耳和瓦特，和 FE 互通时按 1 FE = 1 J 换算（M3 定稿）。

### 3.8 物流

- 物流站的库存和供需设置存在全局 SavedData 里，运输由服务端统一排程，按距离和速度算出到达时间。
- 无人机和运输船只负责表现，它们丢失或未加载都不影响结算。
- 站点所在区块未加载时，站点之间的运输照常结算，但站点和周围设备之间的物品交换要等区块加载（M5 细化）。

### 3.9 戴森球

- 数据模型在 `core.dyson`：每颗恒星一个戴森球，包括戴森云轨道（半径、倾角、升交点，太阳帆按到期时间分批）和球壳层（半径、朝向、网格模板和密度，每个节点、框架、壳面的建造进度）。网格由模板算出来（`DysonGrid`），存档和同步只有进度（ADR-0016）。
- 服务端每秒结算一次太阳帆消散、火箭到达、节点吸帆铺壳和发电量，射线接收站按各自要的比例分电。
- 状态存进全局的 SavedData。客户端只拿自己所在恒星系的戴森球：进入时发一份完整的，之后有变化时每 5 秒最多发一次。
- 渲染时把节点、框架、壳面和帆投到天球上画，世界里不摆实体（`DysonSkyRenderer`，画在天空自己的 RenderPass 里，排在最后：本模组的维度由 `PlanetSkyRenderer` 调用，主世界由 `SkyRendererMixin` 调用）。星球上按观察者的位置算方向，再把角距离映射到恒星圆盘外面；太空维度里按 `SpaceLayout` 的真实位置。编辑器的三维视图在界面里自己画三角形（`GuiMesh`）。

### 3.10 网络与存档

- 网络包用 NeoForge 的 `CustomPacketPayload` 加 `StreamCodec`，在 `RegisterPayloadHandlersEvent` 里注册。发往客户端的包在这里只登记编解码器，处理器在客户端的 `RegisterClientPayloadHandlersEvent` 里注册（见 §4）。
- 客户端按恒星系订阅，只接收所在恒星系的状态（戴森球、物流站等）。
- 世界级数据用 SavedData，玩家数据（外骨骼能量、发现记录）用数据附件（data attachments）。
- 每份存档数据都带版本号，加载时逐级迁移。注意 DFU 的 `optionalFieldOf(名称, 默认值)` 编码时会省略等于默认值的字段，版本号要用 `optionalFieldOf(名称)` 总是写出。

### 3.11 与其他模组互通

- 设备通过 NeoForge 21.9 起的新传输接口（Transfer Rework）对外提供物品、流体和能量。
- 可选兼容放在 `compat` 包，只在对方模组存在时加载：JEI 显示配方，Jade 显示设备状态。
- 可选依赖在 26.3 上的情况（2026-10-16 查 Modrinth）：GeckoLib 5.5.7、JEI 31.14、Jade 26.3.5、Curios 17.0 beta、Sodium 0.9.3 beta、Iris 1.11.7 都有 26.3 的 NeoForge 版；EMI 还没有 26.x 版本。

## 4. 26.x 开发注意事项

网上的教程和 AI 助手的训练资料大多基于 1.21.1 及更早的版本，写代码时要留意下表这些变化。"已确认"表示在官方 MDK、NeoForge 公告或 26.3 的源码里见到过（2026-10-16）。

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
| 方块模型的元素超出 0–16（比如 -0.01 到 16.01 的发光外壳）时面要写明 `uv`，自动算的 UV 会越界，烘焙时报"Cannot compute translucency out of bounds"，整个模型丢失 | 已确认，跃迁引擎在用 |
| 视野：`ViewportEvent.ComputeFov` 的 `getFOV/setFOV`；最上层的 HUD 用 `RegisterGuiLayersEvent#registerAboveAll` | 已确认，跃迁画面在用 |
| 动作栏消息是 `Player#sendOverlayMessage(Component)`，`displayClientMessage(…, true)` 没有了；`ChunkPos` 是记录，`ChunkPos.pack(BlockPos)`、`unpack(long)`；看得见某个区块的玩家 `chunkMap.getPlayers(ChunkPos, false)` | 已确认 |
| `BlockPos#getCenter` 没有了，用 `Vec3.atCenterOf(pos)`；`Player#drop(ItemStack, boolean)` 多了 `Prediction` 参数，给玩家物品用 `getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY)`（放不下自动丢在脚下） | 已确认，发现日志在用 |
| 文本框 `EditBox(font, x, y, w, h, 名字)`、`setHint`、`setMaxLength`；按钮 `Button.builder(文字, 回调).bounds(...).build()`；控件有公开的 `visible`、`active` 字段；带控件的界面在 `extractRenderState` 里最后调 `super` 才会画控件 | 已确认，发现日志在用 |
| 物品染色：染色源是带 `MapCodec` 的 `ItemTintSource`，在 `RegisterColorHandlersEvent.ItemTintSources` 里 `register(Identifier, MapCodec)`；数据生成用 `ItemModelGenerators#generateItemWithTintedOverlay(物品, 染色源)`，底图不染色，`<名字>_overlay` 那层染色 | 已确认，技术模块的品级边框在用 |
| 物品上存一组物品：`ItemContainerContents`（`fromItems(列表)`、`copyInto(NonNullList)`、`nonEmptyItemCopyStream()`），自带 `CODEC`、`STREAM_CODEC` | 已确认，多功能工具的模块在用 |
| 打开带附加数据的菜单：`player.openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)`，客户端构造函数从缓冲读；`Slot#isActive()` 为假的格子不画也不能点 | 已确认，技术配置界面在用 |
| 受伤：`LivingIncomingDamageEvent` 在护甲之前，`getAmount/setAmount`；难度缩放在它之前，和平难度下生物攻击已经是 0，`/damage` 会报"目标免疫"。死亡掉落：`LivingDropsEvent#getDrops()` 里加 `player.createItemStackToDrop(物品, true, false)`，墓碑类模组能收到 | 已确认，个人护盾、货舱在用 |
| 拾取：`ItemEntityPickupEvent.Pre#setCanPickup` 用 `net.minecraft.util.TriState`（NeoForge 自己的 `TriState` 没有了）；`ItemEntity#hasPickUpDelay()`；背包空位 `Inventory#getFreeSlot()`、`getSlotWithRemainingSpace(物品)`，当前格 `getSelectedSlot()` | 已确认，货舱在用 |
| 十六色的方块和物品收进了记录：`Items.WOOL.white()`、`Items.WOOL.red()` 这样取，没有 `Items.WHITE_WOOL` 了 | 已确认 |
| 铜制方块和物品也收进了记录，按氧化程度取：`Items.LIGHTNING_ROD.weathering().unaffected()`（`WeatheringCopperCollection`），配方里不能直接写 `Items.LIGHTNING_ROD` | 已确认，信号增强器的配方在用 |
| 战利品表：`LootItem.lootTableItem(...)` 返回 `UniformContainerBase.Builder<?>`，没有 `LootPoolSingletonContainer` 了。自定义战利品函数继承 `LootItemConditionalFunction`，`simpleBuilder(condition -> ...)` 造 builder，`MapCodec` 登记在 `Registries.LOOT_FUNCTION_TYPE`；带战利品表的箱子方块实体继承 `RandomizableContainerBlockEntity`，世界生成时用 `RandomizableContainer.setBlockEntityLootTable(level, random, pos, 表)` 挂上表，第一次打开才生成物品 | 已确认，兴趣点的物资箱、随机模块在用 |
| 弹射物：继承 `ThrowableProjectile`，重写 `getDefaultGravity()`（0 就直线飞）和 `getAirDrag()`；`shoot(x, y, z, 速度, 散布)`；`canHitEntity`、`onHitEntity`、`onHit` 处理命中，`handleEntityEvent` 在客户端放命中特效。发射者经生成包同步，客户端的 `getOwner()` 也拿得到 | 已确认，等离子弹在用 |
| 伤害：`hurtServer(ServerLevel, DamageSource, float)`；`invulnerableTime` 是私有字段，连发武器每发都要算伤害时先 `setInvulnerableTime(0)`。原版击退（`dealDefaultKnockback` → `knockback(0.4, …)`）会把站在地上的目标往上抛，低重力下抛得很远；伤害类型进 `minecraft:no_knockback` 标签就不击退，自己用 `push(Vec3)` 推（`hurtMarked` 改名 `needsSync`，`push` 会自己置上） | 已确认，等离子弹在用 |
| `EntityType.Builder`：`noSave()`（不存盘）、`eyeHeight(高度)`、`notInPeaceful()`（默认和平难度也能存在）；`EntityType#getSpawnAABB(x, y, z)` 配合 `level.noCollision(AABB)` 找空位 | 已确认，守卫机在用 |
| `getNearestPlayer(实体, 距离)` 和 `getNearestPlayer(x, y, z, 距离, 是否排除创造)` 都跳过旁观者：用旁观模式看"附近没人就消失"的实体，实体会当着你的面消失 | 已确认，守卫机踩过 |
| `ParticleTypes.FLASH` 现在是 `ParticleType<ColorParticleOption>`，要给颜色；一部分 `SoundEvents` 常量是 `Holder<SoundEvent>`（`GENERIC_EXPLODE`、`CROSSBOW_LOADING_END`、`NOTE_BLOCK_*`、`UI_BUTTON_CLICK` 等），`level.playSound` 有接 `Holder` 的重载，`Entity#playSound` 要 `.value()` | 已确认 |
| 物品提示：`Item#appendHoverText(ItemStack, TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`，往 `Consumer` 里加行；`Component.keybind("key.…")` 显示玩家当前绑定的按键 | 已确认，多功能工具在用 |
| 界面里换行的文字：`graphics.textWithWordWrap(font, 文字, x, y, 宽, 颜色, 阴影)` 只管画；要按行数排下面的控件，用 `font.split(文字, 宽).size() * font.lineHeight` 算高度（中英文行数不同） | 已确认，信号增强器界面踩过 |
| 原版漏斗（`HopperBlockEntity.getContainerOrHandlerAt`）只要方块实体是 `Container` 就只走 `Container` 的接口，不查物品能力；同时实现两者的方块实体，`Container` 那一面也得能正常进出货 | 已确认，物流站踩过 |
| 太阳角度 `EnvironmentAttributes.SUN_ANGLE` 是按位置插值的属性：`environmentAttributes().getDimensionValue(...)` 直接抛异常，要用 `getValue(属性, BlockPos)`。单位是度，0 是正午、180 是午夜，正值时太阳在西边（原版天空先绕 Y 转 -90°，再绕 X 转这个角） | 已确认，太阳能板踩过（崩溃） |
| 在世界里画不是实体的东西：`ExtractLevelRenderStateEvent` 里用 `state.setRenderData(ContextKey, 数据)` 记下要画的（坐标减去 `event.getCamera().position()`），`SubmitCustomGeometryEvent` 里取出来提交；插值的时间用 `LevelRenderState.gameTime + worldPartialTicks` | 已确认，物流载具在用 |
| 方块实体渲染器画到方块外面：`getRenderBoundingBox(方块实体)` 返回要画的范围（可以是 `AABB.INFINITE`），`getViewDistance()` 改可见距离，`shouldRenderOffScreen()` 为真时方块本身出了视锥也画 | 已确认，弹射器、发射井、射线接收站在用 |
| 信标光束能朝任意方向画：`BeaconRenderer.submitBeaconBeam(poseStack, collector, BeaconRenderer.BEAM_LOCATION, 缩放, 动画时间, 起点, 高度, 颜色, 内半径, 外半径)` 沿 +Y 画，里面先平移 (0.5, 0, 0.5)；调用前 `poseStack.rotate(Quaternionf)` 转到目标方向 | 已确认，射线接收站的光束在用 |
| 叠加发光的四边形用 `RenderTypes.lightning()`（位置加颜色），全亮度的光照值是 `LightCoordsUtil.FULL_BRIGHT` | 已确认，弹射器的光迹在用 |
| 在天空上叠加：`RenderLevelStageEvent.AfterSky` 在原版天空或自定义天空画完以后触发，每个维度都有；事件不给渲染通道，要自己 `createRenderPass`。顶点放进常驻的 `GpuBuffer`（`USAGE_VERTEX \| USAGE_COPY_DST`），每帧用 `CommandEncoder.writeToBuffer(slice, ByteBuffer)` 写入；四边形的索引用 `RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)`；原版着色器 `core/position_color` 配 `PROJECTION`、`DYNAMIC_TRANSFORMS` 两个绑定组。这时模型视图栈里只有视角的旋转。开 Iris 光影包时这里画的东西会被盖掉，见下一行 | 已确认；戴森球原来用，M9 改到天空的渲染通道里 |
| 在天空的渲染通道里画：原版 `SkyRenderer.render` 整个天空用一个 `RenderPass`，主世界的太阳、月亮、星星在 `renderSunMoonAndStars(RenderPass, PoseStack, sunAngle, moonAngle, starAngle, MoonPhase, rainBrightness, starBrightness)` 里画，可以注入在它末尾；末地走另一条路，不调它。通道开着时不能 `writeToBuffer`，顶点要在 `ExtractLevelRenderStateEvent` 里先上传；`getSequentialBuffer(...).getBuffer(n)` 在通道里调没问题，原版也这样用。开 Iris 光影包时，原版天空的管线换成光影包的程序、画进光影包的缓冲区，同一个通道里后面画的也跟着进去；另开的通道绑的是原版的主帧缓冲，光影包最后一步会把它整个盖掉 | 已确认，戴森球的天空渲染在用（ADR-0019） |
| 原版太阳是边长 30 的贴图，放在 100 格外（`SkyRenderer.SUN_SIZE`、`SUN_HEIGHT`），亮的方块只占中间一半，视半径约 4.3°，比自定义天空里按真实大小画的恒星大得多。天色 `EnvironmentAttributes.SKY_COLOR` 是 `Vector3fc`，入夜时已经乘暗 | 已确认，戴森球踩过 |
| 界面里画任意三角形：实现 `GuiElementRenderState`（自己写顶点，带上 `pose`、裁剪框 `graphics.peekScissorStack()` 和范围），用 `graphics.submitGuiElementRenderState(...)` 提交 | 已确认，戴森球编辑器在用 |
| 判断语言键有没有翻译：`Language.getInstance().has(键)`（`I18n.exists` 没有了） | 已确认，科技树的效果说明在用 |
| `Level#addAlwaysVisibleParticle(...)` 不受粒子设置和距离的限制，几十格外的火箭尾焰和烟也看得见 | 已确认，发射井在用 |
| `OnDatapackSyncEvent#sendRecipes` 漏登记的配方类型，客户端拿不到，配方界面的清单是空的 | 已确认，M5 的化工厂踩过 |
| 无头验收用到的客户端接口：`Screenshot.grab(目录, 文件名, minecraft.gameRenderer.mainRenderTarget(), 1, 回调)` 截帧缓冲（窗口在屏幕外也行）；代替玩家右键方块用 `minecraft.gameMode.useItemOn(player, hand, BlockHitResult)`，服务端照样检查距离（约 5 格以内）；执行命令 `player.connection.sendCommand(不带斜杠的命令)`；创造模式悬停是改 `getAbilities().flying` 再 `onUpdateAbilities()` | 已确认，`DevScript` 在用 |
| 实体模型的提交：`OrderedSubmitNodeCollector.submitModel(model, state, poseStack, RenderType, light, overlay, outlineColor)`（另有带染色和 `UvMapping` 的 9 参数版本）；渲染类型在 `RenderTypes`（`entityCutout(Identifier)`、发光层 `eyes(Identifier)`）；`PoseStack` 转动用 `rotate(Quaternionf)`（没有 `mulPose` 了），配 `com.mojang.math.Axis` | 已确认，海盗战机的渲染器在用 |
| `ParticleTypes.FLASH` 带颜色参数，要 `ColorParticleOption.create(ParticleTypes.FLASH, argb)`；`SoundEvents.RAID_HORN` 是 `Holder` | 已确认，海盗在用 |
| 自定义统计：`DeferredRegister.create(Registries.CUSTOM_STAT, 模组ID)` 注册 `Identifier`，通用初始化里 `Stats.CUSTOM.get(id, StatFormatter.DEFAULT)` 一次；语言键是 `stat.<命名空间>.<路径>`，记分项准则写 `minecraft.custom:<命名空间>.<路径>` | 已确认，海盗的击落统计在用 |
| 代码里掷实体战利品表：`EntityType.getDefaultLootTable()` 返回 `Optional<ResourceKey<LootTable>>`，`server.reloadableRegistries().getLootTable(key)`；参数用 `LootParams.Builder(level).withParameter(THIS_ENTITY/ORIGIN/DAMAGE_SOURCE)`，`withOptionalParameter(ATTACKING_ENTITY/DIRECT_ATTACKING_ENTITY)`，`create(LootContextParamSets.ENTITY)`，再 `table.getRandomItems(params, consumer)` | 已确认，海盗战机的掉落在用 |
| 非生物实体：`hurtServer(ServerLevel, DamageSource, float)` 是抽象方法；不存档用 `shouldBeSaved()` 返回 false；客户端插值 `createInterpolationHandler()` 返回 `LinearInterpolationHandler.create(this, 刻数)` | 已确认，海盗战机在用 |
| 结算顺序：服务端按实体加入的先后结算，一刻里新加入的实体（比如刚打出的弹体）下一刻才开始动。所以打移动目标的提前量要多算一刻：目标排在开火者后面时，弹体飞第一步前目标已经又动了一步 | 已确认，飞船炮的提前量踩过 |
| 无头验收的截图分辨率跟着窗口走，显示器布局一变就可能变成 854×480；`options.txt` 里写 `overrideWidth`、`overrideHeight` 固定窗口大小 | 已确认，`headless.ps1` 在用 |
| 原版 `/tick freeze`、`/tick unfreeze` 可以冻住实体近看（骑着载具的玩家和载具不冻） | 已确认，海盗验收在用 |
| 区块卸载时 `PersistentEntitySectionManager` 只把 `shouldBeSaved()` 为真的实体存盘并移出世界；不存档的实体留在区段里，不移除也不更新，区块再加载时又出现。要清就自己按表 `discard()` | 已确认，小行星的生成器踩过 |
| `ModelBlockRenderer.tesselateBlock(BlockQuadOutput, …)` 按面给出每个四边形，层在 `quad.materialInfo().layer()`（叠在实心面上的贴花是 `CUTOUT`）。实体里用 `submitCustomGeometry` 分层提交时，几种渲染类型谁先画不一定：同一个方块的实心面和贴花分开交，贴花会被盖住，要整块放进镂空那一批 | 已确认，`BlockMesh` 在用 |
| 载具：控制者是本地玩家时 `isLocalInstanceAuthoritative()` 为真，由驾驶员的客户端算移动，服务端不模拟，收 `ServerboundMoveVehiclePacket` 的位置；`handleMoveVehicle` 在服务端重放一遍移动后，竖直方向的偏差判断写成了 `yDist > -0.5 \|\| yDist < 0.5`（恒真），竖直方向的偏差从不算"移动有误"，爬台阶、下坡都不会被拉回去 | 已确认，探索车在用 |
| 骑着载具时 `LocalPlayer.applyInput` 照样设 `xxa`（向左为正）、`zza`（向前为正），载具在 `tick` 里读控制者的这两个值；服务端按键状态用 `ServerPlayer.getLastClientInput()` | 已确认，探索车在用 |
| `VehicleEntity` 带受伤晃动（`setHurtTime`、`setHurtDir`）和累计伤害（`setDamage`），原版的船每刻回 1 点伤害；`destroy(ServerLevel, DamageSource)` 可覆盖，`kill()` 只移除、不掉落 | 已确认，探索车在用 |
| 第三人称镜头：`Camera.alignWithEntity` 先摆到眼睛，再沿视线往后拉（`CalculateDetachedCameraDistanceEvent` 改距离），准星永远是眼睛的视线，驾驶员的头正挡在准星上；要抬镜头得在往后拉之前 `move(0, 抬高, 0)`（`CameraMixin`），这时准星的视线也抬高了，按准星打东西要从抬高的那一点算 | 已确认，飞船和探索车在用 |
| 原版第三人称不画准星（`Gui` 只在第一人称画） | 已确认，探索车自己画激光准星 |
| 取方块状态的模型零件：`minecraft.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource, list)`，再用 `BlockStateModelPart.getQuads(Direction 或 null)` 拿四边形；资源包重载后 `getBlockStateModelSet()` 换成新对象，缓存的零件要清掉。半透明的方块预览用 `RenderTypes.translucentMovingBlock()`，`QuadInstance.setColor(ARGB)` 染色；方块外框用 `submitShapeOutline(pose, VoxelShape, RenderTypes.linesTranslucent(), 颜色, 线宽, false)` | 已确认，蓝图预览在用 |
| `Item.onItemUseFirst(ItemStack, UseOnContext)`（NeoForge 加的）在方块自己的右键之前跑，返回 SUCCESS 就不再打开设备界面 | 已确认，蓝图在用 |
| 拿着物品远距离右键：客户端监听 `InputEvent.InteractionKeyMappingTriggered`（`isUseItem()`，主手那一次），自己用 `player.pick(距离, partialTick, false)` 找目标，取消事件，再发数据包给服务端；服务端要自己查距离和区块是否加载 | 已确认，蓝图在用 |
| 存档的 `Codec` 里引用嵌套记录类的静态 `CODEC` 时，如果嵌套类先被初始化、它又反过来用到外层类的静态字段，外层的 `CODEC` 会拿到 null，到存档时才报空指针。用 `Codec.lazyInitialized(() -> Inner.CODEC)` | 已确认，`BlueprintData` 踩过 |
| 驯服的生物：实现 `OwnableEntity`，主人存成 `EntityReference<LivingEntity>`（`EntityReference.of/read/store`），同步用 `EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE`；`isAlliedTo` 两边都问 `considersEntityAsAlly`，它是 protected 的 | 已确认，异星动物在用 |
| 能骑的生物：`getControllingPassenger` 返回骑手后由 `tickRidden`、`getRiddenInput`、`getRiddenSpeed` 接管移动；蓄力跳实现 `PlayerRideableJumping`；坐的高度是 `getPassengerAttachmentPoint(Entity, EntityDimensions, float)` | 已确认，异星动物在用 |
| `lastHurtMob` 只在真打中时记下（玩家和生物的近战、箭、风弹），`/damage … by` 不记；按主人的 `lastHurtMob` 帮打的生物，测试时得真打一下 | 已确认 |
| 数据生成里配方的 `save(output, id)`：id 和默认的一样时直接抛异常，要用 `save(output)` | 已确认 |
| 鼠标键的编号换了：左键 `InputConstants.MOUSE_BUTTON_LEFT` 是 1、右键是 3（不是 GLFW 的 0、1），`AbstractWidget.isValidClickButton` 只认 1；自己造 `MouseButtonInfo` 时要用这些常量 | 已确认，开发脚本的 `click` 原来传 0，按钮点不动 |
| 方块换掉时的收尾 `affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)` 只在方块真的变了、而且带了标志 1（通知邻居）时调用；`/setblock … strict` 不带这个标志，不会触发 | 已确认，基地信标在用 |
| 玩家放下方块后调 `Block.setPlacedBy(Level, BlockPos, BlockState, @Nullable LivingEntity, ItemStack)`；按 UUID 查玩家名用 `server.services().nameToIdCache().get(UUID)`，返回 `Optional<NameAndId>` | 已确认，基地信标在用 |
| `Item.getName` 要传 `ItemStack`；只有 `Item` 时用 `new ItemStack(item).getHoverName()` | 已确认 |
| 界面输入事件是记录：`CharacterEvent(int codepoint)`、`KeyEvent(key, keycode, modifiers)`；按钮的悬停说明 `Button.Builder.tooltip(Tooltip.create(Component))` | 已确认，传送器界面在用 |
| 进度的数据生成：进度是数据包注册表，在 `GatherDataRegistryEntriesEvent` 里 `event.add(Registries.ADVANCEMENT, new AdvancementProvider(List.of(工厂)))`；子类继承 `AdvancementSubProvider`，构造参数是 `BootstrapContext<Advancement>`，查方块用 `output.lookup(Registries.BLOCK)`，`Advancement.Builder.save(output, "命名空间:路径")`。`display` 只收 `ItemLike` 的物品版本，方块要 `.asItem()`；根进度的背景图写 `Identifier`，指向 `textures/<路径>.png` | 已确认，"寰宇"进度在用 |
| 自定义进度触发器：继承 `net.minecraft.advancements.triggers.SimpleCriterionTrigger`，在 `Registries.TRIGGER_TYPE` 上注册；`ContextAwarePredicate` 没了，条件里的玩家谓词是 `Optional<Holder<LootItemCondition>>`，编解码用 `LootItemCondition.CODEC.optionalFieldOf("player")` | 已确认，`universe:milestone` 在用 |
| 工作台配方也不同步：要在客户端显示合成表，`sendRecipes` 里得加上 `RecipeType.CRAFTING`；`Ingredient.items()` 返回 `Stream<Holder<Item>>` | 已确认，手册的配方在用 |
| 原版自动换行（`StringSplitter.LineBreakFinder`）只在空格处断，一行里有过空格就退回到最后一个空格，中文夹空格时提前换行；本模组界面统一用 `client.TextWrap` | 已确认 |
| GameTest 是注册表条目：mod 总线的 `RegisterGameTestsEvent` 里 `registerEnvironment(Identifier)`、`registerTest(Identifier, new FunctionGameTestInstance(函数键, new TestData<>(环境, 结构, 最长刻数, 准备刻数, 必须通过)))`；测试函数注册在 `Registries.TEST_FUNCTION`（`Consumer<GameTestHelper>`）。只有 `GameTestHooks.isGametestEnabled()` 时才触发。结构从 `data/<命名空间>/structure/*.nbt` 读，原版的 `minecraft:empty` 只有 1×1×1。`gameTestServer` 不走 EULA，有失败时退出码非零 | 已确认，`gametest.UniverseGameTests` |
| 对局域网开放：`IntegratedServer.publishServer(MultiplayerScope.LAN, 允许命令, 端口)`，监听所有网卡（`startTcpServerListener(null, 端口)`）；内置服务器默认 `setUsesAuthentication(true)`，离线的开发客户端连不进来，主机先 `setUsesAuthentication(false)` | 已确认，开发脚本的 `lan` |
| 游戏规则：`level.getGameRules().get(GameRules.X)`、`set(GameRules.X, 值, server)`，`server` 传 null 时不触发 `onGameRuleChanged`；进度的聊天播报是 `GameRules.SHOW_ADVANCEMENT_MESSAGES` | 已确认，剧情补进度时用 |
| `neoforge.mods.toml` 的图标：方形图标用 `iconFile`（模组列表左边），横幅用 `bannerFile`；`logoFile` 已弃用，还写着会在启动时弹"模组加载警告"界面，挡住 `--quickPlay` | 已确认 |
| 客户端配置界面：`container.registerConfig(ModConfig.Type.CLIENT, SPEC)`，`container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)`；枚举项实现 `TranslatableEnum` 给出显示名，语言键 `<模组>.configuration.<键>`、`.tooltip`，分节标题 `<模组>.configuration.section.<模组>.client.toml` | 已确认，`client.ClientConfig` |

| 界面层的叠加混合：原版的 `position_tex_color.fsh` 丢掉透明度为 0 的像素，`BlendFunction.ADDITIVE` 那样"颜色乘 0 再加"的贴图画不出来。要叠加就用 `BlendFunction.LIGHTNING`（`SRC_ALPHA, ONE`），贴图存白色、亮度放在透明度里；管线从 `RenderPipelines.GUI_TEXTURED_SNIPPET` 派生，在 `RegisterRenderPipelinesEvent` 里注册 | 已确认，`client.sky.SunGlare` |
| 当前屏幕是 `minecraft.gui.screen()`（没有 `minecraft.screen` 字段了）；颜色插值是 `ARGB.srgbLerp`；这一帧实际的视野（飞行、疾跑会改）是 `camera.getFov()`，`options.fov()` 只是设置 | 已确认 |
| 换维度的加载画面：mod 总线的 `RegisterDimensionTransitionScreenEvent`，`registerIncomingEffect(维度, (tracker, reason) -> 画面)` 返回一个 `LevelLoadingScreen` 子类；画面在 `tick()` 里一看 `isLevelReady()` 就关，覆盖 `tick()` 可以多放一会儿。工厂被调用时客户端的玩家是新建的，还没坐上载具 | 已确认，`client.sky.AtmosphereTransitions` |
| `SubmitCustomGeometryEvent` 的 `PoseStack` 是单位矩阵，坐标相对摄像机；`RenderTypes.lightning()` 是 `POSITION_COLOR` 四边形、叠加混合、带背面剔除，顶点要按摄像机看过去逆时针排 | 已确认，`client.sky.SpaceDust` |
| 原版一分钟没有键鼠输入就把帧率压到 30（`FramerateLimitTracker`，选项 `inactivityFpsLimit`），十分钟后压到 10；脚本按的键不算输入，要调 `minecraft.getFramerateLimitTracker().onInputReceived()` | 已确认，开发脚本里在用 |
| `DimensionType` 没有 `ultraWarm()` 了，判断下界用 `level.dimension() == Level.NETHER`；`BaseEntityBlock` 不用再写 `codec()` | 已确认，`factory.power.GeothermalBlockEntity` |
| 物品能力的组合：`CombinedResourceHandler`（`net.neoforged.neoforge.transfer`）把几个 `ResourceHandler` 拼成一个；`ItemResource.with(组件, 值)` 带上数据组件，`isComponentsPatchEmpty()` 判断有没有组件；事务 `Transaction.openRoot()`，不 `commit()` 就回滚，`SnapshotJournal` 记快照 | 已确认，叠着的储物仓、物流站 |
| 生存里飞：给 `NeoForgeMod.CREATIVE_FLIGHT` 属性加一个临时修饰符，`IPlayerExtension.mayFly()` 是 `abilities.mayfly || 属性 > 0`；`ServerPlayer.tick` 在 `!mayFly()` 时把 `flying` 关掉；`Player.causeFallDamage` 对 `mayFly()` 的玩家不扣血，收回飞行后要自己给缓降 | 已确认，`mecha.Mecha` |
| 物品的燃烧时间是数据组件：`ResolvableInt.getFromItem(物品, DataComponents.COOKING_FUEL, CookingFuel::burnTime, 战利品上下文, 0)`。给玩家用的上下文是 `LootContextParamSets.EQUIPMENT`（`ORIGIN`、`THIS_ENTITY`），别的模组的燃料可能要别的参数，要接住异常 | 已确认，`mecha.Mecha.fuelEnergy` |
| `ContainerData` 按 short 发给客户端，超过 32767 的数要拆成两格（`MachineMenu.low/high/join`） | 已确认 |
| 游戏内测试里的玩家：`GameTestHelper.makeMockServerPlayerInLevel()` 标了待删除，它的连接没协商过模组的频道，`PacketDistributor.sendToPlayer` 会抛 `UnsupportedOperationException`（may not be sent to the client）。发之前用 `player.connection.hasChannel(TYPE)` 查；`Inventory` 没有 `countItem` 了 | 已确认，`mecha.Flyers` |
| 上一条的模拟玩家一进服务器就会收到登录时的同步包（天气、物流飞行之类），别的系统没查频道就整个游戏内测试服务器崩掉。游戏内测试不要建模拟玩家，把要测的逻辑拆成不依赖玩家的部分来测 | 已确认，`fleet_voyage_claim` |
| 染料改成一组颜色的集合：`Items.DYE` 是 `ColorCollection<Item>`，取黄色染料用 `Items.DYE.yellow()` | 已确认，配方在用 |
| 床的规则是维度类型属性 `minecraft:gameplay/bed_rule`：`can_sleep`、`can_set_spawn` 取 `always`、`when_dark`、`never`，可选 `destroy_on_use`、`destroy_on_leave`、`error_message`（文本组件） | 已确认，太空和货船船舱的维度类型 |
| 嵌套类型上的 `@Nullable`（JSpecify）要写在内层类名前面：`FreighterLayout.@Nullable Exterior`，写成 `@Nullable FreighterLayout.Exterior` 编译不过 | 已确认 |
| `Screen` 自己有 `width`、`height` 字段，子类再声明同名字段会把它遮住，居中算错，面板高度另起名字（`panelHeight`） | 已确认，货船控制台界面 |
| 聊天栏里能点、又不走命令的按钮：`new ClickEvent.Custom(Identifier, Optional<Tag>)`，点了客户端发 `ServerboundCustomClickActionPacket`，服务端在 NeoForge 的 `CustomClickActionEvent` 里接（`getPlayer()` 可能是 null，`getIdentifier()`、`getPayload()`，处理了就取消）。悬停文字是 `new HoverEvent.ShowText(Component)` | 已确认，`encounter.SpaceEncounters` 船长的报价 |
| 服务端改玩家朝向要用 `ServerPlayer.teleportTo(ServerLevel, x, y, z, Set<Relative>, yaw, pitch, resetCamera)`，只 `setYRot` 不会同步给客户端 | 已确认，`/universe encounter visit` |
| 给箱子设战利品表：`RandomizableContainer.setBlockEntityLootTable(BlockGetter, RandomSource, BlockPos, ResourceKey<LootTable>)`；拆方块不掉东西、不触发方块实体的副作用：`Block.UPDATE_CLIENTS`、`UPDATE_SUPPRESS_DROPS`、`UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS` 三个标志合起来（2、32、256） | 已确认，废弃货船 |
| 每个世界有自己的边界：`WorldBorder` 是存在世界数据里的 `SavedData`，`ServerLevel.getWorldBorder()` 从 `getDataStorage()` 取，不再从主世界转发；`isWithinBounds(x, z, margin)`、`getDistanceToBorder(entity)`，`setWarningBlocks` 控制屏幕边缘发红的距离 | 已确认，`world.planet.PlanetBorder` |
| `PushReaction` 改了名：`PUSH_PULL`、`PUSH`、`POPPED`（原来的 `DESTROY`，活塞推了就掉）、`IMMOVEABLE`、`IGNORE_ENTITY` | 已确认，资源植物 |
| `BlockState.blocksMotion()` 没有了，挡不挡路用 `isSolid()`（就是原来的 legacySolid）；`Player.drop(物品, 布尔)` 多了个 `Prediction` 参数，给玩家东西用 `getInventory().placeItemBackInInventory(物品, Prediction.SERVER_ONLY)`，背包满了自己掉在脚下 | 已确认，`survival.Shelters`、水培槽 |
| 开发脚本里先 `cmd` 换手里的东西再右键，服务端可能先处理右键：聊天命令要排队验签（`chatMessageChain`），右键包当刻就处理。中间等几刻 | 已确认，M14 验收 |
| 燃料是物品上的 `DataComponents.COOKING_FUEL`：原版用 `Item.Properties.cookingFuel(ResourceKey<ContextIntProvider>)`（`ContextIntProviders.COOKING_TIME_WOOD_BLOCKS` 这类数据驱动的键），模组物品可以直接给 `new CookingFuel(new ResolvableInt.Constant(刻数), new ResolvableFloat.Constant(1))`；读的时候 `ResolvableInt.getFromItem(物品, COOKING_FUEL, CookingFuel::burnTime, 战利品上下文, 0)` | 已确认，营养处理器、便携精炼机 |
| 拆方块时撒出容器里的东西在方块实体的 `preRemoveSideEffects(BlockPos, BlockState)` 里（`BaseContainerBlockEntity` 默认 `Containers.dropContents`），潜影盒覆盖成空的；东西跟着物品走靠战利品表的 `CopyComponentsFunction.copyComponentsFromBlockEntity(BLOCK_ENTITY).include(DataComponents.CONTAINER)`，放下时 `applyImplicitComponents` 读回来。创造模式拆不掉落，要在 `playerWillDestroy` 里自己掉一个带 `collectComponents()` 的物品 | 已确认，便携精炼机 |
| `ItemStack.getCraftingRemainder()` 返回可空的 `ItemStackTemplate`（牛奶桶 → 桶）；`Item.finishUsingItem(ItemStack, Level, LivingEntity)` 吃完的回调还在；喝的东西用 `Item.Properties.food(食物, Consumables.DEFAULT_DRINK)` | 已确认，营养处理器、菜 |
拿不准的 API 先查源码再写，核实后把结果补进这张表。源码在：

- Minecraft（含 NeoForge 补丁）：`build/moddev/artifacts/minecraft-patched-26.3.0.95-sources.jar`，IDE 里可以直接跳转。
- NeoForge 自己的类：Gradle 缓存里的 `neoforge-26.3.0.95-sources.jar`（`~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.3.0.95/`）。

## 5. 性能预算（目标）

- 服务端：中等规模的基地（5,000 段传送带、500 台设备、同时加载 3 颗星球）保持 20 TPS，本模组每 tick 耗时低于 10 ms。
- 客户端：完整显示戴森球时，中端显卡在 1080p 下帧率下降不超过 10%。
- 首次前往一颗星球（含创建维度）的等待时间低于 5 秒。

M9 实测（2026-10-20，本机，ADR-0019）：主世界 5,000 段传送带、500 台设备，另有三颗星球各 1,000 段、100 台，本模组每刻 1.16 ms（服务器每刻 6.25 ms）；戴森球天空在 1600×900 下折算到 60 fps，常见的四层球壳完整画质约多 2.5%，十层壳加十八条轨道的上限约多 10%（简化画质约 4%）；第一次去一颗星球，建维度 4 ms，命令到落地约 2 秒。

## 6. 测试

- core：JUnit 6 单元测试，随 `gradlew build` 运行，CI 上也跑。
- 数据：`SurvivalProgressionTest` 读 `src/generated/resources`，检查科技树和配方在生存里走得通（ADR-0011）；`BalanceTest` 拿同样的配方跑进度模型（`core.balance.Progression`），检查每档研究时间没有断崖（ADR-0018）。改了数据生成要先跑 `runData`。
- 游戏内行为：NeoForge GameTest，`./gradlew runGameTestServer` 跑完退出。现有：通电的制造台做出齿轮、没电的制造台停在缺电、传送带把物品送进储物仓、研究站攒点数（`gametest.UniverseGameTests`）。这也顺带验证了服务端不加载客户端的类。
- 脚本验收：`./gradlew runClient -PdevScript=<脚本>` 启动时，`client.dev.DevScript` 进存档后逐行执行脚本：执行命令、转视角、悬停、右键设备、按界面按钮、等设备动作一次、截图、退出，不碰键盘鼠标。截的是帧缓冲，窗口可以一直放在屏幕外；要在 `options.txt` 里关掉 `pauseOnLostFocus`，否则窗口没有焦点时游戏会暂停。
- 性能：core 层做基准测试（M1 定方案）。游戏内 `/universe perf` 按子系统计时，`/universe bench <传送带> <设备>` 搭参照基地；要看方法栈时再装 Spark。
- 多人：`runClient2`（游戏目录 `run2`，用户名 Dev2）。两个客户端都带开发脚本无头启动：主机用 `lan <端口>` 开放（只监听回环地址），`waitplayers <人数>` 等客人连上、退出；客人用 `-PjoinServer=localhost:<端口>` 直接连。
- 回归：保留几个固定种子的测试世界，每次升级 MC 版本后手动走一遍。

## 7. 版本跟进

见 [ADR-0001](adr/0001-target-version.md)。
