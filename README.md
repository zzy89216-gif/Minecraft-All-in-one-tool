# Omni Tool · 全能工具

> 一把工具，四种用途：镐、斧、铲、剑合一。  
> One tool, four jobs: pickaxe, axe, shovel and sword in a single item.

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Mod Loader](https://img.shields.io/badge/Mod%20Loader-Forge-e04e14.svg)](#兼容性--compatibility)
[![Java](https://img.shields.io/badge/Java-17%2B-007396.svg)](#兼容性--compatibility)
[![Minecraft](https://img.shields.io/badge/Minecraft-%E8%A7%81%E5%85%BC%E5%AE%B9%E6%80%A7%E8%A1%A8-3c8527.svg)](#兼容性--compatibility)

> 具体支持的 Minecraft / Forge 版本见下方 [兼容性](#兼容性--compatibility)；工程内的版本常量集中在
> [`gradle.properties`](gradle.properties)，本文件不再重复写死版本号。

---

## 项目简介 · Introduction

**Omni Tool（全能工具）** 是一个 Minecraft **Forge** 模组，添加了一种"万能工具"：
同一个物品同时具备**镐、斧、铲**的挖掘能力与**剑**的攻击能力。

与"把四种工具拼在一起"的常见做法不同，本项目把两个难点当作一等公民：

1. **挖掘判定基于原版 BlockTag**（`mineable/pickaxe`、`mineable/axe`、`mineable/shovel`），
   而不是硬编码方块白名单 —— 因此任何模组新增的、挂在原版 tag 下的方块都自动支持。
2. **配方在运行时动态生成**：任何"能合成镐子"的材料（包括其他模组后续添加的自定义材料）
   都会自动获得对应的全能工具配方，无需为本模组写一行代码或手工登记。

---

## 核心特性 · Features

| # | 特性 | 说明 |
|---|------|------|
| 1 | **四器合一** | 镐 / 斧 / 铲 / 剑四种能力集于一件物品 |
| 2 | **Tag 驱动挖掘** | 依据方块所属的 `mineable/*` tag 选择对应工具类型的挖掘速度 |
| 3 | **平衡而非全能** | 每一类速度都是对应专用工具的 **80%**，绝不超过专精工具 |
| 4 | **剑式攻击** | 伤害采用剑的公式 `3 + tier攻击加成`；攻速 1.4，比纯剑（1.6）略慢 |
| 5 | **沿用 Tier 耐久** | 耐久直接取自该材料的 Tier（木 59 … 下界合金 2031），不做额外缩放 |
| 6 | **四类附魔通吃** | 镐/斧/铲共有附魔（效率、精准采集、时运…）+ 武器附魔（锋利、抢夺、火焰附加…） |
| 7 | **动态材料支持** | 运行时扫描全部 `PickaxeItem`，为未知 Tier 自动注册全能工具 |
| 8 | **动态配方克隆** | 服务端配方加载后克隆镐子配方，只替换输出物，材料与摆放位置完全一致 |
| 9 | **数据生成** | 原版六个 Tier 的配方 / 模型 / 中英语言文件 / Tag 全部由 `runData` 产出 |
| 10 | **绝不崩溃** | 找不到对应 Tier、遇到无法识别的配方类型时优雅跳过并打日志 |

---

## 兼容性 · Compatibility

| 项目 | 支持范围 | 说明 |
|---|---|---|
| Minecraft | 见 `gradle.properties` 的 `minecraft_version` | 当前工程以此版本为目标 |
| 模组加载器 | **Forge** | 版本见 `gradle.properties` 的 `forge_version` |
| JDK | **17+** | 由构建脚本的 `java.toolchain` 指定，不依赖本机默认 JDK |
| 运行环境 | 单人 / 局域网 / 专用服务器 | 客户端与服务端都需要安装 |

### 版本策略 · Versioning strategy

本项目按"Minecraft 大版本一线一支"的方式维护，方便逐步覆盖更多版本：

- `main` 永远指向**最新支持**的那条线；
- 每条历史支持线保留独立分支（例如 `1.20.1`），互不干扰；
- **所有版本常量只写在 [`gradle.properties`](gradle.properties)**
  （`minecraft_version`、`forge_version`、`mapping_version` 等），构建脚本与资源生成都从那里取值；
- 新增一条版本线时只需要：改 `gradle.properties` → 按该版本修正被改名的原版 API →
  更新本节的兼容性表。README 里不写死具体版本号，就是为了让这一步不需要四处改文案。

> 移植提示：不同 MC 版本之间最大的差异集中在
> `DiggerItem` / `Tier` / `MINEABLE_WITH_*` 的命名与掉落判定重载上。
> [`HANDOFF.md`](HANDOFF.md) §4 已经把当前版本的准确签名与陷阱逐条列出，可作为移植对照表。

---

## 数值一览 · Stats

挖掘速度 = 对应专用工具速度 × 0.8（下限 1.0，即不会比空手慢）；攻击伤害 = `3 + Tier 攻击加成`；攻速均为 **1.4**（纯剑为 1.6）。

| 材料 Material | 挖掘等级 | 耐久 | 镐 Pickaxe | 斧 Axe | 铲 Shovel | 攻击伤害 | 攻击速度 |
|---|---|---|---|---|---|---|---|
| 木 Wood | 0 | 59 | 1.6 | 1.0 | 1.0 | 3 | 1.4 |
| 石 Stone | 1 | 131 | 3.2 | 1.0 | 1.6 | 4 | 1.4 |
| 铁 Iron | 2 | 250 | 4.8 | 2.4 | 3.2 | 5 | 1.4 |
| 金 Gold | 0 | 32 | 9.6 | 7.2 | 8.0 | 3 | 1.4 |
| 钻石 Diamond | 3 | 1561 | 6.4 | 4.0 | 4.8 | 6 | 1.4 |
| 下界合金 Netherite | 4 | 2031 | 7.2 | 4.8 | 5.6 | 7 | 1.4 |

> 对比：钻石镐 8.0、钻石斧 5.0、钻石铲 6.0、钻石剑伤害 6 / 攻速 1.6。
> 全能工具在**每一种**用途上都比专用工具慢，这正是"多功能但不专精"的平衡点。
> 数值只随目标版本的 `Tiers` 变化，公式本身与版本无关。

---

## 合成配方 · Recipe

```
      M M M        第一行：三块材料 —— 镐头。它同时读作铲斗的切削沿。
      M S M        第二行：两侧材料 —— 斧的双刃；中间木棍 —— 剑身中轴。
      . S .        第三行：一根木棍 —— 四器共享的握柄。

      M = 该 Tier 的材料（木板 / 圆石 / 铁锭 / 金锭 / 钻石 / 下界合金锭）
      S = 木棍
```

**设计理由**：原版镐子的"品"字形在中间留出空洞；这个图案把**中轴填满**了 ——
顶部一条横向"heads"（镐 + 铲），左右两个"blades"（斧），中间一条纵向"shaft"（剑），
最后收束到同一个握柄。一个填满的十字，正是"四种工具融合为一"的视觉签名，
且与任何原版工具图案都不会混淆。

**成本**：5 材料 + 2 木棍。比最强的单件工具（镐 3+2）贵，但远低于四件套（9 材料 + 8 木棍）。

---

## 动态材料支持 · Dynamic material support

模组的硬性目标：**"只要某种材料能合成镐子，这种材料就必须能合成全能工具"**，且不能用静态白名单绕过。

实现分两层，两层的交汇点是"Tier → 全能工具"的查找表：

```
① 注册阶段（物品注册表仍然开放）
   DynamicOmniToolRegistrar 以 EventPriority.LOWEST 扫描全部已注册物品
   └─ 发现 PickaxeItem → 取出它的 Tier
      └─ 该 Tier 还没有全能工具？→ 现场注册一个 omni_tool_<材料>

② 配方阶段（数据包加载之后）
   OnDatapackSyncEvent（服务端，在 ClientboundUpdateRecipesPacket 构建之前触发）
   └─ OmniToolRecipeCloner 遍历当前全部配方
      ├─ 输出是 PickaxeItem？→ 查 Tier 对应的全能工具
      ├─ 找不到 → 记录日志并跳过（绝不崩溃）
      ├─ 已经有配方能产出它 → 跳过（避免与数据生成配方重复）
      └─ 否则 → 克隆：相同材料、相同摆放、相同数量，仅替换输出物

③ 同步
   服务端把克隆后的配方交给原版同步包，客户端（单人 / LAN / 专用服务器）自动获得
```

原版六个 Tier 的配方由**数据生成**产出，因此原版部分是 100% 确定性的；
动态层只负责"额外模组材料"这一层。二者不会产生重复配方。

详细的原理图、事件选择理由与已知局限见 → [`HANDOFF.md`](HANDOFF.md)。

---

## 安装 · Installation

**玩家：**

1. 安装受支持的 Minecraft 版本与对应版本的 **Forge**（见[兼容性](#兼容性--compatibility)）。
2. 把 `omni_tool-<版本>.jar` 放进 `.minecraft/mods/`。
3. 启动游戏即可。支持单人、局域网与专用服务器；客户端与服务端都需要安装。

**开发者：**

```bash
./gradlew build          # 产物：build/libs/
./gradlew runClient      # 启动带模组的客户端
./gradlew runData        # 生成资源（配方/模型/语言/Tag）
./gradlew test           # 运行单元测试
```

---

## 从源码构建 · Building from source

用到的版本全部由 [`gradle.properties`](gradle.properties) 决定，无需在别处修改：

| 依赖 | 取值来源 |
|---|---|
| JDK | `java.toolchain`（`build.gradle`），当前为 **17** |
| Gradle | 由 `gradlew` 自动下载（版本见 `gradle/wrapper/gradle-wrapper.properties`） |
| Minecraft | `minecraft_version`（`gradle.properties`） |
| Forge | `forge_version`（`gradle.properties`） |
| 映射 | `mapping_channel` / `mapping_version`（`gradle.properties`） |

```bash
git clone https://github.com/zzy89216-gif/Minecraft-All-in-one-tool.git
cd Minecraft-All-in-one-tool
./gradlew build
```

首次构建会由 ForgeGradle 下载并反编译 Minecraft，视网络情况需要数分钟。

**验证流程**（无需启动游戏）：

```bash
./gradlew runData     # 生成 src/generated/resources 下的配方/模型/语言/Tag
./gradlew compileJava # 仅编译检查
```

---

## 目录结构 · Project layout

```
Minecraft-All-in-one-tool/
├── build.gradle               # ForgeGradle 构建脚本
├── gradle.properties          # 版本与模组元数据的唯一来源
├── settings.gradle
├── docs/
│   ├── ARCHITECTURE.md        # 分层架构与数据流
│   └── RECIPES.md             # 配方与数值表（含动态克隆示例）
├── scripts/
│   └── upload_and_cleanup.sh  # 分批提交 → 推送 → 校验 → 清理
├── src/main/java/com/omnitool/omni_tool/
│   ├── OmniToolMod.java            # 主类（@Mod）
│   ├── registry/
│   │   ├── OmniToolMaterial.java   # 材料模型：由 Tier 推导速度/伤害
│   │   ├── ModItems.java           # 原版六 Tier 静态注册 + Tier 查找
│   │   └── DynamicOmniToolRegistrar.java  # 运行时发现模组 Tier 并注册物品
│   ├── item/
│   │   └── OmniToolItem.java       # 核心物品：挖掘 / 掉落 / 攻击 / 附魔
│   ├── recipe/
│   │   └── OmniToolRecipeCloner.java  # 动态配方克隆（纯逻辑，便于测试）
│   ├── event/
│   │   ├── OmniToolForgeEvents.java   # 服务端配方注入
│   │   └── OmniToolModEvents.java     # 创造模式物品栏
│   ├── client/
│   │   └── OmniToolClientSetup.java   # 动态物品的兜底模型
│   └── datagen/                       # 数据生成（模型/语言/配方/Tag）
├── src/main/resources/
│   ├── META-INF/mods.toml
│   └── pack.mcmeta
├── src/generated/resources/    # runData 的输出（已提交，保证开箱可用）
└── src/test/java/              # 单元测试
```

每一层的职责、依赖方向与扩展点见 [`HANDOFF.md`](HANDOFF.md)。

---

## 文档 · Documentation

| 文档 | 内容 |
|---|---|
| [`README.md`](README.md) | 项目介绍、兼容性、数值、配方、构建方式（本文件） |
| [`HANDOFF.md`](HANDOFF.md) | **交接文档**：架构、动态克隆原理图、API 陷阱、已知局限、后续方向 |
| [`CHANGELOG.md`](CHANGELOG.md) | 版本变更记录（Keep a Changelog 格式） |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 代码风格、提交规范、PR 流程 |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 分层架构与关键调用链 |
| [`docs/RECIPES.md`](docs/RECIPES.md) | 配方细节、数值推导表 |

---

## 许可证 · License

本项目采用 **MIT License**，见 [`LICENSE`](LICENSE)。

> 说明：物品模型引用的是原版贴图（`minecraft:item/*_pickaxe`），仓库内不包含任何 Mojang 美术资源文件。
> 你可以通过资源包替换为自己的贴图。
