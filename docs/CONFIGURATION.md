# 配置说明（0.3.6）

文件：客户端实例下的 `config/create_stock_recipe_compat-client.toml`。以下键均位于已有的 `[general]` 节中；不要重复添加同名节。旧配置值会保留，新默认值不会自动覆盖它们。

## 功能开关

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| enableUniversalJeiMaterialTransfer | true | 总开关 |
| forceJeiForCraftingRecipes | false | 强制工作台配方使用材料订单，不附带自动合成信息 |
| allowSyntheticRecipes | true | 允许没有 Minecraft Recipe 对象的 JEI 配方使用本地材料适配 |
| expandMissingMaterials | true | 缺料时递归查找受支持的中间配方 |
| allowNonItemOutputs | true | 无物品输出时使用材料批次图标 |
| useBatchUnitsForAllRecipes | false | 被本 Mod 接管的配方统一按材料批次计数；不会自动识别概率 |
| rejectNonItemInputs | true | 保留的兼容键；当前即使设为 false，也仍拒绝必需的非物品输入 |
| debugLogging | false | 输出材料候选、接受订单和拒绝原因等排查信息 |

`expandMissingMaterials=false` 可关闭递归补料。普通工作台在直接材料足够且未开启强制模式时，保留 Create 原处理路径。

## 搜索限制

| 键 | 默认值 | 允许范围 | 含义 |
| --- | ---: | ---: | --- |
| maxDependencyDepth | 16 | 1–32 | 中间配方层数 |
| maxExpandedIngredients | 512 | 1–2048 | 展开后的物品材料单位数 |
| maxStockVariants | 16384 | 256–32768 | 参与处理的库存物品变体数 |
| maxDependencyRecipes | 65536 | 1024–131072 | 中间配方数量 |
| dependencySearchOperations | 2000000 | 80000–20000000 | 单次递归规划的工作量预算 |
| maxDependencyDecisionFrames | 256 | 64–384 | 活跃决策帧限制；与配方层数不同 |
| dependencyPlanningMillis | 500 | 50–5000 | 单次递归规划的协作式时间预算，单位毫秒 |

提高上限可能让一次规划占用客户端更久，并不保证找到结果或减少卡顿。降低时间预算会更早结束复杂搜索。时间预算不能在模组回调执行中途强行打断它。

另外保留不可通过这些配置取消的限制：Create 每单最多 9 种实际物品；匹配器最多 16,777,216 个矩阵单元、262,144 条候选边，并有独立的流与类型集合搜索预算。提高递归上限不会消除这些限制。

## 为什么会提示失败

- **原料不足**：在允许展开的配方范围内没有找到库存足够的完整方案，也可能是对应工艺被忽略。
- **配方复杂 / 搜索量超限**：计算触及工作量或决策保护，不能据此认定缺料。
- **规划超时**：达到时间预算，不能据此认定缺料。
- **深度、材料数、库存变体数、配方数超限**：对应的配置限制被触及。
- **超过 9 种物品**：Create 的实际订单种类限制。

序列组装、粉碎等机器工艺，以及已识别的附魔书制作和纹饰配方，不作为递归生产路径；需要直接储备对应材料。

## 预览冷却

同一菜单与配方的递归预览刚因时间、工作量或决策帧超限失败时，一秒内复用这个失败提示。成功方案和普通缺料结果不在此缓存中，实际下单也不使用它。修改库存或配置后，上一次的限制提示可能最多保留一秒。
