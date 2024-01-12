# Java8-21通杀的高性能反射解决方案
## 优点
 * [x] get/set field
 * [x] invoke method
 * [x] new object
 * [x] 无需改动VM参数，可应对未来任何需求
 * [x] ASM生成，几乎不损失性能
 * [x] 无法访问Field Method甚至Class对象也可以构建
## 缺点
 * 需要建立一个接口类
 * 无视所有安全保护措施 (可以使用roj.reflect.ILSecurityManager缓解)
 * （已解决请无视）在Java17或更高版本时，需要添加VM参数 --add-opens=java.base/java.lang=ALL-UNNAMED
## 使用方法

### 假设有个需求：用NMS给玩家发标题
这本来是个很简单的事... 但是如果游戏有多个版本, 每个版本的类名都不一样....
* 你可以用反射, 效率损失... 因为不频繁也可以忍受
* 你还可以给每个游戏版本建一个类, 共同实现一个接口
* 你也可以用 `DirectAccessor`

#### fake NMS code
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
      
    class TitlePacket implements Packet {  
      public TitlePacket(String title, int time) {  
        // ...  
      }  
    }
```

*在java17或更高版本需要添加JVM参数（JVM太安全了）

### 定义接口
由于不能直接写出方法参数的类型（它们会随着NMS版本改变），你需要使用模糊匹配(all-object)模式, 它的特点是所有非基本类型都需要表示为Object
如果不使用模糊匹配模式，可以和调用的参数一样
```java
interface H {  
    Object getConn(Object player);  
    void sendPacket(Object conn, Object packet);  
    Object getTitlePacket(Object title, int time);  
}
```
### 生成代码
```java 
import roj.reflect.DirectAccessor;

class Example {
	static final H h;

	static {
		String version = getNMSPackage(); // for example: net.minecraft.server.v1_12_R2.
		h = DirectAccessor.builder(H.class)
		    .access(Class.forName(version+"Player"), {"conn"}, {"getConn"}, null)
		    .delegate(Class.forName(version+"Connection"), {"sendPacket"}, DirectAccessor.EMPTY_BITS,  {"sendPacket"}, Collections.emptyList())
		    .construct(Class.forName(version+"TitlePacket"), {"getTitlePacket"}, Collections.emptyList())
		    .build();
	}

	public static void sendTitle(AbstractPlayer player, String title) {
		h.sendPacket(h.getConn(player), h.getTitlePacket(title, 114514));
	}
}
```
下面展开讲

### 访问字段 （介绍参数最多的方法，下同)
`access(Class<?> target, String[] fields, String[] getters, String[] setters)`  
此方法用来访问字段
* target是字段位于的class
* fields是字段的名字的列表
* getters是在`H`中它的getter（得到值的方法）的名字, 一项或整个数组可以为null 不构建对应的getter
* setters是setter(设置值的方法)的名字, 一项或整个数组可以为null 不构建对应的setter

### 调用方法
`delegate(Class<?> target, String[] methodNames, MyBitSet flags, String[] selfNames, List<Class<?>[]> fuzzyMode)`

大部分参数同上，介绍不同的：
* flags: 第i项是true时，代表使用‘直接’访问  
  也就是静态绑定(INVOKESPECIAL)到target  
  不懂静态绑定是啥的话传入`DirectAccessor.EMPTY_BITS`


* fuzzyMode 指的是 模糊匹配(all-object) 模式  
  `null`: 不启用  
  `空列表`: 模糊匹配  
  `长methodNames.length的列表`: 其中为null的项模糊匹配，否则用这项中的Class<?>[]表示的类精确匹配  
  <br>
  模糊匹配：函数的参数和返回值中不是基本类型的部分全为Object  
  只匹配参数个数、基本和非基本类型的位置，可能会重复，这时候需要手动指定参数的类型 (精确)  
  例如:
```java
void test(int a, String b);  
void test(int a, List<String> b);
```


### 构造器
`construct(Class<?> target, String[] names, List<Class<?>[]> fuzzy)`  
参数都介绍过了

### 其他方法
`i_`开头的方法以绕过需要Class实例的限制，直接生成字节码  
`unchecked()`关闭类型检查  
生成的类无视权限控制 (但是不能写final字段)  