# HANDOFF · 交接文档

> 面向"下一个接手这个项目的人"。本文假设你已经会写 Forge 模组，因此重点不在教程，
> 而在**这个项目为什么这样设计、坑在哪里、下一步该做什么**。

---

## 版本范围说明（先读这一段）

本项目按"Minecraft 大版本一线一支"维护：`main` 指向最新支持线，历史支持线各自保留分支。
**所有版本常量只写在 [`gradle.properties`](gradle.properties)**
（`minecraft_version` / `forge_version` / `mapping_version`），构建与数据生成都从那里取值。

因此本文中出现的具体版本号（例如 §5 的 API 陷阱清单、缓存 jar 路径）指的是
**你当前检出的这条版本线**，不是"这个模组只支持这一个版本"。
新增一条版本线时：改 `gradle.properties` → 按该版本修正被改名的原版 API → 更新 `README.md` 的兼容性表。
§5 的清单结构可以直接当作移植对照表使用。

---

## 0. 五分钟快速上手

```bash
git clone https://github.com/zzy89216-gif/Minecraft-All-in-one-tool.git
cd Minecraft-All-in-one-tool

./gradlew build       # 编译 + 单元测试 + 产出 build/libs/ 下的 jar
./gradlew test        # 只跑单元测试
./gradlew runData     # 重新生成 src/generated/resources（配方/模型/语言/Tag）
./gradlew runClient   # 启动带模组的客户端（需要图形环境）
```

**验证清单**（按顺序，每步都应该是绿的）：

| 步骤 | 命令 | 期望结果 |
|---|---|---|
| 1 | `./gradlew compileJava` | BUILD SUCCESSFUL |
| 2 | `./gradlew test` | 30 个测试全部通过（`OmniToolMaterialTest` 5 + `DynamicOmniToolRegistrarTest` 5 + `EnchantmentCompatibilityTest` 20） |
| 3 | `./gradlew runData` | `src/generated/resources` 下 6 配方 + 6 推进 + 6 模型 + 2 语言 + 1 Tag |
| 4 | `./gradlew build` | `build/libs/` 下生成 `omni_tool-<mod_version>.jar`（jar 内 `mods.toml` 的 version 应等于 `gradle.properties` 的 `mod_version`） |
| 5 | 进游戏 `/give @s omni_tool:omni_tool_diamond` | 物品可用，挖石头/木头/泥土都有正确速度与掉落 |

> 首次运行会由 ForgeGradle 下载并反编译 Minecraft（数分钟）。`runData` / `runClient` 会自动
> 触发这一步，不需要专门的 `setup` 任务（**ForgeGradle 6 没有 `setup` 任务**，别去找它）。

---

## 1. 这个项目在解决什么问题

需求一句话：**"只要某种材料能合成镐子，这种材料就必须能合成全能工具"**，包括其他模组后续添加的材料，
且不允许用静态白名单绕过。

于是就出现了这个项目里唯一真正困难的部分：**物品必须在"注册阶段"存在，而配方要等到"数据包加载之后"才知道**。
本项目的全部设计都是围绕这对时间差展开的。

---

## 2. 架构总览

```
                        ┌─────────────────────────────────────────────┐
                        │             OmniToolMod (@Mod)               │
                        │  只做三件事：注册 DeferredRegister、          │
                        │  挂 commonSetup、打启动日志                   │
                        └───────────────────┬─────────────────────────┘
                                            │
        ┌───────────────────────────────────┼────────────────────────────────────┐
        │                                   │                                    │
   【静态层】                          【核心行为】                          【动态层】
  registry/ModItems                item/OmniToolItem                  registry/DynamicOmniToolRegistrar
  registry/OmniToolMaterial              │                             recipe/OmniToolRecipeCloner
        │                               │                                     │
        │  六个原版 Tier                 │  挖掘/掉落/攻击/附魔                  │  发现模组 Tier → 注册物品
        │  用 DeferredRegister 注册      │  只覆盖"多工具确实不同"的部分         │  克隆镐子配方 → 替换输出
        │                               │                                     │
        └──────────────┬────────────────┴──────────────────┬──────────────────┘
                       │                                   │
              datagen/DataGenerators               事件层 event/
              （配方/模型/语言/Tag）            OmniToolForgeEvents（服务端配方注入）
                                               OmniToolModEvents（创造栏）
                       │                                   │
                       └──────────────► src/generated ◄─────┘
                                     （开箱即用的资源）
```

### 分层职责与依赖方向

| 包 | 职责 | 允许依赖 |
|---|---|---|
| `registry` | 材料模型、静态注册、运行时注册、Tier→物品查找 | `item` |
| `item` | 物品行为（唯一有游戏逻辑的地方） | `registry`（只读材料数据） |
| `recipe` | 纯逻辑：给定配方集合 → 产出克隆配方 | `registry`（查表） |
| `event` | 事件绑定，不写业务逻辑，只做转换与异常兜底 | 全部 |
| `client` | 仅客户端：动态物品的兜底模型 | `registry`, `item` |
| `datagen` | 数据生成，只在 `runData` 时运行 | `registry` |

依赖方向是**单向**的：`event → recipe/client/datagen → item → registry`。没有反向依赖，也没有循环。

---

## 3. 动态配方克隆：原理图（文字版）

```
时间轴 ──────────────────────────────────────────────────────────────────────────────►

① 注册阶段（物品注册表开放中，EventPriority.LOWEST）
   ┌──────────────────────────────────────────────────────────────────────────────┐
   │ DynamicOmniToolRegistrar#onRegisterItems(RegisterEvent)                      │
   │                                                                              │
   │   pass 1  遍历 registry（Iterable<Item>）                                     │
   │           └─ item instanceof PickaxeItem ?                                   │
   │                └─ tier = ((PickaxeItem) item).getTier()                       │
   │                     ├─ 已被静态六 Tier 覆盖 → 跳过（它们用数据生成配方）        │
   │                     ├─ 本层已处理过该 Tier   → 跳过                            │
   │                     └─ 否则收集候选：{tier, 名称, 材料key, 展示名}             │
   │                                                                              │
   │   pass 2  对每个候选 event.register(Registries.ITEM, id, supplier)            │
   │           id = omni_tool:omni_tool_<材料>   （冲突时加命名空间前缀）           │
   │           物品构造时把自己登记进 ModItems.dynamicTools()                       │
   │                                                                              │
   │   ⚠ pass 1 / pass 2 必须分开：注册表不能在遍历中被修改（ConcurrentModification）│
   │   ⚠ 整体包在 try/catch(Throwable) 里：发现失败绝不阻断游戏启动                 │
   └──────────────────────────────────────────────────────────────────────────────┘
                                        │
                     FMLCommonSetupEvent │  ModItems.init() 建立 Tier → Item 缓存
                                        ▼
② 配方阶段（数据包加载完成，玩家加入 / /reload）
   ┌──────────────────────────────────────────────────────────────────────────────┐
   │ OmniToolForgeEvents#onDatapackSync(OnDatapackSyncEvent)   ← 在服务端          │
   │                                                                              │
   │   existing = recipeManager.getRecipes()                                      │
   │   alreadyCraftable = { 所有配方当前能产出的物品 }                              │
   │                                                                              │
   │   for recipe in existing:                                                    │
   │     result = recipe.getResultItem(registryAccess)                            │
   │     ├─ 不是 PickaxeItem           → 忽略                                      │
   │     ├─ 查不到对应 Tier 的全能工具  → 记日志 + 跳过（优雅降级）                  │
   │     ├─ 该全能工具已经能合成        → 跳过（避免与数据生成配方重复 / 幂等）      │
   │     ├─ ShapedRecipe   → 复制 width/height/ingredients/group/category，换输出   │
   │     ├─ ShapelessRecipe→ 复制 ingredients/group/category，换输出               │
   │     └─ 其它配方类型    → 计数 + 记日志 + 跳过                                  │
   │                                                                              │
   │   有克隆结果 → recipeManager.replaceRecipes(existing + clones)                │
   └──────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
③ 同步（本模组不需要写任何客户端代码）
   原版 PlayerList 紧接着执行：
       connection.send(new ClientboundUpdateRecipesPacket(recipeManager.getRecipes()))
   → 单人 / LAN / 专用服务器客户端自动拿到克隆后的配方，JEI 等也自动可见
```

### 事件选择的理由（重要）

需求原文建议监听 `RecipesUpdatedEvent`，**但那个事件在
`net.minecraftforge.client.event` 包里，只在逻辑客户端触发**。如果把它当主入口：

- 专用服务器上永远不会发生克隆 → 服务器发出去的还是原版镐子配方；
- 单人也只是"客户端自己看到" → 与服务端不同步，配方书 / JEI 行为不可预期。

因此本项目改用 **`OnDatapackSyncEvent`**，依据是 Forge 自己的补丁
（`patches/net/minecraft/server/players/PlayerList.java.patch`）：

```java
// 玩家加入时
MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(this, player));
connection.send(new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes()));
//                     ^^^ 事件先于同步包构建，此时改配方才会被一起发出去

// /reload 时
MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(this, null));
ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(...);
```

这个顺序保证了：**改在服务端、一次改动、所有端可见**。

---

## 4. 模组兼容层（Enchanting Infuser）

本节记录 Omni Tool 与 [Enchanting Infuser](https://www.curseforge.com/minecraft/mc-mods/enchanting-infuser)
（附魔灌注台，mod id `enchantinginfuser`）的集成方式。它同时是"本模组如何与其他模组协作"的范例：
以后接入别的模组可以照这个结构办理（先读对方源码确认判定路径 → 抽契约 → 加自检 → 补测试 → 写文档）。

### 4.1 对方是怎么判断的（结论：判定入口在物品自己身上）

灌注台在 `EnchantmentUtil#getAvailableEnchantments` 里问两个问题，两个问题都由**物品**回答：

```
① 这个附魔能作用到这个物品上吗？
   ForgeAbstractions#canApplyAtEnchantingTable(enchantment, stack)
     └─ Enchantment#canApplyAtEnchantingTable(ItemStack)            // Forge 补丁新增
        └─ ItemStack#canApplyAtEnchantingTable(Enchantment)         // Forge 补丁新增
           └─ IForgeItem#canApplyAtEnchantingTable(stack, enchantment)   ← 本模组覆盖它
   （等价路径：Enchantment#canEnchant(stack) 也被 Forge 补丁转接到同一个钩子）

② 灌注台允许修改这个物品吗？
   ServerConfig.ModifiableItems 的三种模式（UNENCHANTED / ALL / FULL_DURABILITY）
   都要求 ItemStack#isEnchantable() == true
```

- 对方在 Forge 侧**全程没有直接读 `EnchantmentCategory`**。逐一核对过 1.20.1 分支源码：
  `EnchantmentCategory` 在 Common/Forge 代码中出现 **0 次**；唯一的 `category.canEnchant(...)`
  出现在它的 **Fabric** 分支（Fabric 没有 Forge 那个钩子，只能查品类）。
- 因此不需要注册物品标签、白名单或任何"兼容注册"——我们的物品本身就给出了答案。
- 副作用提示：灌注台默认 `allowModifyingEnchantments = UNENCHANTED`，只允许改**未附魔**物品；
  要编辑已附魔的全能工具需把该项改成 `ALL`。这是对方的配置行为，与本模组无关。

### 4.2 我们做了什么

| 层次 | 内容 |
|---|---|
| `mods.toml` | 声明**可选依赖** `enchantinginfuser`（`mandatory=false`、`ordering="AFTER"`、`side="BOTH"`）：不硬依赖，但加载顺序与兼容关系对玩家可见 |
| 契约 | 附魔接受集合抽到 `compat/EnchantmentCompatibility`（接受 `DIGGER/WEAPON/BREAKABLE/VANISHABLE`，其余拒绝），`OmniToolItem#canApplyAtEnchantingTable` 只做委托 |
| 运行时自检 | `compat/EnchantingInfuserCompat`：检测到灌注台时，对每个全能工具复现上面两个问题并把结论写进日志 |
| 单元测试 | `compat/EnchantmentCompatibilityTest`：逐个枚举全部 14 个 `EnchantmentCategory`，钉死"只接受这四个" |

自检通过时的日志：

```
[OmniTool] Enchanting Infuser compatibility verified: 6 tool(s) x 43 registered enchantment(s)
           = 258 checked combination(s); accepted categories [DIGGER, WEAPON, BREAKABLE, VANISHABLE];
           weapon enchantments in registry 7 (all answered through canApplyAtEnchantingTable)
```

不匹配时输出 WARN 并列出具体是哪个物品 × 哪个附魔，把**静默的兼容性回归**变成可见问题
（例如 Forge 改了默认实现、或对方改了判定路径）。自检从不抛异常，最坏情况只是少一条日志。

### 4.3 为什么不做"更强"的兼容

- **不注册 `EnchantStatsProvider`**：那是给 Apotheosis 这类**替换整个附魔系统**的模组准备的；
  本模组不替换附魔系统，注册进去只会误导使用者（也会抢占优先级）。
- **不硬依赖、不 `compileOnly` 对方 jar**：兼容只依赖原版 + Forge 的公开钩子，
  对方更新或卸载都不影响本模组编译与加载。所以 `EnchantingInfuserCompat` 里**没有一行**对方 API。
- **不在物品上"假装是剑"**：`SwordItem` 无法多继承，而真正的判定入口是 Forge 钩子；
  那些写死 `instanceof SwordItem`（或直接查 `EnchantmentCategory`）的模组，本来就无法被任何
  多工具模组满足——这一点已如实写在 README 的兼容性表里，不夸大兼容范围。

### 4.4 对方改动后如何重新验证

1. 打开灌注台 1.20.1 源码的 `EnchantmentUtil#getAvailableEnchantments`，
   确认它仍然只用 `canEnchant` / `canApplyAtEnchantingTable` 两条路径；
2. 确认 `ServerConfig.ModifiableItems` 仍然要求 `ItemStack#isEnchantable()`；
3. 起一个装了灌注台的实例，看日志里有没有
   `[OmniTool] Enchanting Infuser compatibility verified: ...`；
4. 若自检报 mismatch：先核对 `EnchantmentCompatibility` 的集合是否仍与需求一致，
   再确认 Forge 的 `IForgeItem#canApplyAtEnchantingTable` 默认实现是否变化。

> 对方代码位置（`1.20.1` 分支）：
> `Common/src/main/java/fuzs/enchantinginfuser/util/EnchantmentUtil.java`、
> `Forge/src/main/java/fuzs/enchantinginfuser/core/ForgeAbstractions.java`、
> `Common/src/main/java/fuzs/enchantinginfuser/config/ServerConfig.java`。

---

## 5. 1.20.1 / Forge 47 的实际 API 陷阱（血泪清单）

写这个项目时踩到的坑，全都已修正。接手人改代码前建议先读一遍 —— 这些点靠"记忆"几乎必然写错。

| # | 陷阱 | 事实 |
|---|---|---|
| 1 | 基类名 | 是 `DiggerItem`（镐/斧/铲的公共父类），**不是** `ToolItem`；材料接口是 `Tier`，不是 `ToolMaterial` |
| 2 | BlockTag 常量名 | 1.20.1 是 `BlockTags.MINEABLE_WITH_PICKAXE / _AXE / _SHOVEL`（`MINEABLE_BY_*` 是更早/更晚的命名，此处会编译失败） |
| 3 | 掉落判定走哪个重载 | `ItemStack#isCorrectToolForDrops(BlockState)` → `IForgeItem#isCorrectToolForDrops(ItemStack, BlockState)`。**`DiggerItem` 覆盖了后者并只检查自己那一个 tag**，所以只覆盖 `(BlockState)` 版本会导致斧/铲方块无掉落 |
| 4 | `DiggerItem` 构造参数 | 签名是 `(float attackDamageBaseline, float attackSpeedModifier, Tier, TagKey<Block>, Properties)`：第 1 个参数会**再加上** tier 的攻击加成；第 2 个是**攻速修饰符**；而挖掘 `speed` 字段其实取自 `tier.getSpeed()`，与第 2 个参数无关 |
| 5 | 挖掘速度 | 原版 `getDestroySpeed` 是 `state.is(blocks) ? speed : 1.0F`，**不检查等级**：工具太弱也按全速挖，只是拿不到掉落 |
| 6 | 等级判定 | 不要手写 `NEEDS_*_TOOL` 比较，用 `TierSortingRegistry.isCorrectTierForDrops(tier, state)`：它能正确处理模组通过 `TierSortingRegistry` 注册的 tier，未排序的 tier 自动回退到原版逻辑 |
| 7 | 附魔兼容 | 物品不是 `SwordItem`，所以 `WEAPON` 类附魔默认不适用。Forge 提供了 `IForgeItem#canApplyAtEnchantingTable(ItemStack, Enchantment)`（`Enchantment#canEnchant` 被 Forge 补丁转接到它），覆盖它即可放行武器附魔 |
| 8 | 配方构造器 | `ShapedRecipe(ResourceLocation, String, CraftingBookCategory, int width, int height, NonNullList<Ingredient>, ItemStack, boolean)`；1.20.1 **没有** `ShapedRecipePattern` 类 |
| 9 | `ResourceLocation` | 用 `new ResourceLocation(ns, path)`；`fromNamespaceAndPath` 是 1.21+ 的 API |
| 10 | TagsProvider | Forge 给 `TagsProvider`/`ItemTagsProvider` 增加了带 `modId` + `ExistingFileHelper` 的构造器；用旧的 3 参数版本会生成到 `vanilla` 命名空间下 |
| 11 | `LanguageProvider` | 没有 `addItem(Item, String)`：具体物品用 `add(Item, String)`，延迟物品才用 `addItem(Supplier<Item>, String)` |
| 12 | `mods.toml` | 它被 `processResources` 当作 **Groovy 模板**处理。注释里写普通的 `${...}` 会导致构建失败（本项目已因此翻车一次） |
| 13 | ForgeGradle 6 | **没有 `setup` 任务**。dev 环境由 `prepareRunData` / `prepareRuns` / 任何 `runXxx` 自动准备 |
| 14 | 客户端物品模型 | 用 `ItemModelShaper#register(Item, ModelResourceLocation)`（`Minecraft#getItemRenderer()#getItemModelShaper()`），在 `FMLClientSetupEvent#enqueueWork` 里执行 |

> 想核实任何一条：在映射过的 MC jar 上反编译即可。
> 路径：`~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.2.0_mapped_official_1.20.1/forge-1.20.1-47.2.0_mapped_official_1.20.1.jar`
> （该 jar 使用官方映射，类名与方法名与开发环境一致）

---

## 6. 已知局限性

诚实列表 —— 每一条都明确写了影响范围与规避方式。

### 5.1 非 Shaped / Shapeless 的自定义配方类型无法克隆
- **现象**：模组的镐子如果是用自定义 `RecipeSerializer`（比如机械加工、锻造台扩展）产出的，
  只会被计数并打一条 debug 日志，不会生成全能工具配方。
- **影响面**：仅限这类模组材料；原版六 Tier 与所有使用有序/无序工作台配方的模组**不受影响**。
- **规避**：该模组可以自己提供 `omni_tool_<material>` 的配方；数据生成配方优先级更高，
  克隆器发现有配方能产出该物品时会自动跳过（不会冲突）。
- **改进方向**：为 `SmithingRecipe` / `StonecutterRecipe` 增加克隆分支，或提供
  "配方类型 → 克隆器" 的可注册接口（见 §7.2）。

### 5.2 运行时注册的物品无法数据生成模型与语言
- **原因**：`runData` 运行时其他模组并不存在，因此不知道会有哪些材料。
- **当前处理**：
  - 模型：客户端启动时把这些物品映射到**harvest level 最接近的原版全能工具模型**
    （木≤0 / 石1 / 铁2 / 钻石3 / 下界合金4），见 `OmniToolClientSetup`；
  - 语言：`OmniToolItem#getName` 用"源镐子的本地化名称 + 后缀"拼出名字，
    形如 `Tin Pickaxe (Omni Tool)` / `锡镐（全能工具）`，语言文件里是 `item.omni_tool.dynamic_name`。
- **影响面**：图标是借用原版模型的（不会出现紫黑丢失材质），但"锡镐"的图标看起来像对应等级的
  原版全能工具；名字里保留了源镐子的名字，读起来略啰嗦。
- **改进方向**：若模组数量可控，改为"提供数据生成时期的材料清单"配置项；
  或给动态物品生成运行时资源包（`PackResources`），实现真正的独立贴图与翻译（见 §7.3）。

### 5.3 复用原版 Tier 的模组材料会映射到原版全能工具
- **现象**：某模组的"钢镐"如果直接复用 `Tiers.IRON`，则它对应 `omni_tool_iron`，
  而该物品已经有数据生成配方，于是克隆被跳过。
- **为什么这样设计**：Tier 是唯一可靠的"材料强度"信息来源；按 Tier 匹配是保守且可预测的行为，
  也避免了重复配方。这类材料的作者若想要独立的全能工具，应当定义自己的 Tier。
- **改进方向**：支持按"配方材料物品"而非 Tier 建立映射（见 §7.1）。

### 5.4 不做"掉落之外的采矿特化"
- 支持镐/斧/铲三类 tag，**不包含** `mineable/hoe`（锄）与剪刀类。这符合需求描述，
  但如果你也想当锄头用，`OmniToolItem#isOmniMineable` 是唯一需要改的地方。

### 5.5 附魔按类别放行，不做逐条白名单
- 放行 `WEAPON` 类别意味着**横扫之刃（Sweeping Edge）**也能附到全能工具上。
  这在原版是剑专属效果，行为上不会报错，但属于"比需求更宽"的一处设计取舍。
- 若要收紧，把 `canApplyAtEnchantingTable` 改成对具体 `Enchantment` 的白名单即可。

### 5.6 未做服务端配置化
- 目前平衡系数（0.8）、攻速（1.4）、配方形状都是**编译期常量**。
- 若要支持整合包作者调参，需要引入 Forge Config（见 §7.4）。

---

## 7. 后续可扩展方向

按"投入产出比"排序，前两项是最值得先做的。

### 6.1 用"配方材料"而不是 Tier 建立映射（推荐）
目前动态层基于 `Tier` 身份匹配。更精确的做法是：从镐子配方里取出**主要材料物品/标签**
（例如 `#forge:ingots/tin`），用材料作为键来建立映射。
- 好处：复用原版 Tier 的模组材料也能得到独立的全能工具；命名更贴近玩家认知。
- 落点：`OmniToolRecipeCloner` 里已经有完整的配方与结果，可在那里补充材料键的提取。

### 6.2 可插拔的配方克隆器（推荐）
把"配方类型 → 克隆策略"抽象成接口：
```java
public interface RecipeCloneStrategy<R extends Recipe<?>> {
    Class<R> type();
    Recipe<?> clone(R source, ResourceLocation newId, ItemStack newResult);
}
```
用 `ServiceLoader` 或 Forge 的事件注册策略，第三方模组即可支持自己的配方类型。
- 落点：`recipe` 包新增 `RecipeCloneStrategy` + `OmniToolRecipeCloner` 内部改为遍历策略表。

### 6.3 运行时资源包，给动态物品真正的贴图与翻译
实现 `PackResources`，通过 `AddPackFindersEvent` 注入一个内存中的资源包，
为动态物品生成 `models/item/<id>.json` 与 `lang/*.json`。
- 好处：彻底解决 §6.2。
- 注意：`AddPackFindersEvent` 的时机早于注册表冻结，需要把"要生成哪些条目"缓存下来，
  在资源包被查询时惰性生成。

### 6.4 Forge Config 调参
`ModConfig` + `ForgeConfigSpec`，暴露：
- `balanceFactor`（默认 0.8）
- `attackSpeedModifier`（默认 -2.6）
- `enableAxeTag` / `enableShovelTag` / `enableHoeTag`
- `enableDynamicRegistration`（关掉后就只剩原版六 Tier）

### 6.5 更多语种
`ModLanguageProvider` 已经是"按 locale 分支"的结构，加语种只需：
1. `addProvider` 里多一行 `new ModLanguageProvider(output, "ja_jp")`；
2. `displayName()` 里补一个分支。

### 6.6 兼容性测试矩阵
当前未做任何模组联动实测。建议至少覆盖：
- 自定义 Tier 的模组（验证 §3 pass 2 的注册路径）
- 工作台 + 锻造台配方并存的模组（验证 §6.1 的降级路径）
- JEI / REI（验证克隆配方在配方浏览器中可见）
- 服务端 + 多客户端（验证同步只发生一次、不重复）

### 6.7 性能优化点
- `ModItems#omniToolForTier` 的缓存未命中会全量扫描物品注册表。正常情况只扫一次（结果入缓存），
  但若某个 Tier 永远查不到，每次调用都会重扫。可加入"负缓存"（记录已确认无结果的身份）。
- `OmniToolRecipeCloner#clonePickaxeRecipes` 每次同步都会遍历全部配方。
  可缓存"最后一次配方集合的哈希 + 结果"，配方未变时直接短路。
- `DynamicOmniToolRegistrar` 的扫描是 `O(物品数)`，只发生在启动期，无需优化。

---

## 8. 排错指南

| 症状 | 可能原因 | 检查点 |
|---|---|---|
| 物品能拿到但挖方块没掉落 | 掉落判定只覆盖了一个 tag | `OmniToolItem#canHarvestWith` 是否被两个重载都调用 |
| 模组材料没有生成全能工具 | 该镐子的 Tier 已被原版六 Tier 之一覆盖（§6.3），或镐子的 `Tier` 为 null | 开 `debug` 日志看 `Dynamic recipe pass` 统计行 |
| 控制台没有克隆日志 | 事件没触发（比如不是服务端） | 确认 `OnDatapackSyncEvent` 在 FORGE bus 上、且玩家真的加入了 |
| 动态物品是紫黑方块 | 客户端兜底模型没注册成功 | `OmniToolClientSetup` 的 warn 日志；确认在客户端运行 |
| `./gradlew` 报找不到 `GradleWrapperMain` | 文件系统损坏了 wrapper jar（本项目在 `/sdcard` 上踩过） | 重新下载 `gradle/wrapper/gradle-wrapper.jar` |
| `processResources` 报 Groovy 模板错误 | `mods.toml` 里出现了字面量 `${...}` | 见 §5 第 12 条 |
| 构建时提示找不到 `setup` 任务 | ForgeGradle 6 没有这个任务 | 直接用 `runData` / `runClient` / `build` |

日志关键字：
```
[OmniTool] Constructed                         启动
[OmniTool] Discovered modded material ...      动态发现（注册阶段）
[OmniTool] Dynamic recipe pass: X cloned ...   配方克隆统计
[OmniTool] Added N dynamically cloned recipe(s) 注入成功
```

---

## 9. 发布清单（给维护者）

- [ ] 改 `gradle.properties` 的 `mod_version`
- [ ] 更新 `CHANGELOG.md`
- [ ] `./gradlew clean build` 全绿
- [ ] `./gradlew runData` 后确认 `src/generated` 无意外 diff
- [ ] 在真实客户端里跑一遍：合成、挖掘、附魔、修理、配方书
- [ ] 打 tag：`git tag -a v<mod_version> -m "Omni Tool <mod_version>"` 并推送（例如 `v2.0.0`）
- [ ] **在 GitHub 上创建 Release 并上传 jar 附件**（这一步容易漏：只推代码不会出现在 Releases 页面）
  ```bash
  # 建 release（用 tag 名，body 用 CHANGELOG 对应段落）
  curl -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    https://api.github.com/repos/<owner>/<repo>/releases \
    -d '{"tag_name":"v<mod_version>","name":"Omni Tool <mod_version>","body":"见 CHANGELOG"}'
  # 上传 jar 附件（<release_id> 取上一步返回的 id）
  curl -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Content-Type: application/java-archive" \
    --data-binary "@build/libs/omni_tool-<mod_version>.jar" \
    "https://uploads.github.com/repos/<owner>/<repo>/releases/<release_id>/assets?name=omni_tool-<mod_version>.jar"
  ```
- [ ] 校验附件：`GET /releases/tags/v<mod_version>` 返回的 `assets[].digest` 应与
  `sha256sum build/libs/omni_tool-<mod_version>.jar` 一致
