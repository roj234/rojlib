## 这是个模板

### 💻 变更类型 | Change Type
* [ ] ✨ feat
* [ ] 🐛 fix
* [ ] ♻️ refactor
* [ ] 💄 style
* [ ] 🔨 chore
* [x] ⚡️ perf
* [ ] 📝 docs
### 🔀 变更说明 | Description of Change
将性能提升了-73%  
修复了-429个bug

### 📝 补充信息 | Additional Information

## TODO LIST
* [ ] DNS服务器重构
* [ ] 修复P2P问题
* [ ] yield (Generator)
* [ ] data flow analyze for bytecode
* [ ] retransform based advanced hot-reload
* [ ] FrameVisitor
* [ ] UDP Transport Protocol
* [ ] ReedSolomon
* [ ] FRP finish
* [ ] PNG-8 Encode
* [ ] QOA Encode/Decode
* [ ] TextDiff



### Lava Compiler
* [ ] 把CompileUnit里面readType移动到JavaLexer  
↑ 职能专一
* [ ] 完整的Record
* [ ] switch Class
* [ ] StreamChain
* [ ] lambda
* [ ] auto type merge
* [ ] struct
* [ ] generator

### CompileUnit的多线程和同步解析

Stage 1  
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