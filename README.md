
### 本项目存在大量无意留下的漏洞，仅供学习研究用途，如用作商业行为，不提供任何保证

## 有单独文档的类(包)
[Lava编译器](docs/Re_Lavac.md)  
[自动识别中文编码](docs/Re_ChineseCharset.md)  
[带指令注册的终端模拟器](docs/Re_CommandConsole.md)  
[Java8-21通杀的高性能反射解决方案](docs/Re_DirectAccessor.md)  
[高性能的字符串全文匹配方案](docs/Re_MatchMap.md)  
[NAT打洞](docs/Re_NAT.md)  
[注解定义的HTTP路由](docs/Re_OKRouter.md)  
[(并不是很)安全的插件系统](docs/Re_PluginSystem.md)  
[多线程高性能7z压缩和解压](docs/Re_QZArchiver.md)  
[任意对象的安全序列化解决方案](docs/Re_SerializerFactory.md)  
没列出来的还在WIP

## 上新
roj.reflect.litasm.Intrinsics
  Literally 'ASM'  
  你可以写对应ABI的汇编  
  除此之外还提供了@FastJNI注解以让你更快的调用DLL函数  
   * 仅支持64位windows平台

# 这里都有啥
## roj.archive
`ZipArchive/ZipFileWriter`: zip
* 读写
* 任意编码
* AES加密/ZipCrypto加密
* 分卷
* 增量修改 —— 无需处理没修改的,比如给加密的压缩文件增加新文件
* 仅读取CEN(更快)
* Info-ZIP UnicodePath和NTFS高精度时间戳(现已加入可写入豪华午餐)  

`QZArchive/QZFileWriter`: 7z
* 读写
* AES加密
* 分卷
* 固实
* 压缩文件头
* 并行压缩 / 解压
* 全新* 的并行压缩方式！既支持文件级别的并行压缩，又支持单个文件的并行压缩（LZMA2 only）
* 支持BCJ2等复杂coder
* 追加修改(复制字块)  
* 高性能（大量使用Unsafe，请注意线程安全）

注释：  
 *: 比起上一版本  


## roj.asm  
    自己做的ASM, 资料来自VM规范  
    不支持的项目：
      内容（方法内部的）注解
      计算StackMapTable
    性能、内存占用、易用性（至少对我来说）均优于ow的asm

### Parser.forEachConstant(DynByteBuf buf, Consumer&lt;Constant&gt; c)
* 【只读】处理每一个常量

### AccessData Parser.parseAccess(DynByteBuf buf, boolean modifiable)
* 【只读】类、方法、字段、继承、接口、修饰符等
* 【读写】类和其中元素的修饰符

### ConstantData Parser.parseConstant(DynByteBuf buf)
* 包含一个类的所有信息，和常量池  
* 属性未解析，因为没有人会修改每一个方法  
* * 比如如果你要修改方法的调用，可以直接改常量池  
* * 如果你要修改方法的结构，可以用roj.asm.visitor.CodeWriter
* * 如果上面两个都不符合你的需求，你才应该用roj.asm.visitor.XInsnList
* 上面讲的还都是Code属性，如果要先检测有没有注解再决定如何操作呢
* 使用`T roj.asm.tree.Attributed#parsedAttr(@Nullable ConstantPool cp, TypedName&lt;T&gt; name)`获取存在的属性（它是可读写的）
* TypedName在`roj.asm.tree.Attribute`中列举了（或者你也可以new一个，它只是为了通过泛型规范Attribute的类型）
* 使用`roj.asm.tree.CNode#parsed(ConstantPool cp)`解析一个方法或字段的所有属性

实例见`roj.asmx.mapper.Mapper`

### ConstantData Parser.parse(DynByteBuf buf)
* 同上，而后解析所有属性，最后清空常量池  

## roj.asmx
    基于Transformer的各种骚操作
### classpak
使用LZMA2压缩你的jar
### event
基于ASM的高性能事件系统
取消、继承、泛型
### launcher
对bytecode进行转换，运行在pre-defineClass的javaagent
### mapper
class映射(对方法/类改名)器 Mapper
* 上面我说到ASM的ConstantData等级好就好在这里
* 它的速度是SpecialSource的十倍 _(2023/2/11更新:更快了)_

class混淆器 Obfuscator`
* 还支持反混淆，也就是把所有接受常量字符串的函数eval一遍
* [ ] 字符串解密
* [ ] 字符串解密+堆栈
* [ ] 流程分析(先保存至一个(本地)变量,也许很久之后再解密)

#### 图片展示
![roj.asmx.mapper.MapperUI](docs/images/mapper v3.png)  
![roj.asmx.mapper.ObfuscatorUI](docs/images/ofbuscator.png)

### nixim
 * 使用注解注入一个class，修改其中一些方法，或者让它实现接口  
 * 在头部、尾部、（使用SIJ模式）或中间插入你的代码
 * 删除或替换方法
 * 通过模糊匹配替换一个连续（不包含if、switch、循环）的代码段
 * 替换常量的值，或将其的求值语句替换成一个函数
 * 替换方法的调用
 * 灵感来自spongepowered:mixin  

### AnnotationRepo
 * 公共注解缓存 方便获取注解信息

### NodeFilter
 * transform过滤器，支持declare | reference | annotated => Class Field Method

## roj.collect  
包含了各种我写的集合,举几个好玩的  
 * `MyHashMap` `MyHashSet` 不缓存hash更省内存（对于String之类速度不影响）
 * `MatchMap`  见独立说明
 * 带压缩的字典树 `TrieTree`
 * 取各区间的交集 `RSegmentTree<T>`  
   可以用来计算变量的作用域  
   或者对于区间只是挨着的, 比如bundle中的某些小文件, 用来减少IO次数  
   ZipArchive中有用到
 * 环形缓冲 `RingBuffer`
 * `LFUCache` / `LRUCache`
 * 完美哈希表

## roj.compiler
 java编译器  
 半成品，暂时不怎么支持泛型  
 然而，支持很多绝赞语法  
 更多见独立说明  

## roj.concurrent
Promise:
```java
	Promise.new_(TaskPool.CpuMassive(), (op) -> {
		LockSupport.parkNanos(1000_000_000L);
		op.resolve("a");
	}).thenF((val) -> {
		return val+"b";
	}).thenF((val) -> {
		return Promise.new_(TaskPool.CpuMassive(), (op) -> {
			LockSupport.parkNanos(1000_000_000L);
			op.resolve("c");
			}).thenF((val2) -> {
				return val.toString()+val2;
			});
		}).thenF((val) -> {
			System.out.println(val);
			return null;
		});
```
其它：定时任务
  
## roj.config  
  JSON YAML TOML INI XML NBT Torrent(Bencode) CSV 解析器  
  ConfigMaster  

### 特点：
* 自动识别编码（仅支持中英，默认开启可关闭）
* 所有配置类型（除xml）使用统一结构 `roj.config.data.CEntry`
* 访问者模式的读取和写入（仅支持JSON YAML和NBT）
* 支持dot-get: 形如`a.b[2].c` 详见`roj.config.data.CEntry#query`
* XML的dot-get更高级 详见`roj.config.data.Node#querySelector`
* 支持Xlsx和Csv的处理，它们在roj.excel包
* 人性化的错误提示
* 自动对象序列化


#### 人性化的错误(仅适用于等宽字体)  
```  
解析错误:  
  Line 39: "最大线程数": 96, , ,  
-------------------------------------^  
总偏移量: 773  
对象位置: $.通用.  
原因: 未预料的: ,  
  
at roj.config.word.Tokenizer.err(ITokenizer.java:967)  
at roj.config.word.Tokenizer.err(ITokenizer.java:963)  
at roj.config.JSONParser.unexpected(JSONParser.java:232)  
at roj.config.JSONParser.jsonObject(JSONParser.java:153)  
at roj.config.JSONParser.jsonRead(JSONParser.java:217)  
......  
```

## roj.crypt  
* XChaCha20-Poly1305
* Blake3
* EdDSA / X25519DH
* XXHash32

## roj.excel
    xlsx读写

## roj.exe
    PE文件格式(.exe .dll)和ELF文件格式(.so)的解析

## roj.io  
  多线程下载 `Downloader`  
`BinaryDB` 分块锁的实验品,似乎效率还行
  “分页”缓冲池
  RegionFile

## roj.math  
    各种向量啊矩阵啊并不是我写的，不过我感觉我现在也能写出来...  

`MutableBigInteger`: 如其名  
`Version`: 1.2.3版本解析
  
## roj.misc  
    不好分类的命令行工具
  
1. `FindClass` 查找类中元素的引用或定义，比jd-gui更好！
2. `ModuleKiller` 一行代码干掉Jigsaw
3. `UIEntry` 所有GUI的入口
  
## roj.mod
    我自己写的模组编译器  
    考虑到Minecraft开发的需求, 和ForgeGradle那'惊人'的速度  
    我决定制作它  

功能:
* 编译 (需要JDK,  不过相信我,  迟早有一天我会自己做javac的)
* 增量编译
* 屏蔽部分警告
* 自动检测文件更新并编译
* 在程序运行时根据编译修改其代码(热重载)

特点:
* 我没有做过时间的比较, 除了ForgeGradle
* 在我2019年使用当时所知的最优配置时  
  FG需要30秒  
  FMD则是50ms-1s (增量模式) 4s (全量)

### 图片展示
![roj.mod.FMDMain](docs/images/fmd.png)

## roj.net
    基于管线的网络请求

HTTP服务器, 客户端  
  * 长连接
  * 压缩缓存
  * 注解路由
  * 错误友好
  * Websocket
  * HTTP2.0

DNS服务器  

MSS协议，My Secure Socket`roj.net.mss`  
  因为(jvav的)SSL不好用，自用的话还不如自己写一个协议  
* [x] 加密方式协商
* [x] 前向安全
* [x] 0-RTT

协议混淆  
P2P  

## roj.plugin
  * 插件系统，见独立介绍

## roj.plugins
内网穿透工具 AEClient / AEServer / AEHost`roj.plugins.frp`
* 带或不带中转服务器的端口转发程序
* 客户端与服务器均能自签证书（用户ID）
* 中转服务器模式下支持多个房间(主机)并行
### 图片展示
![roj.plugins.frp.AEGui](docs/images/port transfer.png)
ddns
* DDNS服务器，现支持阿里云和dynv6 API  
* CardSleep: 显卡频率限制  
* MyPassIs: 安全密码生成器  
* `Websocketed` 用Websocket执行任意脚本
* PHP  
* EasySSO
* WebTerminal
* MusicPlayer
* SimpleMQ
* CodeStat
* Unpacker
* MinecraftServer

## roj.reflect
`EnumHelper`,  动态增删枚举  
`DirectAccessor`, 实现高效率的‘反射’操作
`ModuleKiller`, 在Java9-21中一键禁用模块系统
`Proxy`, ASM版本的java.lang.reflect.Proxy

## roj.media.audio  
    MP3和WAV解码器
    TODO => AudioContext

## roj.sql
    从PHP搬过来的简易连接池和链式查询

## roj.text  
  `ACalendar`，又一个日历，提供: prettyTime,  formatDate  
  `Logger`  
  `FastMatcher` 基于改进版BM算法的字符串寻找

### 图片展示 (/WIP)
![roj.text.novel.NovelFrame](docs/images/novel manager.png)


## roj.ui  
    请在支持虚拟终端转义序列的Console中执行 （在windows上可能需要libcpp.dll）
  `CLIUtil.Minecraft` 将Minecraft的小节号转义或JSON字符串原样映射到控制台中  
  `EasyProgressBar` 进度条
  `terminal.DefaultConsole` 基于虚拟终端序列的终端模拟器  

## roj.unpkg
1. `AsarExporter` 导出ASAR
2. `HarExporter` 导出开发者工具通过【Save as Har with contents】导出的har文件 (copy网站)
3. `ScenePkg` 导出小红车的壁纸包

## roj.util  
  `DynByteBuf`，能扩展的ByteBuffer，也许Streamed，可作为Input/OutputStream, DataInput/Output  
  `GIFDecoder` 解码GIF文件  
  `VarMapperX` 变量ID分配器

# Properties （不全）
int roj.nativeDisableBit [禁用native优化] (RojLib)
int roj.cpuPoolSize [CPU Count]  (TaskPool)  
String roj.text.outputCharset [UTF-8]  (TextWriter)  
Path roj.archiver.temp [.]  (ArchiverUI)  
File roj.lavac.i18n [null]  (Lava Compiler)  
boolean roj.debug.dumpClass  (ClassDefiner)
  

# Libcpp.dll
Windows：
  * 具名共享内存
  * ReusePort
  * ANSI转义序列
  * Fast LZMA2 (https://github.com/conor42/fast-lzma2)
  
公共：
  * AES-NI
  * BsDiff
  * XXHash

如果有人问，你为什么不给Linux做优化？  
因为没有人问 ×  

# 特别鸣谢
JFormDesigner  
  * 让我不用碰恶心的AWT界面  

STM32F10X
  * 让我意识到C语言多么简单

@huige233
  * 提供了很多梗图
  * 提供了子模块的命名建议
  * 提供了一些乱七八糟还被我真的实现了的需求
  * 在我学会用IDA之前提供了一些Assembler中的opcode

黄豆
  * 为7z压缩GUI提供了设计建议
  * 找到了一些bug
  * 给我的硬盘增加了许多写入量

ETC
  * 提供EpubWriter的新模板

CraftKuro
  * 帮助安装和配置OpenWRT
  * 提供情绪价值（吉祥物 ×）

咖喱人
  * 提供很多很多支持 (真的)
  * 提供编程天赋 (雾, 但大概也是真的)