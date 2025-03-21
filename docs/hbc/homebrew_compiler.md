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
- UnaryPre
- ExprGen
- ExprStart
- ExprNext
- ExprTerm
- UnaryPost
- 词法分析器状态
- 如何优雅的抛出异常
- 二元表达式的优先级
- 三目运算符
- delete
### 第三章·表达式执行
- 类型容器: CInt CMap CString……
- 表达式上下文
- ExprNode#eval(Context)
### 第四章·表达式执行-优化（指令集和虚拟机）
- Opcode & OpcodeNode
- StackFrame和行号追踪
- Goto
- 如何调用方法
- try-catch-finally和throw: TryStartNode & TryEndNode
### 第五章·方法解析
- ExprNode#resolve: Frame和变量槽
- 作用域: 全局和闭包
- SwitchMap
- 变量初始化 VisMap
- Goto (part2)
### 第六章·方法执行
- 继承
- 类型容器的扩展，比如defineProperty（API，并非语法）
### 第七章·这不是玩具 - 总体性能与交互优化
- 可变对象和不可变对象的状态管理
- ThreadLocal
- 编译到ASM
- MethodResolver: 与Java方法、字段的互访
- CJavaClass: Java类型和对象的引用