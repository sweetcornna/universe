# Universe 模组：工作说明

## 项目

- Minecraft 模组 Universe（寰宇）：MC 生存 + 无人深空式的程序化探索 + 戴森球计划式的工业和戴森球。
- 先读 `docs/00-charter.md`（立项书），当前进度和下一步看 `docs/03-roadmap.md`。
- 用户用中文交流；文档、注释、提交说明用中文，代码标识符用英文。

## 版本与环境

- Minecraft 26.3 + NeoForge 26.3.0.86，Java 25，Gradle 9.2.1，ModDevGradle 2.0.148。版本号集中在 `gradle.properties`。
- 策略是跟随最新正式版，升级按 `docs/adr/0001-target-version.md` 的步骤做。
- 本机 `JAVA_HOME` 是 JDK 21（REA 自带的 Temurin），只用来运行 Gradle；编译用的 JDK 25 由 Gradle 工具链自动下载到 `~/.gradle/jdks`。
- 本机下载 Gradle 发行包等较慢（约 0.7 MB/s），偶尔会超时。wrapper 的超时已调到 60 秒；再失败就重试，或用 `curl -C -` 续传。

## 命令

- 构建并运行单元测试：`./gradlew build`（PowerShell 下用 `.\gradlew.bat build`）
- 只跑单元测试：`./gradlew test`
- 数据生成：`./gradlew runData`
- 开发客户端：`./gradlew runClient`。会在用户桌面上打开游戏窗口，运行前先问用户。
- 开发服务器 `runServer` 需要在 `run/eula.txt` 同意 EULA，这必须由用户本人决定。

## 代码约定

- 包名 `io.github.sweetcornna.universe`，模组 ID `universe`；`Universe.id("path")` 生成本模组的 `Identifier`。
- 分层：core（纯 Java）← 适配层 ← client（`docs/02-architecture.md` §1–2）。core 及其子包不得 import `net.minecraft`、`com.mojang`、`net.neoforged`，`CoreIsolationTest` 会检查。新逻辑优先写进 core，并配上单元测试。
- 只在客户端用到的代码放在 `client` 包或 `UniverseClient`，免得专用服务器加载客户端类。
- 注册统一用 `DeferredRegister`，集中放在 `registry` 包。
- 方块状态、模型、配方、战利品表、语言文件优先用数据生成；`src/generated/` 下的文件不要手改。
- 语言文件同时维护 `zh_cn` 和 `en_us`。
- `core.procgen.Seeds` 的派生结果属于存档格式，不要随意修改（`SeedsTest` 固定了具体数值）。

## 26.x API

- 训练资料和网上教程大多是 1.21.1 及更早的版本。写 MC 或 NeoForge 相关的代码前，先在源码里确认类名和方法签名，不要凭记忆。
- 源码（首次构建后才有）：
  - Minecraft（含 NeoForge 补丁）：`build/moddev/artifacts/minecraft-patched-26.3.0.86-sources.jar`
  - NeoForge 自己的类：`~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.3.0.86/*/neoforge-26.3.0.86-sources.jar`
  - 查法：`unzip -l <jar> | grep <类名>` 找路径，`unzip -p <jar> <路径>` 看内容。
- 已知的变化见 `docs/02-architecture.md` §4；核实了新的变化，就补进那张表。

## 文档

- 重大的技术或设计决定写成 ADR，放在 `docs/adr/`，模板见 `docs/README.md`。
- 里程碑的进度有变化时，更新 `docs/03-roadmap.md` 里的勾选状态。
