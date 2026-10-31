# 0.4.6-client.1 源码恢复

2026-10-31，因旧工作区已被环境维护清理且未找到对应源码归档，从仓库下载区保留的 `create_stock_recipe_compat-0.4.6-client.1.jar` 恢复实现。

## 来源和方法

- 原 Jar 大小 162015 字节，含 74 个 class 文件。
- 使用 Vineflower 1.10.1 恢复为 41 个顶层 Java 源文件，包含内部类、记录、回调与 Mixin。
- 保留 Jar 内的模组元数据、语言文件、Mixin 配置、pack.mcmeta；模组版本字段恢复为 Gradle 构建占位符。
- 92 个不同的 Minecraft SRG 方法/字段名称转换回 1.20.1 官方开发名称，转换结果逐项与 ForgeGradle 生成的 createMcpToSrg 映射核对，未发现不一致。
- 原 refmap 保存在 `docs/recovery/original-refmap.json`，构建时由注解处理器重新生成发布 refmap。
- 构建框架沿用仓库中 0.3.6 的 Gradle 配置，并恢复 0.4.6 所需的 JEI common/gui 编译依赖、运行依赖版本分离与固定 ForgeGradle 版本。

## 可恢复和不可恢复

可恢复的是字节码中的实现、签名、常量、分支、字段、注解和资源。部分局部变量名保留，部分由反编译器生成。原始注释、排版、完整测试源码和当时的构建日志不能从成品 Jar 找回。反编译代码不应宣称为逐字还原的原始源码。

## 验证

11 个不依赖 Minecraft 的 Java 源文件已独立编译。通过隔离类加载器，让恢复后的算法与原 Jar 使用相同输入运行：

- 3000 组材料分配：候选重叠、缺料、库存数量、已存在物品、种类限制和批次数量，返回的轮数、完整分配矩阵或异常相同。
- 5000 组按钮布局：高度、间隔和多行排列结果相同。

对照工具：`tools/RecoveryEquivalence.java`。需要先编译恢复源码，将原 Jar 路径和恢复后的 classes 目录作为两个参数传入。固定随机种子便于复查。

全部 41 个 Java 源文件已经通过 Java 17 编译（不运行注解处理器）。修正了三处反编译产物：输出槽变量名错位、Recipe 泛型信息丢失、Mixin 交叉转型缺少 Object 中间转换。

将开发名称转换因素计入后，编译生成的 74 个类、449 个非 synthetic 方法、294 个非 synthetic 字段与原 Jar 的类/成员契约一致；这属于结构核对，不是全部行为等价的证明。结果见 `docs/recovery/java-contracts.json`。

Gradle 已完成 compileJava、jar、reobfJar 和 compileTestJava，产出重建 Jar。原 Jar 与重建包的全部 74 个类、449 个非 synthetic 方法、294 个非 synthetic 字段契约，以及 6 项资源内容一致（JSON 按结构比较）。结果见 `docs/recovery/rebuilt-contracts.json`。这不等于逐字节相同或完整行为等价。

本环境通过本地依赖镜像进行构建验证，使用 Forge 同时声明的 mergetool 1.1.7；因 processor 分类包下载受限，使用同版本 Mixin 0.8.5 普通 Jar 中的原有注解处理器及其依赖，重新生成的 refmap 与原包一致。仓库的常规构建配置保留官方 processor 依赖。

Gradle test 任务因离线环境缺少 Flywheel / JEI 客户端运行 Jar 而未启动；随后使用 JUnit Platform Console 1.10.2 对 Gradle 编译出的全部现有测试（CapacityMatcherTest）运行，19 项全部通过，0 项跳过。测试摘要见 `docs/recovery/unit-tests.txt`。不能将此表述为完整 Gradle test/build 任务成功。

此前的 3000 组材料分配与 5000 组按钮布局对照也通过。公开下载区保留原成品；重建验证包不覆盖原 Jar。没有启动游戏，也没有完成服务器未安装本附属时的联机验收。不能把算法对照等同于全部游戏功能已验证。
