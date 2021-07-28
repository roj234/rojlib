# rojlib
Unfortunately, another wheel comes.
除了fmd组件，其他组件无依赖哦
喵喵喵

# Catalog
roj.asm: 自己写的ASM, 正在制作StackFrame计算and流式处理 (目前只支持树结构)
roj.collect: 集合们, 包括:
	不缓存hash的map，适合String做key(它自己就缓存了)
	WeakHashSet
	环形缓冲区
	BitSet复刻版
	BiMap复刻版
	基本类型Map
	FindSet/FindMap:
		代码：
		static class A {
			String name;
			Data[] aLotData;
			
			@ hashCode: name
			@ equals: name
		}
		
		A a = new A("name", data, data, data, data);
		set.add(a);
		
		A b = new A("name");
		// a == set.find(b)
		// 略微省点内存吧
	ConcatedCollection 合并一些集合
	ArrayIterator 数组迭代器
	二叉堆
	Stack
	SortHashTrieMap: 
		先排序，再检查 O(m * n^2) => O(n)
		any a = [c, w, b]
		// 如何保证a中有c, b, w?
		// 如何使用以后再补
		
	字典树(前缀)
roj.concurrent
	ConcurrentFindMap (WIP)
	原子搞的自旋不公平锁
	线程池 (高级功能再说), Executor and Task
roj.config
	JSON5 YAML XML 解释器
		对象反序列化不够方便
		三个可以转换到统一格式
		支持dot-get: a.b.c
		非常人性化的错误
	制作中:
		TOML CSS
roj.fbt
	FixedBinaryTag 定长数据格式，给啥用呢... db吧
roj.io
	多线程下载器
	缓存格式
	BOMInputStream
	非阻塞Util (用于Oracle JVM)
	StreamingCharSequence (UTF-8, from InputStream)
roj.kscript
	原创js解释器
		编译执行 (wip)
		并没有完全支持ECMAScript
		基本类型还没加函数: charAt之类的
		尾调用优化
roj.math
	向量，矩阵，多边形，回归（wip）
	XXTea算法
	柏林噪声
	MutableInt
	ComparableVersion
roj.mod (依赖: md_5.SpecialSource, 垃圾forge)
	FMD的源代码
roj.net
	Http Server / Client
	UDP Server / Client
roj.reflect
	EnumHelper: add remove modify Enum class
	NativeMemcpy
	ReflectionUtils: Basic reflection
	DirectFieldAccess: generate a class to direct modify field
	PackagePrivateProxy: generate a class to replace package-private methods
	Direct [Constructor / Method] Access: generate a class to
		Direct instance creation
		Call static method
		Call non static method: 
			by provide instance in parameter 
			or a field setted by function (cache)
roj.text
	Custom StringBuilder
	Calendar
	Char to Pinyin
	String cache pool
	Fast splitting
	SimpleLineReader
roj.util
	ByteWriter, Reader and List:
		readInt, readIntelInt, readUTF8
		readUseDelegationByteList
	Array.shuffle
	Base64, MD5
	GIF Decoder
	computeIfAbsent.suppliers
	Idx (计数器)
	OS, Pid
	TCO 递归优化

# TODO
class混淆器
MutableBigInteger

修BUG

KScript.Jit
LAVAC
