# Lava™编译器  (WIP)
![lavac](images%2Flavac.png)* 纯属玩梗，并未注册任何商标

## 用前须知
Lava编译器并非旨在取代Javac，其API主要为修改Java语法设计（个人认为比Javac的API更优）。
与Javac不同，Lava编译器不支持所有Javac的API和部分Java语言特性。
因此，如果您习惯使用编译插件来辅助编码，Lava编译器可能无法支持。
对于那些习惯使用特定语言特性的人来说，迁移代码到Lava编译器可能并不十分顺畅。

## 绝赞语法
### 参数调用 (已实现)
  使用 `method({参数名:表达式,(可叠加)})` 来按参数调用方法
```java
List.of({e1: null, e2: 3});
List.of(null, null, {e3: null, e4: null})
```
该表达式只能位于方法的最后一个参数
### for-else (已实现)
这个语法来自python, 目前只有for支持, while和do-while不支持  
for之后可以接else
```java
for (int i = 0; i < arr.length; i++) {
  // do something
  if (i == 8) break;
} else {
  System.out.println("循环未开始，或未通过break终止");
}
```
### 多返回值 (已实现)
一个方法可以返回多个返回值（不超过256个）
```java
static [int, long, String] multiReturnTest() {
	return [3, 5, "233"];
}

var [a, b, c] = multiReturnTest();
var a = multiReturnTest()[0];
```
### 分支完全忽略 (已实现)
如果一个if、while、do-while或switch语句处理的是立即或非立即常量，那么比较会在编译期完成  
下列代码能够通过编译  
它的目的是作为编译前预处理器（宏）的简易替代  
你可以使用static final变量，或通过编译参数定义一些编译期常量  
如此这般，就可以方便用到多版本内部代码的应用
```java
if (false) {
 fsdjghsdfnnv95&%^&%^&fnnvbnvbada;ear
} else {  
 System.out.println("1");
}
```
### List foreach (已实现)
针对同时实现List和RandomAccess接口的类，其foreach循环将按顺序访问而非使用Iterator进行编译。  
若遇到这种类，并且需要在多线程环境中进行访问（必须使用Iterator），请考虑使用【...for】关键字以禁用该优化。
### 参数默认值 (已实现)
  在方法定义处使用形如 `method(int a = 3)` 的语句来为参数指定默认值  
  默认值可以是*任意*表达式，实际上它相当于一个宏，你可以使用仅在函数调用上下文可用的变量  
  如下代码会*成功编译*
```java
static void test(int a = 3, Object o = some_variable) {
	// ...
}

{
    int some_variable = 5;
    test(5);
}
```
### lambdaify
  只有一个抽象方法的抽象类（不是接口），也可以使用lambda(不过会编译成new匿名类)  
  没有抽象方法的可继承类，可以使用*什么*指定一个方法？
### EasyMap (已实现)
  使用类似PHP的语法来构建Map&lt;?,?&gt;
```java
var map = [ 1+1 -> 3+4 , "mixed type" -> also.can.be.used() ];
```
### goto语句 (部分实现)
  使用goto语句在任意标签之间跳转  
  在跳转过程中增加的变量必须立即赋值
### 基本类型函数 (已实现)
  12345 . toHexString() => Integer.toHexString (static)
### Switchable (已实现)
- Lavac生成的switch语句具有更小的文件大小和更优越的性能。
- 通过在任何类上加上@Switchable注解，该类可以像枚举一样进行switch操作。
- 时间复杂度为O(1)。
- 使用【...switch】关键字可以强制启用此功能，即使没有Switchable注解。
- 支持基本数据类型和非编译时常量。
```java
		var v = Test.A;
		switch (v) {
			case Test.A -> System.out.println("A");
			case Test.B -> System.out.println("B");
			case Test.NulL -> System.out.println("null (real)");
		}
```
#### SwitchEx (WIP)
  看到隔壁CSharp那么多switch的语法糖，我给switch加了一个goto default  
  你可以用goto default跳到default分支的开始，不能在default分支中使用
#### SwitchArray / SwitchTuple (WIP)
  数组类型和record类型
  switch (someRecord) {
    case (_, > 3): ...
    case (< 3, _): ...
    case [..., > 114514]: ...
    case [1, 2, 3]: ...
    case [1, _, 3]: ...
  }
### 操作符重载 (已实现)
  允许重载已有的二元或一元运算符，还能添加自定义的运算符，它们会在解析时被换成方法调用
#### 默认的重载
* ! String => String#isEmpty
* String * int => String#repeat(int)
### 可选连接操作符 (已实现)
```javascript
 var nullable = a?.b?.c?.d;
```
任意为null => 结果为null  
支持方法调用 a?.b()?.c;
### 基本类型泛型
  通过模板生成基本类型的泛型类，在运行时节约内存  
### 递归泛型推断 (已实现)
  我没找到什么好例子，反正正常一级也够用，javac很少报错
### finally优化 (已实现)
  在多个嵌套的finally块中，防止代码体积暴增
### 预编译函数 (已实现)
  在编译期间执行标为【可预编译 @roj.compiler.plugins.constant.Constant】并已知入参的函数，并生成常量结果
### 数组压缩
  将基本类型数组压缩为字符串(Base128)，减少文件体积
### 隐式导入|ImportAny (已实现)
  若全局没有同名的类，则该类视为已导入，妈妈再也不要去操心import了
### 受信执行环境|Package-Restricted (已实现)
在任何import语句之前，可以插入package-restricted关键字。  
启用此选项后，只能使用通过import语句导入的类。  
若需取消导入特定类（可能是由于使用import *通配符导入），可通过使用import - className来实现。此外，可以省略减号旁边的空格。  
在使用未导入的方法返回值或字段类型时，同样会进行导入检查，从而有效地防止某些潜在的漏洞。
### 别名 (已实现)
  import a.b as c;  
  或  
  import static a.b as c;
### with (已实现)
```javascript
with (System.out) {
	println("hello world!");
}
```
如果需要对某个类静态的使用，你可以 with (X.class) {} 或者，简单的import static
### 可选的分号 (部分实现)
  部分语句可以省略分号
### 直接赋值操作符 AssignD
```java
Object yyy; // 这也可能是字段或任何东西

int a = yyy = 3; // 这句话无法通过编译

// 通常来说，你要这么写
int a;
yyy = a = 3;

// 但是，现在有了第二种方式
int a = yyy <<< 3;
```
### defer (已实现)
在代码块结束时，可以通过try-with-resource形式执行多个表达式。  
如果其中一个表达式发生错误，不会影响其他表达式的执行，所有异常会一起抛出。  
此外，可以与AutoCloseable接口混用。最后一个分号可以省略。  
尽管可能无法完全避免缩进，但至少可以减少使用try-finally的情况。

需要注意的是，defer的执行顺序和定义顺序是相反的（对于AutoCloseable是先创建的实例后调用close方法）。下面是代码执行的结果：
- 打印 b
- 打印 a
- 关闭文件输入流
```java
try (
      var in = new FileInputStream(...);
      defer System.out.println("a");
      defer System.out.println("b");
) {
	
}
```
### 尾递归优化 (已实现)
在return中调用当前函数会被转换为循环而不是再次调用
* 对于不可继承的方法（含有任意修饰符：static，final，private），它是自动启用的
* 对于其它方法，你可以使用@Tailrec注解来启用优化
* 你也可以使value=false来禁用优化
### yield
   生成器函数 (WIP)
### async / await
   Promise (WIP)
## 扩展功能 (通过Lavac API实现)
### 注解处理: annotations
#### @Attach
  将静态函数附加到其它类上

```java

import roj.compiler.plugins.annotations.Attach;

@Attach
public class Attacher {
    public static boolean isNotEmpty(String s) {
		return !s.isEmpty();
    }
}

```

这么做之后，无论这个类以源代码编译还是在classpath中  
都可在字符串对象（或它的子类，虽然字符串是final的）上使用 isNotEmpty() 函数
#### @AutoIncrement
  以指定的start和step为每个字段分配整数常量值
```java
@AutoIncrement(value = 100, step = 1)
public static final int A,B,C,D;
```
等效于  
public static final int A = 100,B = 101,C = 102,D = 103;
#### @Getter/Setter
  生成getXXX和setXXX方法  
#### @Property
  以属性访问getter和setter  
  这个注解可以*堆叠*
* getter和setter可以自动从value生成，但不会为布尔值生成isXXX，依然是getXXX
* 你也可以手动输入名称
```java
@Property(value = "some", getter = "getSome", setter = "setSome")
public class Some {
	public String getSome() { return "我是一个字段"; }
	public void setSome(String setter) {}

	{
		System.out.println(this.some);
		this.some = "awsl";
		System.out.println(this.some);
	}
}
```
  你可以直接以属性调用这些字段
#### @Operator
  自定义操作符  
  WIP
### 编译期执行: constant
  使用@Constant标记纯函数  
  自动推断还在研发  
### 参数注入 / inline ASM / 泛型转换: asm

	/**
	 * 获取注入的属性
	 * @return 返回类型为属性或def的真实类型，不一定是对象，并且可能随属性注入改变
	 */
	public static <T> T inject(String name, T def) {return def;}

	/**
	 * 执行无返回值的操作，语法为AsmLang
	 * @param asm 常量
	 * @return true => 不在Lavac环境中
	 */
	public static boolean __asm(String asm) {return true;}
	//public static void __asmx(Object... asm) {}

	/**
	 * 强制的 <b>泛型</b> 转换
	 * @see roj.util.Helpers#cast(Object)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T cast(Object input) {return (T) input;}

	/**
	 * 解释为<none>
	 */
	public static boolean i2z(int v) { return v != 0; }
	/**
	 * 解释为<none>
	 */
	public static int z2i(boolean b) { return b?1:0; }
	/**
	 * 解释为POP或POP2
	 */
	public static void pop(Object input) {}
### PrimGen

## Java17支持，Lava暂时不支持
### record
### 模块系统
### 非静态类，以及和它相关的泛型调用
### 在内部类中使用this或变量
### lambda
### instanceof cast
### 这(大概)是全列举，未写出的，比如匿名类、参数注解、封闭类、都已经实现


## Lava未来不会支持的
### 方法中的具名类
### javac API


## API
TODO  
ExprApi  
StreamChain  
ResolveApi  
GlobalContext  
LocalContext  
TypeResolver  
Library  


## 多线程编译 (CompileUnit)

### Stage 1  
解析基本结构
* package / name
* module-info / package-info
* import / import static / import any / package-restricted
* access modifier
* inner class / helper class
* method / constructor / {} / static {}
* field
* generic
  同时会解析注解和field initializator中的表达式，但不resolve  
  TODO 检测子类名称是否唯一？

****
线程同步
****

### Stage 2.1
解析并验证对其它类的直接引用
* extends / implements / permits (仅类型)
* field type / method parameter
* signature (泛型)
* annotation (仅类型)

****
线程同步
****

### Stage 2.2
解析并验证对其它类的(可能的)间接引用
* 循环引用
* 泛型异常
* 收集可被覆盖的方法
* sealed和permits检查
* 方法定义冲突 / throws (要判断instanceof Throwable，所以需要放在2.2)
* field type, method parameter or throws
* 生成默认构造器、Enum的values和valueOf和$VALUES

****
线程同步
****

### Stage 3
PreCheck  
该阶段还在TODO  
该阶段的注解不能引用static final字段，十分遗憾  
所以应该把static field的赋值放到这个阶段解析，并且应该给这些字段放属性，确保依赖能正确解析
* 检查方法是否可以覆盖
* * 处理Override
* * 接口方法是否存在实现冲突而必须在本类实现
* * 父类是否实现了本类接口的方法
* * 是否有某些抽象方法未实现
* * 访问权限是否降级
* * 生成泛型桥接方法
* 注解

****
线程同步
****

### Stage 4
解析方法体
* static {}和 {}的代码合并
* 匿名类
* assert
* final字段是否被赋值