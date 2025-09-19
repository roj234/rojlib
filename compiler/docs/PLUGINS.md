
# Lava™ 编译器内部结构与插件API

Lava™ 编译器提供了一套强大的API，允许开发者深入编译器内部，进行字节码操作、语法扩展、以及自定义编译行为。本章节将重点介绍其核心扩展点和官方提供的常用插件。

## 核心字节码操作接口

Lava™ 编译器暴露了以下抽象类作为字节码的非序列化属性，允许开发者在特定编译器事件（如方法调用、字段访问、注解处理）处插入自定义逻辑。

### 1. `InvokeHook`

`InvokeHook` 是一种非序列化类字节码属性，可应用于方法节点 (`MethodNode`)。当带有此属性的方法被调用时（必须是精确匹配的调用，例如编译期可确定的子类重载将不会触发），它将生成实例自定义的字节码序列。

### 2. `FieldAccessHook`

`FieldAccessHook` 是一种非序列化类字节码属性，可应用于字段节点 (`FieldNode`)。当该字段被读取或写入时，它将生成实例自定义的字节码序列。

### 3. `AStatementParser`

`AStatementParser` 是一种非序列化类字节码属性，可应用于注解类型的`ClassNode`。当编译器解析器遇到带有此属性的注解时，会将控制权转交给 `AStatementParser` 实例，允许其执行自定义编译逻辑。

**特性说明：**
*   **高度自定义：** 下方代码仅为理解方便，花括号是宏处理器，而不是该属性本身要求的
*   **共存性：** 允许单个 `AStatementParser` 注解与多个普通注解并存，且其顺序无关紧要。
    ```java
    public void someMethod() {
        @LavaMacro
        @DisableOptimization {
             code.append("1+1;");
        }
    }
    ```
*   **冲突检测：** 如果在同一位置出现两个或以上 `AStatementParser`，编译器无法决定哪个实例应处理，将导致编译错误。
*   **作用域：** 此注解仅在方法体（即允许语句 `Statement` 的环境）中生效；在其他位置，它将被视为一个普通注解处理。

**使用建议：**
Lava™ 编译器内部也广泛使用这些属性（例如，为内部类生成 `private` 字段的访问器方法）。为避免潜在冲突，建议避免直接在源代码的 `CompileUnit` 实例上放置这些属性。更推荐在自行生成的 `ClassNode` 或库函数中应用它们。

---

## `PseudoType` (伪类型)

`PseudoType` 是Lava™编译器中的一个特殊类型（**已弃用**，未来计划由基本类型泛型API或值类型定义取代）。它在类型声明时看起来是一个对象类型，但在特定情况下，其行为更接近于基本数据类型。

**相关API：**
*   `Type#DirtyHacker` 类：用于创建多数情况下表现为对象类型，但特殊情况下可表现为基本类型的类型实例。
*   `ValueBased` 接口：已弃用。
*   `'paren'` 操作符重载：与 `PseudoType` 的行为相关。

**行为说明与限制：**
*   **行为不确定性：** 将 `PseudoType` 实例传递到方法外部（包括Lambda表达式和内部类）的后果是**未定义**的。目前仅在方法内部的执行经过了充分测试。
*   **编译期限制：** 如果 `PseudoType` 被用作方法参数或字段类型，编译器将报告错误，除非该类型能够被擦除为某种基本类型。

**示例：无符号类型 (`UintPlugin`)**  
`PseudoType` 的一个经典应用是在 `UintPlugin` 中引入 `uint32` 和 `uint64` 两种无符号整数类型。它们在Lava™语言中可以像基本类型一样使用，但当离开函数边界时，会被擦除为 `long`/`int` 类型。

```java
uint32 unsignedType = -114;
System.out.println(unsignedType > 114); // 按照无符号逻辑进行比较
System.out.println(""+unsignedType); // 示例：转换为字符串
```

---

## `StreamChain` API (插件实例)

`StreamChain` API 本身也是一个插件，它能够在编译期展开链式调用，从而实现**零成本抽象**。这意味着您在使用类似Stream API的链式语法时，编译器会将其转换为直接的if/else判断或循环结构，避免在运行时产生大量的中间对象开销。

**核心原理：**
通过在编译期对链式方法调用进行分析和重写，将高级抽象转换为低级、高效的字节码。

**示例：`ComparisonChain` 插件**
`ComparisonChain` 是一个利用 `StreamChain` API 实现的插件，旨在优化一系列比较操作。

```java
// 以 ComparisonChain 插件为例，其内部通过 StreamChain API 定义了各种比较操作。
// 这里的代码展示了如何构造一个 StreamChain，并定义其起始、中间和终止操作。
// 这些操作将指导编译器在遇到 ComparisonChain 的链式调用时如何进行字节码转换。

// ... (省略具体的 StreamChain 构造代码，其核心在于定义了 compare 和 result 等方法将被如何展开) ...

// 以下 Lava™ 代码将不再产生方法调用，而是被编译为一系列的 if/else 语句：
var awa = ComparisonChain.start().compare(2.0, 1);
time += awa.compare(1, 2.0).result();
```
上述代码在编译后，`ComparisonChain.start().compare(...).result()` 这样的链式调用将不会生成实际的方法调用指令，而是被编译器展开成直接的条件判断 (`if` 语句)，从而实现了性能上的“零成本抽象”。

---

## 全局上下文API

`roj.compiler.api.Compiler` 接口提供了访问编译器全局上下文的方法，包括类型解析、错误报告等核心功能，以及以下高级扩展机制：

### 1. 注册自定义运算符重载

Lava™ 允许开发者为现有运算符注册自定义实现，甚至添加新的运算符。

*   **一元运算符 (`onUnary`)：** 为已存在的一元运算符（如 `!`）指定方法。
    例如，将 `!""` 编译为 `"".isEmpty()`。
    **参数：** `operator` (运算符字符串), `type` (适配类型), `node` (调用的方法，可以是静态的), `side` (侧边，用于区分前缀/后缀操作符)。
*   **二元运算符 (`onBinary`)：** 为已存在的二元运算符（如 `*`, `**`）指定方法。
    例如，将 `"awsl" * 10` 编译为 `"awsl".repeat(10)`，或将 `5 ** 3` 编译为 `Math.pow(5, 3)`。
*   **添加操作符处理器 (`addOpHandler`)：** 注册自定义的操作符处理逻辑，提供更灵活的语法树转换能力。

### 2. 注册自定义Token (已弃用)

此功能已弃用，请使用新的注解语句API。

### 3. 注册自定义表达式解析器

Lava™ 编译器允许插件扩展其表达式解析流程，以支持全新的语法结构。

**表达式解析流程概述：**
典型的表达式解析遵循以下阶段：
1.  **不可重复前缀一元运算符：** 例如 `++`, `--`。
2.  **可重复前缀一元运算符：** 例如 `+`, `-`, `!`, `~`, 类型转换。
3.  **值生成运算符：** 例如 `new`, `this`, `super`, 字面量常量, 类型字面量 (`.class`, `.this`, `.super`), Lava特定的值构造 (`EasyMap`, `NamedParamList`), 嵌套数组创建, `switch` 表达式模式。
4.  **值转换运算符：** 例如方法调用、数组访问、字段读取。
5.  **值终止运算符：** 例如后缀 `++`, `--`, 赋值 (`=`), 方法引用 (`::`)。

或者：
*   **表达式生成运算符：** 例如 Lambda 表达式。

随后解析二元运算符。

**提供的解析器注册方法：**
*   `newUnaryOp(String token, ContinueOp<PrefixOperator> parser)`：新增前缀一元运算符。
*   `newExprOp(String token, StartOp parser)`：新增一个完整的表达式生成运算符，该运算符自身构成一个完整表达式。
*   `newStartOp(String token, StartOp parser)`：新增值生成运算符（例如 `<minecraft:stone>` 这样的特殊字面量）。
*   `newContinueOp(String token, ContinueOp<Expr> parser)`：新增值转换运算符。
*   `newTerminalOp(String token, ContinueOp<Expr> parser)`：新增值终止运算符。
*   `newBinaryOp(String token, int priority, BinaryOp parser, boolean rightAssoc)`：新增二元运算符，可指定优先级和结合方式。

---

## 其它官方插件

### 1. `Annotations` 插件

提供一系列实用注解，增强代码功能和可读性。

#### `@Attach`

将静态函数逻辑附加到其他类上，使其看起来像该类的成员方法。

```java
import roj.compiler.plugins.annotations.Attach;

@Attach
public class Attacher {
    public static boolean isNotEmpty(String s) {
        return !s.isEmpty();
    }
}
```
通过上述定义，无论 `Attacher` 类是以源代码形式编译还是通过 `classpath` 加载，您都可以在 `String` 对象（或其子类，尽管 `String` 是 `final` 的）上直接调用 `isNotEmpty()` 函数。  
**重要提示：** 此功能默认是**全局性**的，不受包范围限制，因此可能导致名称冲突，限制作用域为包内尚未实现。

#### `@AutoIncrement`

为一组静态 `final` 字段自动分配整数常量值，从指定的初始值开始，并按指定步长递增。

```java
@AutoIncrement(value = 100, step = 1)
public static final int A, B, C, D;
```
等效于：`public static final int A = 100, B = 101, C = 102, D = 103;`

#### `@Getter`/`@Setter`

自动生成字段的 `getXXX()` 和 `setXXX()` 方法。

#### `@Property`

允许通过类似字段访问的语法来调用 `getter` 和 `setter` 方法。此注解支持**堆叠**使用。

```java
@Property(value = "some", getter = "getSome", setter = "setSome")
public class Some {
    public String getSome() { return "我是一个字段"; }
    public void setSome(String setter) {}

    public void example() {
        System.out.println(this.some); // 编译为 this.getSome()
        this.some = "awsl";           // 编译为 this.setSome("awsl")
        System.out.println(this.some); // 编译为 this.getSome()
    }
}
```
*   `getter` 和 `setter` 方法可以通过 `value` 属性自动生成，但布尔类型字段不会生成 `isXXX()`，而是依然生成 `getXXX()`。
*   您也可以手动指定 `getter` 和 `setter` 的名称。
*   允许直接通过属性 (`this.some`) 语法调用对应的 `getter` 和 `setter` 方法。

#### `@Operator` (WIP)

自定义操作符的注解接口。
*   目前，此功能主要依赖 `Lavac` 提供的API进行操作符自定义，具体实现正在进行中。

### 2. `Asm` 插件

提供低级别字节码操作和泛型处理能力。

#### 参数注入 (`ASM.inject`)

通过 `<T> T ASM.inject(String name, T defaultValue)` 方法，获取通过编译器参数或插件注入的属性。尽管方法签名是泛型，但该属性的实际值可以是任意表达式，且其类型可能与默认值类型不同。

#### 内联 ASM (`ASM.asm`)

通过 `boolean ASM.asm(Consumer<CodeWriter> macro)` 方法在编译期间直接修改生成的字节码。如果ASM插件已加载并启用，此方法将返回 `false` 常量，从而可用于实现条件编译。需要注意的是，传递给 `Consumer` 的 Lambda 表达式无法访问方法内部的局部变量，且在受限环境中执行。

#### 泛型转换 (`ASM.cast`)

通过 `<T> T ASM.cast(Object source)` 方法实现泛型类型的无条件转换，例如将 `List<String>` 强制转换为 `List<Integer>`，这是一种非检查转换。

### 3. `Evaluator` 插件

提供编译期代码执行、优化和宏处理能力。

#### 编译期执行 (`@Constexpr`)

使用 `@Constexpr` 注解标记纯函数，使其能够在编译期执行。
*   **执行机制：** 纯函数目前不会在编译时执行两次，而是“只读取 `classpath` 中已编译好的纯函数”。
*   **类型限制：** 纯函数的入参和返回值仅限于字符串、基本类型及其数组。
*   **白名单机制：** 纯函数只能使用白名单中有限的类。
*   **注意：** 上述限制可能会在未经通知的情况下进行修改（WIP）。

#### 数组压缩

将常量基本类型数组压缩为 `Base128` 编码的字符串数据，以减小最终 `.class` 文件的体积。
*   目前支持 `int[]` 和 `byte[]` 类型。
*   **使用方式：** `RtUtil.unpackI(RtUtil.pack(new byte[] { ... }));`

#### 宏 (`@LavaMacro`)

通过 `@LavaMacro { ... }` 编写一个宏。宏类似于一个 Lambda 代码块，其中有且仅有一个参数 `CharList code`。
*   **重要提示：** 在当前版本中，`@LavaMacro` **并非**一个真实的注解，而是编译器内置的特殊语法处理。
*   **示例：** `@LavaMacro { code.append("1+1"); }` 将会直接编译为 `1+1`。
*   **能力：** 宏生成器作为编译过程的一部分，可以使用Lava™语言的所有特性，但同样只允许使用白名单内的部分类。
*   **安全性考量：** 与 `@Constexpr` 不同，`@LavaMacro` 对可用类的限制更为精确（类似于 `package-restricted`）。而 `@Constexpr` 可能存在沙箱逃逸的风险（例如通过 `getClass().getClassLoader()`），因此建议仅对信任的代码启用 `@Constexpr` 功能。

### 4. `Uint` 插件

引入了无符号整数数据类型。

#### 无符号数据类型 (`uint32`, `uint64`)

该插件为 Lava™ 语言引入了 `uint32` 和 `uint64` 两种无符号整数类型。
```java
uint32 number = -7;
System.out.println("(uint32) -7 = " + number.toString()); // 打印无符号表示
System.out.println("(uint32) -7 + 12 = " + (number + 12).toString()); // 执行无符号运算
System.out.println("number > 114514 = " + (number > 114514)); // 执行无符号比较
```
这些类型在代码中可以像基本类型一样使用，在执行算术和比较操作时会遵循无符号语义。但在离开函数边界时（例如作为方法返回值或存储到非 `Uint` 类型的变量中），它们会被擦除为对应的 `long`/`int` 类型。

### 5. `MoreOp` 插件

扩展了 Java 语言的运算符功能。

#### 扩展的数组取值操作符

传统的数组取值语法 `a[b]` 现在可以应用于 `Map` 或 `List` 类型的对象。
*   对于 `Map`，它将被编译为 `get()` / `put()` (取决于上下文) 方法调用。
*   对于 `List`，它将被编译为 `get()` / `set()` 方法调用。
*   集合类型还支持使用 `+=` 操作符进行 `add()` 或 `addAll()` 操作。

此插件提供了一个很好的示例，展示了如何利用编译器提供的表达式API来扩展语言语法。

### 6. `TypeDecl` 插件

改进了编译期泛型推断机制。

#### 编译期泛型推断 (`__Type(type)`) 表达式

引入了支持编译期泛型推断的 `__Type(type)` 表达式。
*   与传统的 `new TypeToken<XXXX>() {}` 方式不同，`__Type(type)` 表达式无需创建匿名类或其实例，即可在编译阶段获取到精确的类型信息。
*   **语法示例：** `__Type(Map<String, int[]>)` 或更复杂的类型表示。

---
