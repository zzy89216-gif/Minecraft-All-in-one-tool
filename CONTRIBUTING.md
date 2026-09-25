# 贡献指南 · Contributing

感谢你愿意参与 Omni Tool。本文档说明代码风格、提交规范与验证流程。

---

## 1. 开发环境

| 项目 | 要求 |
|---|---|
| JDK | 见 `build.gradle` 的 `java.toolchain`（该属性由目标 Minecraft 版本决定） |
| IDE | IntelliJ IDEA 或 Eclipse，导入为 **Gradle 工程** |
| Gradle | 使用仓库自带的 `gradlew`，不要用系统 Gradle（版本见 `gradle/wrapper/gradle-wrapper.properties`） |
| Minecraft / Forge | 见 `gradle.properties`；README 的兼容性表列出各支持线 |
| 网络 | 首次构建需要访问 Forge / Mojang / Maven Central |

```bash
git clone https://github.com/zzy89216-gif/Minecraft-All-in-one-tool.git
cd Minecraft-All-in-one-tool
./gradlew build
```

> 常见问题：如果 `./gradlew` 报 `Could not find or load main class org.gradle.wrapper.GradleWrapperMain`，
> 说明 `gradle/wrapper/gradle-wrapper.jar` 损坏（在某些 FUSE / 移动存储文件系统上会发生）。
> 重新下载该文件即可。

---

## 2. 代码风格

### 通用
- **语言**：注释与文档可用中文；**标识符、日志、提交信息的主体一律使用英文**（对外可读性）。
- **缩进**：4 个空格，不使用 Tab。
- **编码**：UTF-8（构建脚本已强制 `options.encoding = 'UTF-8'`）。
- **行宽**：建议不超过 110 列。
- **导入**：不要使用 `*` 通配导入；保持 IDE 的自动排序（本项目按字母序）。
- **禁止**：`System.out.println`（用 `LOGGER`）、空的 `catch` 块、吞异常的 `catch (Exception ignored)`。

### Java 约定
- 每个公开类型都要有类级 Javadoc，说明**职责**与**为什么这样设计**，而不只是"这是什么"。
- 涉及游戏机制的覆盖方法（`getDestroySpeed`、`isCorrectToolForDrops` 等）
  必须在 Javadoc 里写清楚与**原版行为**的差异及理由。
- 记录类（`record`）优先于手写 POJO。
- 工具方法尽量做成**纯函数**（无副作用、不触碰注册表），以便单元测试覆盖。
- 面向其他模组的入口（事件处理器、物品行为）必须做**异常兜底**：
  任何 `Throwable` 都不能传播到游戏主循环，至少记日志并降级。

### 命名
| 类型 | 规则 | 示例 |
|---|---|---|
| 类 / 记录 | UpperCamelCase | `OmniToolRecipeCloner` |
| 方法 / 字段 | lowerCamelCase | `pickaxeSpeed` |
| 常量 | UPPER_SNAKE_CASE | `BALANCE_FACTOR` |
| 物品注册名 | `omni_tool_` 前缀 + 材料 | `omni_tool_diamond` |
| 语言键 | `item.omni_tool.<id>` | `item.omni_tool.omni_tool_diamond` |

### 分层纪律（重要）
依赖方向必须保持单向：

```
event / datagen / client  →  recipe  →  item  →  registry
```

- 不要把游戏逻辑写进 `event` 包 —— 事件处理器只做"取数据 → 调用逻辑 → 兜底异常"。
- 不要在 `registry` 里反向引用 `item` 的具体行为（只允许引用类型本身）。
- 新增配方克隆能力时，逻辑必须放在 `recipe` 包，且尽量做成可单测的纯函数。

---

## 3. 提交规范

采用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：

```
<type>(<scope>): <subject>
```

**type**

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `docs` | 文档 |
| `refactor` | 重构（不改变外部行为） |
| `perf` | 性能优化 |
| `test` | 测试 |
| `chore` | 构建、脚本、依赖、杂项 |
| `style` | 格式（不改逻辑） |

**scope** 建议取值：`registry`、`item`、`recipe`、`event`、`client`、`datagen`、`scripts`。

**示例**

```
feat(recipe): 支持锻造台配方的克隆
fix(item): 修复斧类方块无掉落的问题
docs(handoff): 补充动态层的负缓存优化点
test(registry): 覆盖材料名解析的边界情况
```

**拆分要求**
- 一个提交只做一件事；重构与行为变更不要混在一起。
- 提交信息用**祈使句**（"修复"而不是"修复了"）。
- 不要提交 `build/`、`.gradle/`、`run/`、`run-data/`、`.env` 或任何密钥。

---

## 4. 测试要求

新增或修改逻辑时，请尽量同时补充测试。

```bash
./gradlew test          # 运行 JUnit 5 测试
./gradlew build         # 编译 + 测试 + 打包
```

**可测试的**（纯逻辑，无需游戏启动）：
- 材料数值推导（`OmniToolMaterial`）
- 名称解析与清洗（`DynamicOmniToolRegistrar#materialKeyOf` / `sanitize`）
- 配方克隆的决策规则（可把 `Recipe` 抽象出来做参数化测试）

**不便单测的**（属于集成验证，请在 PR 描述中说明你如何手工验证）：
- 事件触发时机（`OnDatapackSyncEvent`）
- 客户端模型映射
- 与 `TierSortingRegistry` 的交互

数据生成有回归保护：

```bash
./gradlew runData
git diff --stat src/generated   # 除预期改动外应为空
```

---

## 5. Pull Request 流程

1. Fork 仓库，从 `main` 切出分支：`feat/xxx`、`fix/xxx`。
2. 完成改动，保证 `./gradlew build` 全绿。
3. 若涉及资源变更，执行 `./gradlew runData` 并把生成结果一起提交。
4. PR 描述请包含：
   - **动机**：解决什么问题；
   - **方案**：为什么这样做（尤其是有取舍时）；
   - **验证**：跑了哪些命令、在游戏里怎么验证的；
   - **影响面**：是否影响已有存档、是否影响与其他模组的兼容性。
5. 新增功能请同步更新 `CHANGELOG.md` 的 `[Unreleased]` 段落；
   涉及架构或局限性的变更请同步更新 `HANDOFF.md`。

---

## 6. 报告问题

请附上：
- 游戏版本、Forge 版本、模组版本；
- `logs/latest.log` 中与 `[OmniTool]` 相关的行；
- 使用的其他模组列表（动态材料层的很多行为与"其他模组如何定义 Tier"有关）；
- 复现步骤。

---

## 7. 安全与密钥

- **永远不要**把 GitHub Token、`.env` 或任何凭据提交到仓库。
- 上传脚本 `scripts/upload_and_cleanup.sh` 从本地的 `.env` 读取令牌，该文件已被 `.gitignore` 排除。
- 如果误提交了凭据：立刻在 GitHub 上吊销该 Token，并清理历史（`git filter-repo`）。
