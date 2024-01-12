# Lava™编译器  (WIP)
![lavac](images%2Flavac.png)* 纯属玩梗，并未注册任何商标

## Lava支持，Java17不支持的
### 参数调用  
  使用 `method({参数名:表达式,(可叠加)})` 来按参数调用方法
```java
List.of({e1: null, e2: 3});
List.of(null, null, {e3: null, e4: null})
```
该表达式只能位于方法的最后一个参数
### 参数默认值  
  在方法定义处使用形如 `method(int a = 3)` 的语句来为参数指定默认值  
  默认值可以是任意表达式，实际上它相当于一个宏，你可以使用在当前上下文不可用，而在函数调用上下文可用的变量
```java
static void test(int a = 3, Object o = some_variable.some_field) {
	// ...
}
```
### goto语句  
  使用goto语句在任意标签之间跳转  
  在跳转过程中增加的变量必须立即赋值
### 快速switch  
  使用`ToIntMap`为switch加速（永远TABLESWITCH），并使得switch可以选择任意运行时常量表达式
### 操作符重载  
  允许重载已有的二元或一元运算符，还能添加自定义的运算符，它们会在解析时被换成方法调用
#### 默认的重载
* ! String => String#isEmpty
* String * int => String#repeat(int)
### 基本类型泛型
  通过模板生成基本类型的泛型类，在运行时节约内存  
  (WIP)
### 递归泛型推断
  我没找到什么好例子，反正正常一级也够用，javac很少报错
### finally优化
  在多个finally块中，防止代码体积暴增
### 预编译函数
  在编译期间执行可预编译的函数，并将产生常量结果
### 数组压缩
  将基本类型数组压缩为字符串(Base128)，减少文件体积
### 隐式导入
  若全局没有同名的类，则该类视为已导入，妈妈再也不要去操心import了
### 别名
  import a.b as c;  
  或  
  import static a.b as c;  
### 过程
  在方法中使用`__sub name(type name)`标记一个过程
  几乎用不到，但，我可以不用，你不能没有
```java
static void test() {
	__sub sub1(String string) {
		System.out.println(string);
		__end_sub; // 等同于return
        // 然而它本质上仍然属于这个方法，所以用了return会退出整个方法
    }
}
```
### defer
  在当前代码块结束时执行表达式（try-finally的语法糖，让缩进更好看）  
  若一表达式发生错误，不影响其余表达式执行，异常会一起抛出
```java
defer u.freeMemory(addr);
```
### yield
   生成器函数 (WIP)
### async / await
   Promise (WIP)
### 附加函数
   将自己的静态函数以静态形式或动态形式附加到第三方类
### inline ASM (WIP)

## Java17支持，Lava暂时不支持
### switch作为表达式
### 封闭类
### record
### 模块系统
### 非静态类，以及和它相关的泛型调用

## Lava未来不会支持的
### 方法中的private类
### 兼容javac标准的注解处理程序