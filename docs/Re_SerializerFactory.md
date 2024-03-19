
# 任意对象的 安全* 序列化解决方案
* 不使用反射
* 任意对象
* 通过泛型推断目标类型
* 安全看你指的是什么了...
* 如果是防止不知名类的反序列化
* * 只要不开启动态模式就行，getInstance默认不开启
* 或者是...循环引用不会出问题
* * OBJECT_POOL

## 注意事项
### ToMap
像这样
```java
class T { Object x = new byte[2]; }  
```
如果你有一个字段类型是Object的数组，字符串（或任何不该序列化成Mapping的）  
它们会在序列化之后变成Mapping以避免类型丢失
```json
{"x":{"==":"[B","v":[0,0]}}
```

### Set的序列化
开启对象池后，任何Set都会在反序列化后变成MyHashSet (Identity Hash)  
这可能会造成一些问题，但是不这么做可能反序列化完成之前就会崩溃  

## 标记(flag):
* `GENERATE`        对未知的class自动生成序列化器
* `CHECK_INTERFACE` 检查实现的接口是否有序列化器
* `CHECK_PARENT`    检查父类是否有序列化器
* `NO_CONSTRUCTOR`  不调用&lt;init&gt;
* `SAFE`            扩展安全检查  检查字段访问权限，而不是使用unsafe绕过
* `OBJECT_POOL`     对象池   使用一个ID来替换已存在的对象
* `SERIALIZE_PARENT`**序列化父类**   不能与CHECK_PARENT共用

### 动态模式: 根据对象的class来序列化
* `ALLOW_DYNAMIC`   允许动态模式  仅应用到无法确定类型的字段
* `PREFER_DYNAMIC`  优先动态模式  禁用泛型推断

如果序列化的类满足任一条件
 * 有Object (包括泛型擦除的Object，自动泛型序列化目前仅支持Collection和Map)
 * 是除了 CharSequence Number List Set Collection Map 之外的接口
 * 是抽象类  

那么需要启用ALLOW_DYNAMIC，否则会报错，因为无法确定到一个具体的类  
下面代码中，我启用了ALLOW_DYNAMIC，因为测试的Pojo类中有一个Object（Map的泛型）

```java

import roj.config.ConfigMaster;
import roj.config.auto.*;
import roj.config.serial.ToJson;
import roj.text.CharList;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Map;

public class Test {
	public static void main(String[] args) throws Exception {
		SerializerFactory man = SerializerFactory.getInstance(
			SerializerFactory.GENERATE | SerializerFactory.CHECK_INTERFACE | SerializerFactory.CHECK_PARENT | SerializerFactory.ALLOW_DYNAMIC);
		// 自定义序列化方式(使用@As调用)
		Serializers.registerAsRGB(man);
		// 自定义序列化器(不一定要匿名类)
		// 方法名称也不重要 参数和返回值才重要
		man.add(Charset.class, new Object() {
			public String serializeMyObject(Charset cs) {return cs.name();}

			public Charset deserMyObj(String s) {return Charset.forName(s);}
		});

		// 生成一个序列化器
		Serializer<Pojo> adapter = man.serializer(Pojo.class);

		Pojo p = new Pojo();
		p.color = 0xAABBCC;
		p.charset = StandardCharsets.UTF_8;
		p.map = Map.of("114514", Map.of("1919810", 23333L));

		// simple
		ConfigMaster.YAML.writeObject(p, adapter, new File("C:\\test.yml"));
		p = ConfigMaster.YAML.readObject(adapter, new File("C:\\test.yml"));

		// or CVisitor
		ToJson ser = new ToJson();
		adapter.write(ser, p);
		CharList json = ser.getValue();
		System.out.println(json);

		System.out.println(adapter.read(new CCJson(), json, 0));
	}

	public static class Pojo {
		// 自定义序列化方式
		@As("rgb")
		// 自定义序列化名称
		@Name("myColor")
		private int color;
		// 通过getter或setter来访问字段
		@Via(get = "getCharset", set = "setCharset")
		public Charset charset;
		// 支持任意对象和多层泛型
		// 字段类型为接口和抽象类时，会用ObjAny序列化对象，会使用==表示对象的class
		// 如果要保留这个Map的类型，那就（1）字段改成HashMap(具体)或者（2）开启DYNAMIC
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
}
```