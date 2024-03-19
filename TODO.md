

### TODO LIST
* [ ] DNS服务器重构
* [ ] 修复P2P问题
* [ ] yield (Generator)
* [x] java compiler
* [ ] data flow analyze for bytecode
* [ ] retransform based advanced hot-reload
* [ ] FrameVisitor
* [ ] UDP Transport Protocol
* [ ] MyJSPEngine



FF:
把CompileUnit里面readType移动到JavaLexer  
 ↑ 职能专一  

CompileContext作为ExprParser，BlocKParser和CompileUnit交互的桥梁  
 ↑ 这样可以通过CompileUnit暴露的一些方法，简单且可配置的编译单个表达式，单个方法或整个类  
 ↑ 脱耦  


BlockParser
try和switch必须要做成Node
循环可以做成Node

### 新语法  
"[" LITERAL+ "]" KEYWORD  
比如 [fast] finally  
这样sw-finally就不用单独占一个keyword，还是为了兼容性

#### switch扩展  
[table] switch  
使用static MyHashMap（基本泛型扩展）来优化switch，并使其支持任何基本类型、String和对象  
 * 如果是枚举或int，根据空间利用率可以选择替代方案：ordinal -> index数组  aka. legacy switchmap  
 * 如果是任意对象，才会独立一个$SwitchMap$n，目的是懒执行这些表达式，拖到switch第一次执行的时候，而不是当前类的clinit  

### CompileUnit的多线程和同步解析

Stage0/1  
 解析基本结构
* import
* package / name
* access modifier
* inner class
* helper class
* method / constructor / {} / static {}
* field
* generic
同时会解析注解和field initializator中的表达式，但不resolve  
如果是立即常量（1+1）会在此阶段合并  
TODO 检测子类名称是否唯一？

****
线程同步
****

Stage 2
 解析并验证类中的各种引用
* parent, interface
* field type, method parameter or throws
* enum
* generic
* resolve字段的立即初始化，并尝试计算所有常量值
* Constant和Operator在常量计算的应用
  必须有编译好的版本，而不是立即解析，计算字符串SHA1判断metadata确定是否调用（如果用别人的方法1pass，自己2pass（若有修改））
  在JVM中安全的（类白名单+DPS）加载
* apt_rt 注解处理
* 处理AutoIncrement 分配ID

****
线程同步
****

Stage 3
 解析方法体
* anonymous class
* throws check
* 注解二阶段