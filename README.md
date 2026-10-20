# Universe · 寰宇

Minecraft 模组：从方块世界出发，驾驶飞船探索程序生成的星系，建设跨星球的自动化工业，最终在恒星周围建造戴森球。

**当前版本：0.7.0-alpha，第一次公开测试。** 主线从坠毁的探测器一直做到戴森球和最后的"任务完成"，每一段都在游戏里验过，但还没有人从头到尾完整玩过一遍。剧情文字还是占位稿；正式版之前不保证存档兼容。

| | |
|---|---|
| Minecraft | 26.3（跟随最新正式版） |
| 加载器 | NeoForge 26.3.0.95 或更新 |
| Java | 25 |
| 可选 | JEI（配方分类）、Jade（设备状态提示） |

## 玩法

- 宇宙：每个世界一个星团，64 个恒星系，三四百颗能降落的星球，全部由世界种子算出来。主世界是母星，夜空里的月亮就是第一个目的地。
- 星球：8 类星球，各有地形、天空、天气、植物和动物。高温、严寒、毒性、辐射四种危害，重力各不相同。兴趣点、先驱者遗迹、守卫机。
- 生存：外骨骼管氧气和防护，多功能工具开采、扫描、战斗，技术模块升级。动物能喂养、驯服、骑乘。
- 飞船：用方块拼起来，起飞、太空飞行、降落、星际跃迁。武器、护盾、海盗袭击、小行星采矿，空间站里交易和接任务。探索车。
- 工业：传送带、分拣器、电网和矩阵研究，五种设备配方（冶炼、制造、精炼、化工、粒子对撞），六种矩阵和一棵科技树，研究完以后还有无限升级。行星内和星际物流，蓝图。
- 戴森球：编辑器里排轨道和球壳，弹射器发射太阳帆，发射井发射火箭搭骨架，射线接收站把能量收回地面。建到一半的戴森球就挂在天上。
- 手册和进度：游戏内手册 14 章，进度树"寰宇"跟着主线走。

玩法参考了《无人深空》和《戴森球计划》。本项目和这两款游戏的开发商没有关系，也没有用它们的美术和音效。

## 兼容

- JEI：每种设备配方一个分类，标出要研究的科技；设备界面里点进度箭头就能打开。
- Jade：指着设备显示功率、在做什么、缺什么；采矿机显示产量和储量，物流站显示每格的物品。
- Sodium、Iris：Sodium 0.9.2 在 NeoForge 26.3.0.86 和 26.3.0.95 上启动就崩溃（Sodium 自己的 mixin 对不上，和本模组无关），等能用的版本出来再测。
- 多人：两个客户端局域网联机测过。专用服务器能加载、游戏内测试能过，但还没长时间开服跑过。研究和剧情进度全服共享。

## 文档

- [立项书](docs/00-charter.md)：目标、范围、里程碑、风险
- [玩法设计](docs/01-game-design.md)
- [技术架构](docs/02-architecture.md)
- [路线图](docs/03-roadmap.md)
- [架构决策记录](docs/README.md#架构决策记录)

## 开发

### 环境要求

- JDK 17 或更高版本，用来运行 Gradle（由 `JAVA_HOME` 指定）。编译用的 JDK 25 由 Gradle 自动下载。
- 推荐 IntelliJ IDEA，直接打开项目目录即可导入。

### 常用命令

Windows 上把 `./gradlew` 换成 `.\gradlew.bat`。

```bash
./gradlew build       # 编译、运行单元测试、打包到 build/libs/
./gradlew test        # 只运行单元测试
./gradlew runClient   # 启动装有本模组的开发客户端
./gradlew runServer   # 启动开发服务器（首次运行需在 run/eula.txt 中同意 EULA）
./gradlew runData     # 数据生成，输出到 src/generated/resources/
./gradlew runGameTestServer  # 跑游戏内测试（GameTest），跑完退出
./gradlew runClient2 -PjoinServer=localhost:25599  # 第二个开发客户端（run2，用户名 Dev2），测多人用
```

### 目录结构

```
docs/                立项与设计文档
src/main/java/       模组代码，包名 io.github.sweetcornna.universe
src/main/templates/  neoforge.mods.toml 模板
src/main/resources/  资源（assets、data）
src/generated/       数据生成的输出，不要手动修改
src/test/java/       单元测试
```

## 许可证

- 代码：[GNU LGPL 3.0](LICENSE)（仅此版本）。LGPL 以 GPL-3.0 为基础，全文见 [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt)。
- 美术资源（`src/main/resources/assets/` 下的贴图、模型、音效、结构等）：[CC BY-NC-SA 4.0](LICENSE_ASSETS)。

选择理由见 [ADR-0002](docs/adr/0002-license.md)。
