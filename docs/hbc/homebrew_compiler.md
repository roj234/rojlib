### 第一章·JSON解析器
- Tokenizer
- - firstChar: string | number | whitespace
- - next & retract
- JSON Parser
- JSON5
- - 注释
- - 尾逗号
- Literal Token
- 抛出有意义的异常
### 第二章·表达式解析
- 前缀运算符 UnaryPre
- 表达式生成运算符 ExprGen
- 值生成运算符 ExprStart
- 值转换运算符 ExprNext
- 值终结运算符 ExprTerm
- 后缀运算符 UnaryPost
- 词法分析器状态
- 如何优雅的汇报错误
- 二元运算符的优先级
- 三目运算符
- JS中的delete
### 第三章·表达式执行
- 类型容器: int object string……
- 表达式上下文
- ExprNode#eval(Context)
### 第四章·表达式执行-优化（指令集和虚拟机）
- 设计你的指令集&执行器
- 栈帧和异常追踪
- 跳转
- 如何调用方法
- try-catch-finally和throw: TryStartNode & TryEndNode
### 第五章·方法解析
- ExprNode#resolve: Frame和变量槽
- 作用域: 全局和闭包
- SwitchMap
- 变量初始化 VisMap
- 跳转 (part2)
### 第六章·方法执行
- 继承
- 类型容器的扩展，比如defineProperty（API，并非语法）
### 第七章·性能与交互优化
- 可变对象和不可变对象的状态管理
- 对象池
- 编译到字节码
- 与Java方法、字段的互访
- 在JS中表示Java类和对象