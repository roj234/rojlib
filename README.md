# rojlib
Unfortunately, another wheel comes.<br>
除了fmd组件，其他组件无依赖<br>
PS README解析后极其难看，你可以看raw...
<br>
# Catalog
roj.asm: 自己写的ASM, 正在制作StackFrame计算and流式处理 (目前只支持树结构)<br>
roj.collect: 集合们, 包括:<br>
  不缓存hash的map，适合String做key(它自己就缓存了)<br>
  WeakHashSet<br>
  环形缓冲区<br>
  BitSet复刻版<br>
  BiMap复刻版<br>
  基本类型Map<br>
  FindSet/FindMap:<br>
    代码：<br>
    static class A {<br>
      String name;<br>
      Data[] aLotData;<br>
      <br>
      @ hashCode: name<br>
      @ equals: name<br>
    }<br>
    <br>
    A a = new A("name", data, data, data, data);<br>
    set.add(a);<br>
    <br>
    A b = new A("name");<br>
    // a == set.find(b)<br>
    // 略微省点内存吧<br>
  ConcatedCollection 合并一些集合<br>
  ArrayIterator 数组迭代器<br>
  二叉堆<br>
  Stack<br>
  SortHashTrieMap: <br>
    先排序，再检查 O(m * n^2) => O(n)<br>
    any a = [c, w, b]<br>
    // 如何保证a中有c, b, w?<br>
    // 如何使用以后再补<br>
    <br>
  字典树(前缀)<br>
roj.concurrent<br>
  ConcurrentFindMap (WIP)<br>
  原子搞的自旋不公平锁<br>
  线程池 (高级功能再说), Executor and Task<br>
roj.config<br>
  JSON5 YAML XML 解释器<br>
    对象反序列化不够方便<br>
    三个可以转换到统一格式<br>
    支持dot-get: a.b.c<br>
    非常人性化的错误<br>
  制作中:<br>
    TOML CSS<br>
roj.fbt<br>
  FixedBinaryTag 定长数据格式，给啥用呢... db吧<br>
roj.io<br>
  多线程下载器<br>
  缓存格式<br>
  BOMInputStream<br>
  非阻塞Util (用于Oracle JVM)<br>
  StreamingCharSequence (UTF-8, from InputStream)<br>
roj.kscript<br>
  原创js解释器<br>
    编译执行 (wip)<br>
    并没有完全支持ECMAScript<br>
    基本类型还没加函数: charAt之类的<br>
    尾调用优化<br>
roj.math<br>
  向量，矩阵，多边形，回归（wip）<br>
  XXTea算法<br>
  柏林噪声<br>
  MutableInt<br>
  ComparableVersion<br>
roj.mod (依赖: md_5.SpecialSource, 垃圾forge)<br>
  FMD的源代码<br>
roj.net<br>
  Http Server / Client<br>
  UDP Server / Client<br>
roj.reflect<br>
  EnumHelper<br>
  PackagePrivateProxy: 生成一个子类用于调用package-private的方法<br>
<br>
  从现在开始你可以使用 DirectAccessor 实现高效率的‘反射’操作了<br>
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
roj.text<br>
  Custom StringBuilder<br>
  Calendar<br>
  Char to Pinyin<br>
  String cache pool<br>
  Fast splitting<br>
  SimpleLineReader<br>
roj.util<br>
  ByteWriter, Reader and List:<br>
    readInt, readIntelInt, readUTF8<br>
    readUseDelegationByteList<br>
  Array.shuffle<br>
  Base64, MD5<br>
  GIF Decoder<br>
  computeIfAbsent.suppliers<br>
  Idx (计数器)<br>
  OS, Pid<br>
  TCO 递归优化<br>
<br>
# TODO
class混淆器<br>
MutableBigInteger<br>
<br>
修BUG<br>
<br>
KScript.Jit<br>
LAVAC<br>
<br>