# 配方与数值 · Recipes & Stats

本文档给出配方的完整推导、数值来源，以及动态克隆的实际行为示例。

---

## 1. 原版六 Tier 配方（数据生成产出）

由 `ModRecipeProvider` 生成，输出到
`src/generated/resources/data/omni_tool/recipes/omni_tool_<material>.json`。

### 图案

```
      M M M        第一行：三块材料 —— 镐头。同时读作铲斗的切削沿。
      M S M        第二行：两侧材料 —— 斧的双刃；中间木棍 —— 剑身中轴。
      . S .        第三行：一根木棍 —— 四器共享的握柄。

      M = 该 Tier 的材料，S = 木棍
```

### 设计理由

- **不是镐子的"品"字形**：原版镐子中间留空，这里把中轴填满了。
- **填充的十字 = 融合**：顶部横条（镐/铲）、左右双刃（斧）、纵向中轴（剑）收束到同一个握柄。
- **对称好记**：上下左右对称，玩家看一眼就能记住，也不会与任何原版工具图案混淆。
- **成本**：5 材料 + 2 木棍；比最强单件工具（镐 3+2）贵，远低于四件套（9 材料 + 8 木棍）。

### 各 Tier 的材料

| Tier | 材料（Ingredient） | 说明 |
|---|---|---|
| 木 Wood | `#minecraft:planks` | 使用标签，兼容所有木板 |
| 石 Stone | `minecraft:cobblestone` | |
| 铁 Iron | `minecraft:iron_ingot` | |
| 金 Gold | `minecraft:gold_ingot` | |
| 钻石 Diamond | `minecraft:diamond` | |
| 下界合金 Netherite | `minecraft:netherite_ingot` | 与其它五个保持一致，直接用合成格（原版走锻造台，见下） |

> **与原版的差异**：原版下界合金装备通过锻造台升级。这里为了"六 Tier 一致"以及
> 让"能合成镐子即可合成全能工具"的规则对下界合金同样成立，改用同一套合成格。
> 若希望还原原版流程，可在 `ModRecipeProvider` 中改为 `SmithingTemplateItem` 配方
> （同时需要在 `OmniToolRecipeCloner` 里增加锻造配方的克隆分支）。

### 完整 JSON 示例（钻石）

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "equipment",
  "group": "omni_tool",
  "key": {
    "M": { "item": "minecraft:diamond" },
    "S": { "item": "minecraft:stick" }
  },
  "pattern": ["MMM", "MSM", " S "],
  "result": { "item": "omni_tool:omni_tool_diamond" },
  "show_notification": true
}
```

同时会生成进度（advancement）用于解锁配方书：`has_material` 触发器。

---

## 2. 数值推导

### 2.1 挖掘速度

```
镐 = tier.speed                    × 0.8   （下限 1.0）
斧 = (tier.speed - 3.0)            × 0.8   （下限 1.0，原版斧 = tier.speed - 3）
铲 = (tier.speed - 2.0)            × 0.8   （下限 1.0，原版铲 = tier.speed - 2）
```

下限 1.0 的作用：木 Tier 的斧速度原版为 -1.0、铲为 0.0，
若不设下限会得到负速度（挖得比空手还慢，明显是错误的）。

### 2.2 攻击属性

```
攻击伤害 = 3.0 + tier.attackDamageBonus     （与剑完全相同的公式）
攻击速度 = 4.0 + (-2.6) = 1.4               （剑为 4.0 + (-2.4) = 1.6）
```

### 2.3 Tier 原值（取自原版 `Tiers` 枚举）

| Tier | level | uses | speed | damageBonus | enchantmentValue | 修理材料 |
|---|---|---|---|---|---|---|
| WOOD | 0 | 59 | 2.0 | 0.0 | 15 | `#minecraft:planks` |
| STONE | 1 | 131 | 4.0 | 1.0 | 5 | `#minecraft:stone_tool_materials` |
| IRON | 2 | 250 | 6.0 | 2.0 | 14 | iron ingot |
| DIAMOND | 3 | 1561 | 8.0 | 3.0 | 10 | diamond |
| GOLD | 0 | 32 | 12.0 | 0.0 | 22 | gold ingot |
| NETHERITE | 4 | 2031 | 9.0 | 4.0 | 15 | netherite ingot |

### 2.4 推导结果

| 材料 | 耐久 | 镐 | 斧 | 铲 | 攻击伤害 | 攻速 |
|---|---|---|---|---|---|---|
| 木 | 59 | 2.0×0.8 = **1.6** | max(1, -1.0×0.8) = **1.0** | max(1, 0.0×0.8) = **1.0** | 3+0 = **3** | **1.4** |
| 石 | 131 | 4.0×0.8 = **3.2** | 1.0×0.8 = **1.0**(0.8→1.0) | 2.0×0.8 = **1.6** | 3+1 = **4** | **1.4** |
| 铁 | 250 | 6.0×0.8 = **4.8** | 3.0×0.8 = **2.4** | 4.0×0.8 = **3.2** | 3+2 = **5** | **1.4** |
| 金 | 32 | 12.0×0.8 = **9.6** | 9.0×0.8 = **7.2** | 10.0×0.8 = **8.0** | 3+0 = **3** | **1.4** |
| 钻石 | 1561 | 8.0×0.8 = **6.4** | 5.0×0.8 = **4.0** | 6.0×0.8 = **4.8** | 3+3 = **6** | **1.4** |
| 下界合金 | 2031 | 9.0×0.8 = **7.2** | 6.0×0.8 = **4.8** | 7.0×0.8 = **5.6** | 3+4 = **7** | **1.4** |

### 2.5 与原版专用工具对照（钻石）

| | 镐 | 斧 | 铲 | 剑 |
|---|---|---|---|---|
| 原版专用工具 | 8.0 | 5.0 | 6.0 | 伤害 6 / 攻速 1.6 |
| 钻石全能工具 | 6.4 | 4.0 | 4.8 | 伤害 6 / 攻速 1.4 |
| 比例 | 80% | 80% | 80% | 伤害相同、攻速 87.5% |

结论：**在任何单一用途上都慢于专用工具**，这正是"多功能但不专精"的量化定义。

---

## 3. 动态克隆示例

假设某模组添加了"锡"，并注册了 `example:tin_pickaxe`（自带 `Tier`）。

### 3.1 启动时（注册阶段）

```
[OmniTool] Discovered modded material 'tin' (tier level 1, speed 4.5) from
           item.example.tin_pickaxe; registered omni_tool:omni_tool_tin.
           Its recipe will be cloned once recipes are loaded.
[OmniTool] Dynamic layer registered 1 additional Omni Tool(s)
```

此时 `omni_tool:omni_tool_tin` 已经存在于物品注册表中，数值由该 Tier 推导：

```
镐 = 4.5 × 0.8 = 3.6
斧 = (4.5 - 3) × 0.8 = 1.2
铲 = (4.5 - 2) × 0.8 = 2.0
攻击伤害 = 3 + (该 Tier 的 damageBonus)
耐久 = 该 Tier 的 uses
```

### 3.2 玩家加入时（配方阶段）

假设该模组的配方是：

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["TTT", " S ", " S "],
  "key": { "T": { "item": "example:tin_ingot" }, "S": { "item": "minecraft:stick" } },
  "result": { "item": "example:tin_pickaxe" }
}
```

克隆结果是：

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["TTT", " S ", " S "],
  "key": { "T": { "item": "example:tin_ingot" }, "S": { "item": "minecraft:stick" } },
  "result": { "item": "omni_tool:omni_tool_tin" }
}
```

注册名：`omni_tool:dynamic/example/tin_pickaxe`。

```
[OmniTool] Dynamic recipe pass: 1 cloned, 7 already craftable, 0 skipped (no matching tier),
           0 skipped (unsupported recipe type)
[OmniTool] Added 1 dynamically cloned recipe(s); the recipe manager now holds 1204 recipes
```

注意图案是**原模组自己的**（`TTT / " S " / " S "`），我们只替换了输出物 ——
这符合需求中的"保持相同材料与位置，仅替换输出物品"。

### 3.3 幂等性

玩家 2 再次加入时，`OmniToolRecipeCloner` 会发现 `omni_tool_tin` **已经有配方能产出**，
于是跳过，不会产生第二份重复配方：

```
[OmniTool] Dynamic recipe pass: 0 cloned, 8 already craftable, 0 skipped (no matching tier),
           0 skipped (unsupported recipe type)
```

### 3.4 优雅降级示例

| 情况 | 行为 |
|---|---|
| 镐子的 Tier 没有对应全能工具 | 计入 `skipped (no matching tier)`，debug 日志记录配方 id，不生成配方 |
| 配方是自定义 `RecipeSerializer` | 计入 `skipped (unsupported recipe type)`，debug 日志记录类名 |
| 配方结果解析抛异常 | 该条配方按"无结果"处理并记 debug 日志，其它配方照常处理 |
| 整个阶段抛异常 | 捕获并记 error 日志，**保留**已加载的全部原版配方，服务器继续运行 |

---

## 4. 标签

数据生成产出 `omni_tool:omni_tools`（物品标签），包含六个静态注册的全能工具：

```json
{
  "values": [
    "omni_tool:omni_tool_wood",
    "omni_tool:omni_tool_stone",
    "omni_tool:omni_tool_iron",
    "omni_tool:omni_tool_gold",
    "omni_tool:omni_tool_diamond",
    "omni_tool:omni_tool_netherite"
  ]
}
```

动态注册的物品无法在数据生成阶段写入该标签（那时它们还不存在）。
如果需要把它们也纳入，可在运行时通过数据包补充，或按 §HANDOFF 6.3 的方案生成运行时资源包。
