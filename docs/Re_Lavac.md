# Lava™编译器  (WIP)
![lavac](images%2Flavac.png)* 纯属玩梗，并未注册任何商标

## 用前须知
Lava编译器并非设计为Javac的替代品  
它的API更多为修改java语法设计  
（我认为比Javac的API更好）  
并且不兼容所有Javac的API和部分Java语言特性  
这意味着如果你习惯使用一些编译插件来帮助你写代码，那么它们无法支持  
对于一些喜欢使用某些特性的人来说，代码也无法无缝迁移到Lava编译器

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
### 多返回值
*该语法可能随时变动
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
对于同时实现List和RandomAccess接口的类，它的foreach会以顺序访问而不是Iterator编译  
如果你正好有这么个列表，还是多线程访问的（必须用Iterator），请使用 ...for 来禁用这个优化
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
var map = [ 1+1 => 3+4 , "mixed type" => also.can.be.used() ];
```
### goto语句 (部分实现)
  使用goto语句在任意标签之间跳转  
  在跳转过程中增加的变量必须立即赋值
### Switchable (已实现)
* Lavac生成的switch具有更小的文件体积和更好的性能
* 给任意一个类打上@Switchable注解，这个类就可以像枚举一样switch
* * 时间复杂度 O(1)
* * 使用...switch 可以强行开启这个功能，即使没有Switchable注解
* * 包括基本类型，和非编译期常量
```java
		var v = Test.A;
		switch (v) {
			case Test.A -> System.out.println("A");
			case Test.B -> System.out.println("B");
			case Test.NulL -> System.out.println("null (real)");
		}
```

### 操作符重载 (已实现)
  允许重载已有的二元或一元运算符，还能添加自定义的运算符，它们会在解析时被换成方法调用
#### 默认的重载
* ! String => String#isEmpty
* String * int => String#repeat(int)
### 可选连接操作符 (部分实现)
```javascript
 var nullable = a?.b?.c?.d;
```
任意为null => 结果为null
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
  在import语句之前插入package-restricted  
  启用后，只能使用import语句导入的类  
  使用import - className取消导入某个类（可能是因为import *导入的）  
  空格可以省略  
  同时，在未导入的方法返回值或字段类型上调用也会被阻止 (这有效的防止了一些漏洞)
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
  以try-with-resource形式在代码块结束时执行多个表达式  
  若一表达式发生错误，不影响其余表达式执行，异常会一起抛出  
  可以和AutoCloseable混用  
  最后一个分号可以省略  
  注意：  
  defer的执行顺序和定义顺序是相反的（对于AutoCloseable是先new后close）  
  下列代码的执行结果是：  
  * 打印 b
  * 打印 a
  * 关闭文件输入流  
```java
try (
      InputStream in = new FileInputStram(...);
      defer System.out.println("a");
      defer System.out.println("b");
) {
	
}
```
### yield
   生成器函数 (WIP)
### async / await
   Promise (WIP)
### 附加函数 (已实现 扩展功能)
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
### inline ASM (WIP)
### record

## Java17支持，Lava暂时不支持
### switch作为表达式
### 封闭类
### 模块系统
### 非静态类，以及和它相关的泛型调用

## Lava未来不会支持的
### 方法中的private类
### 兼容javac标准的注解处理程序


## API
TODO  
ExprApi  
StreamChain  
ResolveApi  
GlobalContext  
LocalContext  
TypeResolver  
Library  