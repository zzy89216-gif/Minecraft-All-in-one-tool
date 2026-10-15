# Omni Tool · 全能工具

> [!WARNING]
> **项目已归档，停止维护**
>
> 由于个人学业原因及个人安排，项目作者决定停止本项目的后续开发与维护。
>
> 本项目不再提供功能更新、问题修复或新版本发布。现有代码、文档及历史发行版本继续保留，供查阅、学习和自行使用。
>
> 感谢所有曾经关注、使用或为本项目提供帮助的人。

> 一把工具，四种用途：镐、斧、铲、剑合一。
> One tool, four jobs: pickaxe, axe, shovel and sword in a single item.

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Mod Loader](https://img.shields.io/badge/Mod%20Loader-Forge-e04e14.svg)](#兼容性--compatibility)
[![Java](https://img.shields.io/badge/Java-17%2B-007396.svg)](#兼容性--compatibility)
[![Minecraft](https://img.shields.io/badge/Minecraft-%E8%A7%81%E5%85%BC%E5%AE%B9%E6%80%A7%E8%A1%A8-3c8527.svg)](#兼容性--compatibility)

具体支持的 Minecraft / Forge 版本见下方「兼容性」章节。工程内的版本常量集中在 [`gradle.properties`](gradle.properties)，本文件不再重复写死版本号。

---

## 项目简介 · Introduction

**Omni Tool（全能工具）** 是一个 Minecraft Forge 模组，添加了一种「万能工具」：同一个物品同时具备镐、斧、铲的挖掘能力与剑的攻击能力。

与简单地把四种工具拼在一起的常见做法不同，本项目将两个问题作为核心设计目标：

1. 挖掘判定基于原版 BlockTag（`mineable/pickaxe`、`mineable/axe`、`mineable/shovel`），而不是硬编码方块白名单。因此，其他模组新增且使用原版对应 Tag 的方块，也能自动获得相应的挖掘支持。
2. 配方在运行时动态生成：能够合成镐子的材料，包括其他模组添加的自定义材料，在满足对应条件时也可以自动获得全能工具配方，无需为每种材料手动编写配方。

本项目现已归档，以上内容用于记录现有版本的设计与实现。

---

## 核心特性 · Features

| # | 特性 | 说明 |
|---:|---|---|
| 1 | 四器合一 | 镐、斧、铲、剑四种能力集于一件物品 |
| 2 | Tag 驱动挖掘 | 依据方块所属的 `mineable/*` Tag 选择对应工具类型的挖掘速度 |
| 3 | 平衡而非全能 | 每一类挖掘速度为对应专用工具速度的 80%，不超过对应专用工具 |
| 4 | 剑式攻击 | 伤害采用剑的公式 `3 + Tier 攻击加成`；攻速为 1.4，低于普通剑的 1.6 |
| 5 | 沿用 Tier 耐久 | 耐久直接取自对应材料的 Tier，例如木质工具 59 点、下界合金工具 2031 点 |
| 6 | 四类附魔支持 | 支持镐、斧、铲共有的附魔，以及锋利、抢夺、火焰附加等武器附魔 |
| 7 | 动态材料支持 | 运行时扫描已注册的 `PickaxeItem`，为尚未注册对应全能工具的 Tier 创建工具 |
| 8 | 动态配方克隆 | 服务端加载配方后克隆镐子配方，仅替换输出物，保留材料和摆放位置 |
| 9 | 数据生成 | 原版六个 Tier 的配方、模型、中英文语言文件和 Tag 由 `runData` 生成 |
| 10 | 异常容错 | 找不到对应 Tier 或遇到无法识别的配方类型时，记录日志并跳过，避免因这些情况直接崩溃 |
| 11 | 模组兼容 | 适配 Enchanting Infuser（附魔灌注台），并在启动时执行附魔兼容性检查 |

以上为现有版本的功能记录，不代表所有组合均经过验证，也不代表归档后仍会进行兼容性维护。

---

## 兼容性 · Compatibility

| 项目 | 支持范围 | 说明 |
|---|---|---|
| Minecraft | 见 `gradle.properties` 中的 `minecraft_version` | 当前工程的目标版本 |
| 模组加载器 | Forge | 版本见 `gradle.properties` 中的 `forge_version` |
| JDK | 17+ | 由构建脚本中的 `java.toolchain` 指定 |
| 运行环境 | 单人 / 局域网 / 专用服务器 | 客户端与服务端均需要安装 |

### 版本策略 · Versioning strategy

本项目原先采用「Minecraft 大版本一线一支」的维护策略，以便逐步支持不同的 Minecraft 版本。

- `main` 原本用于指向最新支持的版本线。
- 历史支持线可以保留为独立分支，例如 `1.20.1`。
- 版本常量集中在 [`gradle.properties`](gradle.properties)，包括 `minecraft_version`、`forge_version` 和 `mapping_version` 等。
- 新增版本线时，通常需要修改 `gradle.properties`、适配不同版本中发生变化的原版 API，并更新兼容性说明。

项目归档后，上述内容仅作为原有工程设计记录，不再代表持续执行的维护策略。

**移植提示：** 不同 Minecraft 版本之间的差异可能涉及 `DiggerItem`、`Tier`、`MINEABLE_WITH_*` 的命名以及掉落判定方法的重载。原有 [`HANDOFF.md`](HANDOFF.md) 第 5 节记录了当时使用版本的 API 签名与相关注意事项，可作为自行移植时的参考。

### 模组兼容 · Mod compatibility

| 模组或类型 | 原有支持状态 | 说明 |
|---|---|---|
| [Enchanting Infuser](https://www.curseforge.com/minecraft/mc-mods/enchanting-infuser) | 已适配，可选依赖 | 支持通过附魔灌注台为全能工具施加兼容的工具附魔与武器附魔；启动时执行兼容性检查 |
| 其他使用 `canApplyAtEnchantingTable` / `canEnchant` 的附魔类模组 | 设计上支持 | 通过原版或 Forge 标准查询路径判断附魔适用性的实现，可以获得对应的兼容判断 |
| 添加新材料的模组 | 设计上支持 | 参见下方「动态材料支持」 |
| 直接调用 `EnchantmentCategory#canEnchant(Item)`、绕过 Forge 钩子的模组 | 取决于对方实现 | 此路径无法直接利用本模组的物品级附魔判断；部分实现可能无法将全能工具视为剑 |

#### Enchanting Infuser 适配说明

- **可选依赖：** 本模组不强制依赖 Enchanting Infuser。`mods.toml` 中将其声明为可选依赖，并使用 `ordering="AFTER"`。
- **附魔判定：** 在兼容路径中，Forge 最终会调用 `IForgeItem#canApplyAtEnchantingTable(stack, enchantment)`，本模组通过对应实现提供附魔适用性判断。
- **兼容性自检：** 从 2.0.0 起，检测到附魔灌注台安装时，会在启动阶段检查各全能工具的附魔判定，并在日志中输出结果。
- **配置提示：** 附魔灌注台默认配置 `allowModifyingEnchantments = UNENCHANTED`，通常只允许修改尚未附魔的物品。若希望修改已附魔物品，可根据对应版本的配置选项调整为 `ALL`。

上述内容描述的是原有实现与设计目标。由于项目已经停止维护，后续版本的附魔 API 或其他模组的实现变化不再得到适配保证。

---

## 数值一览 · Stats

挖掘速度采用对应专用工具速度的 80%，最低为 1.0；攻击伤害为 `3 + Tier 攻击加成`；攻击速度统一为 1.4，普通剑为 1.6。

| 材料 Material | 挖掘等级 | 耐久 | 镐 Pickaxe | 斧 Axe | 铲 Shovel | 攻击伤害 | 攻击速度 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 木 Wood | 0 | 59 | 1.6 | 1.0 | 1.0 | 3 | 1.4 |
| 石 Stone | 1 | 131 | 3.2 | 1.0 | 1.6 | 4 | 1.4 |
| 铁 Iron | 2 | 250 | 4.8 | 2.4 | 3.2 | 5 | 1.4 |
| 金 Gold | 0 | 32 | 9.6 | 7.2 | 8.0 | 3 | 1.4 |
| 钻石 Diamond | 3 | 1561 | 6.4 | 4.0 | 4.8 | 6 | 1.4 |
| 下界合金 Netherite | 4 | 2031 | 7.2 | 4.8 | 5.6 | 7 | 1.4 |

**对比：** 钻石镐的挖掘速度为 8.0，钻石斧为 5.0，钻石铲为 6.0；钻石剑的攻击伤害为 6，攻击速度为 1.6。

全能工具的设计目标是让每一种用途都不超过对应的专用工具，以多功能性换取一定的效率损失。

具体数值取决于目标版本的 `Tiers`，上表记录的是原有工程中的数值。

---

## 合成配方 · Recipe

```text
M M M
M S M
. S .

M = 对应 Tier 的材料
    （木板 / 圆石 / 铁锭 / 金锭 / 钻石 / 下界合金锭）
S = 木棍
. = 空位
````

### 设计理由

原版镐子的配方在中间留有空位。本项目将中轴填满，形成一个同时表达四种工具结构的图案：

* 顶部横向结构：镐头与铲的切削部分。

* 两侧结构：斧的双刃。

* 中间纵向结构：剑身中轴。

* 底部结构：四种用途共享的握柄。

这一填满中轴的十字形结构是全能工具的配方设计特征。

### 合成成本

每次合成需要 5 个对应 Tier 的材料与 2 根木棍。

与原版最强工具的常见配方相比，材料成本更高；与分别制作四种工具相比，仍然可以减少部分材料消耗。

### 动态材料支持 · Dynamic material support

本项目的核心设计目标是：

只要某种材料能够通过相应的镐子配方获得镐子，并且其 Tier 能被本模组识别，就应尽可能自动获得对应的全能工具及配方，而不依赖静态材料白名单。

实现分为三个阶段。

### ① 注册阶段

```text
DynamicOmniToolRegistrar 扫描已注册物品
└── 发现 PickaxeItem
    └── 获取对应 Tier
        └── 如果尚无对应全能工具，则尝试注册新物品
```

### ② 配方阶段

```text
OnDatapackSyncEvent（服务端）
└── OmniToolRecipeCloner 遍历当前配方
    ├── 找到输出为 PickaxeItem 的配方
    ├── 查找该 Tier 对应的全能工具
    ├── 无法识别时记录日志并跳过
    ├── 已有对应全能工具配方时跳过
    └── 否则克隆配方，仅替换输出物
```

### ③ 同步阶段

```text
服务端将处理后的配方交给原版配方同步机制
└── 客户端通过原版同步流程获取配方
```

原版六个 Tier 的配方由数据生成提供；动态处理逻辑主要用于处理其他模组带来的额外材料。

详细的实现原理、事件选择理由及已知限制见 [`HANDOFF.md`](https://HANDOFF.md)。

注意： 动态注册、配方克隆与跨模组兼容均受 Minecraft、Forge 和目标模组版本影响。归档后不会持续验证新增材料或新版本的兼容性。

### 安装 · Installation

### 玩家安装

* 安装与本项目所对应的 Minecraft 版本及 Forge。

* 获取与当前 Minecraft / Forge 版本匹配的 `omni_tool-<版本>.jar`。

* 将模组文件放入游戏目录下的 `.minecraft/mods/` 文件夹。

* 启动游戏。

本项目原有设计支持单人游戏、局域网游戏与专用服务器。多人环境中，客户端和服务端均需要安装兼容版本的模组。

> \[!NOTE\] 项目已归档。安装前请确认所下载文件的来源、版本及兼容性，并自行承担使用风险。

### 开发者

```bash
./gradlew build       # 构建模组
./gradlew runClient   # 启动带模组的客户端
./gradlew runData     # 生成配方、模型、语言文件和 Tag
./gradlew test        # 运行单元测试
```

构建产物通常位于 `build/libs/`。

### 从源码构建 · Building from source

构建依赖由 [`gradle.properties`](https://gradle.properties) 及 Gradle 构建文件管理。

| 依赖        | 取值来源                                                        |
| --------- | ----------------------------------------------------------- |
| JDK       | java.toolchain（build.gradle），原工程使用 JDK 17                   |
| Gradle    | 由 gradlew 自动下载，版本见 gradle/wrapper/gradle-wrapper.properties |
| Minecraft | minecraft_version（gradle.properties）                        |
| Forge     | forge_version（gradle.properties）                            |
| 映射        | mapping_channel / mapping_version（gradle.properties）        |

### 获取源码

```bash
git clone https://github.com/zzy89216-gif/Minecraft-All-in-one-tool.git
cd Minecraft-All-in-one-tool
./gradlew build
```

首次构建时，ForgeGradle 可能需要下载依赖并处理 Minecraft 相关文件，耗时取决于网络与设备环境。

### 验证

无需启动游戏即可执行部分构建验证：

```bash
./gradlew runData
./gradlew compileJava
./gradlew test
```

* `runData`：生成资源文件。

* `compileJava`：检查 Java 代码编译。

* `test`：运行现有单元测试。

以上命令仅作为现有工程的构建参考。由于项目已停止维护，不保证当前依赖仓库、Gradle 版本或构建环境仍能无修改地完成构建。

### 目录结构 · Project layout

```text
Minecraft-All-in-one-tool/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── docs/
│   ├── ARCHITECTURE.md
│   └── RECIPES.md
├── scripts/
│   └── upload_and_cleanup.sh
├── src/main/java/com/omnitool/omni_tool/
│   ├── OmniToolMod.java
│   ├── registry/
│   │   ├── OmniToolMaterial.java
│   │   ├── ModItems.java
│   │   └── DynamicOmniToolRegistrar.java
│   ├── item/
│   │   └── OmniToolItem.java
│   ├── recipe/
│   │   └── OmniToolRecipeCloner.java
│   ├── event/
│   │   ├── OmniToolForgeEvents.java
│   │   └── OmniToolModEvents.java
│   ├── client/
│   │   └── OmniToolClientSetup.java
│   └── datagen/
├── src/main/resources/
│   ├── META-INF/mods.toml
│   └── pack.mcmeta
├── src/generated/resources/
└── src/test/java/
```

### 主要模块职责

* `OmniToolMod.java`：模组主类。

* `OmniToolMaterial.java`：材料模型，根据 Tier 推导工具属性。

* `ModItems.java`：原版 Tier 的静态注册及 Tier 查找。

* `DynamicOmniToolRegistrar.java`：发现额外 Tier 并尝试注册对应物品。

* `OmniToolItem.java`：核心物品逻辑，包括挖掘、掉落、攻击与附魔。

* `OmniToolRecipeCloner.java`：动态配方克隆逻辑。

* `OmniToolForgeEvents.java`：服务端配方处理。

* `OmniToolModEvents.java`：创造模式物品栏相关逻辑。

* `OmniToolClientSetup.java`：动态物品的模型处理。

* `datagen/`：数据生成相关实现。

更多架构说明见 [`HANDOFF.md`](https://HANDOFF.md) 与 [`docs/ARCHITECTURE.md`](https://docs/ARCHITECTURE.md)。

### 文档 · Documentation

| 文档                   | 内容                        |
| -------------------- | ------------------------- |
| README.md            | 项目介绍、兼容性、数值、配方与构建方式       |
| HANDOFF.md           | 架构、动态配方克隆原理、API 注意事项、已知限制 |
| CHANGELOG.md         | 历史版本变更记录                  |
| CONTRIBUTING.md      | 原有代码风格、提交规范与 PR 流程        |
| docs/ARCHITECTURE.md | 分层架构与关键调用链                |
| docs/RECIPES.md      | 配方细节与数值推导表                |

以上文档仅供查阅现有代码、理解历史设计及自行维护时参考。其中的维护计划、提交流程或未来方向不代表项目仍在接受维护。

### 许可证 · License

本项目采用 MIT License，详见 `LICENSE`。

物品模型使用 Minecraft 原版贴图，例如 `minecraft:item/*_pickaxe`。本仓库不包含 Mojang 的原版美术资源文件。

MIT 许可证适用于本项目在许可证文件中明确涵盖的内容。第三方代码、依赖和游戏资源仍应遵守各自适用的许可证与使用条款。

### 项目状态 · Project status

本项目已归档，不再维护。

由于个人学业原因及个人安排，作者决定结束本项目的后续开发。

归档后：

* 不再开发新功能。

* 不再修复 Bug 或处理兼容性问题。

* 不再发布后续版本。

* 不再主动维护构建流程、文档或测试。

* 不再提供持续的技术支持。

现有代码、文档和历史发行版本继续保留，供查阅、学习或自行使用。仓库保留不代表项目仍在维护。

感谢所有曾经关注、使用或为 Omni Tool 提供帮助的人。

项目至此归档。