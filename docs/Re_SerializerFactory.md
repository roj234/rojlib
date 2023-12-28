
# 任意对象的安全序列化解决方案
* 不使用反射
* 任意对象
* 通过泛型推断目标类型

标记(flag):
* `GENERATE`        对未知的class自动生成序列化器
* `CHECK_INTERFACE` 检查实现的接口是否有序列化器
* `CHECK_PARENT`    检查父类是否有序列化器
* `NO_CONSTRUCTOR`  不调用&lt;init&gt;
* `SAFE`            扩展安全检查  检查字段访问权限，而不是使用unsafe绕过
* `SERIALIZE_PARENT`**序列化父类**   不能与CHECK_PARENT共用


* 动态模式: 根据对象的class来序列化
* `ALLOW_DYNAMIC`   允许动态模式  仅应用到无法确定类型的字段
* `PREFER_DYNAMIC`  优先动态模式  禁用泛型推断
* `FORCE_DYNAMIC`   强制动态模式  对所有字段启用

```java

import roj.config.ConfigMaster;
import roj.config.serial.*;
import roj.text.CharList;

import java.io.File;
import java.nio.charset.Charset;

public class Test {
	public static void main(String[] args) throws Exception {
		// 这么设计是为了方便Unsafe绕过字段访问权限
		SerializerFactory man = Serializers.newSerializerFactory();
		// 自定义序列化方式(使用@As调用)
		Serializers.registerAsRGB(man);
		// 自定义序列化器(不一定要匿名类)
		// 方法名称也不重要 参数和返回值才重要
		man.register(Charset.class, new Object() {
			public String serializeMyObject(Charset cs) {return cs.name();}

			public Charset deserMyObj(String s) {return Charset.forName(s);}
		});

		// 生成一个序列化器
		CAdapter<Pojo> adapter = man.adapter(Pojo.class);

		Pojo p = new Pojo();
		p.color = 0xAABBCC;
		p.charset = StandardCharsets.UTF_8;
		p.map = Collections.singletonMap("114514", Collections.singletonMap("1919810", 23333L));

		// simple
		ConfigMaster.write(p, "C:\\test.yml", "YAML", adapter);
		p = ConfigMaster.adapt(adapter, new File("C:\\test.yml"));

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