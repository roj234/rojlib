# rojlib
Unfortunately, another wheel comes.<br>
除了fmd组件，其他组件无依赖<br>
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
  EnumHelper: add remove modify Enum class<br>
  NativeMemcpy<br>
  ReflectionUtils: Basic reflection<br>
  DirectFieldAccess: generate a class to direct modify field<br>
  PackagePrivateProxy: generate a class to replace package-private methods<br>
  Direct [Constructor / Method] Access: generate a class to<br>
    Direct instance creation<br>
    Call static method<br>
    Call non static method: <br>
      by provide instance in parameter <br>
      or a field setted by function (cache)<br>
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