# Universe · 寰宇

Minecraft 模组：从方块世界出发，驾驶飞船探索程序生成的星系，建设跨星球的自动化工业，最终在恒星周围建造戴森球。

**当前阶段：M2 首飞（开发中），还没有发布版本。** 开发环境里已经能探索程序生成的星球（地形、天空、天气、动植物各不相同），用方块拼装飞船飞到卫星和其他行星，穿外骨骼在没有大气的星球上生存，采集外星矿物并冶炼。

| | |
|---|---|
| Minecraft | 26.3（跟随最新正式版） |
| 加载器 | NeoForge 26.3.0.86 |
| Java | 25 |

## 计划中的玩法

- 程序生成的星团：几十个恒星系，上百颗风格各异的星球
- 飞船：起飞、太空飞行、着陆、星际跃迁
- 外骨骼和多功能工具，应对各星球的环境危害
- 传送带、分拣器、电网、科研，行星内和星际物流
- 戴森云和戴森球，在天空中一点点成形

玩法灵感来自《无人深空》和《戴森球计划》。本项目与这两款游戏的开发商没有任何关联，也不使用它们的美术和音效。

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
