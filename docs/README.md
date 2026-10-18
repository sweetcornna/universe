# 文档

| 文档 | 内容 |
|---|---|
| [立项书](00-charter.md) | 目标、范围、成长线、里程碑、风险 |
| [玩法设计](01-game-design.md) | 宇宙、星球、生存、飞船、工业、戴森球 |
| [技术架构](02-architecture.md) | 分层、包结构、关键技术方案、26.x 注意事项 |
| [路线图](03-roadmap.md) | 里程碑任务和时间表 |

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
| [0009](adr/0009-belts-and-power-core.md) | 工厂模拟的核心：按线路推进的传送带，覆盖式电网 | 已采纳 |
| [0010](adr/0010-exosuit-and-hazards.md) | 星球生存：外骨骼、环境危害和重力 | 已采纳 |
| [0011](adr/0011-factory-adapters.md) | 工业的游戏适配层：设备、电网、传送带、配方和研究 | 已采纳 |

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
