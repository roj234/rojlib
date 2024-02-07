package roj.config.serial;

import org.jetbrains.annotations.Nullable;
import roj.asm.Parser;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstInt;
import roj.asm.cp.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.AttrString;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.*;
import roj.asm.util.Attributes;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.LongByeBye;
import roj.asm.visitor.SwitchSegment;
import roj.collect.*;
import roj.config.BinaryParser;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.reflect.FastInit;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import static roj.asm.Opcodes.*;

/**
 * 得益于VMInternals兼容了Java8-21，我可以放心的使用weak class了
 *
 * @author Roj233
 * @version 2.3
 * @since 2022/1/11 17:49
 */
public final class SerializerFactory {
	private static final ReentrantLock lock = new ReentrantLock();
	private static final Map<String, Adapter> GENERATED = new MyHashMap<>();
	private static final Map<String, Adapter> DEFAULT = new MyHashMap<>();
	static {
		PrimObj STR = new PrimObj(Type.CLASS);
		DEFAULT.put("java/lang/CharSequence", STR);
		DEFAULT.put("java/lang/String", STR);
		DEFAULT.put("java/lang/Long", new PrimObj(Type.LONG));
		DEFAULT.put("java/lang/Double", new PrimObj(Type.DOUBLE));
		DEFAULT.put("java/lang/Float", new PrimObj(Type.FLOAT));
		PrimObj INT = new PrimObj(Type.INT);
		DEFAULT.put("java/lang/Integer", INT);
		DEFAULT.put("java/lang/Number", INT);
		DEFAULT.put("java/lang/Short", new PrimObj(Type.SHORT));
		DEFAULT.put("java/lang/Character", new PrimObj(Type.CHAR));
		DEFAULT.put("java/lang/Byte", new PrimObj(Type.BYTE));
	}

	private final Map<String, Adapter> localRegistry = new MyHashMap<>(DEFAULT);
	private final Map<String, AsType> asTypes = new MyHashMap<>();
	static final class AsType {
		final String name;
		final Type output;
		final MethodNode reader, writer;
		final Object ref;
		final boolean user;

		public AsType(String name, MethodNode writer, MethodNode reader, Object o, boolean user_writer) {
			this.name = name;
			this.ref = o;
			this.reader = reader;
			this.writer = writer;
			this.output = reader.parameters().get(0);
			this.user = user_writer;
		}

		public String klass() { return reader.ownerClass(); }
	}

	private static final int PREFER_DYNAMIC_INTERNAL = 32, FORCE_DYNAMIC_INTERNAL = 64, APPLY_DYNAMIC_INTERNAL = 128;
	public static final int
		GENERATE = 1,
		CHECK_INTERFACE = 2,
		CHECK_PARENT = 4,
		NO_CONSTRUCTOR = 8,
		ALLOW_DYNAMIC = 16,
		PREFER_DYNAMIC = ALLOW_DYNAMIC|PREFER_DYNAMIC_INTERNAL,
		FORCE_DYNAMIC = PREFER_DYNAMIC|FORCE_DYNAMIC_INTERNAL,
		SAFE = 256,
		SERIALIZE_PARENT = 512;

	public int flag;
	public ToIntFunction<Class<?>> flagGetter;
	private int flagFor(Class<?> className) {
		return flagGetter == null ? flag : flagGetter.applyAsInt(className);
	}

	public SerializerFactory(int flag) {
		this.flag = flag;

		ObjAny any = new ObjAny(this);

		// 只是引用，加入分号避免被覆盖
		localRegistry.put(";Obj", any);

		localRegistry.put("java/util/Map", new MapSer(any, null));
		CollectionSer LIST = new CollectionSer(any, false, null);
		localRegistry.put("java/util/List", LIST);
		localRegistry.put("java/util/Set", new CollectionSer(any, true, null));
		localRegistry.put("java/util/Collection", LIST);
	}

	public <T> T deserialize(Class<T> type, File file) throws IOException, ParseException {
		return deserialize(adapter(type), IOUtil.extensionName(file.getName()), file);
	}
	@SuppressWarnings("unchecked")
	public <T> T deserialize(IType type, File file) throws IOException, ParseException {
		return (T) deserialize(adapter(type), IOUtil.extensionName(file.getName()), file);
	}
	public <T> T deserialize(CAdapter<T> adapter, File file) throws IOException, ParseException {
		return deserialize(adapter, IOUtil.extensionName(file.getName()), file);
	}
	// target can be file, outputstream or DynByteBuf
	public <T> T deserialize(CAdapter<T> adapter, String format, Object from) throws IOException, ParseException {
		ConfigMaster man = new ConfigMaster(format);
		BinaryParser bp = man.binParser();
		if (from instanceof File f) {
			bp.parseRaw(adapter, f, man.flag());
		} else if (from instanceof InputStream in) {
			bp.parseRaw(adapter, in, man.flag());
		} else if (from instanceof DynByteBuf buf) {
			bp.parseRaw(adapter, buf, man.flag());
		} else {
			throw new IllegalArgumentException("unknown from type "+from.getClass().getName());
		}
		if (!adapter.finished()) throw new CorruptedInputException("!finished()");
		return adapter.result();
	}

	// 方便预分配空间，并且不用重新转换 (其实就是给我的TrieTreeSet和TrieTree用)
	public <T extends Collection<?>> SerializerFactory customList(Class<T> cls, IntFunction<T> provider) {
		Adapter ser = new CollectionSer(localRegistry.get(";Obj"), false, Helpers.cast(provider));
		synchronized (localRegistry) {
			localRegistry.put(cls.getName().replace('.', '/'), ser);
		}
		return this;
	}
	public <T extends Map<String, ?>> SerializerFactory customMap(Class<T> cls, IntFunction<T> provider) {
		Adapter ser = new MapSer(localRegistry.get(";Obj"), Helpers.cast(provider));
		synchronized (localRegistry) {
			localRegistry.put(cls.getName().replace('.', '/'), ser);
		}
		return this;
	}

	public SerializerFactory auto(Class<?>... type) {
		for (Class<?> cl : type) auto(cl);
		return this;
	}
	public SerializerFactory auto(Class<?> type) {
		String name = type.getName().replace('.', '/');
		Adapter ser;

		int _flag = flagFor(type);
		name = (_flag&(SAFE|NO_CONSTRUCTOR|SERIALIZE_PARENT))+"|"+name;
		generate:
		if (!GENERATED.containsKey(name)) {
			lock.lock();
			try {
				if (GENERATED.containsKey(name)) break generate;

				if (type.isEnum()) {
					ser = new EnumSer(type);
				} else if (type.getComponentType() != null) {
					ser = array(type);
				} else {
					ser = klass(type, _flag);
				}

				GENERATED.put(name, ser);
			} finally {
				lock.unlock();
			}

			if (ser instanceof GenAdapter) {
				((GenAdapter) ser).init(new IntBiMap<>(fieldIds), optionalEx);
			}
		}

		return this;
	}

	/**
	 * 同下，除了自动选择W/R
	 * @see SerializerFactory#register(Class, Object, String, String)
	 */
	public SerializerFactory register(Class<?> type, Object adapter) {
		if (type.isPrimitive() || type == String.class) throw new IllegalStateException("type不能是基本类型或字符串");

		// A: auto
		String name = "U|A|"+type.getName()+"|"+adapter.getClass().getName();
		Adapter ser = GENERATED.get(name);
		if (ser == null) {
			ConstantData data = Parser.parse(adapter.getClass());
			if (data == null) throw new IllegalArgumentException("无法获取"+adapter+"的类文件");

			Type clsType = TypeHelper.class2type(type);
			MethodNode writer = null, reader = null;
			for (MethodNode mn : data.methods) {
				List<Type> par = mn.parameters();
				if (par.size() != 1) continue;

				Type ret = mn.returnType();
				if (par.get(0).equals(clsType) && ret.type != Type.VOID) {
					if (writer != null) throw new IllegalArgumentException("找到了多个符合的writer: " + writer + " / " + mn);
					writer = mn;
				} else if (ret.equals(clsType)) {
					if (reader != null) throw new IllegalArgumentException("找到了多个符合的reader: " + reader + " / " + mn);
					reader = mn;
				}
			}

			if (reader == null) throw new IllegalArgumentException("没找到reader");
			if (writer == null) throw new IllegalArgumentException("没找到writer");

			lock.lock();
			try {
				if ((ser = GENERATED.get(name)) == null) {
					ser = user(data, writer, reader);
					GENERATED.put(name, ser);
				}
			} finally {
				lock.unlock();
			}
		}
		ser = (Adapter) ((GenAdapter)ser).clone();

		synchronized (localRegistry) {
			localRegistry.put(type.getName().replace('.', '/'), ser);
		}

		((GenAdapter) ser).secondaryInit(this, adapter);

		return this;
	}
	/**
	 * 注册该类型的适配器，在序列化时会转换成另一种类型(方法返回值) <BR>
	 * 和SerializerFactory绑定
	 * @param type 类型
	 * @param adapter 转换器实例，不支持.class
	 */
	public SerializerFactory register(Class<?> type, Object adapter, String writeMethod, String readMethod) {
		if (type.isPrimitive() || type == String.class) throw new IllegalStateException("type不能是基本类型或字符串");

		ConstantData data = Parser.parse(adapter.getClass());
		if (data == null) throw new IllegalArgumentException("无法获取"+adapter+"的类文件");

		int wid = data.getMethod(writeMethod), rid = data.getMethod(readMethod);
		MethodNode w = data.methods.get(wid), r = data.methods.get(rid);

		String name = "U|"+wid+"|"+rid+"|"+type.getName()+"|"+adapter.getClass().getName();
		Adapter ser = GENERATED.get(name);
		if (ser == null) {
			Type clsType = TypeHelper.class2type(type);
			for (MethodNode mn : data.methods) {
				List<Type> par = mn.parameters();
				if (par.size() != 1) continue;

				Type ret = mn.returnType();
				if (par.get(0).equals(clsType) && ret.type != Type.VOID) {
					if (mn == w) wid = -1;
				} else if (ret.equals(clsType)) {
					if (mn == r) rid = -1;
				}
			}

			if (rid >= 0) throw new IllegalArgumentException("reader不符合要求");
			if (wid >= 0) throw new IllegalArgumentException("writer不符合要求");

			lock.lock();
			try {
				if ((ser = GENERATED.get(name)) == null) {
					ser = user(data, w, r);
					GENERATED.put(name, ser);
				}
			} finally {
				lock.unlock();
			}
		}
		ser = (Adapter) ((GenAdapter)ser).clone();

		synchronized (localRegistry) {
			localRegistry.put(type.getName().replace('.', '/'), ser);
		}

		((GenAdapter) ser).secondaryInit(this, adapter);

		return this;
	}
	/**
	 * 同下，除了自动寻找下面doc中第一种例子的方法
	 * @see SerializerFactory#registerAsAdapter(String, Class, Object, String, String)
	 */
	public SerializerFactory registerAsAdapter(String name, Class<?> targetType, Object adapter) {
		ConstantData data = Parser.parse(adapter.getClass());
		if (data == null) throw new IllegalArgumentException("无法获取"+adapter+"的类文件");

		Type clsType = TypeHelper.class2type(targetType);
		MethodNode writer = null, reader = null;
		for (MethodNode mn : data.methods) {
			List<Type> par = mn.parameters();
			if (par.size() != 1) continue;

			Type ret = mn.returnType();
			if (par.get(0).equals(clsType) && ret.type != Type.VOID) {
				if (writer != null) throw new IllegalArgumentException("找到了多个符合的writer: " + writer + " / " + mn);
				writer = mn;
			} else if (ret.equals(clsType)) {
				if (reader != null) throw new IllegalArgumentException("找到了多个符合的reader: " + reader + " / " + mn);
				reader = mn;
			}
		}

		if (reader == null) throw new IllegalArgumentException("没找到reader");
		if (writer == null) throw new IllegalArgumentException("没找到writer");

		synchronized (asTypes) {
			AsType old = asTypes.putIfAbsent(name, new AsType(name, writer, reader, adapter, false));
			if (old != null) throw new IllegalArgumentException(name+"已存在");
		}
		return this;
	}
	/**
	 * 注册@{@link As}目标的转换器 <BR>
	 * 和SerializerFactory绑定 <pre>
	 * 转换方法两种方式参考:
	 *   {@link Serializers#writeHex(byte[])}
	 *   {@link Serializers#writeISO(long, CVisitor)}
	 * @param name 注解的value
	 * @param targetType 转换到的目标的类
	 * @param adapter 转换器实例，不支持.class
	 * @param writeMethod 转换方法名称(原类型 -> targetType)
	 * @param readMethod 反转换方法名称(targetType -> 原类型)
	 */
	public SerializerFactory registerAsAdapter(String name, Class<?> targetType, Object adapter, String writeMethod, String readMethod) {
		ConstantData data = Parser.parse(adapter.getClass());
		if (data == null) throw new IllegalArgumentException("无法获取"+adapter+"的类文件");

		MethodNode w = data.methods.get(data.getMethod(writeMethod)),
			r = data.methods.get(data.getMethod(readMethod));

		boolean user_writer = false;
		sp: {
			List<Type> wp = w.parameters(), rp = r.parameters();
			if (wp.get(0).equals(r.returnType()) && rp.get(0).equals(TypeHelper.class2type(targetType))) {
				if (rp.get(0).equals(w.returnType())) break sp;
				if (wp.size() == 2 && "roj/config/serial/CVisitor".equals(wp.get(1).getActualClass())) {
					if (!wp.get(0).isPrimitive()) throw new IllegalArgumentException("object output not support user");
					user_writer = true;
					break sp;
				}
			}
			throw new IllegalStateException("R/W param not mirrored");
		}

		synchronized (asTypes) {
			AsType old = asTypes.putIfAbsent(name, new AsType(name, w, r, adapter, user_writer));
			if (old != null) throw new IllegalArgumentException(name+"已存在");
		}
		return this;
	}

	public <T> CAdapter<T> adapter(Class<T> type) { return context(make(null, type, null, false)); }
	public CAdapter<?> adapter(IType generic) {
		Adapter root = get(generic);
		if (root == null) return Helpers.maybeNull();
		return context(root);
	}
	public <T> CAdapter<List<T>> listOf(Class<T> content) {
		Adapter root = get(Generic.parameterized(List.class, content));
		assert root != null;
		return context(root);
	}
	public <T> CAdapter<Map<String, T>> mapOf(Class<T> content) {
		Adapter root = get(Generic.parameterized(Map.class, String.class, content));
		assert root != null;
		return context(root);
	}
	private <T> CAdapter<T> context(Adapter root) { return Helpers.cast((flag&FORCE_DYNAMIC_INTERNAL) == 0 ? new AdaptContext(root) : new AdaptContextEx(root)); }

	final Object getAsType(String as) {
		AsType a = asTypes.get(as);
		if (a == null) throw new IllegalStateException("Missing as-type adapter " + as);
		return a.ref;
	}
	final Adapter get(Class<?> type) { return make(null, type, null, false); }
	final Adapter get(Class<?> type, String generic) {
		IType genericType = null;

		if ((flagFor(type) & PREFER_DYNAMIC_INTERNAL) != 0) {
			generic = null;
		} else if (generic != null) {
			genericType = Signature.parse(generic, Signature.FIELD).values.get(0);
		}

		Adapter ser = make(generic, type, (Generic) genericType, true);
		if (ser == null) ser = make(null, type, null, false);
		return ser;
	}
	@Nullable
	final Adapter get(IType generic) {
		Type type = generic.rawType();
		Class<?> klass;
		try {
			klass = type.toJavaClass();
		} catch (ClassNotFoundException e) {
			throw new TypeNotPresentException(generic.toDesc(), e);
		}

		if (type == generic || (flagFor(klass) & PREFER_DYNAMIC_INTERNAL) != 0) {
			return make(null, klass, null, true);
		} else {
			return make(generic.toDesc(), klass, (Generic) generic, true);
		}
	}
	final Adapter getByName(String name) { return make(name, null, null, false); }

	private static boolean isInvalid(Class<?> type) {
		if (type.getComponentType() != null) return false;
		if ((type.getModifiers() & (ACC_ABSTRACT|ACC_INTERFACE)) != 0) return true;
		return type == Object.class;
	}

	private Adapter make(String name, Class<?> type, Generic generic, boolean checking) {
		if ((flag & APPLY_DYNAMIC_INTERNAL) != 0) return localRegistry.get(";Obj");

		Adapter ser = localRegistry.get(name!=null?name:(name=type.getName().replace('.', '/')));
		if (ser != null) return ser;

		if (type == null) {
			try {
				type = Class.forName(name, false, SerializerFactory.class.getClassLoader());
			} catch (ClassNotFoundException e) {
				return Helpers.nonnull();
			}
		}

		if (generic != null) {
			ser = localRegistry.get(type.getName().replace('.', '/'));
			if (ser != null) return ser.withGenericType(this, generic.children);
		}

		int flag = flagFor(type);
		findExist:
		if ((flag & (CHECK_INTERFACE|CHECK_PARENT)) != 0) {
			CharList sb = new CharList();
			Class<?> c = type;
			while (true) {
				if ((flag & CHECK_INTERFACE) != 0) {
					for (Class<?> itf : c.getInterfaces()) {
						sb.clear();
						// noinspection all
						ser = localRegistry.get(sb.append(itf.getName()).replace('.', '/'));
						if (ser != null) break findExist;
					}
				}

				c = c.getSuperclass();
				if (c == null) break;

				if ((flag & CHECK_PARENT) != 0) {
					sb.clear();
					// noinspection all
					ser = localRegistry.get(sb.append(c.getName()).replace('.', '/'));
					if (ser != null) break findExist;
				}
			}
			sb._free();
		}

		if (ser != null) {
			ser = ser.inheritBy(this, type);
			if (generic != null) ser = ser.withGenericType(this, generic.children);
			synchronized (localRegistry) {
				localRegistry.put(name, ser);
			}
			return ser;
		}

		// 是否要移到377行？
		// 377行？在哪，所以这种东西以后还是别写了
		if ((flag & GENERATE) == 0) throw new IllegalArgumentException("未找到"+name+"的序列化器");

		if (isInvalid(type)) {
			if ((flag & ALLOW_DYNAMIC) != 0) return localRegistry.get(";Obj");

			if (checking) return null;
			throw new IllegalArgumentException(name+"无法被序列化(ALLOW_DYNAMIC)");
		}

		name = (flag&(SAFE|NO_CONSTRUCTOR|SERIALIZE_PARENT))+"|"+name;
		generate:
		if ((ser = GENERATED.get(name)) == null) {
			lock.lock();
			try {
				if ((ser = GENERATED.get(name)) != null) break generate;

				if (type.isEnum()) {
					ser = new EnumSer(type);
				} else if (type.getComponentType() != null) {
					ser = array(type);
				} else {
					ser = klass(type, flag);
				}

				GENERATED.put(name, ser);
			} finally {
				lock.unlock();
			}

			if (ser instanceof GenAdapter) {
				((GenAdapter) ser).init(new IntBiMap<>(fieldIds), optionalEx);
			}
		}

		if (ser instanceof GenAdapter) {
			ser = (Adapter) ((GenAdapter)ser).clone();
		}

		synchronized (localRegistry) {
			localRegistry.put(name, ser);
		}

		if (ser instanceof GenAdapter) {
			int prev = this.flag;
			if ((flag & FORCE_DYNAMIC_INTERNAL) != 0) {
				this.flag = prev | APPLY_DYNAMIC_INTERNAL;
			}

			try {
				((GenAdapter) ser).secondaryInit(this, null);
			} finally {
				this.flag = prev;
			}
		}

		return ser;
	}

	private Adapter array(Class<?> type) {
		type = type.getComponentType();
		if (type.isPrimitive()) {
			byte[] b;
			try {
				b = IOUtil.getResource("roj/config/serial/PrimArr.class");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			Type asmType = TypeHelper.class2type(type);
			Type methodType = asmType.nativeName().equals("I") ? Type.std(Type.INT) : asmType;
			ConstantData c = new LongByeBye(Type.LONG,asmType,methodType,asmType,true) {
				@Override
				protected String replaceName(String name, int from, Type to) {
					if (name.equals("putLong")) {
						if (Type.BYTE == asmType.type) {
							return "put";
						}
					}
					return super.replaceName(name, from, to);
				}
			}.generate(ByteList.wrap(b));

			switch (asmType.type) {
				case Type.LONG: addUpgrader(c, I2L); copyArrayRef(c, Type.LONG); break;
				case Type.FLOAT: addUpgrader(c, F2D); break;
				case Type.INT: copyArrayRef(c, Type.INT); break;
				case Type.BYTE: copyArrayRef(c, Type.BYTE); break;
			}

			c.putAttr(new AttrString("SourceFile", "Adapter@"+asmType));
			c.name("roj/config/serial/PAS$"+asmType);

			FastInit.prepare(c);
			return (Adapter) FastInit.make(c);
		} else {
			return new ObjArr(type, make(null, type, null, false));
		}
	}

	private static void copyArrayRef(ConstantData c, char type) {
		CodeWriter cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "read", "(Lroj/config/serial/AdaptContext;["+type+")V");
		cw.visitSize(2,3);
		cw.one(ALOAD_1);
		cw.one(ALOAD_2);
		cw.field(PUTFIELD, "roj/config/serial/AdaptContext", "ref", "Ljava/lang/Object;");
		cw.one(ALOAD_1);
		cw.one(ICONST_1);
		cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/AdaptContext", "popd", "(Z)V");
		cw.one(RETURN);
	}

	private static void addUpgrader(ConstantData c, byte code) {
		String orig = showOpcode(code);
		String id = orig.replace('L', 'J');

		CodeWriter ir = c.newMethod(ACC_PUBLIC|ACC_FINAL, "read", "(Lroj/config/serial/AdaptContext;"+id.charAt(0)+")V");
		ir.visitSize(3,3);
		ir.one(ALOAD_0);
		ir.one(ALOAD_1);
		ir.one((byte) opcodeByName().getInt(orig.charAt(0)+"LOAD_2"));
		ir.one(code);
		ir.invoke(DIRECT_IF_OVERRIDE, c.name, "read", "(Lroj/config/serial/AdaptContext;"+id.charAt(2)+")V");
		ir.one(RETURN);
	}

	private Adapter user(ConstantData data, MethodNode writer, MethodNode reader) {
		String klassIn = reader.returnType().getActualClass();
		if (klassIn == null) throw new IllegalArgumentException("覆盖"+reader.returnType()+"的序列化");

		begin();

		// region toString
		CodeWriter cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "toString", "()Ljava/lang/String;");
		cw.visitSize(1,1);
		String toString = "UserAdapter@"+writer.ownerClass();
		cw.ldc(new CstString(toString));
		c.putAttr(new AttrString("SourceFile", toString));
		cw.one(ARETURN);
		cw.finish();
		// endregion
		Type type = writer.returnType();
		String klassOut = type.getActualClass();

		int ua = c.newField(ACC_PRIVATE, "userAdapter", "L"+data.name+";");
		int ser;
		if (klassOut != null && !klassOut.equals("java/lang/String")) {
			String genSig;
			Signature signature = writer.parsedAttr(data.cp, Attribute.SIGNATURE);
			if (signature != null) {
				IType ret = signature.values.get(signature.values.size() - 1);
				genSig = ret.toDesc();
			} else genSig = null;

			ser = ser(klassOut, genSig);
		} else ser = -1;

		// region read()
		String methodType = type.getActualType() == Type.CLASS ? "Ljava/lang/Object;" : type.toDesc();
		cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "read", "(Lroj/config/serial/AdaptContext;"+methodType+")V");
		cw.visitSize(3,3);

		if (ser >= 0) {
			CodeWriter ps = c.newMethod(ACC_PUBLIC|ACC_FINAL, "push", "(Lroj/config/serial/AdaptContext;)V");
			ps.visitSize(2,3);

			ps.one(ALOAD_1);
			ps.one(ALOAD_0);
			ps.field(GETFIELD, c, ser);
			ps.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/AdaptContext", "push", "(Lroj/config/serial/Adapter;)V");
			ps.one(RETURN);
			ps.finish();
		}

		cw.one(ALOAD_1);
		cw.one(ALOAD_0);
		cw.field(GETFIELD, c, ua);
		cw.varLoad(type, 2);
		if (klassOut != null) cw.clazz(CHECKCAST, klassOut);
		cw.invoke(INVOKEVIRTUAL, reader);
		cw.field(PUTFIELD, "roj/config/serial/AdaptContext", "ref", "Ljava/lang/Object;");

		cw.one(ALOAD_1);
		cw.one(ICONST_1);
		cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/AdaptContext", "popd", "(Z)V");

		cw.one(RETURN);
		cw.finish();

		// endregion
		// region write()
		cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "write", "(Lroj/config/serial/CVisitor;Ljava/lang/Object;)V");
		cw.visitSizeMax(3,3);

		cw.one(ALOAD_0);
		cw.field(GETFIELD, c, ua);
		cw.one(ALOAD_2);
		cw.clazz(CHECKCAST, klassIn);
		cw.invoke(INVOKEVIRTUAL, writer);
		cw.varStore(type, 2);

		if (ser >= 0) {
			invokeParent(cw, ALOAD);
		} else {
			cw.one(ALOAD_1);
			cw.varLoad(type, 2);
			cw.invokeItf("roj/config/serial/CVisitor", "value", "("+type.toDesc()+")V");
		}

		cw.one(RETURN);
		cw.finish();
		// endregion

		serializerId.putInt("",0);
		copy.one(ALOAD_0);
		copy.one(ALOAD_2);
		copy.field(PUTFIELD, c, ua);

		return build();
	}

	// region object serializer
	private static final byte DIRECT_IF_OVERRIDE = Serializers.injected ? INVOKESPECIAL : INVOKEVIRTUAL;
	private Adapter klass(Class<?> o, int flag) {
		int t = GENERATE|CHECK_PARENT|SERIALIZE_PARENT;
		if ((flag&t) == t) throw new IllegalArgumentException("GENERATE CHECK_PARENT SERIALIZE_PARENT 不能同时为真");

		if ((o.getModifiers()&ACC_PUBLIC) == 0 && (flag&SAFE) != 0) throw new IllegalArgumentException("类"+o.getName()+"不是公共的");
		ConstantData data = Parser.parse(o);
		if (data == null) throw new IllegalArgumentException("无法获取"+o.getName()+"的类文件");

		int _init = data.getMethod("<init>", "()V");
		if (_init < 0) {
			if ((flag & NO_CONSTRUCTOR) == 0) throw new IllegalArgumentException("不允许跳过构造器(NO_CONSTRUCTOR)"+o.getName());
		} else if ((data.methods.get(_init).modifier() & ACC_PUBLIC) == 0) {
			if (!Serializers.injected) throw new IllegalArgumentException("UnsafeAdapter没有激活,不能跳过无参构造器生成对象"+o.getName());
			if ((flag & SAFE) != 0) throw new IllegalArgumentException("UNSAFE: "+o.getName()+".<init>");
		}

		fieldIds.clear();
		parentExist.clear();
		Adapter parentSerInst;
		if ((flag & SERIALIZE_PARENT) != 0 && !data.parent.equals("java/lang/Object")) {
			parentSerInst = make(data.parent, o.getSuperclass(), null, true);
			assert parentSerInst instanceof GenAdapter : "parentSer is not GenAdapter " + parentSerInst;

			begin();
			parentSer = ser(data.parent, null);
			IntBiMap<String> oldFieldIds = ((GenAdapter) parentSerInst).fieldNames();
			fieldIds.putAll(oldFieldIds);
			for (FieldNode field : data.fields) {
				if (oldFieldIds.containsValue(field.name()))
					throw new IllegalArgumentException("重复的字段名称:"+field+", 位于"+data.name+",先前位于（或它的父类）:"+data.parent);
			}

			for (Method mn : parentSerInst.getClass().getDeclaredMethods()) {
				if (mn.getName().equals("read")) {
					int type = TypeHelper.class2type(mn.getParameterTypes()[1]).getActualType();
					parentExist.add(type);
				}
			}
		} else {
			parentSer = -1;
			parentSerInst = null;
			begin();
		}

		if (data.fields.size() == 0 && fieldIds.size() == 0) throw new IllegalArgumentException("这"+o.getName()+"味道不对啊,怎么一个字段都没有");

		currentObject = o.getName().replace('.', '/');
		// region toString
		CodeWriter cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "toString", "()Ljava/lang/String;");
		cw.visitSize(1,1);
		String toString = "Adapter@"+o.getName();
		cw.ldc(new CstString(toString));
		c.putAttr(new AttrString("SourceFile", toString));
		cw.one(ARETURN);
		cw.finish();
		// endregion
		int fieldIdKey = c.newField(ACC_PRIVATE|ACC_STATIC, "ser$fieldIds", "Lroj/collect/IntBiMap;");
		// region fieldId
		cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "fieldNames", "()Lroj/collect/IntBiMap;");
		cw.visitSize(1,1);
		cw.field(GETSTATIC, c, fieldIdKey);
		cw.one(ARETURN);
		cw.finish();
		// endregion
		// region key函数
		cw = keyCw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "key", "(Lroj/config/serial/AdaptContext;Ljava/lang/String;)V");
		cw.visitSize(3,4);

		cw.field(GETSTATIC, c, fieldIdKey);
		cw.one(ALOAD_2);
		cw.one(ICONST_M1);
		cw.invoke(DIRECT_IF_OVERRIDE, "roj/collect/IntBiMap", "getValueOrDefault", "(Ljava/lang/Object;I)I");
		cw.one(DUP);
		cw.one(ISTORE_3);

		keyPrimitive = null;
		keySwitch = new SwitchSegment();
		cw.addSegment(keySwitch);
		keySwitch.def = cw.label();

		if (parentSer >= 0) {
			invokeParent(cw, ALOAD);
		} else {
			cw.one(ALOAD_1);
			cw.field(GETSTATIC, "roj/config/serial/SkipSer", "INST", "Lroj/config/serial/Adapter;");
			cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/AdaptContext", "push", "(Lroj/config/serial/Adapter;)V");
		}
		cw.one(RETURN);

		// endregion
		// region write()
		write = c.newMethod(ACC_PUBLIC|ACC_FINAL, "writeMap", "(Lroj/config/serial/CVisitor;Ljava/lang/Object;)V");
		write.visitSizeMax(3,3);

		write.one(ALOAD_2);
		write.clazz(CHECKCAST, data.name);
		write.one(ASTORE_2);

		if (parentSer >= 0) invokeParent(write, ALOAD);
		// endregion

		int fieldId = parentSer < 0 ? 0 : parentSerInst.fieldCount();

		CstInt count = new CstInt(fieldId);
		// region map()
		cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "map", "(Lroj/config/serial/AdaptContext;I)V");
		cw.visitSize(3,3);
		cw.one(ALOAD_1);
		if (_init < 0) cw.clazz(NEW, data.name);
		else cw.newObject(data.name);
		cw.field(PUTFIELD, "roj/config/serial/AdaptContext", "ref", "Ljava/lang/Object;");
		fieldIdM1(cw);
		cw.one(RETURN);
		// endregion
		// region fieldCount()
		cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "fieldCount", "()I");
		cw.visitSize(1,1);
		cw.ldc(count);
		cw.one(IRETURN);
		// endregion

		boolean defaultOptional = false;
		int optional = 0;
		optionalEx = null;

		Annotations tmp1 = data.parsedAttr(data.cp, Attribute.ClAnnotations);
		if (tmp1 != null) {
			Annotation opt = Attributes.getAnnotation(tmp1.annotations, "roj/config/serial/Optional");
			if (opt != null) defaultOptional = opt.getBoolean("value", true);
		}

		SimpleList<FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			FieldNode f = fields.get(i);
			if ((f.modifier() & (ACC_TRANSIENT|ACC_STATIC)) != 0) continue;

			int unsafe;
			if ((f.modifier() & ACC_PUBLIC) == 0) unsafe = 3; // 1|2
			else if ((f.modifier() & ACC_FINAL) != 0) unsafe = 5; // 1|4
			else unsafe = 0;

			String name = f.name();
			MethodNode get = null, set = null;
			boolean optional1 = defaultOptional;
			AsType as = null;

			Annotations attr = f.parsedAttr(data.cp, Attribute.ClAnnotations);
			if (attr != null) {
				List<Annotation> list = attr.annotations;
				for (int j = 0; j < list.size(); j++) {
					Annotation anno = list.get(j);
					switch (anno.type) {
						case "roj/config/serial/Name": name = anno.getString("value"); break;
						case "roj/config/serial/Via": {
							String sid = anno.getString("get");
							if (sid != null) {
								int id = data.getMethod(sid, "()".concat(f.rawDesc()));
								if (id < 0) throw new IllegalArgumentException("无法找到get方法" + anno);
								get = data.methods.get(id);
								unsafe &= 5;
							}
							sid = anno.getString("set");
							if (sid != null) {
								int id = data.getMethod(sid, "("+f.rawDesc()+")V");
								if (id < 0) throw new IllegalArgumentException("无法找到set方法" + anno);
								set = data.methods.get(id);
								unsafe &= 2;
							}
							break;
						}
						case "roj/config/serial/Optional":
							optional1 = anno.getBoolean("value", true);
							break;
						case "roj/config/serial/As":
							as = asTypes.get(anno.getString("value"));
							if (as == null) throw new IllegalArgumentException("Unknown as " + anno);
					}
				}
			}

			if (optional1) {
				if (fieldId < 32) {
					optional |= 1 << fieldId;
				} else {
					if (optionalEx == null) optionalEx = new MyBitSet();
					optionalEx.add(fieldId-32);
				}
			}

			if (unsafe != 0 ||
				set != null && (set.modifier()& ACC_PUBLIC) == 0 ||
				get != null && (get.modifier()& ACC_PUBLIC) == 0) {
				if ((flag & SAFE) != 0)
					throw new RuntimeException("无权访问"+data.name+"."+f+" ("+unsafe+")\n" +
					"解决方案:\n" +
					" 1. 开启UNSAFE模式\n" +
					" 2. 定义setter/getter\n" +
					" 3. 为字段或s/g添加public");
				if (!Serializers.injected)
					throw new RuntimeException("无权访问"+data.name+"."+f+" ("+unsafe+")\n" +
						"解决方案: 换用支持的JVM");
			}

			value(fieldId, data, f, name, get, set, optional1, as, (unsafe&4) != 0 ? o : null);
			fieldIds.putInt(fieldId, name);

			fieldId++;
		}

		count.value = fieldId;

		write.one(RETURN);
		write.finish();
		write = null;

		CodeWriter init = c.newMethod(ACC_PUBLIC, "init", "(Lroj/collect/IntBiMap;Lroj/collect/MyBitSet;)V");
		init.visitSize(1, 3);
		init.one(ALOAD_1);
		init.field(PUTSTATIC, c, fieldIdKey);

		if (optional != 0 || optionalEx != null) {
			// region optional
			cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "plusOptional", "(ILroj/collect/MyBitSet;)I");
			cw.visitSize(2,3);
			if (parentSer >= 0) {
				invokeParent(cw, ILOAD);
				cw.one(ISTORE_1);
			}
			if (optionalEx != null) {
				int fid = c.newField(ACC_PRIVATE|ACC_STATIC, "ser$optEx", "Lroj/collect/MyBitSet;");
				cw.one(ALOAD_2);
				cw.field(GETSTATIC, c, fid);
				cw.invoke(DIRECT_IF_OVERRIDE, "roj/collect/MyBitSet", "addAll", "(Lroj/collect/MyBitSet;)Lroj/collect/MyBitSet;");
				cw.one(POP);

				init.one(ALOAD_2);
				init.field(PUTSTATIC, c, fid);
			}
			cw.one(ILOAD_1);
			if (optional != 0) {
				cw.ldc(new CstInt(optional));
				cw.one(IOR);
			}
			cw.one(IRETURN);
			// endregion
		}

		for (IntIterator itr = parentExist.iterator(); itr.hasNext(); ) {
			createReadMethod(data, itr.nextInt());
		}

		init.one(RETURN);
		init.finish();

		return build();
	}

	private String currentObject;
	private int parentSer;
	private final MyBitSet parentExist = new MyBitSet();
	private void invokeParent(CodeWriter cw, byte opcode) {
		cw.one(ALOAD_0);
		cw.field(GETFIELD, c, parentSer);
		cw.vars(opcode, 1);
		cw.one(ALOAD_2);
		cw.invoke(INVOKEVIRTUAL, "roj/config/serial/Adapter", cw.mn.name(), cw.mn.rawDesc());
	}

	private void value(int fieldId, ConstantData data, FieldNode fn,
					   String actualName, MethodNode get, MethodNode set,
					   boolean optional1, AsType as,
					   Class<?> unsafePut) {
		Type type = as != null ? as.output : fn.fieldType();
		int actualType = type.getActualType();
		int methodType;
		switch (actualType) {
			// char序列化成字符串
			case Type.CHAR: methodType = Type.CLASS; break;
			case Type.BYTE:
			case Type.SHORT: methodType = Type.INT; break;
			//case Type.FLOAT: methodType = Type.DOUBLE; break;
			default: methodType = actualType; break;
		}

		CodeWriter cw;
		int asId = -1;

		byte myCode = type.shiftedOpcode(ILOAD);
		while (true) {
		Tmp2 t = readMethods.get(methodType);
		if (t == null) t = createReadMethod(data, methodType);

		cw = t.cw;
		t.seg.branch(fieldId, cw.label());

		cw.one(ALOAD_1);
		cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/AdaptContext", "setFieldHook", "()V");

		cw.vars(ALOAD, t.pos);


		if (as != null) {
			String asName = "as$"+as.klass().replace('/', '`');
			asId = c.getField(asName);
			if (asId < 0) {
				asId = c.newField(ACC_PRIVATE, asName, "L"+as.klass()+";");

				copy.one(ALOAD_0);
				copy.one(ALOAD_1);
				copy.ldc(new CstString(as.name));
				copy.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/SerializerFactory", "getAsType", "(Ljava/lang/String;)Ljava/lang/Object;");
				copy.clazz(CHECKCAST, as.klass());
				copy.field(PUTFIELD, c, asId);
			}

			cw.one(ALOAD_0);
			cw.field(GETFIELD, c, asId);
		}

		if (actualType == Type.CHAR) {
			// 特殊处理: ((String)o).charAt(0);
			cw.one(ALOAD_2);
			cw.clazz(CHECKCAST, "java/lang/String");
			cw.one(ICONST_0);
			cw.invoke(DIRECT_IF_OVERRIDE, "java/lang/String", "charAt", "(I)C");
		} else {
			if (myCode == -1) {
				cw.vars(ALOAD, 2);
				cw.invoke(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
				switch (actualType) {
					case Type.INT: cw.invoke(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I"); break;
					case Type.DOUBLE: cw.invoke(INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D"); cw.visitSizeMax(4,0); break;
					case Type.LONG: cw.invoke(INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;)J"); cw.visitSizeMax(4,0); break;
					case Type.FLOAT: cw.invoke(INVOKESTATIC, "java/lang/Float", "parseFloat", "(Ljava/lang/String;)F"); break;
				}
			} else {
				cw.vars(myCode, 2);
				if (actualType == Type.CLASS) {
					String type1 = type.getActualClass();
					if (type1.equals("java/lang/String")) {
						cw.invoke(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
					} else {
						cw.clazz(CHECKCAST, type1);
					}
				}
			}
		}

		if (methodType != actualType && myCode != -1) {
			switch (actualType) {
				case Type.FLOAT: cw.one(D2F); break;
				case Type.LONG: cw.one(I2L); cw.visitSizeMax(4,0); break;
			}
		}

		if (as != null) cw.invoke(INVOKEVIRTUAL, as.reader);

		if (set == null) {
			if (unsafePut != null) {
				cw.one(POP); // on-stack is local[2]
				cw.field(GETSTATIC, "roj/reflect/FieldAccessor", "u", "Lsun/misc/Unsafe;");
				cw.one(SWAP); // local[1].ref.cast
				cw.one(ALOAD_2);
				try {
					Field field = unsafePut.getDeclaredField(fn.name());
					cw.ldc(ReflectionUtils.u.objectFieldOffset(field));
					cw.invoke(DIRECT_IF_OVERRIDE, "sun/misc/Unsafe", "put"+ReflectionUtils.accessorName(field), "(Ljava/lang/Object;J"+(fn.fieldType().isPrimitive() ? fn.fieldType().toString() : "Ljava/lang/Object;")+")V");
				} catch (NoSuchFieldException e) { Helpers.athrow(e); }
			} else {
				cw.field(PUTFIELD, data.name, fn.name(), fn.rawDesc());
			}
		} else cw.invoke(INVOKEVIRTUAL, set);

		cw.one(RETURN);
		switch (methodType) {
			case Type.FLOAT: methodType = Type.DOUBLE; myCode = DLOAD; continue;
			case Type.LONG: methodType = Type.INT; myCode = ILOAD; cw.visitSizeMax(4,0); continue;
			// parseInt | parseFloat
			case Type.DOUBLE: case Type.INT: methodType = Type.CLASS; myCode = -1; continue;
		}
		break;
		}

		// 如果可选并且不是基本类型
		Label skip;
		if (optional1) {
			skip = new Label();

			write.one(ALOAD_2);
			if (get == null) write.field(GETFIELD, data.name, fn.name(), fn.rawDesc());
			else write.invoke(INVOKEVIRTUAL, get);

			byte code = IFEQ;
			switch (actualType) {
				case Type.CLASS: code = IFNULL; break;
				case Type.LONG: write.one(LCONST_0); write.one(LCMP); write.visitSizeMax(4,0); break;
				case Type.FLOAT: write.one(FCONST_0); write.one(FCMPG); break;
				case Type.DOUBLE: write.one(DCONST_0); write.one(DCMPG); write.visitSizeMax(4,0); break;
			}
			write.jump(code, skip);
		} else {
			skip = null;
		}

		write.one(ALOAD_1);
		write.ldc(new CstString(actualName));
		write.invokeItf("roj/config/serial/CVisitor", "key", "(Ljava/lang/String;)V");

		cw = keyCw;
		block:
		if (actualType == Type.CLASS && !"java/lang/String".equals(type.getActualClass())) {
			int id;
			String serType = type.getActualClass();
			Signature serSig = fn.parsedAttr(data.cp, Attribute.SIGNATURE);
			if (serSig != null) {
				id = ser(serType, serSig.toDesc());
			} else {
				id = ser(serType, null);
			}

			keySwitch.branch(fieldId, cw.label());
			cw.one(ALOAD_1);
			cw.one(ILOAD_3);
			cw.one(ALOAD_0);
			cw.field(GETFIELD, c, id);
			cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/AdaptContext", "pushHook", "(ILroj/config/serial/Adapter;)V");

			cw.one(RETURN);

			cw = write;
			cw.one(ALOAD_0);
			cw.field(GETFIELD, c, id);
			cw.one(ALOAD_1);
			if (as != null) {
				assert !as.user;
				cw.visitSizeMax(4,0);
				cw.one(ALOAD_0);
				cw.field(GETFIELD, c, asId);
			}
			cw.one(ALOAD_2);

			if (get == null) cw.field(GETFIELD, data.name, fn.name(), fn.rawDesc());
			else cw.invoke(INVOKEVIRTUAL, get);

			if (as != null) {
				cw.invoke(INVOKEVIRTUAL, as.writer);
			}

			cw.invoke(INVOKEVIRTUAL, "roj/config/serial/Adapter", "write", "(Lroj/config/serial/CVisitor;Ljava/lang/Object;)V");
		} else {
			if (keyPrimitive == null) {
				keyPrimitive = cw.label();
				cw.one(ALOAD_1);
				cw.one(ILOAD_3);
				cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/AdaptContext", "setKeyHook", "(I)V");

				cw.one(RETURN);
			}
			keySwitch.branch(fieldId, keyPrimitive);

			cw = write;
			if (as != null) {
				if (!as.user) cw.one(ALOAD_1);
				cw.one(ALOAD_0);
				cw.field(GETFIELD, c, asId);
			} else {
				cw.one(ALOAD_1);
			}
			cw.one(ALOAD_2);

			if (get == null) cw.field(GETFIELD, data.name, fn.name(), fn.rawDesc());
			else cw.invoke(INVOKEVIRTUAL, get);

			if (as != null) {
				if (as.user) cw.one(ALOAD_1);
				cw.invoke(INVOKEVIRTUAL, as.writer);
				if (as.user) break block;
			}

			String c = "java/lang/String".equals(type.getActualClass()) ? "Ljava/lang/String;" : Type.toDesc(actualType);

			cw.invokeItf("roj/config/serial/CVisitor", "value", "("+c+")V");
		}

		if (skip != null) cw.label(skip);
	}
	private Tmp2 createReadMethod(ConstantData data, int methodType) {
		String desc;
		int size = 1;
		switch (methodType) {
			case Type.CLASS: desc = "(Lroj/config/serial/AdaptContext;Ljava/lang/Object;)V"; break;
			case Type.INT: desc = "(Lroj/config/serial/AdaptContext;I)V"; break;
			case Type.FLOAT: desc = "(Lroj/config/serial/AdaptContext;F)V"; break;
			case Type.DOUBLE: desc = "(Lroj/config/serial/AdaptContext;D)V"; size = 2; break;
			case Type.LONG: desc = "(Lroj/config/serial/AdaptContext;J)V"; size = 2; break;
			case Type.BOOLEAN: desc = "(Lroj/config/serial/AdaptContext;Z)V"; break;
			default: throw new IllegalStateException("Unexpected value: " + methodType);
		}

		Tmp2 t = new Tmp2();
		readMethods.putInt(methodType,t);
		CodeWriter cw = t.cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "read", desc);

		t.seg = new SwitchSegment();
		t.seg.def = new Label();

		cw.visitSize(3, size+3);

		cw.one(ALOAD_1);
		cw.field(GETFIELD, "roj/config/serial/AdaptContext", "ref", "Ljava/lang/Object;");
		cw.clazz(CHECKCAST, data.name);
		cw.vars(ASTORE, t.pos = (byte) (size+2));

		cw.one(ALOAD_1);
		cw.field(GETFIELD, "roj/config/serial/AdaptContext", "fieldId", "I");
		cw.addSegment(t.seg);

		cw.label(t.seg.def);
		if (parentExist.remove(methodType)) {
			invokeParent(cw, TypeHelper.parseMethod(desc).get(1).shiftedOpcode(ILOAD));
			cw.one(RETURN);
		} else {
			cw.clazz(NEW, "java/lang/IllegalStateException");
			cw.one(DUP);
			cw.one(ALOAD_1);
			cw.field(GETFIELD, "roj/config/serial/AdaptContext", "fieldId", "I");
			cw.invoke(INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;");
			cw.invoke(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V");
			cw.one(ATHROW);
		}
		return t;
	}

	private CodeWriter keyCw;
	private Label keyPrimitive;
	private SwitchSegment keySwitch;

	private static class Tmp2 {
		CodeWriter cw;
		SwitchSegment seg;
		byte pos;
	}
	private final IntMap<Tmp2> readMethods = new IntMap<>();
	private final IntBiMap<String> fieldIds = new IntBiMap<>();

	private MyBitSet optionalEx;
	// endregion

	private static void fieldIdM1(CodeWriter c) {
		c.one(ALOAD_1);
		c.one(ICONST_M1);
		c.field(PUTFIELD, "roj/config/serial/AdaptContext", "fieldId", "I");
	}

	private ConstantData c;
	private CodeWriter copy, write;
	private final ToIntMap<String> serializerId = new ToIntMap<>();

	private int ser(String type, String generic) {
		String _name = generic == null ? type : type.concat(generic);
		int id = serializerId.getOrDefault(_name,-1);
		if (id >= 0) return id;

		id = c.newField(ACC_PRIVATE, "ser$"+c.fields.size(), "Lroj/config/serial/Adapter;");
		serializerId.putInt(_name,id);

		copy.one(ALOAD_0);
		if (!type.equals(currentObject)) {
			copy.one(ALOAD_1);
			copy.ldc(new CstClass(type));
			if (generic == null || generic.equals("java/lang/Object")) {
				copy.invoke(INVOKEVIRTUAL, "roj/config/serial/SerializerFactory", "get", "(Ljava/lang/Class;)Lroj/config/serial/Adapter;");
			} else {
				copy.ldc(new CstString(generic));
				copy.invoke(INVOKEVIRTUAL, "roj/config/serial/SerializerFactory", "get", "(Ljava/lang/Class;Ljava/lang/String;)Lroj/config/serial/Adapter;");
			}
		} else {
			copy.one(ALOAD_0);
		}
		copy.field(PUTFIELD, c, id);
		return id;
	}
	private void begin() {
		c = new ConstantData();
		c.access = ACC_PUBLIC|ACC_SUPER|ACC_FINAL;
		c.name("roj/config/serial/GA$"+GENERATED.size());
		c.parent("roj/config/serial/Adapter");
		c.cloneable();
		c.interfaces.clear();
		c.interfaces.add(new CstClass("roj/config/serial/GenAdapter"));
		FastInit.prepare(c);

		copy = c.newMethod(ACC_PUBLIC, "secondaryInit", "(Lroj/config/serial/SerializerFactory;Ljava/lang/Object;)V");
		copy.visitSize(4, 3);
	}
	private Adapter build() {
		ConstantData c1 = c;
		c = null;

		if (serializerId.isEmpty()) {
			c1.methods.remove(c1.getMethod("secondaryInit"));
		} else {
			copy.one(RETURN);
			copy.finish();
		}
		copy = null;

		readMethods.clear();
		serializerId.clear();

		return (Adapter) FastInit.make(c1);
	}

	private static final MyHashMap<String, IntFunction<?>> DATA_CONTAINER = new MyHashMap<>();
	public static <T> IntFunction<T> dataContainer(Class<?> type) {
		IntFunction<?> fn = DATA_CONTAINER.get(type.getName());
		if (fn == null && !DATA_CONTAINER.containsKey(type.getName()))  {
			synchronized (DATA_CONTAINER) {
				fn = DATA_CONTAINER.get(type.getName());
				if (fn == null) {
					boolean hasNP = false, hasSized = false;
					try {
						type.getDeclaredConstructor(int.class);
						hasSized = true;
					} catch (NoSuchMethodException ignored) {}
					try {
						type.getDeclaredConstructor(ArrayCache.CLASSES);
						hasNP = true;
					} catch (NoSuchMethodException ignored) {}

					if (!(hasNP|hasSized)) {
						DATA_CONTAINER.put(type.getName(), null);
						return null;
					}

					ConstantData c = new ConstantData();
					c.name("roj/config/serial/DataContainer$");
					c.addInterface("java/util/function/IntFunction");
					FastInit.prepare(c);

					CodeWriter cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "apply", "(I)Ljava/lang/Object;");

					String asmName = TypeHelper.class2asm(type);
					if (hasNP) {
						if (hasSized) {
							cw.visitSize(3, 2);
							Label label = new Label();
							cw.one(ILOAD_1);
							cw.jump(IFLT, label);
							cw.clazz(NEW, asmName);
							cw.one(DUP);
							cw.one(ILOAD_1);
							cw.invokeD(asmName, "<init>", "(I)V");
							cw.one(ARETURN);
							cw.label(label);
						}
						cw.visitSizeMax(2, 2);
						cw.newObject(asmName);
					} else {
						cw.visitSize(3, 2);
						Label label = new Label();

						cw.one(ILOAD_1);
						cw.jump(IFGE, label);

						cw.ldc(16);
						cw.one(ISTORE_1);
						cw.label(label);

						cw.clazz(NEW, asmName);
						cw.one(DUP);
						cw.one(ILOAD_1);
						cw.invokeD(asmName, "<init>", "(I)V");
					}

					cw.one(ARETURN);
					fn = (IntFunction<?>) FastInit.make(c);
					DATA_CONTAINER.put(type.getName(), fn);
				}
			}
		}

		return Helpers.cast(fn);
	}
}