# rua
好吧上面写的一大坨最后还是被我干掉了
不过他们还在提交历史里...

## 列出我最喜欢的几个东西吧
### roj.asm
ASM, 里面有半成品反编译器，【内核汉化】工具，注解处理器，class混淆器
它可以计算50%以上(好吧，起码是测试样例里面的)的class的StackFrame
因为它之前不行，所以我就像游戏里的LOD一样分了等级，同时实现统一接口
roj.asm.tree.IClass, rom.asm.tree.MoFNode,  roj.asm.tree.IMethod
等级0：粗略信息 Parser.simpleData，包含类名，继承，实现的接口
等级1：权限信息 Parser.accessData,  除了上面的只读信息还有方法和字段，同时可以修改class、方法、字段的访问权限
常量信息: 解析整个常量池，自此开始，那些信息不再是只读的了，一般在这里，通过这些接口，可以选择性解析方法，以此提高速度
               简单的操作更是完全不需要解析Code属性
完整信息：那就和ASM差不多了

速度我没比较，【完整信息】一级大概是比不上ASM
   但是<font color="red">问题是：大部分ASM操作（起码是我遇到的），都不要用到这一等级，常量信息完全够了</font>

此外，我现在还写了个Visitor模式（WIP），速度没算，起码，比起ASM的容易理解

### roj.collect
包含了各种我写的集合
比如基本类型的map
不保存hash，进而更省内存（对于String之类算hash简单或者有缓存）的Map

亮点之一：UnsortedMultiKeyMap<K, T, V> implements Map<List<K>,  V>
一个K对应多个T，但是只有一个【最关键的】T
方法：无序获取，也就是list中包含且仅包含了这些东西就可以获取到，不要求顺序
稍微修改就可以返回包含了，或者不完全包含的list

用途：无序合成

### roj.config
  JSON5 YAML XML 解析器
    对象反序列化不够方便<br>
    三个配置格式可以转换到统一格式<br>
    支持dot-get: a.b.c<br>
    非常人性化的错误<br>
  制作中:<br>
    TOML CSS<br>
 人性化的错误是我看着snakeyaml学的，确实很好，比如在JS的解释器和以后要写的Java编译器里会用到
 
### roj.io
  多线程下载器
  支持BOM的InputStream
  可变的ZIP文件，嘿，你不妨试试java如何对zip增量修改？
### roj.kscript
  js解释器, 不多说
  并没有完全支持ECMAScript
  基本类型还没加函数, 比如charAt
### roj.mod
  本来只是个替代垃圾ForgeGradle的模组开发工具
  不过我现在离不开它了
  功能：编译java，增量编译，屏蔽警告，热重载
  特点：速度很快，体积很小
  TODO：jar签名
### roj.net
  HTTP服务器，客户端，以及一个DNS服务器，还有内网穿透工具
### roj.reflect<br>
  EnumHelper,  修改Enum字段
  DirectAccessor,  像是我写的【jq类似物】一样，听到一句介绍写出来的东西，早期是DMA, DCA, DFA
  实现高效率的‘反射’操作了<br>
    不管是新建实例，还是访问字段，还是调用方法，它都可以帮你解决<br>
    <br>
    首先，我们要一个接口, 里面定义一些方法吧<br>
    不，先来需求，我要给玩家发标题，以下是假的代码<br>
    <br>
    class Player {<br>
      private Connection conn;<br>
    }<br>
    <br>
    class Connection {<br>
      public void sendPacket(Packet packet);<br>
    }<br>
    <br>
    interface Packet {<br>
       // ...<br>
    }<br>
    <br>
    class TitlePacket {<br>
      public TitlePacket(String title) {<br>
        // ...<br>
      }<br>
    }<br>
    <br>
    这本来是个很简单的事... 但是如果游戏有多个版本, 每个版本的类名都不一样....<br>
    你可以用反射, 效率损失... 因为不频繁也可以忍受<br>
    你还可以给每个游戏版本建一个类, 共同实现一个接口<br>
    你也可以用 DirectAccessor<br>
    由于你不能直接写出（用到）方法的参数，因为它们也会随着游戏版本改变，你需要使用 all-object 模式, 这个模式的有关信息可以在代码注释中找到<br>
    它的特点是所有非基本类型都需要表示为Object<br>
    <br>
    DirectAccessor可以在内部保存一个对象, (以后可能会增多)<br>
    也可以不保存, 这时候就需要在方法第一个参数前加上 Object<br>
    <br>
    interface Helper {<br>
       Object getConn(Object player);<br>
       void sendPacket(Object conn, Object packet);<br>
       Object getTitlePacket(Object title);<br>
    }<br>
    <br>
    然后 使用 DirectAccessor.builder(Helper.class) 获得一个构建器<br>
    接下来给它添加。。。 字段先吧 <br>
      （注，这里教的都是同名方法里参数最多的那个)<br>
      b.access(target, fieldNames, getterNames, setterNames, useCache)<br>
      此方法用来访问字段<br>
      target是字段位于的class<br>
      fieldName是它的名字 (字符串数组，下同)<br>
      getterNames是在Helper中它的getter（得到值的方法）的名字, 项可以为null 不构建对应的getter<br>
      setterName是setter(设置值的方法)的名字, 项可以为null 不构建对应的setter<br>
      useCache代表是否使用内部保存的对象<br>
      所以:<br>
      b.access(Class.forName(version + "Player"), ["conn"], ["getConn"], [null]);<br>
      <br>
      接下来是方法<br>
      b.delegate(target, targetMethodNames, invokeType, selfMethodNames, useCache, objectModes);<br>
      target,targetMethodNames,selfMethodNames,useCache这回就懂了吧, 跳过<br>
        不懂的话在大写字母前面拆开成多个单词去翻译<br>
      invokeType是一个"IBitSet"<br>
        你可以理解为BitSet，啊又是轮子<br>
        也就是boolean[]<br>
        当targetMethodNames的第i项访问这个数组是true时，代表使用‘直接’访问<br>
          也就是方法静态绑定到target<br>
          不懂这是啥的话传入DirectAccessor.EMPTY_BITS<br>
      objectModes (List<Class<?>[]>) 指的是 all-object 模式<br>
        为null: 不启用<br>
        为空列表: 模糊匹配<br>
        为 长 targetMethodNames.length 的列表: 其中为null的项模糊匹配，否则精确匹配<br>
        <br>
        all-object模式：函数的参数和返回值中不是基本类型的部分全为Object<br>
           模糊匹配：匹配参数个数，基本和非基本类型的位置，可能会重复，这时候需要手动指定参数的类型 (精确)<br>
            eg:<br>
              void test(int a, String b);<br>
              void test(int a, List<String> b);<br>
      <br>
      所以：<br>
        b.delegate(Class.forName(version + "Connection"), ["sendPacket"], EMPTY_BITS, ["sendPacket"], false, Collections.emptyList());<br>
      <br>
      <br>
      最后是构造器:<br>
        b.construct(target, objectModes, methodNames)<br>
        没啥好讲的了<br>
        b.construct(Class.forName(version + "TitlePacket"), Collections.emptyList(), ["getTitlePacket"])<br>
      <br>
      然后<br>
        Helper h = b.build();<br>
        保存好了<br>
      <br>
      最后你暴露的方法:<br>
      public static void sendTitle(AbstractPlayer player, String title) {<br>
         h.sendPacket(h.getConn(player), h.getTitlePacket(title));<br>
      }<br>
        <br>
      <br>
### roj.lavac
  计划中的java编译器
  WIP