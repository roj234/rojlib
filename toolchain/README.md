# MCMake，你的最后一个模组开发工具

![Logo](docs/mcmake-icon.png)  
**Version: 3.14.2** | **By: Roj234** | **License: BSD**  
[GitHub](https://github.com/roj234/rojlib) | [文档](docs/) | [问题追踪](https://github.com/roj234/rojlib/issues)

---

## 介绍

MCMake 是一款主要为 Minecraft 模组开发设计的轻量级、快速构建工具。它采用纯 Java 实现，不依赖外部构建系统，集成了编译、资源处理、依赖管理、增量构建以及一个高度可扩展的插件系统。MCMake 的核心目标是**简化 Minecraft 模组、插件和标准 Java 项目的开发流程**，让您将更多时间投入到编码中，而不是漫长的等待。

与传统的构建工具（如 ForgeGradle）不同，MCMake 强调以下特性：

### 极致速度 (~~Blazing Fast~~)

-   **零依赖**：自身仅 3MB 的 JAR 文件，轻巧高效。
-   **开发即构建**：MCMake 作为一个常驻后台的编译服务器，与您的 IDE 相伴相生，而非“点击、构建、退出”的模式。
-   **离线编译**：工作空间创建完成后，**不再需要互联网连接**。
-   **智能缓存**：首次冷启动构建后，后续编译速度显著提升。
    -   **共享符号表**：部分编译器支持，缓存 JDK 标准库、类路径和模块自身的符号（结构），有效减少磁盘 I/O 并提高编译速度。
    -   **依赖图**：若类 A 被类 B 引用，则 A 变更时 B 将同时编译。
    -   **注解缓存**：MCMake 插件可能搜索类文件中的注解，此缓存将时间复杂度优化到 O(依赖链长度)。
    -   **类结构缓存**：追踪类的成员（方法、字段、内部类，递归）。如果这些数据在编译后未发生改变（例如，只修改了方法体），则修改的类不会跨越模块边界，从而减少编译开销。
-   **增量构建**：实时监听源代码和资源变化，仅编译修改/删除的类及其相关依赖。在中端电脑（Intel Core i5）上，典型的增量构建**仅需数十毫秒**。
-   **自动编译**：实时跟踪文件变化并自动触发构建，支持**热重载式开发**，并通过提供 JVM 参数在客户端中加载 `javaagent` 动态修补运行中的 JVM。
-   **异步处理**：资源复制和编译同时进行，名称映射和部分编译器实现支持并发编译。
-   **JAR 签名**：基于 RojLib 的快速实现，无需 `jarsigner` 依赖（暂不支持生成时间戳签名）。
-   **统计**：运行 `statistic` 命令可查看构建次数、成功率和节省的时间等统计数据。

### 极致便利 (Configure Less, Do More)

-   **TUI 支持**：提供彩色输出、进度条、自动补全以及自动生成的指令帮助（按 F1 键查看）。
-   **多项目支持**：在一个工具中同时编译多个文件夹内的多个不同工作空间的多个项目。
-   **依赖管理**：支持项目、本地文件和 Maven 工件依赖，并提供哈希校验。提供 `COMPILE`（仅编译）、`EXPORT`（传递）、`BUNDLED`（打包）三种范围。
-   **项目管理**：项目和工作空间（MC 版本）可以导入/导出为压缩包，方便同步与备份。
-   **变量替换**：构建时替换代码或资源中 `${variable}` 格式的变量。
-   **模组依赖**：使用 `unmap <workspace> mod_file_path [dependency_file_path...]` 创建未映射（编译名）依赖。
-   **模块化编译 (WIP)**：支持编译为 Java 模块。
-   **自定义编译器 (WIP)**：除了 `javac`，未来将支持如 Kotlin 等自定义编译器。

### 极致扩展性 (~~Arbitrary Code Execution lol~~)

-   **插件系统**：可扩展的 `Processor` 接口，支持自定义资源转换、编译钩子和编译期 Mixin 注入。
-   **变换工具**：`CodeWeaver`、`ConstantPoolHooks` 和 `Mapper` 用于代码混淆/映射。
-   **AccessTransformer 支持**：提供编译和 IDE 集成。
-   **Mixin 处理**：支持 Mixin 类的处理（但不生成 refmaps）。
-   **映射**：编译时自动应用映射。
-   **自定义映射**：除了默认的映射配置外，内置 `map create` 图形界面可生成自定义映射（.xsrg 格式）。
-   **执行脚本**：使用 `runscript <filename>.lava` 执行脚本（Lava 编程语言的详细信息请参阅 RojLib 主页）。

> **MCMake 已用于多个生产级模组项目**：自 2019 年起，我所有的模组以及本项目自身都使用 MCMake 进行编译。

---

## 快速开始

### 系统要求
-   Java 17-21。如果您使用 `javac` 而非 Lava 编译器，则必须使用 JDK。
-   支持 Windows/macOS/Linux 操作系统。
-   Minecraft 版本：1.7.10 - 1.20.x。
-   磁盘空间：每个 Minecraft 工作区约 100MB。
-   IDE：推荐使用 IntelliJ IDEA。

### 1. 下载与安装
1.  从 [Releases](https://github.com/roj234/rojlib/releases) 下载 `MCMake-${version}.jar`。
2.  创建项目目录并运行 MCMake：
    ```bash
    mkdir my-mod-project
    cd my-mod-project
    java -jar bin/MCMake.jar
    ```
3.  MCMake 将自动创建 `conf/` 目录（包含 `config.yml` 和 `env.yml`）、`cache/` 和 `build/` 等目录。

#### 配置文件介绍
MCMake 使用 YAML 格式进行配置。编辑 `conf/config.yml` 以定义工作空间类型和编译器类型：
-   工作空间*类型*是一个工厂，用于创建工作空间*实例*。默认的 `Minecraft` 和 `Empty` 类型通常已满足需求。
-   这些工厂的配置不包含 Minecraft 版本、加载器等信息，它们将通过交互式输入提供（参见下文）。
-   编译器*类型*同样是一个工厂，用于创建编译器*实例*。

```yaml
工作空间类型:
  Minecraft:
    type: roj.ci.minecraft.MinecraftWorkspace # 内置 Fabric/Forge 支持
    # ... 其他配置
编译器类型:
  Javac:
    type: roj.ci.JCompiler # 默认使用 javac
    # ... 编译选项
```
更多配置详情请参考 [CONFIG.md](docs/CONFIG.md)。
-   [config.yml 的格式规范](resources/config.schema.yml)
-   [env.yml 的格式规范](resources/env.schema.yml)
-   MCMake会在启动时验证这些schema

### 2. 配置工作空间 (交互式)
使用以下命令创建工作空间：
```bash
workspace create Minecraft
```
-   指定已安装 Mod Loader 的 Minecraft 客户端目录。
-   若版本高于当前 MCMake 支持上限，可能需要手动识别并处理新版本 Minecraft 的布尔规则。
-   提供映射配置 (`MappingBuilder`) 或自定义映射文件（`.xsrg`）。
-   （可选）提供服务器目录（按 Ctrl+C 跳过）。
-   命名并保存工作空间（约 30 秒完成）。
-   新建的工作空间默认使用 `projects/` 作为所有子项目的根目录，这可以在 `env.yml` 中修改。

### 3. 创建项目 (交互式)
使用以下命令创建项目：
```bash
project create mymod mc-1.20-fabric
```
-   这将生成以下项目结构：
    ```
    projects/mymod/
    ├── java/          # 源代码
    └── resources/     # 资源文件（mods.toml 等）
    ```
-   编辑 `conf/env.yml` 添加依赖，例如：
    ```yaml
    projects:
      - name: mymod
        version: 1.0.0
        workspace: mc-1.20-fabric
        dependency:
          core: COMPILE # 其它项目依赖
          maven:///net.java.dev.jna:jna:5.12.1: COMPILE # Maven 依赖
          file:///../other-mod.jar: COMPILE  # 本地文件
          file:///lib: COMPILE  # 库文件夹依赖
          resource:///D:/build/manifest: BUNDLED # 外部（未压缩）资源依赖
    ```
-   保存后输入 `reload` 命令重新加载配置。
-   **注意事项**：请勿在 IntelliJ IDEA 中修改项目的依赖，它们将被 MCMake 覆盖。

### 4. 开发与构建
-   在 IntelliJ IDEA 中打开项目，开始编写代码。 (MCMake 会自动生成 IDEA 项目文件)。
-   构建项目：
    ```bash
    build mymod          # 标准增量构建
    build mymod full     # 强制全量重建
    ```
-   输出的 JAR 文件位于 `build/` 目录，命名为 `mymod-1.0.0.jar`。
-   启用自动编译：`project auto mymod true`。
-   热重载：启用后，MCMake 将提供 JVM 参数，您可以手动将其添加至启动器。

**重要笔记**：
-   **指令帮助**：在 MCMake CLI 中按下键盘 `F1` 键可查看指令列表、参数类型及详细介绍。更多命令详情请参考 [COMMANDS.md](docs/COMMANDS.md)。
-   **无“开发模式”**：MCMake 直接输出可用的 JAR 文件。您可以使用 `fmd:copy_to` 变量让 MCMake 自动将 JAR 复制到 `mods` 文件夹。
-   **MCMake 不启动 Minecraft**：MCMake 专注于构建，将 Minecraft 的启动委托给外部工具。

MCMake 的前身 FMD 曾包含启动器和自动安装功能，但维护多版本兼容性对单个开发者而言过于繁重。全新的 MCMake 专注于核心开发流程，将启动任务委托给成熟的启动器。  
根据开发者自身的经验，使用 MCMake 后，每次构建可节省 90% 以上的时间！

感谢使用 MCMake！把时间花在更有意义的事情上——快乐编码！ 🚀

---
## 限制

-   **不与 Gradle 对齐**：MCMake 完全从零开发，因此不默认支持 Kotlin、Architectury 以及插件生态，前述 Mixin 支持等均为作者编写的插件。
-   **专注于 Minecraft**：非常适合模组/插件开发；纯 Java 项目也能工作，但可能缺少一些生态系统集成。
-   **文件名限制**：不建议在一个 `.java` 文件内定义多个非公共辅助类，也不建议 Java 文件夹的包名与实际包名不符，这些情况可能影响增量编译的完整性。

## 计划 (未实现, 可能还要很久)
- [ ] ShadowJar：目前实现了一半 | 如果是来自Dependency的类，给他们都加个前缀，然后走一遭Mapper
- [ ] 更好的热重载：加入项目标识符，避免广播修改信息；此外，还可以复用结构变更检测结果，若变更，断开与非DCEVM客户端的连接
- [ ] 可复现性：加入package.lock类似物以保证各方能获得一致的依赖
- [ ] 可跟踪性：在构建产物中加入依赖的元数据，例如pom.yml
- [ ] 执行受限代码：目前为了安全性，脚本只能由用户交互执行；考虑通过Lava的类白名单机制允许执行安全的代码，这样还可以让变量通过脚本计算得到（集成现有的Formatter/Template系统）
- [ ] 配置迁移：目前有配置格式校验，但不能自动升级，考虑用比一大堆containsKey + put更好的办法解决
- [ ] 自动化测试：在构建后进行测试
- [ ] 移除全局锁：目前同一时间只能构建一个项目（monorepo），并不是大问题，你能启动多个MCMake实例

---

## 诊断和研究
- 日志保存在 `cache/logs` 中，并以天为单位轮转，默认等级为 `TRACE` 包含构建过程的详细信息
- 构建时使用 `profile` 参数以打开profiler GUI，展示各部分耗时

### MCMake 的自举 (用于构建工具本身) (WIP, 可能会频繁变更)
> 又名：导入项目（非交互式）
1.  在 MCMake CLI 中输入 `workspace import <项目根文件夹>`（例如克隆本项目）。
2.  如果工作空间类型、编译器类型或插件中的任何一项缺失，MCMake 将发出警告并拒绝导入，您需要添加它们并重启 MCMake。
3.  工作空间将从 `env.yml` 的元数据中自动重新构建。对于 Minecraft 类型的工作空间，您需要提供与导出时严格相同的 Minecraft 版本，以及相同或更高版本的模组加载器。
4.  如果工作空间别名或项目别名重复，系统将提示用户交互并重命名。

### 将你的 MCMake 项目添加到版本控制 (VCS) (WIP, 可能会频繁变更)
> 又名：导出项目（非交互式）

将你的 `env.yml` 复制到项目根文件夹中（默认为 `projects/`）。  
你需要对其进行一定程度的修改，以确保其他人也能正确使用你的项目：
1.  删除敏感和无意义的信息，例如某些变量（密码）、以及未使用的工作空间、项目等。
2.  移除本地文件依赖，例如切换为带有 SHA1 校验的 Maven 依赖（若存在），确保其他人也能获取相同的依赖项。
3.  不建议使用 `lib` 目录中的本地依赖，这些文件并不属于“代码”，也不应该被 VCS 管理。

### 高级：自定义 `MappingBuilder` 示例
**以 1.20.1 Forge 为例，使用 Yarn 映射而非 Mojang 映射：**  
*(注意：MCP 命名准备起来更复杂，目前仅通过 GUI 可用。)*

1.  下载映射文件：
    *   Yarn: [ZIP](https://codeload.github.com/FabricMC/yarn/zip/refs/heads/1.20.1)
    *   Intermediary: [1.20.1.tiny](https://github.com/FabricMC/intermediary/raw/refs/heads/master/mappings/1.20.1.tiny)
    *   MCP Config: [ZIP](https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/mcp_config-1.20.1-20230612.114412.zip)
    *   Mojang: 可从客户端/服务器的 JSON 文件中获取。

2.  创建 `custom_mappings.yml` 文件（请替换路径）：
```yaml
# ================================================================
# MappingBuilder 配置文件示例
# 处理操作说明（支持的操作符）：
# ----------------------------------------------------------------
# L-<type>-<path> : 从文件加载特定类型的映射
#   - type: 映射类型 (yarn/intermediary/mojang/tsrg/srg)
#   - path: 文件路径或URL（本地路径推荐）
# Mx-m1-m2-... : 顺序组合多个映射（x表示映射数量）
# F-m         : 翻转映射关系（左⇋右互换）
# Dl-m        : 删除左侧类映射（设置left=right），副作用：会翻转映射
# Dr-m        : 删除右侧类映射（设置right=left）
# E-ma-mb     : 扩展映射（连接两个映射），例如 E-a_to_b-b_to_c 生成 a_to_c
# A-ma-mb     : 将mb中的参数名称复制到ma中，要求两个映射的主键相同
#
# 请注意，不建议直接编辑配置文件，请使用mapping create打开GUI进行编辑！
# 请注意，不建议直接编辑配置文件，请使用mapping create打开GUI进行编辑！
# 请注意，不建议直接编辑配置文件，请使用mapping create打开GUI进行编辑！
# ================================================================
client: L-mojang-client.txt
server: L-mojang-server.txt
mcp_config: L-tsrg-mcp_config-1.20.1-20230612.114412.zip
override: L-srg-override.srg
yarn: L-yarn-yarn-1.20.1.zip
inte: L-intermediary-1.20.1.tiny
yarn_args: F-E-E-F-yarn-F-inte-mojang
tmp1: Dr-F-E-F-Dr-mcp_config-mojang
tmp2: F-A-tmp1-yarn_args
output: M2-tmp2-F-override
```
*   键的名称必须由小写字母和下划线 MCMake。
*   值的语法类似于逆波兰表达式 (RPN)，您可以在 `mapping create` 打开的 GUI 中进行可视化调试。
*   每行的操作从上到下按顺序执行。
*   配置文件必须包含 `output` 键，并且该键必须提供一个**编译名**（yarn/mcp/...）到**中间名**（srg/intermediary）的映射表。

---