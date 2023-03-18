
# 写在前面  
  1. 全是破轮子
  2. 基本就是想到什么就写什么
  3. 重构是家常便饭, 我在努力多写一点注释
  4. 没啥人用, 不用考虑向前兼容
  5. 谁帮我写写FrameVisitor？

# 2023/03/23 更新
    高级序列化器实装
    另外我们还支持了7z的压缩和解压
请看`roj.config.serial.SerializerManager`  
由于接口特殊（见`roj.config.serial.Adapter`）  
可能你想自定义序列化不是那么容易？

# 这里都有啥
## roj.archive
`ZipArchive/ZipFileWriter`: zip
* 读写
* 任意编码
* AES加密/ZipCrypto加密
* 分卷
* 增量修改 —— 无需处理没修改的,比如给加密的压缩文件增加新文件
* 仅读取CEN(更快)
* Info-ZIP UnicodePath和高精度时间戳的支持 （只读）  

`QZArchive/QZFileWriter`: 7z
* 读写
* AES加密
* 分卷
* 固实
* 压缩文件头


## roj.asm  
    自己做的ASM, 资料来自VM规范  
内附：`roj.asm.Translator`, `roj.asm.util.Transformers`, `roj.asm.nixim.*`
  
根据信息详细程度分级，并实现统一接口:  
`roj.asm.tree.IClass`, `rom.asm.tree.FieldNode`, `roj.asm.tree.MethodNode`

### 粗略信息 List<String> simpleData(ByteList buf)
* 包含类名[0]，继承[1]，实现的接口[rest] 

### 权限信息 AccessData parseAccess(byte[] buf)
* +只读的方法和字段  
* +修改类和其中元素的访问级

### 常量信息 ConstantData parseConstants(ByteList buf)  
* +解析整个常量池，自此开始，信息不再是只读的了，  
* +选择性解析方法, 简单的操作不需要解析Code属性，极大提高速度  
示例见ConstMapper

### 完整信息 ConstantData parse(ByteList buf)  
* 和ASM的tree模式差不多  
  
【完整信息】级的速度大概是比不上ASM  
   但是***大部分ASM操作，都不要用到这一等级，常量信息完全够了***
  
此外，我现在还写了个Visitor模式 `roj.asm.visitor.CodeVisitor`  
嗯...只支持Code属性  
不过确实是快了不少  
  
然后还有Nixim:
 * 使用注解注入一个class，增加修改删除其中一些方法，或者让它实现接口  
示例: roj.misc.NiximExample  
 * 暂不支持在方法中间插入  
 * 灵感来自spongepowered:mixin  
  
## roj.collect  
包含了各种我写的集合,举几个好玩的  
 * 不保存hash，进而更省内存（对于String之类算hash简单或者有缓存）的Map `MyHashMap`
 * `UnsortedMultiKeyMap<K, T, V> implements Map<List<K>,  V>`  
`.getMulti(List<K> keys, int limit, Collection<? extends V> col, boolean partial)`  
获取map中全部或部分符合keys的所有组合  
最差时间(0.1% ile?): 阶乘  
平均时间: 接近常数
  
 * 带压缩的前缀后缀树`TrieTree`
 * `RSegmentTree<T>` 取各区间的交集  
* 可以用来计算变量的作用域  
* 或者对于区间只是挨着的, 比如bundle中的某些小文件, 用来减少IO次数  
 详情看MutableZipFile  
 * `RingBuffer`
  
## roj.config  
  JSON YAML TOML INI XML 解析器  
前四个配置文件格式使用统一数据结构 `roj.config.data.CEntry`  

使用`CEntry.toJSON` toYAML toTOML toINI等还原到字符串  
或使用`CConsumer`和配套的`CEntry.foreachChild`  
#### 读写(二进制)`NBT`,`torrent`  
#### 只读`xlsx`,`csv`(这玩意格式如此简单也没必单独弄个方法吧)  

XML也可以与CEntry互转 `CEntry AbstXML.toJSON()`  
XML/CEntry均支持dot-get: `a.b[2].c`  
#### 序列化：  
  使用ASM动态生成类，支持任意对象（不只实体类！）的序列化/反序列化  
  支持数组，不用反射  
  标记(flag):   
 * `GENERATE` = 1, 对未知的class自动生成序列化器  
 * `CHECK_INTERFACE` = 2, 检查实现的接口是否有序列化器  
 * `CHECK_PARENT` = 4, 检查父类是否有序列化器  
 * `DYNAMIC` = 8 动态模式 (不根据字段类型而根据序列化时的对象class来获取序列化器)

```java

import roj.config.serial.Name;
import roj.config.serial.Via;

import java.nio.charset.Charset;

public class Test {
  public static void main(String[] args) throws Exception {
    // 这是必须的，并且在SerializerManager初始化前调用
    AdapterOverride.overridePermission();

    SerializerManager man = new SerializerManager();
	// 自定义序列化方式(使用@As调用)
    man.registerAsType("hex_color", int.class, new UserAdapter());
    // 自定义序列化器
    man.register(Charset.class, new UserAdapter());

    CAdapter<Pojo> adapter = man.adapter(Pojo.class);

    Pojo p = new Pojo();
    p.color = 0xAABBCC;
    p.charset = StandardCharsets.UTF_8;
    p.map = Collections.singletonMap("114514", Collections.singletonMap("1919810", 23333L));

    // 可以换成 ToJson / ToYaml
    ToEntry ser = new ToEntry();
    adapter.write(ser, p);
    System.out.println(ser.get().toJSONb());
    /**
     {
        "charset": "UTF-8",
        "myColor": "#aabbcc",
        "map": {
          "114514": {
              "1919810": 23333
          }
        }
     }
     */

    // 使用CCJson,CCYaml从文本读取(不生成无用对象),或者CEntry
    System.out.println(adapter.read(new CCJson(), ser.get().toJSONb(), 0));
  }

  public static class Pojo {
    // 自定义序列化方式
    @As("hex_color")
    // 自定义序列化名称
    @Name("myColor")
    // 上面的AdapterOverride就是为了写private
    private int color;
    // 通过getter或setter来访问字段
    @Via(get = "getCharset", set = "setCharset")
    public Charset charset;
    // 支持任意对象和多层泛型
    // 字段类型为接口和抽象类时，会用ObjAny序列化对象，会使用==表示对象的class
    // 如果你碰得到这种情况，最好还是手动序列化... 自动生成的序列化器并不会管父类的字段
    public Map<String, Map<String, Object>> map;

    //使用transient避免被序列化
    private transient Object doNotSerializeMe;

    // 若有无参构造器则调用之，否则allocateInstance
    public Pojo() {}

    public Charset getCharset() {
      return charset;
    }

    public void setCharset(Charset charset) {
      this.charset = charset;
    }

    @Override
    public String toString() {
      return "Pojo{" + "color=" + color + '}';
    }
  }

  public static class UserAdapter {
    String doWrite(int c) {
      return "#" + Integer.toHexString(c);
    }

    int doRead(String o) {
      return Integer.parseInt(o.substring(1), 16);
    }

    String serializeMyObject(Charset cs) {
      return cs.name();
    }

    Charset deserMyObj(String s) {
      return Charset.forName(s);
    }
  }
}


```
#### 人性化的错误(仅适用于等宽字体,不适用于CharSequenceInputStream)  
```  
解析错误:  
  Line 39: "最大线程数": 96, , ,  
-------------------------------------^  
总偏移量: 773  
对象位置: $.通用.  
原因: 未预料的: ,  
  
at roj.config.word.ITokenizer.err(AbstLexer.java:967)  
at roj.config.word.ITokenizer.err(AbstLexer.java:963)  
at roj.config.JSONParser.unexpected(JSONParser.java:232)  
at roj.config.JSONParser.jsonObject(JSONParser.java:153)  
at roj.config.JSONParser.jsonRead(JSONParser.java:217)  
......  
```

#### 保存Map中共有的Key节约空间: `roj.config.VinaryParser`

#### Usage
    调用各parser的静态parses方法  
new一个也行  
`roj.config.ConfigMaster.parse`也行  
继承它也行
  
## roj.crypt  
    几种加密/哈希算法，还有CFB等套在块密码上面的壳子  
  `SM3` `SM4` `XChaCha20-Poly1305` `AES-256-GCM`  
  `MT19937`  
  `PBKDF2` `HMAC`  

## roj.dev
    热重载
1. [x] 基于Instrument
2. [ ] 基于VM模拟

## roj.exe
    PE文件格式(.exe .dll)和ELF文件格式(.so)的解析

## roj.io  
  多线程下载 `Downloader`
  `BOMInputStream`  
  `BoxFile` 类似electron的asar  
  `ChineseInputStream`
  * 中文编码检测, 包括 UTF32 UTF16 UTF8 GB18030

`BinaryDB` 分块锁的实验品,似乎效率还行
  
## roj.kscript  
    js解释器, WIP  
并没有完全支持ECMAScript  
基本类型还没加函数, 比如charAt  

## roj.lavac
    自己开发的javac, WIP  

## roj.mapper  
  class映射(对方法/类改名)器 ConstMapper / CodeMapper  
   * 上面我说到ASM的ConstantData等级好就好在这里  
   * 它的速度是SpecialSource的十倍 _(2023/2/11更新:更快了)_  

  class混淆器 `SimpleObfuscator`  
   * 还支持反混淆，也就是把所有接受常量字符串的函数eval一遍  
   * [x] 字符串
   * [x] 字符串+StackTrace
   * [ ] 流程分析(先保存至一个(本地)变量,也许很久之后再解密)
  
## roj.math  
    各种向量啊矩阵啊并不是我写的，不过我感觉我现在也能写出来...  
  `VecIterators`: 两个算法  
  * 由近到远遍历一个Rect3i  
  * 遍历Rect3i的边界 

`MutableBigInteger`: 如其名

  `PolygonUtil`:  
多边形面积，顶点顺序  
折线长度  
  `MathUtil`:  
插值  
快速sin  
`parseInt()` without throwing
  
## roj.misc  
    各种工具，也可以看作是这个lib的测试样例  
  
1.   `AdGuard` 基于DNS的广告屏蔽器  
2.   `AsarExporter` 导出ASAR
3.   `CpFilter` 通过动态分析加载的class精简jar  
4.   `HarExporter` 导出开发者工具通过【Save as Har with contents】导出的har文件 (copy网站)  
5.   `HFTP` 基于MSS和HTTP的文件传输工具  
6.   `MHTParser` 解析mhtml
7.   `MP3Player` MP3播放器  
8.   `MyPassIs` 密码生成器  
9.   `RaytraceCulling` 基于CPU光线追踪的方块剔除(WIP)
10.   `PluginRenamer` 恢复被无良腐竹改了的插件名  
11.   `SameServerFinder` 端口扫描  
14.   `CleanWechat` 清除一定时间以前的文件
15.   `Websocketed` 用Websocket执行任意脚本
  
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

怎么用: (WIP)
* 我提供了详细的配置文件和说明
* 你只需要编译本项目并运行roj.mod.FMDMain即可
* 更多使用方法请看mcbbs的发布贴: xxxx
* 你还可以在这里下载编译好的版本

## roj.net
    基于管线的网络请求

HTTP服务器, 客户端  
  * 长连接
  * 压缩缓存
  * 注解路由
  * 错误友好
  * Websocket ready
  * HTTP2.0 (WIP)

DNS服务器 

内网穿透工具 AEClient / AEServer / AEHost`roj.net.cross`  
* [ ] UPnP  

MSS协议，My Secure Socket`roj.net.mss`  
  因为(jvav的)SSL不好用，自用的话还不如自己写一个协议  
* [x] 加密方式协商  
* [x] 前向安全  
* [x] 1-RTT

## roj.opengl  
  前置: LWJGL  
字体渲染器  
可视化测试工具，math里的不少算法就用它测试，直观极了  
快速的Stitcher
  
## roj.reflect
`EnumHelper`,  动态增删枚举  
`DirectAccessor`, 实现高效率的‘反射’操作  
不管是新建实例，还是访问字段，还是调用方法，它都可以帮你解决

首先，我们要一个接口, 里面定义一些方法吧  
不，先来需求，我要给玩家发标题，以下是假的代码  

```java
    class Player {  
      private Connection conn;  
    }  
      
    class Connection {  
      public void sendPacket(Packet packet);  
    }  
      
    interface Packet {  
       // ...  
    }  
      
    class TitlePacket {  
      public TitlePacket(String title) {  
        // ...  
      }  
    }
```

这本来是个很简单的事... 但是如果游戏有多个版本, 每个版本的类名都不一样....  
* 你可以用反射, 效率损失... 因为不频繁也可以忍受  
* 你还可以给每个游戏版本建一个类, 共同实现一个接口  
* 你也可以用 `DirectAccessor`  

由于你不能直接写出方法的参数的类，因为它们也会随着游戏版本改变，你需要使用模糊匹配(all-object)模式, 这个模式的有关信息可以在代码注释中找到  
它的特点是所有非基本类型都需要表示为Object  
  
DirectAccessor可以在内部保存0至多个对象,  
`makeCache(Class<?> targetClass, String name, int methodFlag)`  
* targetClass为对象的类  
* methodFlag: 创建哪些方法:
  * 1: getter  
  * 2: setter  
  * 4: clear  
  * 8: 检测方法的存在  

使用`useCache(String name)`选择使用这个缓存, null取消  
  
```java
interface H {  
   Object getConn(Object player);  
   void sendPacket(Object conn, Object packet);  
   Object getTitlePacket(Object title);  
}
```
  
然后 使用 `DirectAccessor.builder(H.class)` 获得一个构建器  

### 字段 （注，这里都是同名方法里参数最多的那个)  
`access(Class<?> target, String[] fields, String[] getters, String[] setters)`  
此方法用来访问字段
* target是字段位于的class
* fields是它的名字 (字符串数组，下同)
* getters是在`H`中它的getter（得到值的方法）的名字, 一项或整体可以为null 不构建对应的getter
* setters是setter(设置值的方法)的名字, 一项或整体可以为null 不构建对应的setter

所以:  
```java 
b.access(Class.forName(version + "Player"), ["conn"], ["getConn"], null);
```

### 方法  
`b.delegate(Class<?> target, String[] methodNames, MyBitSet flags, String[] selfNames, List<Class<?>[]> fuzzyMode)` 

`target`,`methodNames`,`selfNames`同`target`,`fields`,`getters/setters`

* flags: 第i项是true时，代表使用‘直接’访问  
也就是方法静态绑定(INVOKESPECIAL)到target  
不懂这是啥的话传入`DirectAccessor.EMPTY_BITS`  

* fuzzyMode 指的是 模糊匹配(all-object) 模式  
`null`: 不启用  
`空列表`: 模糊匹配  
`长methodNames.length的列表`: 其中为null的项模糊匹配，否则用这些类精确匹配  
<br>
  模糊匹配：函数的参数和返回值中不是基本类型的部分全为Object  
  只匹配参数个数、基本和非基本类型的位置，可能会重复，这时候需要手动指定参数的类型 (精确)  
  例如:
```java
void test(int a, String b);  
void test(int a, List<String> b);
```

所以：
```java 
b.delegate(Class.forName(version + "Connection"), ["sendPacket"], EMPTY_BITS, ["sendPacket"], Collections.emptyList());
```

### 构造器  
`b.construct(Class<?> target, String[] names, List<Class<?>[]> fuzzy)`  
没啥好讲的  
```java 
b.construct(Class.forName(version + "TitlePacket"), ["getTitlePacket"], Collections.emptyList())
```

### 生成！
```java
static final H Helper;
...
Helper = b.build();
```

最后你方法
```java
public static void sendTitle(AbstractPlayer player, String title) {  
  h.sendPacket(h.getConn(player), h.getTitlePacket(title));  
}
```

### 这还没完！
`i_`开头的方法以绕过需要Class实例的限制，直接生成字节码  
`unchecked()`关闭类型检查  
生成的类无视权限控制 (不能写final字段)

## roj.terrain  
    地形生成器, WIP  
  
## roj.text  
  `ACalendar`，又一个日历，提供: prettyTime,  formatDate  
  `CharList` 又一个SB  
  `Logger`  
  `Template` 使用 {xx} 标识变量并替换  
`Template.compile("您是第{count}位客户！").replace(Collections.singletonMap("count", "8848"))`
  > “您是第8848位客户！”  

  `LineReader` 按行读取  
  `FastMatcher` 基于改进版BM算法的字符串寻找
  
## roj.ui  
    请搞到libcpp.dll或在ps中执行
  `CmdUtil` 给控制台来点颜色看看！支持MC转义  
  `EasyProgressBar` tqdm in java!  
  
## roj.util  
  `DynByteBuf`，能扩展的ByteBuffer，也许Streamed，可作为Input/OutputStream, DataInput/Output
  `ComboRandom`，多个种子的random  
  `FastThreadLocal` 空间换时间，与`FastLocalThread`更配哦  
  `GIFDecoder` 解码GIF文件  
  `VarMapperX` 变量分配器(WIP)，即使是上一版验证过没bug的VarMapper也比sb的javac好
