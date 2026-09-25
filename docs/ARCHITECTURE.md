# 架构说明 · Architecture

本文说明 Omni Tool 的分层结构、关键调用链与设计取舍。面向维护者。
更偏"上手与排错"的内容见 [`../HANDOFF.md`](../HANDOFF.md)。

---

## 1. 分层与依赖方向

```
        ┌────────────────────────────────────────────────────────────────┐
        │                        event / datagen / client                │
        │   事件绑定 · 数据生成 · 客户端模型映射（都只做"适配"，不写业务）  │
        └───────────────┬────────────────────────────┬───────────────────┘
                        │                            │
                        ▼                            ▼
        ┌──────────────────────────────┐   ┌──────────────────────────────┐
        │           recipe             │   │             item             │
        │  动态配方克隆（纯逻辑）        │   │  物品行为（唯一游戏逻辑所在）  │
        └───────────────┬──────────────┘   └───────────────┬──────────────┘
                        │                                  │
                        └──────────────┬───────────────────┘
                                       ▼
                        ┌──────────────────────────────┐
                        │           registry           │
                        │ 材料模型 · 静态注册 · 动态注册 │
                        │ Tier → Item 查找表            │
                        └──────────────────────────────┘
```

**规则**：箭头只能向下。

- `registry` 不知道 `item` 的具体行为，只知道"我注册的是一个 OmniToolItem"。
- `item` 只从 `registry` **读**材料数据（`OmniToolMaterial`），不触发注册。
- `recipe` 只通过 `registry` 的查找表解析"这个 Tier 对应哪个物品"。
- `event` 承担"胶水"职责：取事件数据 → 调用下层 → 兜底异常。

这样做的收益：`recipe` 与 `registry` 的核心逻辑可以脱离游戏启动做单元测试
（见 `src/test/java`）。

---

## 2. 类清单

| 类 | 层 | 职责 | 关键点 |
|---|---|---|---|
| `OmniToolMod` | 根 | `@Mod` 入口 | 只注册 `DeferredRegister` 与 `commonSetup`，不做业务 |
| `OmniToolMaterial` | registry | 材料模型（record） | 全部数值**由 `Tier` 推导**，是动态层能通用化的前提 |
| `ModItems` | registry | 静态注册六 Tier + Tier 查找缓存 | 缓存未命中会做一次防御性全表扫描 |
| `DynamicOmniToolRegistrar` | registry | 运行时发现模组 Tier 并注册物品 | `EventPriority.LOWEST`；两阶段（先收集再注册）；整体 try/catch |
| `OmniToolItem` | item | 挖掘速度、掉落判定、攻击属性、附魔、命名 | 覆盖两个 `isCorrectToolForDrops` 重载 |
| `OmniToolRecipeCloner` | recipe | 配方 → 克隆配方的纯函数 | 幂等；不支持的类型降级跳过 |
| `OmniToolForgeEvents` | event | 服务端注入克隆配方 | `OnDatapackSyncEvent`，先于原版同步包 |
| `OmniToolModEvents` | event | 加入创造模式物品栏 | `BuildCreativeModeTabContentsEvent` |
| `OmniToolClientSetup` | client | 动态物品 → 原版模型兜底映射 | `ItemModelShaper`，在 `enqueueWork` 中执行 |
| `EnchantmentCompatibility` | compat | 附魔接受契约的单一来源 | 纯函数，可单测；`OmniToolItem` 只做委托 |
| `EnchantingInfuserCompat` | compat | Enchanting Infuser 的启动自检 | 不引用对方任何 API，只用原版 + Forge 钩子 |
| `DataGenerators` + 各 Provider | datagen | 资源生成 | 双语语言文件、融合形状配方、物品 Tag |

---

## 3. 关键调用链

### 3.1 挖掘一个方块

```
玩家挖掘
  └─ 游戏计算挖掘速度
     └─ ItemStack → Item#getDestroySpeed(stack, state)
        └─ OmniToolItem#getDestroySpeed
           ├─ state.is(MINEABLE_WITH_PICKAXE) → material.pickaxeSpeed()   (tier.speed * 0.8)
           ├─ state.is(MINEABLE_WITH_AXE)     → material.axeSpeed()       ((tier.speed-3) * 0.8, 下限 1)
           ├─ state.is(MINEABLE_WITH_SHOVEL)  → material.shovelSpeed()    ((tier.speed-2) * 0.8, 下限 1)
           └─ 其它                             → super (1.0F)
```

### 3.2 判定是否掉落

```
方块被破坏 → 需要判断"工具是否正确"
  └─ ItemStack#isCorrectToolForDrops(BlockState)          [Forge 补丁转接]
     └─ IForgeItem#isCorrectToolForDrops(ItemStack, BlockState)
        └─ DiggerItem 覆盖了它（只检查自己那一个 tag）→ 我们再次覆盖
           └─ OmniToolItem#isCorrectToolForDrops(ItemStack, BlockState)
              └─ canHarvestWith(state)
                 ├─ isOmniMineable(state)                       三个 tag 任一命中
                 └─ TierSortingRegistry.isCorrectTierForDrops   Forge 的等级判定
```

同时覆盖 `isCorrectToolForDrops(BlockState)`，保证第三方调用者得到一致结论。

### 3.3 动态材料从发现到可合成

```
启动
 ├─ RegisterEvent(minecraft:item) [LOWEST]
 │   └─ DynamicOmniToolRegistrar
 │      ├─ pass 1: 收集"未覆盖 Tier"的候选
 │      └─ pass 2: event.register(...) 注册 omni_tool_<材料>
 ├─ FMLCommonSetupEvent
 │   └─ ModItems.init()  建缓存
 └─ 玩家加入 / /reload
     └─ OnDatapackSyncEvent
        ├─ OmniToolRecipeCloner.clonePickaxeRecipes(...)
        └─ recipeManager.replaceRecipes(existing + clones)
            └─ 原版紧接着发送 ClientboundUpdateRecipesPacket → 全端可见
```

### 3.4 判定"某附魔能否作用于本工具"（含 Enchanting Infuser）

```
任何调用方（附魔台 / 铁砧 / Enchanting Infuser / 其它模组）
  └─ Enchantment#canApplyAtEnchantingTable(ItemStack)      // Forge 补丁
     或 Enchantment#canEnchant(ItemStack)                  // Forge 也转接到同一钩子
     └─ ItemStack#canApplyAtEnchantingTable(Enchantment)   // Forge 补丁
        └─ IForgeItem#canApplyAtEnchantingTable(stack, enchantment)
           └─ OmniToolItem#canApplyAtEnchantingTable
              ├─ EnchantmentCompatibility.accepts(category)   // 契约：DIGGER/WEAPON/BREAKABLE/VANISHABLE
              └─ super.canApplyAtEnchantingTable(...)         // DiggerItem 默认（品类判定）

启动期（仅在检测到 Enchanting Infuser 时）
  └─ EnchantingInfuserCompat.runSelfCheck()
     ├─ 对每个全能工具：stack.isEnchantable()            ← 对方 ModifiableItems 的门槛
     └─ 对每个已注册附魔：canApplyAtEnchantingTable(stack) 是否 == 契约判定
        └─ 一致 → INFO 汇总；不一致 → WARN + 具体条目
```

---

## 4. 设计取舍

### 4.1 为什么用 `DiggerItem` 而不是直接继承 `Item`
继承 `Item` 需要自己实现耐久、附魔能力、修理材料、耐久消耗、属性表等一堆行为，
任何一处与原版不一致都会产生"看似能跑但手感不对"的 bug。
`DiggerItem` 已经把这些做对了，我们只覆盖确实不同的三件事。

### 4.2 为什么挖掘速度用 0.8 系数
需求要求"多功能但不专精"。若直接使用专用工具速度，全能工具就变成"四种工具的完全上位替代"，
破坏游戏平衡。0.8 让它在任何单一用途上都慢于专用工具，却仍远快于空手。

### 4.3 为什么掉落判定要覆盖两个重载
Forge 为掉落判定引入了 `(ItemStack, BlockState)` 重载，而 `DiggerItem` 覆盖了它。
只覆盖 `(BlockState)` 会得到一个"速度正确、但斧/铲方块不掉落"的隐蔽 bug。
两个都覆盖是最省心的做法。

### 4.4 为什么克隆配方放在服务端
客户端拿到的配方来自服务端的同步包。服务端改了，客户端自动正确；
反过来（只改客户端）则会造成两端不一致，且专用服务器上完全失效。

### 4.5 为什么动态物品要在注册阶段创建
Forge 在注册阶段结束后冻结注册表，之后无法再新增物品。
配方却在更晚的时候才加载 —— 这正是本项目必须"两层分离"的根本原因。

### 4.6 为什么给动态物品做模型兜底
数据生成时其他模组还不存在，无法为它们生成模型文件。
不做兜底就会出现紫黑方块；做兜底只需一次映射注册，成本极低。

### 4.7 为什么兼容层只做"契约 + 自检"，而不用硬依赖
接入 Enchanting Infuser 时，一个"看起来更彻底"的做法是把它加进 `dependencies`
（`compileOnly`）并直接调用它的 API。没有这样做，理由是：

- **兼容的判定入口本来就在我们这边**：对方经由 Forge 的 `canApplyAtEnchantingTable` 钩子询问物品，
  我们已经实现了该钩子，硬依赖不会带来任何额外能力，只会引入一个"对方一改就编译失败"的耦合。
- **依赖应当反映真实需要**：本模组不替换附魔系统，也不需要注册对方的 `EnchantStatsProvider`
  （那是 Apotheosis 这类替换整套系统的模组才需要的），注册进去只会误导使用者。
- **可验证性优先于"看起来集成"**：真正缺的是"怎么知道它还兼容"。所以改为：
  抽出可单测的 `EnchantmentCompatibility` 契约 + 启动期自检 `EnchantingInfuserCompat`
  （只用原版/Forge API，零对方依赖），把兼容性变成一个会打日志、会被测试覆盖的事实。

这个模式可以直接复用到下一个要兼容的模组：先读对方源码确认判定路径 → 抽成契约 →
加自检与单测 → 在 `mods.toml` 里声明可选依赖。

---

## 5. 扩展点速查

| 想做什么 | 改哪里 |
|---|---|
| 调整平衡系数 / 攻速 | `OmniToolMaterial.BALANCE_FACTOR` / `SWORD_ATTACK_SPEED_MODIFIER` |
| 增加锄（`mineable/hoe`）支持 | `OmniToolItem#isOmniMineable` + `getDestroySpeed` + `OmniToolMaterial` 增加 hoeSpeed |
| 支持新的配方类型 | `OmniToolRecipeCloner#cloneOne` 增加分支（或实现 §HANDOFF 7.2 的策略接口） |
| 改名 / 加语种 | `ModLanguageProvider#displayName` + `DataGenerators` 增加一个 provider |
| 换配方形状 | `ModRecipeProvider#buildOmniToolRecipe` 的 `pattern(...)` |
| 关闭动态层 | 移除 `DynamicOmniToolRegistrar` 的 `@Mod.EventBusSubscriber`（或加配置开关） |
| 改附魔接受范围 | `EnchantmentCompatibility` 的集合（记得同步更新 `EnchantmentCompatibilityTest`） |
| 兼容新的模组 | 照 §4.7 的模式：确认对方判定路径 → 抽契约 → 加自检 → 声明可选依赖 → 更新 README 兼容性表 |
| 换贴图 | 覆盖 `src/generated/.../models/item/*.json` 或资源包替换 |
