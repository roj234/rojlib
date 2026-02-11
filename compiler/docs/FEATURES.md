# Lava™ 编译器功能概览

## 引言

Lava™ 编译器目前处于持续开发（WIP）阶段，旨在提供卓越的扩展性和创新功能。它并非现有编译器的直接替代品，其API专为语法层面的扩展而设计，相较于标准编译器API具有更高的可扩展性。

**重要提示：**
*   **兼容性限制：** Lava™ 编译器完全不支持标准库的编译器API，且部分语言特性可能未受支持或行为有所差异。
*   **生态适配：** 插件生态系统目前较为有限。
*   **迁移成本：** 针对Lava不计划支持的特定语法特性的代码迁移可能需要一定的适配工作。

---

## 核心功能与增强

### 1. 命名参数 (Named Arguments)

允许在方法调用时，通过参数名显式指定值，提升代码可读性。

```java
List.of({e1: null, e2: 3});
List.of(null, null, {e3: null, e4: null});
```

**注意事项：**
*   **位置限制：** 命名参数目前仅支持作为方法的最后一个参数。
*   **推断复杂性：** 在存在方法重载的情况下，使用命名参数可能会导致类型推断出现不直观的错误。

### 2. 同步块增强 (`synchronized` 扩展)

此特性修改了 `synchronized` 关键字的默认行为，以更直接地调用锁机制。

**⚠️ 行为变更警告：**
通过设置 `Compiler.SYNCHRONIZED_LOCK` 可启用此功能。启用后，`synchronized` 块将自动调用 `Lock` 接口的 `lock()` 和 `unlock()` 方法，而非传统的 `monitorEnter`/`monitorExit` 指令。

```java
synchronized(lockInstance) {
    // 编译器将自动插入 lockInstance.lock() 和 lockInstance.unlock()
    // 从而替代传统的 monitorEnter/Exit 指令。
    // 您不再需要手动编写 try-finally 块进行加锁和解锁操作。
}
```

### 3. `for-else` 循环 (部分实现)

借鉴 Python 的 `for-else` 语法，当循环未通过 `break` 语句终止时，将执行 `else` 块。

目前该特性仅支持 `for` 循环，`while` 和 `do-while` 循环尚未支持。

```java
for (int i=0; i<arr.length; i++) {
    if (i == 8) break;
} else {
    System.out.println("循环未通过 break 终止");
}
```

### 4. 多返回值函数 (Multiple Return Values)

允许函数返回多个值，以元组（tuple）形式进行封装与解构。

```java
static [int, long, String] multiReturnTest() {
    return {3, 5, "233"};
}

var {a, _, c} = multiReturnTest(); // 使用方括号进行解构，可通过 '_' 跳过不需要的返回值。
```

**特性细节与限制：**
*   **基本类型限制：** 返回的基本类型数据总字节数不能超过 1KB（例如，256 个 `int` 或 128 个 `long`）。
*   **对象类型：** 对象类型返回值无此限制。
*   **实现机制：** 采用 `ThreadLocal` 对象实现，而非创建额外的不可变对象，以优化内存使用。
*   **禁止嵌套（编译期错误）：** 不支持多返回值函数的嵌套调用。
*   **解构要求：** 必须通过特定的解构语法来获取返回值。编译器会自动插入参数类型哈希以进行验证，并在不匹配时抛出 `IncompatibleClassChangeError`。
*   **语法选择：** 采用方括号 `[]` 和花括号 `{}` 形式，是为了避免修改 `var[]` 和 `{}`（静态构造器）等现有语法的默认行为。

### 5. 编译期条件分支优化 (Compile-Time Branch Pruning)

如果 `if`、`while`、`switch` 等条件语句的判断条件是编译期常量，编译器将在编译阶段执行条件判断，并移除不可能执行的分支代码。

此优化同样适用于 `for`、`do-while` 等其他循环语句。它显著有助于减少生成代码体积，并可有效支持多版本依赖项目的管理。

```java
static final boolean SOME_CONSTANT = false;

if (SOME_CONSTANT) {
  /* 此分支代码不会被编译进最终的字节码，也不会分析它的语法错误 */
  fsdjghsdfnnv95&%^&%^&fnnvbnvbada;ear
} else {  
  System.out.println("1"); // 仅此分支会被保留
}
```

### 6. `for-each` 循环优化

针对满足特定条件的集合，Lava™ 编译器会优化 `for-each` 循环的实现方式。

**优化条件(满足任一)：**
*   类同时实现 `List` 和 `RandomAccess` 接口。
*   类带有 `@RandomAccessible` 注解。

**优化行为：**
在上述情况下，`for-each` 循环将通过顺序索引访问元素，而非传统的 `Iterator` 迭代器方式，以提升性能。

**禁用优化：**
若需在多线程环境中确保迭代器的正确性（例如，防止 `ArrayIndexOutOfBoundsException`），可使用 `@DisableOptimization` 注解来禁用此特性。

### 7. 函数参数默认值 (Default Parameter Values)

允许在方法定义时为参数指定一个默认值。

```java
static void test(int a = 3, Object o = some_variable) {
    // ...
}

{
    int some_variable = 5; // 此变量在函数调用上下文中可用
    test(5); // 相当于调用 test(5, some_variable 的值)
    test(); // 相当于 test(3, some_variable 的值)
}
```

**特性说明：**
*   **表达式作为默认值：** 默认值可以是任意有效的表达式。
*   **宏替换机制：** 默认值实际上被视为一种宏，在函数调用点展开。这意味着默认值表达式中可以使用仅在函数调用上下文可用的变量。

### 8. 单一抽象方法类 Lambda 表达式 (SAM Class Lambda)

即使是只有一个抽象方法的抽象类（非接口），现在也可以像函数式接口一样使用 Lambda 表达式进行实例化。

```java
AbstractType obj = () -> System.out.println("Magic!"); // 编译器会将其编译为匿名字段的实例。
```

> **待办事项 (TODO):** 对于没有抽象方法的可继承类，如何指定一个方法以允许 Lambda 实例化？（此功能正持续完善中）

### 9. 覆盖隐式生成方法

编译器隐式生成的方法，例如枚举类型的静态 `valueOf()` 和 `values()` 方法，现在可以由用户手动覆盖。

**行为说明：**
*   一旦手动覆盖，编译器将不再生成相应的方法。
*   **编译期错误：** 覆盖方法的访问权限修饰符必须与其应有的权限修饰符保持一致，否则将导致编译错误。

### 10. 集合字面量 (Map/List Literal)

提供类似于 PHP 的语法来快速构建 `Map` 和 `List` 实例。

```java
var map = [ 1+1 -> 3+4 , "mixed type" -> also.can.be.used() ]; // 结果为 Map<Object, 表达式的公共父类型>。
var list = [1, 2, 3]; // 结果为 List<Integer>。
```

### 11. `goto` 语句

允许使用 `goto` 语句在任意带有标签（label）的代码位置之间进行跳转。

```java
// 示例（功能示意，非标准用法）
public void example() {
    outer:
    for (int i = 0; i < 10; i++) {
        inner:
        for (int j = 0; j < 10; j++) {
            if (i * j > 50) {
                goto end; // 跳转到 'end' 标签
            }
        }
    }
    end: // 标签
    System.out.println("Finished.");
}
```

**⚠️ 警告：**
由于内部 `VisMap` 的未经过这种情况测试，使用 `goto` 语句可能导致变量初始化状态有误。

### 12. 基本类型扩展方法 (Primitive Type Extension Methods)

允许基本类型值调用附加的函数，这些函数会被编译为对应的静态方法调用。

```java
12345 . toHexString(); // 编译为 Integer.toHexString(12345)
```

**解析提示：**
为避免解析歧义（例如被误解析为浮点数），请在小数点前添加空格或使用括号明确界定。

```java
(123) .toHexString(); // 推荐写法
123 . toHexString();  // 亦可
```

### 13. `switch` 表达式增强

Lava™ 编译器生成的 `switch` 语句具有更小的字节码体积和更优异的运行时性能。

**增强特性：**
*   **类型无关性：** 任何类实例都可以作为 `switch` 表达式的判断对象，行为类似于枚举类型。
*   **O(1) 复杂度：** `switch` 操作的平均时间复杂度为 O(1)。
*   **注解控制：** 使用 `@Switchable` 注解可以改变 `switch` 的默认行为。
*   **禁用优化：** 使用 `@DisableOptimization` 注解可以禁用此功能。
*   **数据类型支持：** 支持long或运行时常量作为 `case` 分支。

```java
class Test { 
    static final Test A = new Test(), B = new Test(), NulL = null;
}

public class MySwitchDemo {
    public static void main(String[] args) {
        var v = Test.A;
        switch (v) {
            case Test.A -> System.out.println("A");
            case Test.B -> System.out.println("B");
            case Test.NulL -> System.out.println("null (real)"); // 支持匹配运行时的null
        }
    }
}
```

#### `switch with` (WIP)

此功能计划结合模式匹配 (`pattern match`) 进一步增强 `switch` 表达式，但目前尚未实现。

### 14. 运算符重载 (Operator Overloading)

允许重载现有的运算符，甚至添加自定义运算符，使其能够被替换为任意表达式，并支持右结合性。在运算符重载中，重载的优先级最低，无法覆盖 `!true` 等内置运算。

**可重载运算符：**
*   **二元运算符:** `+`, `-`, `*`, `/`, `%`, `**`, `<<`, `>>`, `>>>`, `&`, `|`, `^`, `&&`, `||`, `??`, `==`, `!=`, `<`, `>=`, `>`, `<=`
*   **前缀/后缀运算符:** `++`, `--`, `~`, `!`
*   **复合赋值运算符:** `+=`, `-=`, `*=`, `/=`, `%=`, `**=`, `<<=`, `>>=`, `>>>=`, `&=`, `^=`, `|=`

**不可重载运算符：**
*   三目运算符 (`? :`)
*   Lambda 表达式 (`->`)
*   直接赋值运算符 (`=`)
*   成员访问/安全访问运算符 (`.` , `?.`)
*   扩展运算符 (`...`)
*   方法引用 (`::`)

### 15. 可选链操作符 (`?.`)

参考 JavaScript 的可选链语法，当链中任何一级对象为 `null` 时，表达式结果直接返回 `null`，避免 `NullPointerException`。

```java
var nullable = a?.b?.c?.d; // 如果 a、b、c 中任一为 null，则 nullable 为 null
var result = obj?.method()?.field; // 支持方法调用和字段访问
```

此功能目前仅支持对象类型，如果最终返回值为基本类型会产生编译错误  
此功能未来也许会允许返回0，或通过特殊语法指定默认值

### 16. 基本类型泛型 (Primitive Generics) (WIP)

通过模板生成机制，支持对基本类型进行泛型化处理。

**主要优势：** 在运行时能够节约内存开销，避免因自动装箱/拆箱而产生的额外对象。

### 17. 连续泛型类型推断优化

Lava™ 编译器改进了连续泛型方法的类型推断能力，解决了 Javac 在某些场景下推断失败的问题，从而减少手动指定泛型参数的需要。

**示例：**
```java
public class Test {
    static <T> T inferType(T example) { return example; }
    
    static <K, V> TypedMapBuilder<K, V> builder(Class<K> keyType) {return null;}
    interface TypedMapBuilder<K, V> { Map<K, V> build(); }

    public static void main(String[] args) {
        // 在 Javac 中，此处会推断失败，导致 map1_1 的类型为 Map<String, Object>
        Map<String, Integer> map1_1 = Test.builder(String.class).build();
        // 尝试通过辅助方法进行推断，Javac 也无法成功
        Map<String, Integer> map1_2 = inferType(Test.builder(String.class)).build();

        // 传统 Javac 需显式指定泛型参数才能成功推断
        Map<String, Integer> map2 = Test.<String, Integer>builder(String.class).build();
        // Lava™ 编译器无需显式指定即可正确推断 map1_1 和 map1_2 的类型
    }
}
```

### 18. `finally` 块优化

在存在多个嵌套的 `finally` 块时，Lava™ 编译器会进行优化，以防止生成过大的字节码文件。

### 19. 隐式导入 (Implicit Import) / `ImportAny` (部分实现)

提供简化的导入机制，减少冗余的 `import` 语句。

**特性说明：**
*   **全量导入：** 使用 `import *` 语句即可开启。如果当前classpath中没有同名的类，则该类被视为已自动导入。这将极大减轻手动管理 `import` 语句的负担。
*   **模块导入：** 您也可以使用 `import module java.base;` 等语句，仅导入一个模块中所有导出的包。
*   **无需模块激活：** 使用此功能时，无需激活模块编译。

### 20. 包级/成员级访问限制 (原 `package-restricted` 已弃用，改用函数式 API)

此特性旨在提供一种更细粒度的沙盒式安全机制，限制代码对外部类及成员的访问。

**旧版 `package-restricted` 弃用说明：**

原有在任何 `import` 语句之前插入 `package-restricted` 关键字的方式现已弃用。该关键字仅提供类级别的访问限制，且配置灵活性较差。当编译器检测到 `package-restricted` 关键字时，将报告一个语法错误。现在推荐使用基于 `FlagSet` 的函数式 API，它提供了更强大、更细粒度的控制能力。

**新版函数式 API 说明：**

新的访问限制机制通过 `FlagSet` 对象进行管理。你可以通过 `ImportList` 类的实例获取 (`getRestriction()`) 或设置 (`setRestriction(FlagSet restriction)`) 当前的访问限制规则。

*   **启用方式：** 通过调用 `ImportList.setRestriction(FlagSet restriction)` 方法，传入一个已配置好的 `FlagSet` 对象来激活或更新访问限制。如果 `restriction` 为 `null`，则表示不启用任何额外的访问限制。
*   **配置 `FlagSet`：** `FlagSet` 对象包含了一系列允许或禁止访问的类、方法和字段规则。这些规则可以：
    *   **从配置文件加载：** 通过 `ImportList.parseAllowList(LineReader lr, FlagSet set)` 方法，从指定的文本流中加载规则。例如，系统提供了一个默认的全局允许列表 `ImportList.DEFAULT_ALLOWLIST`，它会在启动时从 `Allowlist.cfg` 文件加载。
    *   **编程方式添加：** 你也可以直接通过 `FlagSet` 实例的 API 动态添加或修改规则。
*   **访问控制：** 启用限制后，代码将只能访问 `FlagSet` 中明确允许的类及成员。任何未明确允许的访问都将导致编译错误。
*   **深度检查：** 即使是方法的返回值类型、参数类型或字段类型，也会进行导入检查。未允许的类型将引发编译错误，例如：
    ```
    MacroSandboxTest.java/Macro#180:10: 错误: 类 java/lang/System 的方法 exit 不在导入允许列表中
                            System.exit(0);
                                         ^
        at roj.compiler.CompileContext.checkRestriction(CompileContext.java:449)
    ```

**Allowlist 配置文件格式**

`FlagSet` 的规则可以通过文本优雅的配置。每一行代表一条规则：

*   **注释：** 以 `#` 开头的行将被视为注释并忽略。
*   **允许规则：** 直接写出类、方法或字段的完整路径，表示允许访问。
    *   `java/lang/Class`：允许访问 `java.lang.Class` 类。
    *   `java/lang/Class/getName`：允许访问 `java.lang.Class.getName()` 方法或字段。
*   **禁止规则：** 以 `-` 开头表示禁止访问。
    *   `-java/lang/Class/*`：禁止访问 `java.lang.Class` 的所有公共成员（方法和字段）。
    *   `-java/lang/Object/notify`：禁止访问 `java.lang.Object.notify()` 方法。
*   **通配符规则：**
    *   `java/lang/annotation/*`：允许访问 `java.lang.annotation` 包下的所有类（不包括子包）。
    *   `java/time/**`：允许访问 `java.time` 包及其所有子包下的所有类。
*   **成员规则：**
    *   当一个类被允许，它所有的成员默认都被允许，可以通过禁止规则来覆盖
    *   请勿提供冲突的规则，其结果是未定义的，而不一定被后继者覆盖

**配置示例及说明:**
在 `Allowlist.cfg` 中，可以先允许一个类，然后通过 `-` 规则禁止其特定成员，实现精细控制：
```cfg
# 允许 java.lang.System 类本身
java/lang/System
# 禁止 java.lang.System 类的所有成员 (例如 exit(), gc() 等敏感方法)
-java/lang/System/*
# 重新允许 System.err 字段
java/lang/System/err
# 重新允许 System.out 字段
java/lang/System/out
# 重新允许 System.in 字段
java/lang/System/in
```
这种配置方式使得 `System.exit(0)` 等敏感操作能被精确禁止，正如上述测试输出所示。

**新机制的优点：**

*   **成员级细粒度控制：** 这是最大的改进点。新机制不仅可以限制对类的访问，还可以精确到类中的特定方法、字段，例如禁止 `System.exit()` 但允许 `System.out`，这解决了旧有 `package-restricted` 只能进行类级别限制的不足。
*   **配置灵活性大幅提升：** 规则可以通过外部配置文件加载，也可以通过代码动态配置和修改。这使得它能够适应更复杂、多变的沙盒场景，并且配置的管理更加便捷。
*   **更强大的安全性：** 能够精确禁止如 `System.exit()`、`Object.notify()` 等潜在危险的成员函数，有效防范传统基于 `ClassLoader` 沙盒的潜在漏洞，进一步增强了沙盒的安全性。
*   **可复用性：** `FlagSet` 对象可以在不同的 `ImportList` 实例之间共享或复制，方便管理和应用多套不同的访问策略。
*   **清晰的配置结构：** 通过 `Allowlist.cfg` 文件，规则的定义变得清晰、易读、易维护。

### 21. 别名导入 (Aliased Import)

允许为导入的类或静态成员指定一个别名，以避免命名冲突或简化使用。

```java
import a.b as c; // 将 a.b 类导入并命名为 c
import static a.b.staticMethod as myMethod; // 为静态方法指定别名
```

### 22. `with` 语句

提供一个作用域限定的语句块，使其内部的代码可以更简洁地访问特定对象的成员。  
特别适用于record类型

```java
with (System.out) {
    println("hello world!"); // 相当于 System.out.println("hello world!");
}
```

**静态上下文：**
*   若要应用于某个类的静态成员，可以使用 `with (X.class) {}`。
*   或者，简单地使用 `import static` 语句也能达到类似效果。

### 23. 可选分号 (Optional Semicolons)

在大多数情况下，语句末尾的分号可以省略。

**缺点：**
*   目前，此特性可能导致诊断信息（如错误位置）在报告时发生一行漂移。

### 24. `defer` 语句 (已实现)

`defer` 语句允许在 `try` 代码块结束时执行一个或多个表达式，其行为类似于 `try-finally` 块，但提供了更简洁的语法和更robust的错误处理。

**特性说明：**
*   **多表达式执行：** 一个 `try-with-resources` 块中可以包含任意个 `defer` 语句。
*   **错误隔离：** 如果其中一个 `defer` 表达式执行失败，不会影响其他 `defer` 表达式的执行。所有发生的异常将被收集并统一抛出。
*   **与 `AutoCloseable` 兼容：** `defer` 可以与实现 `AutoCloseable` 接口的对象结合使用，自动管理资源的关闭。
*   **执行顺序：** `defer` 表达式的执行顺序与定义顺序**相反**（遵循 LIFO 原则）。对于 `AutoCloseable` 资源，会先创建的实例后调用 `close` 方法。

**示例：**
```java
// 以下代码执行结果：先打印 "b"（如果代码成功执行到了那行defer的位置），再打印 "a"，最后关闭文件输入流。
try (
    var in = new FileInputStream("file.txt");
    defer System.out.println("a");
) {
    // ... 核心业务逻辑 ...
    defer System.out.println("b");
}
```

注1：try-with-resources 使用了Lava运行时库(DeferList)以减少 code bloat  
注2：写在try的圆括号部分的defer和写方括号部分的defer其实不是同一个东西，并且前者性能更好（确定会执行）  
注3：后者基于 invokedynamic，不建议在*真正的*性能关键代码中使用 defer  
注4：不要在循环里执行 defer，它真的会在退出时执行很多次！

### 25. 尾递归优化 (Tail Recursion Optimization)

Lava™ 编译器能够识别并优化尾递归调用，将其转换为循环结构，从而避免栈溢出并提升性能。

**优化策略：**
*   **自动启用：** 对于不可继承的方法（即带有 `static`, `final`, `private` 任意修饰符的方法），尾递归优化将自动启用。
*   **显式启用：** 对于其他方法，可以使用 `@Tailrec` 注解来显式启用尾递归优化。
*   **禁用优化：** 可以通过设置 `@Tailrec(value=false)` 来禁用自动优化。

### 26. 生成器函数 (Generator Functions)

提供一种创建可暂停和恢复的函数的机制，通过 `yield` 关键字逐步生成序列值。

**定义方法：**
在函数名称前添加星号 (`*`) 来声明一个生成器函数。

```java
    static String *generatorTest(String inputArg) {
        yield inputArg+="a";
        yield inputArg+="b";
        yield inputArg+="c";
    }
```

**使用方法：**
生成器函数实际上返回一个 `Generator` 接口的实例。您可以在其中使用基本类型泛型参数。

```java
    Generator<String> itr = generatorTest("喵");
    while(itr.hasNext()) {
        System.out.println(itr.next());
    }
    // 预期输出：
    // 喵a
    // 喵ab
    // 喵abc
```

**实现细节：**
*   通过匿名类实现，类似于无栈协程的机制。
*   **优先级：** `yield` 关键字的优先级低于 `switch` 表达式中的 `yield`。

### 27. 编译期联合类型 (`Union<T...>`)

#### 功能概述
`roj.compiler.api.Union` 是 Lava™ 编译器提供的一种**编译期联合类型**特性。它允许在方法声明中指定一个返回值类型集合，即方法可能返回 `Union` 中的任意一个类型（例如 `String | Integer | Map<String, Integer>`）。编译器会在编译阶段强制执行类型约束，并通过模式匹配 (`switch`) 机制在运行时安全地处理具体的类型。

#### 核心代码示例
```java
// 声明方法可能返回 String、Integer 或 Map<String, Integer> 中的任何一种类型
static Union<String, Integer, Map<String, Integer>> manyTypes() {
    return "asdf"; // 实际返回 String 类型
}

public static void main(String[] args) {
    // 接收联合类型返回值 (此处使用 var，亦支持显式声明 Union 类型)
    var x = manyTypes();
    
    // 类型安全的模式匹配，用于处理联合类型值
    String n = switch (x) {
        case String s -> s;         // 处理 String 类型
        case Integer l -> l.toString(); // 处理 Integer 类型
        case Map<String, ?> m -> m.toString();   // 处理 Map 类型 (此处故意使用更不具体的泛型参数，表明 Union 支持非精确匹配)
        // case null -> "处理 null 值"; // 建议显式处理 null 分支
    };
    System.out.println(n); // 输出: "asdf"
}
```

#### ✨ 关键特性
1.  **编译期类型约束**
    *   **方法返回值声明：** 方法返回值须明确声明为 `Union<Type1, Type2, ...>` 形式。
    *   **禁止返回 `null`：** 若直接返回 `null`，将触发编译错误，提示 `<AnyType>不可能转换为* extends String&Integer&Map<String, Integer>`，因为 `null` 无法解析为联合类型中的任何一个具体成员。
    *   **禁止类型擦除后相同的泛型类型：** 若联合类型中包含类型擦除后相同的泛型类型（例如 `List<String>` 和 `List<Integer>`），将导致编译错误，因运行时无法通过 `checkcast` 有效区分。

2.  **运行时类型安全**
    *   **完备性检查：** 在使用 `switch` 表达式处理联合类型时，必须覆盖 `Union` 声明中的所有可能的类型分支，否则将导致编译错误。
    *   **`null` 导致的运行时异常：** 如果运行时联合类型的值为 `null` 且 `switch` 表达式中未包含 `case null` 分支，将抛出 `IncompatibleClassChangeError`。

3.  **模式匹配优化**
    *   **与 Record 类型类似：** 其模式匹配用法与 Java 的 Record 类型相似，支持自动类型转换等便利功能。
    *   **具化泛型支持：** 在 `pattern switch` 中，可以使用不冲突的具化泛型类型，例如 `Map<String, Integer>`，而不仅仅是 `Map<?, ?>`。

> **设计意图：** 在静态类型语言中模拟联合类型，旨在提供编译期类型安全，避免简单的使用Object并用javadoc等“局外”方式传递可能的类型，消除运行时潜在的 `ClassCastException`，从而显著提升代码的类型安全性。

### 28. `async` / `await` (Promise) (WIP)

计划引入 `async` 和 `await` 关键字，以支持基于 `Promise` 机制的异步编程模型，简化并发代码的编写和管理。

---