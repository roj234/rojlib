
# MCMake 命令参考

本文档列出了 MCMake 中的所有可用命令。在 MCMake 的命令行界面（CLI）中，您可以按下 `F1` 键查看当前上下文下的指令帮助。

## 命令结构

MCMake 的命令通常遵循以下结构：

```
<主命令> [子命令] [参数] <值> [...]
```

其中：
-   `<主命令>`：顶级命令，如 `build`、`project`、`workspace`。
-   `[子命令]`：可选的子命令，用于进一步细化操作，如 `project create`。
-   `[参数]`：命令或子命令的参数名称。
-   `<值>`：参数的实际值。

## 命令列表

### `build` - 构建项目

用于编译一个或多个项目。

```
build <项目名称> [选项]
```

**参数：**
-   `<项目名称>`：必填，项目名称。可以是一个项目，也可以是多个项目（以空格分隔），或留空以构建所有可构建的项目。
    -   可用的项目名称：您当前配置的所有项目。
-   `[选项]`：可选，构建选项。
    -   `full`：强制执行全量重建，忽略增量缓存。
    -   `diagnostic`：启用诊断输出，显示更详细的编译信息。
    -   `profile`：启用性能分析，构建完成后会显示详细的耗时报告。

**示例：**

-   构建名为 `mymod` 的项目：
    ```
    build mymod
    ```
-   强制全量重建 `mymod` 项目：
    ```
    build mymod full
    ```
-   构建所有可构建的项目：
    ```
    build
    ```

### `project` - 项目管理

用于创建、删除、设置默认项目或控制项目自动编译。

```
project <子命令> [参数]
```

**子命令：**

#### `project create` - 创建新项目

```
project create <项目名称> <工作空间名称>
```

**参数：**
-   `<项目名称>`：必填，新项目的名称。
-   `<工作空间名称>`：必填，指定新项目所关联的工作空间。
    -   可用的工作空间名称：您当前配置的所有工作空间。

**示例：**
-   创建一个名为 `newmod`，关联到 `mc-1.20-fabric` 工作空间的项目：
    ```
    project create newmod mc-1.20-fabric
    ```

#### `project delete` - 删除项目

```
project delete <项目名称>
```

**参数：**
-   `<项目名称>`：必填，要删除的项目的名称。
    -   可用的项目名称：您当前配置的所有项目。

**示例：**
-   删除名为 `oldmod` 的项目：
    ```
    project delete oldmod
    ```
    **注意**：此命令仅从 MCMake 配置中删除项目，并清理其缓存。您需要手动删除 `projects/` 目录下的项目文件。

#### `project setdefault` - 设置默认项目

设置 CLI 的默认项目，这样在执行 `build` 命令时，如果未指定项目，将默认构建此项目。

```
project setdefault <项目名称>
```

**参数：**
-   `<项目名称>`：必填，要设置为默认项目的名称。
    -   可用的项目名称：您当前配置的所有项目。

**示例：**
-   将 `mymod` 设置为默认项目：
    ```
    project setdefault mymod
    ```
    设置后，MCMake 命令行提示符会显示默认项目名称。

#### `project auto` - 启用/禁用项目自动编译

控制一个项目是否启用自动编译（监听文件变化并自动触发构建）。

```
project auto <项目名称> <自动编译开关>
```

**参数：**
-   `<项目名称>`：必填，要设置自动编译的项目名称。
    -   可用的项目名称：您当前配置的所有项目。
-   `<自动编译开关>`：必填，布尔值。
    -   `true`：启用自动编译。
    -   `false`：禁用自动编译。

**示例：**
-   为 `mymod` 项目启用自动编译：
    ```
    project auto mymod true
    ```
-   为 `mymod` 项目禁用自动编译：
    ```
    project auto mymod false
    ```

#### `project export` - 导出项目

将一个项目导出为一个可分享的归档文件（`.7z` 格式）。

```
project export <项目名称>
```

**参数：**
-   `<项目名称>`：必填，要导出的项目名称。

**示例：**
-   导出 `mymod` 项目：
    ```
    project export mymod
    ```
    导出的文件将命名为 `mcmake-project-mymod-YYYYMMDD.7z`，包含项目的所有代码和配置，方便分享。

#### `project import` - 导入项目

从一个归档文件导入项目。

```
project import <项目归档>
```

**参数：**
-   `<项目归档>`：必填，项目归档文件的路径（`.7z` 格式）。

**示例：**
-   导入 `myproject-archive.7z`：
    ```
    project import myproject-archive.7z
    ```
    如果导入的项目名称已存在，MCMake 会提示冲突处理。如果项目目录非空，会询问是否清空目录。

### `workspace` - 工作空间管理

用于创建、删除、导入或导出工作空间。

```
workspace <子命令> [参数]
```

**子命令：**

#### `workspace create` - 创建新工作空间

```
workspace create <工作空间类型>
```

**参数：**
-   `<工作空间类型>`：必填，要创建的工作空间的类型。
    -   可用的工作空间类型：在 `config.yml` 中定义的类型（例如 `Minecraft`）。

**示例：**
-   创建一个新的 Minecraft 工作空间：
    ```
    workspace create Minecraft
    ```
    该命令会启动交互式向导，引导您配置新工作空间（如 Minecraft 客户端路径、映射文件等）。

#### `workspace delete` - 删除工作空间

```
workspace delete <工作空间名称>
```

**参数：**
-   `<工作空间名称>`：必填，要删除的工作空间的名称。
    -   可用的工作空间名称：您当前配置的所有工作空间。

**示例：**
-   删除名为 `old-mc-workspace` 的工作空间：
    ```
    workspace delete old-mc-workspace
    ```
    此命令会提示您确认，并删除相关的文件和缓存。

#### `workspace import` - 导入工作空间

从一个归档文件导入工作空间。

```
workspace import <工作空间归档>
```

**参数：**
-   `<工作空间归档>`：必填，工作空间归档文件的路径（例如 `.7z` 格式）。

**示例：**
-   导入 `my-workspace-archive.7z`：
    ```
    workspace import my-workspace-archive.7z
    ```
    此命令会从归档中提取工作空间配置和相关的库文件。

#### `workspace export` - 导出工作空间

将一个工作空间导出为可分享的归档文件。

```
workspace export <工作空间名称>
```

**参数：**
-   `<工作空间名称>`：必填，要导出的工作空间名称。

**示例：**
-   导出 `mc-1.20-fabric` 工作空间：
    ```
    workspace export mc-1.20-fabric
    ```
    导出的文件将命名为 `workspace-mc-1.20-fabric.7z`，包含工作空间的所有配置和依赖库。

### `reload` - 重新加载配置

重新加载 MCMake 的配置（`conf/env.yml`）。

```
reload
```

**示例：**
-   在 `env.yml` 文件修改后，使更改生效：
    ```
    reload
    ```

### `statistic` - 查看统计信息

显示 MCMake 的构建统计数据。

```
statistic
```

**示例：**
-   查看 MCMake 的使用统计：
    ```
    statistic
    ```
    该命令会显示自 MCMake 启动以来的构建次数、成功率以及预估节省的时间。

### `runscript` - 执行脚本

运行 Lava 编程语言编写的脚本文件。

```
runscript <脚本名称>
```

**参数：**
-   `<脚本名称>`：必填，位于 `conf/scripts/` 目录下的脚本文件名称。

**示例：**
-   运行名为 `test.lava` 的脚本：
    ```
    runscript test.lava
    ```
    **注意**：Lava 是一种轻量级编程语言，MCMake 的插件系统也基于此。更多信息请查阅 RojLib 主页。

---
**提示**：在 MCMake 的 CLI 中，随着您输入命令，系统会自动进行补全或提示下一个可能的参数。您可以按 `Tab` 键来快速补全命令。
