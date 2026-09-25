# Changelog

本项目的所有重要变更都记录在此文件中。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [Unreleased]

### Changed
- 文档不再写死 Minecraft / Forge 版本号：README 的徽章改为版本无关，新增「兼容性」与「版本策略」两节，
  版本信息统一以 `gradle.properties` 为唯一来源，为后续扩展更多游戏版本做准备。
- README 移除了「目录 · Table of contents」章节。
- `HANDOFF.md` 增加"版本范围说明"，明确文中出现的版本号均指当前检出的版本线。
- `CONTRIBUTING.md` 的开发环境要求改为引用 `gradle.properties` / `build.gradle`，不再写死版本。

### Planned
- 可插拔的配方克隆策略接口，支持自定义 `RecipeSerializer`
- 运行时资源包：为动态材料生成独立贴图与翻译
- Forge Config：把平衡系数、攻速、tag 开关变成可配置项
- 更多语种（ja_jp / ko_kr / ru_ru 等）
- 支持更多 Minecraft 版本线（多分支维护）

---

## [1.0.0] - 2025-09-25

首个正式版本，也是本项目的第一条支持线。该版本线的目标平台为
Minecraft **1.20.1** / Forge **47.x** / Java **17**（版本常量见 `gradle.properties`）。

### Added

**核心物品**
- 新增"全能工具"物品，同一件物品同时具备镐、斧、铲的挖掘能力与剑的攻击能力。
- 六种原版材料版本：`omni_tool_wood`、`omni_tool_stone`、`omni_tool_iron`、`omni_tool_gold`、
  `omni_tool_diamond`、`omni_tool_netherite`，注册名统一以 `omni_tool_` 为前缀。
- 挖掘判定完全基于原版 BlockTag（`mineable/pickaxe`、`mineable/axe`、`mineable/shovel`），
  不使用方块白名单，因此任何挂在原版 tag 下的模组方块都自动支持。
- 挖掘速度按方块所属 tag 匹配对应工具类型，并统一乘以 **0.8** 的平衡系数
  （下限 1.0），实现"多功能但不专精"。
- 攻击属性采用剑的伤害公式 `3 + Tier 攻击加成`，攻速为 **1.4**（纯剑为 1.6）。
- 耐久、附魔能力、修理材料直接沿用该材料 Tier 的原值。
- 附魔同时支持镐/斧/铲/剑四类共有附魔与武器类附魔（通过 Forge 的
  `canApplyAtEnchantingTable` 钩子放行 `WEAPON` 类别）。

**动态材料支持（核心特性）**
- 新增运行时材料发现：注册阶段扫描物品注册表中所有 `PickaxeItem`，
  为尚未覆盖的 `Tier` 自动注册对应的全能工具物品，无需静态白名单。
- 新增动态配方克隆：在服务端 `OnDatapackSyncEvent`（先于原版配方同步包构建）中
  遍历全部配方，把输出为 `PickaxeItem` 的有序/无序工作台配方克隆为全能工具配方，
  保持材料与摆放位置完全一致，仅替换输出物。
- 克隆具备幂等性：已经可以合成的全能工具不会被重复生成配方。
- 找不到对应 Tier 的全能工具时优雅跳过并记录日志，任何异常都会被捕获，绝不崩溃。
- 动态注册的物品在客户端自动映射到 harvest level 最接近的原版模型，避免丢失材质。

**数据生成**
- 六个原版 Tier 的配方、物品模型、中英双语语言文件、物品标签全部由 `runData` 产出，
  生成结果提交在 `src/generated/resources`。
- 设计并实现了独特的"四器融合"3x3 配方形状（`MMM / MSM / " S "`），
  既不是镐子的"品"字形，也在视觉上表达四种工具共用一条中轴与握柄。
- 新增 `omni_tool:omni_tools` 物品标签，便于其他模组与数据包引用整套工具。

**工程**
- 采用 ForgeGradle 6 的标准 MDK 工程结构，Java 17。
- 分层架构：`registry` / `item` / `recipe` / `event` / `client` / `datagen`，依赖方向单向。
- 新增 JUnit 5 单元测试，覆盖材料推导的平衡数值、Tier 属性继承与动态材料命名解析。
- 新增 `scripts/upload_and_cleanup.sh`：读取 `.env` 中的令牌，分批提交、推送、
  校验远程 SHA，校验通过后经确认再清理本地构建产物与缓存。
- 新增 `.env.example` 模板；`.gitignore` 排除 `build/`、`.gradle/`、`run/`、`run-data/`、
  `.env` 及任何密钥文件。

**文档**
- `README.md`：项目介绍、特性、兼容性与版本策略、数值表、配方展示、安装与构建方式、目录结构。
- `HANDOFF.md`：架构说明、动态克隆文字版原理图、当前版本线的原版 API 陷阱清单、
  已知局限性、后续扩展方向、排错指南、发布清单。
- `CONTRIBUTING.md`：代码风格、提交规范、测试与 PR 流程。
- `docs/ARCHITECTURE.md`、`docs/RECIPES.md`：分层架构细节与配方/数值推导。

### Known Issues
- 自定义 `RecipeSerializer` 产出的镐子无法被克隆，仅记录日志并跳过（见 `HANDOFF.md` §5.1）。
- 运行时注册的物品使用原版模型的兜底映射，尚无独立贴图与翻译（见 `HANDOFF.md` §5.2）。
- 复用原版 Tier 的模组材料会映射到该原版 Tier 对应的全能工具（见 `HANDOFF.md` §5.3）。

---

[Unreleased]: https://github.com/zzy89216-gif/Minecraft-All-in-one-tool/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/zzy89216-gif/Minecraft-All-in-one-tool/releases/tag/v1.0.0
