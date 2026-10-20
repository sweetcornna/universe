# ADR-0002 许可证：代码 LGPL-3.0-only，美术资源 CC BY-NC-SA 4.0

- 状态：已采纳
- 日期：2026-10-16

## 背景

希望做到：

- 整合包可以收录，附属模组可以调用本模组；
- 别人不能把代码改了之后闭源发布；
- 美术资源不被拿去商用。作者的整合包 Essence Odyssey 使用 CC BY-NC-SA 4.0。

## 调研

- [Modrinth 许可指南](https://modrinth.com/news/article/licensing-guide/)：平台上最常见的是 MIT，其次是 LGPL-3.0。需要 copyleft 时推荐 LGPL-3.0，因为 GPL、AGPL 和链接 Minecraft 存在冲突。Creative Commons 不建议用于软件。
- [NeoForged 社区的开源许可指南](https://neoforged.net/personal/telepathicgrunt/open-source-licenses/)：单独使用 GPL 不适合 MC 模组；代码和美术资源应分开授权，分别放在 LICENSE 和 LICENSE_ASSETS，并在 README 里写清楚各自覆盖哪些文件。
- 先例：Applied Energistics 2 的代码用 LGPLv3、API 用 MIT、美术用 CC BY-NC-SA 3.0；Create、Mekanism、GeckoLib 用 MIT。

## 决定

- 代码用 GNU LGPL 3.0，仅此版本（LGPL-3.0-only），全文见 `LICENSE`。LGPL 建立在 GPL-3.0 之上，GPL 全文附在 `licenses/GPL-3.0.txt`。
- 美术资源用 CC BY-NC-SA 4.0，全文见 `LICENSE_ASSETS`。范围是 `src/main/resources/assets/` 下的贴图、模型、音效、结构文件等非代码资源。
- 以后给附属模组用的公开 API 包可以改用 MIT，到时另写 ADR。

## 理由

- LGPL 允许附属模组和整合包使用本模组，同时要求修改后的模组本身继续开源。
- CC BY-NC-SA 让美术资源不能商用，改编作品也必须用同样的许可，和作者的整合包一致。
- 两者都是常见许可证，Modrinth、CurseForge 都能正确标注。

## 后果

- 接受外部贡献前，要写明贡献内容按上述许可授权（必要时加 DCO 或 CLA）。
- 可以并入 MIT、Apache-2.0、LGPL-3.0 的代码，但要保留原许可声明；不能并入只用 GPL 授权的代码。
- 已知局限：如果有人修改后只在自己的服务器上运行、不对外分发，LGPL 不要求他公开源码。
- 发布时：Modrinth 选 LGPL-3.0-only，并在描述里说明美术资源的许可；CurseForge 选 Custom License，链接到仓库里的两个许可文件。
- 构建出的 jar 里附带 `LICENSE_universe` 和 `LICENSE_ASSETS_universe`。

## 备选方案

- MIT：最宽松，但别人可以闭源修改和商用，不符合上面的要求。
- All Rights Reserved：保护最强，但无法接受贡献，整合包和第三方分发也受限。
- GPL-3.0：和链接 Minecraft 存在冲突，社区不推荐。
- MPL-2.0：文件级 copyleft，也可行。LGPL 在 MC 社区更常见，所以没有选它。
