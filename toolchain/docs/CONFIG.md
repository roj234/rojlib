好的，根据你提供的 Java 代码片段和 `env.schema.yml`，以下是为你编写的 `CONFIG.md` 文件内容。这将详细解释 MCMake 的配置文件结构和各项参数。

---

# MCMake 配置指南

MCMake 的核心配置通过 `conf/config.yml` 和 `conf/env.yml` 两个 YAML 文件进行管理。`config.yml` 定义了全局的工厂类型，例如工作空间工厂和编译器工厂，而 `env.yml` 则用于定义特定环境（如你的项目环境）下的工作空间实例和项目实例，以及它们的具体属性和依赖。

## env.yml 文件结构

`env.yml` 是 MCMake 中最重要的配置文件之一，它定义了你的所有工作空间和项目。

```yaml
# env.yml 示例结构
workspaces:
  - id: my_mc_workspace
    path: mc_env # 可选，默认为id
    # ... 其他工作空间配置
  - id: my_java_workspace
    # ...
projects:
  - name: my_first_mod
    workspace: my_mc_workspace
    # ... 其他项目配置
  - name: my_shared_lib
    type: MODULE # 项目类型
    # ...
default_project: "my_first_mod" # 默认项目，可选
auto_compile: ["my_first_mod"] # 自动编译的项目列表，可选
```

### 1. `workspaces` (列表)

`workspaces` 键包含一个列表，每个元素定义了一个 MCMake 工作空间。一个工作空间可以被视为一个物理目录、一组固定的依赖项、预处理器和预定义变量的集合。你可以通过 MCMake 的 `workspace` 指令对其进行修改。

#### 工作空间属性 (每个列表元素)：

-   `type` (字符串, 可选)：
    -   **描述**：工作空间的类型标识符。虽然目前未被 MCMake 内部代码直接使用，但它是对工作空间进行分类的有用标记。
    -   **示例**：`Minecraft` (mod开发), `Java` (普通Java项目)。
-   `id` (字符串, **必需**)：
    -   **描述**：工作空间的唯一标识符。MCMake 指令补全和项目依赖中都会用到这个 ID。
    -   **示例**：`mc_1_20_forge`, `my_lib_dev`。
-   `path` (字符串, 可选)：
    -   **描述**：相对于 MCMake 根目录的工作空间子路径。如果缺失，默认等于 `id`。
    -   **示例**：`mc_envs/1.20_forge`。
-   `depend` (字符串列表, 可选)：
    -   **描述**：公共的、不受映射影响的依赖列表。这些依赖可以是位于 MCMake 缓存目录的相对路径也可以是绝对路径。
    -   **示例**：
        ```yaml
        depend:
          - "path/to/libraries.jar"
        ```
-   `mappedDepend` (字符串列表, 可选)：
    -   **描述**：已映射的依赖列表（使用中间名或映射的 target 名）。这些依赖可以是位于 MCMake 缓存目录的相对路径也可以是绝对路径。
    -   **示例**：`mappedDepend: ["path/to/minecraft_client.jar"]`
-   `unmappedDepend` (字符串列表, 可选)：
    -   **描述**：未映射的依赖列表（使用编译名或映射的 source 名）。这些依赖可以是位于 MCMake 缓存目录的相对路径也可以是绝对路径。
    -   **示例**：`unmappedDepend: ["path/to/unmapped_minecraft_client.jar"]`
-   `mapping` (字符串, 可选)：
    -   **描述**：常量池格式的压缩映射表文件路径。这个文件路径可以是相对于 MCMake 缓存目录的相对路径也可以是绝对路径。你可以在 MCMake 的 `Mapper GUI` 中预览和编辑这种格式的映射。
    -   **示例**：`mapping: "mappings/my_custom_map.lzma"`
-   `processors` (字符串列表, 可选)：
    -   **描述**：处理器的全限定类名列表，即 MCMake 插件。例如 `com.example.MyProcessor`。这些处理器必须在 `config.yml` 中声明，才能在此处被启用。
    -   **示例**：`processors: ["roj.ci.minecraft.FabricEnvProcessor"]`
-   `variables` (映射, 可选)：
    -   **描述**：自定义变量，可用于自定义配置，也可以进行模板替换。包含 `project_name` 和 `project_version` 两个内置变量。
    -   **内置系统变量**：
        -   `fmd:maven:central` (字符串, 默认: `https://repo1.maven.org/maven2/`)：Maven 远程仓库地址。可以使用 `;` 连接多个地址，MCMake 将从第一个不返回 404 的地址下载。
        -   `fmd:maven:cache` (字符串, 默认: `.m2`)：Maven 本地缓存目录（相对于 MCMake 构建缓存目录）。可以手动更改到用户目录。
        -   `fmd:at` (字符串, 默认: `META-INF/accesstransformer.cfg`)：AccessTransformer.cfg 的默认位置。通常不需要改动。
        -   `fmd:exportBundle` (布尔值, 默认: `true`)：当该项目作为 `EXPORT` 类型的依赖被递归 `BUNDLED` 时，是否应该包含它。
        -   `fmd:annotation_cache` (布尔值, 默认: `true`)：在构建产物中生成注解缓存。
        -   `fmd:compiler` (字符串, 默认: `Javac`)：在 `config.yml` 中定义的编译器 ID。
        -   `fmd:name_format` (字符串, 默认: `${project_name}-${project_version}.jar`)：构建产物的名称格式。
        -   `fmd:signature:keystore` (字符串, 可选)：JAR 签名密钥库 (Keystore) 路径。
        -   `fmd:signature:keystore_pass` (字符串, 可选)：JAR 签名密钥库密码。
        -   `fmd:signature:key_alias` (字符串, 可选)：JAR 签名密钥 aliases。
        -   全部变量可在`env.schema.yml`中查看
    -   **示例**：
        ```yaml
        variables:
          my_custom_var: "some_value"
          fmd:maven:central: "https://maven.fabricmc.net;https://repo1.maven.org/maven2/"
        ```
-   `variableReplaceContext` (字符串列表, 可选)：
    -   **描述**：需要进行变量替换的文件路径模式集合。MCMake 会扫描这些路径下匹配的文件，并将其中的 `${variable}` 格式的字符串替换为实际变量值。
    -   **示例**：`variableReplaceContext: ["**.java", "resources/*.json"]`

### 2. `projects` (列表)

`projects` 键包含一个列表，每个元素定义了一个 MCMake 项目。

#### 项目属性 (每个列表元素)：

-   `name` (字符串, **必需**)：
    -   **描述**：项目的名称，必须唯一。
    -   **示例**：`my_mod`, `shared_api`。
-   `type` (字符串, 默认: `PROJECT`)：
    -   **描述**：项目的类型。
        -   `PROJECT`：可独立构建的项目，拥有自己的输出 JAR。
        -   `MODULE`：子模块，不可单独构建，必须作为其他项目的一部分。其输出通常会合并到引用它的项目中。
        -   `ARTIFACT`：本身没有源代码需要编译，仅用于组合 `PROJECT` 或 `MODULE` 来生成自定义的构建产物。
    -   **允许值**：`PROJECT`, `MODULE`, `ARTIFACT`。
-   `version` (字符串, 默认: `1.0.0`)：
    -   **描述**：项目的版本号。遵循 SemVer 规范。
    -   **示例**：`1.0.0-SNAPSHOT`, `2.1.3+build123`。
-   `charset` (字符串, 默认: `UTF-8`)：
    -   **描述**：源代码文件的字符集。**此项已弃用，Java 编译器通常会自动识别。**
    -   **示例**：`UTF-8`, `GBK`。
-   `compiler_options` (字符串列表, 可选)：
    -   **描述**：传递给编译器的额外选项。**此项已弃用，现在更推荐通过 `config.yml` 配置编译器类型以及在工作空间的 `variables` 中使用 `fmd:compiler` 配置编译器。**
    -   **示例**：`compiler_options: ["-Xlint:all", "-parameters"]`
-   `workspace` (字符串, **必需**)：
    -   **描述**：项目所属的工作空间 ID。该 ID 必须与 `workspaces` 列表中定义的某个 `id` 匹配。
    -   **示例**：`my_mc_workspace`。
-   `dependency` (映射表, 可选)：
    -   **描述**：项目的依赖列表。键是依赖的标识符，值是依赖的作用域。
    -   **依赖标识符格式**：
        -   **项目依赖**：直接使用项目名称。例如 `other_mod: EXPORT`。
        -   **文件依赖**：`file://<路径>`。路径可以是相对路径或绝对路径。支持 `file://<checksum_algo>@<checksum_hex>:<path>` 格式进行校验。
            -   **示例**：`file:///path/to/mylib.jar: COMPILE`, `file://SHA1@d5e3f4a...:relative/lib.jar: BUNDLED`
        -   **Maven 依赖**：`maven://<groupId>:<artifactId>:<version>[:<classifier>]`。
            -   **示例**：`maven://net.fabricmc:fabric-api:1.0:COMPILE`, `maven://org.lwjgl:lwjgl:3.3.1:natives-linux: BUNDLED`。支持 `maven://<checksum_algo>@<checksum_hex>:<groupId>...` 格式校验。
    -   **依赖作用域**：
        -   `COMPILE`：仅在编译时添加到类路径中，不会打包到最终产物。
        -   `EXPORT`：在编译时添加到类路径中，并且会传递给所有依赖该项目的项目，若引用者类型为BUNDLED，最终也会打包到产物中。
        -   `BUNDLED`：编译时和运行时都可用，强制打包到最终产物中，即使未被直接引用。
    -   **示例**：
        ```yaml
        dependency:
          fabric-api:1.20.1: COMPILE # Maven 风格依赖
          file:../other-mod.jar: BUNDLED # 本地文件依赖
          my_shared_lib: EXPORT # 项目依赖
        ```
-   `variables` (映射表, 可选)：
    -   **描述**：项目专属的自定义变量。会覆盖工作空间中同名变量，并可用于模板替换。
    -   **请参阅上文工作空间 `variables` 部分的详细描述。**
-   `variableReplaceContext` (字符串列表, 可选)：
    -   **描述**：项目专属的需要进行变量替换的文件路径模式集合。
    -   **请参阅上文工作空间 `variableReplaceContext` 部分的详细描述。**
-   `shade` (映射表, 可选)：
    -   **描述**：打包时需要重命名的文件路径模式映射。键是查找模式，值是替换字符串。
        -   以 `/` 开头的键会被视为正则表达式，替换字符串中可以使用 `$1`, `$2` 等捕获组。
        -   非 `/` 开头的键会被视为前缀匹配。
        -   值可以设置为 `null` 以删除匹配的文件。
    -   **示例**：
        ```yaml
        shade:
          "org/example/": "com/mycompany/shaded/org/example/" # 前缀重命名
          "/^(META-INF\\/services\\/.*)$/": "$1" # （示例）使用正则，值设置为自身等效于不做处理
          "some/unwanted/file.class": null # 删除文件
        ```

### 3. `default_project` (字符串, 可选)

-   **描述**：当未在 MCMake 命令中指定项目名称时，MCMake 将使用此值作为默认项目。可以通过 `project setdefault` 指令修改。
-   **示例**：`default_project: "my_first_mod"`

### 4. `auto_compile` (字符串列表, 可选)

-   **描述**：MCMake 将自动跟踪并编译此列表中指定的所有项目。可以通过 `project auto` 指令修改。
-   **示例**：`auto_compile: ["my_first_mod", "my_second_mod"]`

---

## config.yml 文件结构
戏说不是胡说

---

通过理解并正确配置 `env.yml` 和 `config.yml`，你将能够充分利用 MCMake 的强大功能，高效管理你的 Minecraft 模组项目和 Java 项目。