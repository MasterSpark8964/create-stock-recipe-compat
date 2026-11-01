# 0.4.6-client.1 验证记录

## 已完成

- Java 17 源码编译、Mixin 注解处理、Jar 打包及重混淆。
- 19 项材料分配单元测试通过，0 项跳过，使用 JUnit Platform Console 1.10.2 运行。
- 3000 组材料分配和 5000 组按钮布局输入与发布 Jar 对照，结果一致。
- 构建产物与发布 Jar 的 74 个类、449 个非 synthetic 方法、294 个非 synthetic 字段契约以及 6 项资源一致。类成员核对不等于全部行为验证。

数据：[单元测试摘要](recovery/unit-tests.txt)、[类成员与资源核对](recovery/rebuilt-contracts.json)。

## 构建环境

Minecraft 1.20.1、Forge 47.4.18、Create 6.0.8、JEI 15.8.2.26、Java 17。

本次使用本地依赖镜像、mergetool 1.1.7，以及 Mixin 0.8.5 普通 Jar 内的注解处理器；生成的 refmap 与发布包一致。仓库常规配置使用官方 processor 依赖。

Gradle 已完成 compileJava、jar、reobfJar 和 compileTestJava。test 任务因离线环境缺少 Flywheel / JEI 客户端运行 Jar 未能启动，随后通过 JUnit Platform Console 运行全部现有单元测试。不能将该结果表述为完整 Gradle test/build 任务成功。

## 未覆盖

实机界面、实际发包、服务器未安装本附属时的联机行为，以及整合包中的长期性能和内存表现，尚未完成验证。`src/integrationTest` 和 `docs/VALIDATION.md` 为早期测试资料，未用于此次验收。
