<pre>  
## 碎碎念  
本来有想在这里写点东西，不过觉得还是算了  
哦，谁教教我md的语法啊，现在就是一路pre莽过去  
  
## About this library  
我的，也许很不好的理念，就是  
  对于功能:  
  自己能做的，自己做   
  自己不能做的，想办法把它换成自己能做的  
  或者等待我会的时候  
  即使这浪费时间，浪费很多时间，用于本无需的debug等  
  但是我坚持  
  
## 这里都有啥  
### roj.asm  
自己做的ASM, 资料来自VM规范  
内附：class汉化工具，AccessTransformer  
  
我用信息详细程度分了等级，同时实现统一接口:  
    roj.asm.tree.IClass, rom.asm.tree.MoFNode,  roj.asm.tree.IMethod  
  
粗略信息 List<String> simpleData(ByteList buf)  
    包含类名[0]，继承[1]，实现的接口[rest]  
权限信息 AccessData parseAccess(byte[] buf)  
    除了上面的还有只读的方法和字段  
    可以修改class、方法、字段的访问权限  
常量信息: ConstantData parseConstants(ByteList buf)  
    解析整个常量池，自此开始，那些信息不再是只读的了，  
    可以选择性解析方法, 简单的操作完全不需要解析Code属性，以此极大提高速度  
完整信息： Clazz parse(ByteList buf)  
    和ASM的tree模式差不多  
  
速度我没比较，【完整信息】一级大概是比不上ASM  
   但是<font color="red">大部分ASM操作（起码是我遇到的），都不要用到这一等级，常量信息完全够了</font>  
  
此外，我现在还写了个Visitor模式（WIP），速度没算，起码，比起ASM的容易理解  
  
然后还有, Nixim系统  
  使用注解注入一个class，增加修改删除其中一些方法，或者让它实现接口  
  示例: roj.misc.NiximExample  
  暂不支持在方法中间插入  
  灵感来自spongepowered:mixin  
  
### roj.collect  
包含了各种我写的集合  
1. 比如基本类型的map  
2. 不保存hash，进而更省内存（对于String之类算hash简单或者有缓存）的Map  
  
3. UnsortedMultiKeyMap<K, T, V> implements Map<List<K>,  V>  
一个K对应多个T，但是只有一个【最关键的】T  
特殊方法：无序获取，也就是list中包含且仅包含了这些东西就可以获取到，不要求顺序  
稍微修改就可以返回【包含了】，或者【不完全包含】的list  
  
4. 前缀后缀字典树 带entry压缩  
5. Unioner<T> 取各区间的交集  
   A. 可以用来计算变量的作用域  
   B. 或者对于区间只是挨着的, 比如bundle中的某些小文件, 用来减少IO次数  
        详情看MutableZipFile  
6. 8叉树  
  
### roj.config  
  JSON YAML TOML INI XML 解析器  
    前四个配置文件格式使用统一数据结构 roj.config.data.CEntry  
    CEntry包含了接收StringBuilder做参数的toJSON toYAML toTOML toINI以及序列化为二进制的toBinary/fromBinary  
    XML也可以与CEntry互转 CEntry AbstXML.toJSON()  
    XML/CEntry均支持dot-get: a.b.c  
    序列化：  
      使用ASM动态生成类，支持任意对象（不只实体类！）的序列化/反序列化  
      支持数组，不用反射  
      标记(flag):   
       LENIENT = 1, 对于只能序列化不能反序列化的class不报错  
       NONSTATIC = 2, 非静态模式 (不根据字段类型而根据序列化时的对象class来获取序列化器)  
       AUTOGEN = 4, 对未知的class自动生成序列化器  
       NOINHERIT = 8 不开启序列化器的继承 (任意序列化器都将处理class的所有字段)  
    非常人性化的错误  
```  
解析错误:  
  Line 39:         "最大线程数": 96, , ,  
-------------------------------------^  
总偏移量: 773  
对象位置: $.通用.  
原因: 未预料的: ,  
  
        at roj.config.word.AbstLexer.err(AbstLexer.java:967)  
        at roj.config.word.AbstLexer.err(AbstLexer.java:963)  
        at roj.config.JSONParser.unexpected(JSONParser.java:232)  
        at roj.config.JSONParser.jsonObject(JSONParser.java:153)  
        at roj.config.JSONParser.jsonRead(JSONParser.java:217)  
        ......  
```  
    使用：  
      你可以直接调用各parser的静态parse方法  
        此时不支持序列化  
        除非你手动向Serializers.DEFAULT注册那些class  
      你还可以调用它们的builder方法或者直接new一个  
      若serializers传入null则代表不启用序列化  
  
### roj.crypt  
  几种加密/哈希算法，还有CFB等套在块密码上面的壳子  
  SM3 SM4 CRC64  
  
### roj.io  
  多线程下载器 FileUtil.downloadFile / FileUtil.downloadFileAsync  
  BOMInputStream  
  BoxFile 类似那种大型游戏用的，把大量小文件（不压缩）装成个整体  
     哦，electron的asar是个更好的例子  
  可变的ZIP文件 MutableZipFile，嘿，你不妨试试java如何对zip增量修改？  
  
### roj.kscript  
  js解释器, 不多说  
  并没有完全支持ECMAScript  
  基本类型还没加函数, 比如charAt  
  
### roj.mapper  
  class映射(方法/类改名)器 ConstMapper / CodeMapper  
    上面我说到ASM的ConstantData等级好就好在这里  
    它的速度是SpecialSource的十倍  
  class混淆器 SimpleObfuscaor  
    还支持反混淆，也就是把所有接受常量字符串的函数eval一遍  
    就算要StackTrace也支持（未测试！）  
  
### roj.math  
  各种向量啊矩阵啊并不是我写的，不过我感觉我现在也能写出来...  
  VecIterators: 两个算法  
    a. 由近到远遍历一个Rect3i  
    b. 遍历Rect3i的边界  
  MathUtils:  省略一些...  
    多边形面积，顶点顺序  
    折线长度  
    插值  
    数字长度  
    数字转中文  
    快速sin  
  
### roj.misc  
  各种用得到的小工具，也可以看作是这个lib的测试样例  
  
  AdGuard 基于DNS的广告屏蔽器  
  ADownloader 从HTML中提取有效数据  
  CpFilter 通过动态分析加载的class精简jar  
  HarExporter 导出开发者工具通过【Save as Har with contents】导出的har文件 (copy网站)  
  HttpExample HTTP服务器测试  
  HttpTest HTTP客户端测试  
  MP3Player MP3播放器  
  NiximExample Nixim测试样例  
  PFClassMerger 针对性的修改指定class中某几个方法  
  PluginRenamer 恢复被无良腐竹改了的插件名  
  Prefixer 加LICENSE  
  Stripper 截取小说，或者用CRLF换行文件的指定几行  
  
### roj.mod  
  可选前置: md_5:SpecialSource, lzma:LZMA-0.1  
  本来只是个替代垃圾ForgeGradle的模组开发工具  
  不过我现在离不开它了  
  功能：编译java，增量编译，屏蔽警告，热重载  
  特点：速度很快，体积很小  
  
### roj.net  
  HTTP服务器, 客户端  
  DNS服务器  
  内网穿透工具 AEClient / AEServer / AEHost    roj.net.cross  
    todo: 支持UPnP  
  MSS协议，My Secure Socket    roj.net.mss  
    因为SSL不好用，自用的话还不如自己写一个协议  
    支持选密码套件，支持流式处理而不是数据包  
  WrappedSocket socket的NIO包装 （用DirectAccessor干了native方法）  
  Pipeline 管道处理模式 (未测试)  
  Gay 简单版本管理系统，我是拿来做增量更新的  
    这个名字？ Gayhub     2333333  
  
### roj.opengl  
  前置: LWJGL  
  字体渲染器  
  自己做的破烂游戏  
    其实不如说是个测试工具，math里的不少算法就用它测试，直观极了  
  
### roj.pe  
  PE文件格式 (.exe .dll)  
  todo: DOS头,  unix的ELF  
  
### roj.reflect  
  EnumHelper,  修改Enum字段  
  DirectAccessor, 实现高效率的‘反射’操作  
    不管是新建实例，还是访问字段，还是调用方法，它都可以帮你解决  
  
    首先，我们要一个接口, 里面定义一些方法吧  
    不，先来需求，我要给玩家发标题，以下是假的代码  
      
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
      
    这本来是个很简单的事... 但是如果游戏有多个版本, 每个版本的类名都不一样....  
    你可以用反射, 效率损失... 因为不频繁也可以忍受  
    你还可以给每个游戏版本建一个类, 共同实现一个接口  
    你也可以用 DirectAccessor  
    由于你不能直接写出（用到）方法的参数，因为它们也会随着游戏版本改变，你需要使用 all-object 模式, 这个模式的有关信息可以在代码注释中找到  
    它的特点是所有非基本类型都需要表示为Object  
      
    DirectAccessor可以在内部保存0至多个对象,  
    使用makeCache(Class<?> targetClass, String name, int methodFlag)创建  
    targetClass为对象的类  
    methodFlag: 创建哪些方法:   
      1: getter  
      2: setter  
      4: clear  
      8: 检测方法的存在  
    使用useCache(String name)选择, null取消  
      
    interface Helper {  
       Object getConn(Object player);  
       void sendPacket(Object conn, Object packet);  
       Object getTitlePacket(Object title);  
    }  
      
    然后 使用 DirectAccessor.builder(Helper.class) 获得一个构建器  
    接下来给它添加字段 （注，这里都是同名方法里参数最多的那个)  
      access(Class<?> target, String[] fields, String[] getters, String[] setters)  
      此方法用来访问字段  
      target是字段位于的class  
      fields是它的名字 (字符串数组，下同)  
      getters是在Helper中它的getter（得到值的方法）的名字, 一项或整体可以为null 不构建对应的getter  
      setters是setter(设置值的方法)的名字, 一项或整体可以为null 不构建对应的setter  
      所以:  
      b.access(Class.forName(version + "Player"), ["conn"], ["getConn"], [null]);  
        
      接下来是方法  
      b.delegate(Class<?> target, String[] methodNames, @Nullable IBitSet flags, String[] selfNames, List<Class<?>[]> fuzzyMode);  
      target,methodNames,selfNames跳过  
        不懂的话在大写字母前面拆开成多个单词去翻译  
      flags可以理解为BitSet，啊又是轮子  
        也就是boolean[]  
        当methodNames的第i项是true时，代表使用‘直接’访问  
          也就是方法静态绑定到target  
          不懂这是啥的话传入DirectAccessor.EMPTY_BITS  
      fuzzyMode (List<Class<?>[]>) 指的是 all-object 模式  
        为null: 不启用  
        为空列表: 模糊匹配  
        为 长 targetMethodNames.length 的列表: 其中为null的项模糊匹配，否则精确匹配  
          
      all-object模式：函数的参数和返回值中不是基本类型的部分全为Object  
         模糊匹配：匹配参数个数，基本和非基本类型的位置，可能会重复，这时候需要手动指定参数的类型 (精确)  
          eg:  
            void test(int a, String b);  
            void test(int a, List<String> b);  
        
      所以：  
        b.delegate(Class.forName(version + "Connection"), ["sendPacket"], EMPTY_BITS, ["sendPacket"], Collections.emptyList());  
        
        
      最后是构造器:  
        b.construct(Class<?> target, String[] names, List<Class<?>[]> fuzzy)  
        没啥好讲的了  
        b.construct(Class.forName(version + "TitlePacket"), ["getTitlePacket"], Collections.emptyList())  
        
      然后  
        Helper h = b.build();  
        保存好了  
        
      最后你暴露的方法:  
      public static void sendTitle(AbstractPlayer player, String title) {  
         h.sendPacket(h.getConn(player), h.getTitlePacket(title));  
      }  
  
### roj.terrain  
  地形生成器, WIP  
  
### roj.text  
  ACalendar，又一个日历，提供: prettyTime,  formatDate  
  CharList 又一个SB  
  LoggingStream  
  Placeholder 使用 {xx} 标识变量并从map中替换  
    Placeholder.assign('{',  '}', “您是第{count}位客户！”).replace(  
    	Collections.singletonMap("count", "8848")  
    ) == “您是第8848位客户！”  
  SimpleLineReader 按行读取  
  StringPool 类似常量池，存储N多字符串  
  TextUtil  
    String toFixed(double n,  int length)  
    escape() / unescape()  
    dumpBytes(byte[] b,  int off,  int len)  
    join(String[] arr,  String rope)  
    split(List<String> container, CharSequence seq, char/CharSequence rope);  
  UTFCoder 编码/解编码UTF-8  
  
### roj.ui  
  可选前置: JAnsi  
  CmdUtil 给控制台来点颜色看看！  
  
### roj.util  
  ByteList，能扩展的ByteBuffer  
  ComboRandom，多个种子的random  
  FastThreadLocal 空间换时间  
  GIFDecoder 解码GIF文件  
</pre>  
  
## About English version  
Firstly, I dont think this tiny library will be found by a foreigner,   
since mostly function of this library was implemented by other libraries.   
Secondly, I spent an afternoon writing this readme, I'm tired now,    
maybe there will be an English version a few months(years, LOL) later.   
Thirdly, my English score is only 120/150, and the poorest section is composition,   
so I have no heart to write such a big document in English.   
  
## 最后吓吓你 (这可不是骗你)  
我今年高考  