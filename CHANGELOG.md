# Changelog

本项目的所有重要变更都记录在此文件中。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [Unreleased]

### Planned
- 可插拔的配方克隆策略接口，支持自定义 `RecipeSerializer`
- 运行时资源包：为动态材料生成独立贴图与翻译
- Forge Config：把平衡系数、攻速、tag 开关变成可配置项
- 更多语种（ja_jp / ko_kr / ru_ru 等）
- 支持更多 Minecraft 版本线（多分支维护）

---

## [2.0.0] - 2025-09-25

本版本的主题是**模组兼容层**：把"与其他模组协作"从"碰巧能用"变成**有契约、有自检、有测试**的工程能力，
首个适配对象是附魔类模组 **Enchanting Infuser（附魔灌注台）**。
同时把附魔接受规则从物品类中抽出，成为可被运行时自检与单元测试共同验证的单一来源。

### Added
- **Enchanting Infuser 兼容**（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/enchanting-infuser)）：
  - 附魔灌注台现在可以给全能工具施加**镐/斧/铲共有附魔 + 武器附魔**（锋利、抢夺、火焰附加…），
    与附魔台、铁砧的行为完全一致。
  - `mods.toml` 新增对 `enchantinginfuser` 的**可选依赖**声明
    （`mandatory=false`、`ordering="AFTER"`、`side="BOTH"`）：本模组不硬依赖它，
    但加载顺序与兼容关系对玩家可见；未安装灌注台时行为完全不变。
  - 新增 `compat/EnchantingInfuserCompat`：检测到灌注台时，在公共初始化阶段自动执行**兼容自检**——
    对每个全能工具复现灌注台的两个判定问题（`ItemStack#isEnchantable()` 与
    `canApplyAtEnchantingTable`），并将结论写入日志；发现契约不一致时输出 WARN 与具体条目。
    自检为只读诊断，从不抛异常、不改变游戏行为。
- 新增 `compat/EnchantmentCompatibility`：附魔接受契约的**单一来源**
  （接受 `DIGGER` / `WEAPON` / `BREAKABLE` / `VANISHABLE`，其余一律拒绝），
  `OmniToolItem#canApplyAtEnchantingTable` 改为委托它。
- 新增单元测试 `EnchantmentCompatibilityTest`：逐个枚举全部 14 个 `EnchantmentCategory`，
  断言"只接受这四个"、拒绝护甲/弓/弩/三叉戟/钓竿/可穿戴，并覆盖 `null` 入参。

### Changed
- `OmniToolItem` 的附魔判定不再内联写死 `EnchantmentCategory.WEAPON` 判断，改为委托给 `EnchantmentCompatibility`。
- 文档不再写死 Minecraft / Forge 版本号：README 徽章改为版本无关，新增「兼容性」与「版本策略」两节，
  版本信息统一以 `gradle.properties` 为唯一来源，为后续扩展更多游戏版本做准备。
- README 新增「模组兼容」小节（含兼容矩阵与 Enchanting Infuser 适配说明），移除了「目录」章节。
- `HANDOFF.md` 新增 §4「模组兼容层（Enchanting Infuser）」：判定路径原理图、我们做的四件事、
  为什么不做更强兼容、以及对方更新后的重新验证步骤；原 §4~§8 顺延为 §5~§9，引用同步更新。
- `HANDOFF.md` 增加「版本范围说明」，明确文中出现的版本号均指当前检出的版本线。
- `CONTRIBUTING.md` 的开发环境要求改为引用 `gradle.properties` / `build.gradle`，不再写死版本。
- 版本号提升至 **2.0.0**（新增兼容层与附魔契约抽象属于对外行为增强，故做次版本线的整体提升）。

### Fixed
- 修复兼容自检的日志计数：原实现把「工具 × 附魔」的**组合数**当成附魔总数输出，
  会打印出 `weapon enchantments 42` 这类与注册表实际数量不符的值（6 个工具时被重复计数 6 次）。
  现在先读取一次附魔注册表，日志明确区分「注册表附魔数 / 实际校验的组合数 / 注册表中的武器附魔数」。
  该缺陷只影响日志可读性，自检的判定逻辑与结果本身是正确的。

### Verified
- `./gradlew build` 通过；`./gradlew test` 运行 30 个用例，覆盖材料推导（5）、材料名解析（5）与
  附魔契约（20，含全部 14 个 `EnchantmentCategory` 的参数化校验）。
- 兼容性依据为对方 1.20.1 分支源码的全量核对：其 Forge 侧判定只经过
  `canApplyAtEnchantingTable` / `canEnchant`，未直接读取 `EnchantmentCategory`。
- 已发布 jar 开箱核对：zip 结构完整、无测试类、`mods.toml` 占位符全部展开且 version 与
  `gradle.properties` 一致、附件 sha256 与本地构建一致。

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
- 自定义 `RecipeSerializer` 产出的镐子无法被克隆，仅记录日志并跳过（见 `HANDOFF.md` §6.1）。
- 运行时注册的物品使用原版模型的兜底映射，尚无独立贴图与翻译（见 `HANDOFF.md` §6.2）。
- 复用原版 Tier 的模组材料会映射到该原版 Tier 对应的全能工具（见 `HANDOFF.md` §6.3）。

---

[Unreleased]: https://github.com/zzy89216-gif/Minecraft-All-in-one-tool/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/zzy89216-gif/Minecraft-All-in-one-tool/releases/tag/v1.0.0
