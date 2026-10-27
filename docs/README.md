# 文档

| 文档 | 内容 |
|---|---|
| [立项书](00-charter.md) | 目标、范围、成长线、里程碑、风险 |
| [玩法设计](01-game-design.md) | 宇宙、星球、生存、飞船、工业、戴森球 |
| [技术架构](02-architecture.md) | 分层、包结构、技术方案、26.x 注意事项 |
| [路线图](03-roadmap.md) | 里程碑任务和时间表 |
| [发布](release/page.md) | Modrinth、CurseForge 的页面文字，图标和截图在 `release/` 目录 |

## 架构决策记录

重大的技术或设计决定写成 ADR，放在 `adr/` 目录，编号递增，不删除旧记录。某条决定被新的取代时，把旧记录的状态改成"已被 ADR-xxxx 取代"。

| 编号 | 标题 | 状态 |
|---|---|---|
| [0001](adr/0001-target-version.md) | 目标版本：跟随最新正式版 | 已采纳 |
| [0002](adr/0002-license.md) | 许可证：代码 LGPL-3.0-only，美术资源 CC BY-NC-SA 4.0 | 已采纳 |
| [0003](adr/0003-core-decoupling.md) | 纯 Java 核心与游戏适配层分离 | 已采纳 |
| [0004](adr/0004-planets-as-runtime-dimensions.md) | 每颗星球是一个运行时创建的维度 | 已采纳 |
| [0005](adr/0005-planet-terrain.md) | 星球地形：核心层高度场加原版噪声管线 | 已采纳 |
| [0006](adr/0006-planet-sky-and-weather.md) | 星球天空、大气与天气 | 已采纳 |
| [0007](adr/0007-planet-ecology.md) | 星球生态：植物和动物 | 已采纳 |
| [0008](adr/0008-block-built-ships.md) | 飞船：方块拼装、服务端飞控、太空维度用真实坐标 | 已采纳 |
| [0009](adr/0009-belts-and-power-core.md) | 工厂模拟的核心层：按线路推进的传送带，覆盖式电网 | 已采纳 |
| [0010](adr/0010-exosuit-and-hazards.md) | 星球生存：外骨骼、环境危害和重力 | 已采纳 |
| [0011](adr/0011-factory-adapters.md) | 工业的游戏适配层：设备、电网、传送带、配方和研究 | 已采纳 |
| [0012](adr/0012-interstellar-travel.md) | 星际航行与探索：星图、跃迁、发现与兴趣点 | 已采纳 |
| [0013](adr/0013-dsp-and-nms-core-loops.md) | 先复刻戴森球计划和无人深空的核心玩法 | 已采纳 |
| [0014](adr/0014-exploration-rewards.md) | 探索的回报：技术模块、兴趣点、武器和守卫机 | 已采纳 |
| [0015](adr/0015-logistics-and-midgame-industry.md) | 物流网络与中期工业 | 已采纳 |
| [0016](adr/0016-dyson-sphere.md) | 戴森球 | 已采纳 |
| [0017](adr/0017-space-and-trade.md) | 太空与贸易：空间站、信用点、小行星、飞船战斗、探索车、驯养和传送 | 已采纳 |
| [0018](adr/0018-endgame-and-content.md) | 终局与内容：反物质、宇宙矩阵、人造恒星、升级科技、通关、蓝图、指南 | 已采纳 |
| [0019](adr/0019-alpha.md) | Alpha：性能、兼容、多人和发布 | 已采纳 |
| [0020](adr/0020-second-gap-review.md) | 第二轮对照：两款原作剩下的缺口，M10–M17 | 已采纳 |
| [0021](adr/0021-grand-space.md) | 宏伟的太空：放大尺度，逐像素画星球，我的世界的画风 | 已采纳（M10）；画风部分已被 ADR-0025 取代 |
| [0022](adr/0022-factory-depth.md) | 工厂深度：增产剂、集装、电力补全、高级设备 | 已采纳（M11） |
| [0023](adr/0023-mecha.md) | 机甲：能量核心、飞行、建造无人机、物流配送 | 已采纳（M12） |
| [0024](adr/0024-ships-and-voyages.md) | 飞船与航行：评级、技术、买船、货船和护卫舰、跃迁档次、太空遭遇 | 已采纳（M13） |
| [0025](adr/0025-grounded-in-reality.md) | 以现实为底：写实的太空，模拟到现实水平但不伤可玩性 | 已采纳 |
| [0026](adr/0026-planet-life.md) | 星球生活：种植和就地补给、烹饪和精炼、守卫机、新兴趣点、武器、载具、水下、动物 | 已采纳（M14） |
| [0027](adr/0027-alien-civilizations.md) | 外星文明：种族、语言、声望、对话、礼物、公会 | 已采纳（M15） |
| [0028](adr/0028-dark-fog.md) | 黑雾：地面基地、进攻和防御、太空巢穴、行星护盾、建世界的选项 | 已采纳（M16） |
| [0029](adr/0029-original-sound.md) | 原创音效：合成、注册、机器的环境声、真空里的声音 | 已采纳（M17） |
| [0030](adr/0030-wrap-up.md) | 各系统的收尾：酸液、奇异星球、模组互通、戴森球编辑和零散的尾巴 | 已采纳（M17） |
| [0031](adr/0031-texture-art.md) | 材质：共用调色板、按色阶画的画布、设备运转时的发光层 | 已采纳（M17） |
| [0032](adr/0032-third-gap-review.md) | 第三轮对照：两款原作 2024 年以来加的东西，排出 M18、M19 | 已采纳 |
| [0033](adr/0033-fleets-and-logistics-dispatch.md) | 机甲舰队、机甲战斗、物流调度和配套的面板 | 已采纳（M18） |
| [0034](adr/0034-fishing-settlements-deep-space.md) | 钓鱼、聚落和深空 | 已采纳（M19） |
| [0035](adr/0035-release-1-0.md) | 1.0：发布前的检查、存档的承诺和版本号 | 已采纳（1.0.0 已发布） |
| [0036](adr/0036-chemistry-and-biology.md) | 化学与生物研究（M20）：就地取材、反应釜、微生物和基因、星球改造 | 已采纳 |

模板：

```markdown
# ADR-xxxx 标题

- 状态：提议 / 已采纳 / 已被 ADR-yyyy 取代
- 日期：YYYY-MM-DD

## 背景
## 决定
## 理由
## 后果
## 备选方案
```
