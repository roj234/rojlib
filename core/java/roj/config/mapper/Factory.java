package roj.config.mapper;

import org.jetbrains.annotations.NotNull;
import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.attr.Attribute;
import roj.asm.attr.StringAttribute;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstInt;
import roj.asm.cp.CstString;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.Label;
import roj.asm.insn.SwitchBlock;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asmx.GenericTemplate;
import roj.ci.annotation.Public;
import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.collect.*;
import roj.compiler.resolve.Inferrer;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.Reflection;
import roj.reflect.Unaligned;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @version 3.0
 * @since 2022/1/11 17:49
 */
@Public
final class Factory extends ObjectMapperFactory {
	private static final ReentrantLock lock = new ReentrantLock();
	private static final Map<String, TypeAdapter> DEFAULT = new HashMap<>();
	private static final List<ClassLoader> LOADER_CHAIN = new ArrayList<>();
	static {
		var cl = Factory.class.getClassLoader();
		while (cl != null) {
			LOADER_CHAIN.add(cl);
			var cl1 = cl.getClass().getClassLoader();
			if (cl1 == cl) break;
			cl = cl1;
		}

		DEFAULT.put("java.lang.CharSequence", PrimitiveAdapter.STR);
		DEFAULT.put("java.lang.String", PrimitiveAdapter.STR);
		DEFAULT.put("java.lang.Long", new PrimitiveAdapter(Type.LONG));
		DEFAULT.put("java.lang.Double", new PrimitiveAdapter(Type.DOUBLE));
		DEFAULT.put("java.lang.Float", new PrimitiveAdapter(Type.FLOAT));
		DEFAULT.put("java.lang.Boolean", new PrimitiveAdapter(Type.BOOLEAN));

		PrimitiveAdapter INT = new PrimitiveAdapter(Type.INT);
		DEFAULT.put("java.lang.Integer", INT);
		DEFAULT.put("java.lang.Number", INT);
		DEFAULT.put("java.lang.Short", new PrimitiveAdapter(Type.SHORT));
		DEFAULT.put("java.lang.Character", new PrimitiveAdapter(Type.CHAR));
		DEFAULT.put("java.lang.Byte", new PrimitiveAdapter(Type.BYTE));

		DEFAULT.put("java.util.Map", new MapAdapter(PrimitiveAdapter.STR, null, null));
		CollectionAdapter LIST = new CollectionAdapter(null, false, null);
		DEFAULT.put("java.util.Collection", LIST);
		DEFAULT.put("java.util.List", LIST);
		DEFAULT.put("java.util.Set", new CollectionAdapter(null, true, null));
		DEFAULT.put("java.util.Optional", new OptionalAdapter());

		DEFAULT.put("roj.config.auto.Either", new EitherAdapter());
	}

	private final ClassLoader classLoader;
	private final Map<String, TypeAdapter> GENERATED;

	private final AnyAdapter dynamicRoot;
	private final Map<String, TypeAdapter> localRegistry = new HashMap<>(DEFAULT);
	private final Map<String, AsType> asTypes = new HashMap<>();
	static final class AsType {
		final String name;
		final Type output;
		final MethodNode reader, writer;
		final Object ref;
		final boolean user, fieldType;

		public AsType(String name, MethodNode writer, MethodNode reader, Object o, boolean user_writer, boolean actualTypeMode) {
			this.name = name;
			this.ref = o;
			this.reader = reader;
			this.writer = writer;
			this.output = reader.parameters().get(0);
			this.user = user_writer;
			this.fieldType = actualTypeMode;
		}

		public String type() { return reader.owner(); }
	}

	Factory(int flag, ClassLoader cl) {
		super(flag);
		this.classLoader = cl;
		this.GENERATED = CACHE.computeIfAbsent(cl, CACHE_NEW).adapters;

		// 不开启AllowDynamic仍然可以序列化集合，只是不能反序列化
		AnyAdapter any = dynamicRoot = new AnyAdapter(this);
		if ((flag & PARSE_DYNAMIC) != 0) {
			localRegistry.put("java.util.Map", new MapAdapter(DEFAULT.get("java.lang.String"), any, null));
			CollectionAdapter LIST = new CollectionAdapter(any, false, null);
			localRegistry.put("java.util.Collection", LIST);
			localRegistry.put("java.util.List", LIST);
			localRegistry.put("java.util.Set", new CollectionAdapter(any, true, null));
		}
	}

	@Override
	public ObjectMapperFactory registerType(Class<?> type) {
		make(type, type.getName(), perClassFlag.applyAsInt(type));
		return this;
	}
	@Override
	public ObjectMapperFactory registerType(Class<?> type, SerializeSetting setting) {
		if (type.getComponentType() != null || type.isPrimitive() || type.isEnum()) throw new IllegalArgumentException();

		int flag1 = perClassFlag.applyAsInt(type);

		lock.lock();
		try {
			var ser = klass(type, flag1, setting);
			localRegistry.put(type.getName(), ser);
			if (ser instanceof GA g) {
				g.init(new IntBiMap<>(fieldIds), optionalEx);
				g.init2(this, null);
			}
		} finally {
			lock.unlock();
		}
		return this;
	}

	@Override
	public ObjectMapperFactory registerAdapter(Class<?> type, Object adapter, String serializeMethod, String deserializeMethod) {
		if (type.isPrimitive() || type == String.class) throw new IllegalStateException("type不能是基本类型或字符串");

		var adapterType = adapter.getClass() == Class.class ? (Class<?>) adapter : adapter.getClass();
		var data = ClassNode.fromType(adapterType);
		if (data == null) throw new IllegalArgumentException("无法获取"+adapterType.getName()+"的类文件");

		int wid = data.getMethod(serializeMethod), rid = data.getMethod(deserializeMethod);
		MethodNode w = data.methods.get(wid), r = data.methods.get(rid);

		String name = "U|"+wid+"|"+rid+"|"+type.getName()+"|"+adapterType.getName();
		TypeAdapter ser = GENERATED.get(name);
		if (ser == null) {
			Type clsType = Type.from(type);
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
			if (((r.modifier ^ w.modifier)&ACC_STATIC) != 0) throw new IllegalArgumentException("R/W方法静态不同");
			boolean isStatic = adapterType == adapter;
			if ((r.modifier&ACC_STATIC) == 0 == isStatic) throw new IllegalArgumentException("R/W方法与参数的静态不同");

			lock.lock();
			try {
				if ((ser = GENERATED.get(name)) == null) {
					ser = user(data, w, r, isStatic);
					GENERATED.put(name, ser);
				}
			} finally {
				lock.unlock();
			}
		}
		ser = (TypeAdapter) ((GA)ser).clone();

		synchronized (localRegistry) {
			localRegistry.put(type.getName(), ser);
			((GA) ser).init2(this, adapter);
		}
		return this;
	}
	/**
	 * @inheritDoc
	 */
	@Override
	public ObjectMapperFactory registerAdapter(Class<?> type, Object adapter) {
		if (type.isPrimitive() || type == String.class) throw new IllegalStateException("type不能是基本类型或字符串");

		// A: auto
		var adapterType = adapter.getClass() == Class.class ? (Class<?>) adapter : adapter.getClass();
		String name = "U|A|"+type.getName()+"|"+adapterType;
		TypeAdapter ser = GENERATED.get(name);
		if (ser == null) {
			RW rw = autoRW(type, adapterType);

			lock.lock();
			try {
				if ((ser = GENERATED.get(name)) == null) {
					ser = user(rw.data(), rw.writer(), rw.reader(), adapterType == adapter);
					GENERATED.put(name, ser);
				}
			} finally {
				lock.unlock();
			}
		}
		ser = (TypeAdapter) ((GA)ser).clone();

		synchronized (localRegistry) {
			localRegistry.put(type.getName(), ser);
			((GA) ser).init2(this, adapter);
		}
		return this;
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public ObjectMapperFactory registerAdapter(String name, Class<?> type, Object adapter, String serializeMethod, String deserializeMethod) {
		var adapterType = adapter.getClass() == Class.class ? (Class<?>) adapter : adapter.getClass();
		var data = ClassNode.fromType(adapterType);
		if (data == null) throw new IllegalArgumentException("无法获取"+adapterType.getName()+"的类文件");

		MethodNode w = data.getMethodObj(serializeMethod), r = data.getMethodObj(deserializeMethod);

		boolean visitorMode = false, actualTypeMode;
		sp: {
			List<Type> wp = w.parameters(), rp = r.parameters();
			if (((r.modifier ^ w.modifier)&ACC_STATIC) != 0) throw new IllegalArgumentException("R/W方法静态不同");
			if ((r.modifier&ACC_STATIC) == 0 == (adapterType == adapter)) throw new IllegalArgumentException("R/W方法与参数的静态不同");
			if (wp.get(0).equals(Type.from(type))) {
				actualTypeMode = rp.size() == 2 && "java/lang/String".equals(rp.get(1).getActualClass());

				if (rp.get(0).equals(w.returnType())) break sp;
				if (wp.size() == 2 && "roj/config/ValueEmitter".equals(wp.get(1).getActualClass())) {
					if (!wp.get(0).isPrimitive()) throw new IllegalArgumentException("只有基本类型目标可以使用UserVisitor输出");
					visitorMode = true;
					break sp;
				}
			}
			throw new IllegalStateException("R/W方法参数不正确");
		}

		synchronized (asTypes) {
			AsType old = asTypes.putIfAbsent(name, new AsType(name, w, r, adapterType == adapter ? null : adapter, visitorMode, actualTypeMode));
			if (old != null) throw new IllegalArgumentException(name+"已存在");
		}
		return this;
	}
	/**
	 * @inheritDoc
	 */
	@Override
	public ObjectMapperFactory registerAdapter(String name, Class<?> type, Object adapter) {
		var adapterType = adapter.getClass() == Class.class ? (Class<?>) adapter : adapter.getClass();
		RW rw = autoRW(type, adapterType);
		synchronized (asTypes) {
			AsType old = asTypes.putIfAbsent(name, new AsType(name, rw.writer, rw.reader, adapterType == adapter ? null : adapter, false, false));
			if (old != null) throw new IllegalArgumentException(name+"已存在");
		}
		return this;
	}

	private record RW(ClassNode data, MethodNode writer, MethodNode reader) {}
	private static RW autoRW(Class<?> type, Class<?> adapter) {
		ClassNode data = ClassNode.fromType(adapter);
		if (data == null) throw new IllegalArgumentException("无法获取"+adapter.getName()+"的类文件");

		Type clsType = Type.from(type);
		MethodNode writer = null, reader = null;
		for (MethodNode mn : data.methods) {
			List<Type> par = mn.parameters();
			if (par.size() != 1) continue;

			Type ret = mn.returnType();
			if (par.get(0).equals(clsType) && ret.type != Type.VOID) {
				if (writer != null) throw new IllegalArgumentException("找到了多个符合的writer: "+writer+" / "+mn);
				writer = mn;
			} else if (ret.equals(clsType)) {
				if (reader != null) throw new IllegalArgumentException("找到了多个符合的reader: "+reader+" / "+mn);
				reader = mn;
			}
		}

		if (reader == null) throw new IllegalArgumentException("没找到reader");
		if (writer == null) throw new IllegalArgumentException("没找到writer");
		if (((reader.modifier^writer.modifier)&ACC_STATIC) != 0) throw new IllegalArgumentException("抱歉，自动为"+adapter.getName()+"识别的方法名称貌似不对");
		return new RW(data, writer, reader);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public <T> ObjectMapper<T> serializer(Class<T> type) { return context(make(null, type, null, false)); }
	/**
	 * @inheritDoc
	 */
	@Override
	public ObjectMapper<?> serializer(IType generic) { return context(get(generic)); }
	private <T> ObjectMapper<T> context(TypeAdapter root) {
		var allowDeser = (flag&SERIALIZE_ONLY) == 0;
		return Helpers.cast((flag&OBJECT_POOL) == 0
					? new MappingContext(root, allowDeser)
					: new MappingContextEx(root, allowDeser));
	}

	// 被手动Adapter调用的部分也应当使用ObjectPoolWrapper封装
	// 不过bug貌似很多（hashCode failed）
	@NotNull
	final TypeAdapter get(IType generic) {
		Type type = generic.rawType();
		Class<?> klass;
		try {
			klass = type.toClass(classLoader);
		} catch (ClassNotFoundException e) {
			throw new TypeNotPresentException(generic.toString(), e);
		}

		if (type == generic || (flag & PREFER_DYNAMIC_0) != 0) {
			return make(null, klass, null, false);
		} else {
			return make(generic.toDesc(), klass, (Generic) generic, false);
		}
	}
	//ObjAny
	final TypeAdapter getByName(String name) {
		Class<?> type;
		try {
			type = Class.forName(name, false, classLoader);
		} catch (ClassNotFoundException e) {
			throw new TypeNotPresentException(name, e);
		}
		name = type.getName();
		if ((flag & PARSE_DYNAMIC) == 0) return localRegistry.get(name);
		return make(name, type, null, true);
	}

	private TypeAdapter make(String name, @NotNull Class<?> type, Generic generic, boolean mustExact) {
		TypeAdapter ser = localRegistry.get(name!=null?name:(name=type.getName()));
		if (ser != null) return ser;

		// if (generic != null) name = generic.toDesc();
		// so check original type and derive
		if (generic != null) {
			ser = localRegistry.get(type.getName());
			if (ser != null) return ser.transform(this, type, generic.children);
		}

		int flag = perClassFlag.applyAsInt(type);
		findExist:
		if ((flag & (CHECK_INTERFACE|CHECK_PARENT)) != 0) {
			CharList sb = new CharList();
			Class<?> c = type;
			found:
			while (true) {
				if ((flag & CHECK_INTERFACE) != 0) {
					for (Class<?> itf : c.getInterfaces()) {
						sb.clear();
						// noinspection all
						ser = localRegistry.get(sb.append(itf.getName()));
						if (ser != null) break found;
					}
				}

				c = c.getSuperclass();
				if (c == null) break findExist;

				if ((flag & CHECK_PARENT) != 0) {
					sb.clear();
					// noinspection all
					ser = localRegistry.get(sb.append(c.getName()));
					if (ser != null) break;
				}
			}
			sb._free();

			// 就是有点太重量级了
			// 然而，java反射自带的泛型API就是依托答辩
			// 20250213 好了，去除了预处理静态库，现在没那么重量级了
			List<IType> childType = generic == null ? null : generic.children;
			//if (generic != null) {
				try {
					Inferrer.TEMPORARY_DISABLE_ASTERISK.set(true);
					var types = genericInferrer().inferGeneric(generic == null ? Type.klass(name.replace('.', '/')) : generic, c.getName().replace('.', '/'));
					if (types != null) childType = types;
				} catch (Exception e) {
					e.printStackTrace();
				}
				Inferrer.TEMPORARY_DISABLE_ASTERISK.remove();
			//}

			ser = ser.transform(this, type, childType);
			synchronized (localRegistry) {localRegistry.put(name, ser);}
			return ser;
		}

		if ((flag & GENERATE) == 0) throw new IllegalArgumentException("未找到"+name+"的序列化器");

		if (mustBeDynamic(type)) {
			if (!mustExact/* && (this.flag & ALLOW_DYNAMIC) != 0*/) return dynamicRoot;
			throw new IllegalArgumentException(name+"是抽象的，无法直接生成序列化器");
		}

		// 它们不会有withGenericType
		ser = make(type, name, flag);

		if (ser instanceof GA) {
			ser = (TypeAdapter) ((GA)ser).clone();
		}

		synchronized (localRegistry) {
			localRegistry.put(name, ser);
			if (ser instanceof GA) ((GA) ser).init2(this, null);
		}
		return ser;
	}

	private ClassUtil inferrer;
	private ClassUtil genericInferrer() {
		if (inferrer == null) {
			inferrer = new ClassUtil(Factory.class.getClassLoader(), classLoader);
		}
		return inferrer;
	}

	private static boolean mustBeDynamic(Class<?> type) {
		if (type.getComponentType() != null) return false;
		if ((type.getModifiers() & (ACC_ABSTRACT|ACC_INTERFACE)) != 0) return true;
		return type == Object.class;
	}
	/**@param flag 只检查这些FLAG，其中SAFE,NO_CONSTRUCTOR不影响生成结果: CHECK_PARENT,SERIALIZE_PARENT,SAFE,NO_CONSTRUCTOR,OPTIONAL_BY_DEFAULT */
	private TypeAdapter make(Class<?> type, String name, int flag) {
		TypeAdapter ser;

		if (!type.isEnum() && (type.getComponentType() == null || !type.getComponentType().isPrimitive())) {
			name = ((this.flag&OBJECT_POOL) | (flag&(SERIALIZE_PARENT | NO_SCHEMA)))+";"+name;
		}

		var GENERATED = CACHE.computeIfAbsent(type.getClassLoader(), CACHE_NEW).adapters;
		if ((ser = GENERATED.get(name)) == null) {
			lock.lock();
			try {
				if ((ser = GENERATED.get(name)) != null) return ser;

				if (type.isEnum()) {
					ser = new EnumAdapter(type);
				} else {
					Class<?> component = type.getComponentType();
					if (component != null) {
						if (component.isPrimitive()) {
							ser = array(type);
						} else {
							ser = new ObjectArrayAdapter(component, make(null, component, null, false));
						}
					} else {
						ser = klass(type, flag, SerializeSetting.IDENTITY);
					}
				}

				if (ser instanceof GA g) g.init(new IntBiMap<>(fieldIds), optionalEx);
				GENERATED.put(name, ser);
			} finally {
				lock.unlock();
			}
		}
		return ser;
	}

	// region ASM-回调
	@ReferenceByGeneratedClass
	@Public
	final Object gas(String as) {
		AsType a = asTypes.get(as);
		if (a == null) throw new IllegalStateException("缺少@As("+as+")的适配器");
		return a.ref;
	}
	@ReferenceByGeneratedClass
	@Public
	final TypeAdapter get(Class<?> type) {
		TypeAdapter make = make(null, type, null, false);
		if ((this.flag&OBJECT_POOL) != 0 && make != dynamicRoot) make = new PooledAdapter(make);
		return make; }
	@ReferenceByGeneratedClass
	@Public
	final TypeAdapter gsa(Class<?> type, String generic) {
		Generic genericType = null;

		if ((flag & PREFER_DYNAMIC_0) != 0) {
			generic = null;
		} else if (generic != null) {
			genericType = Signature.parse(generic, Signature.FIELD).values.get(0) instanceof Generic g ? g : null;
		}

		TypeAdapter make = make(generic, type, genericType, false);
		if ((this.flag&OBJECT_POOL) != 0 && make != dynamicRoot) make = new PooledAdapter(make);
		return make;
	}
	// Get parent adapter
	@ReferenceByGeneratedClass
	@Public
	final TypeAdapter gpa(Class<?> type) {
		TypeAdapter ser = make(type, type.getName().replace('.', '/'), SERIALIZE_PARENT | SERIALIZE_ONLY);
		if (ser instanceof GA g) ser = (TypeAdapter) g.clone();
		if (ser instanceof GA g) g.init2(this, null);
		return ser;
	}
	// endregion
	// region ASM
	// region 数组序列化器
	private static byte[] primitiveArrayTemplate;
	/**
	 * 数组序列化器
	 */
	private TypeAdapter array(Class<?> type) {
		type = type.getComponentType();
		assert type.isPrimitive();

		if (primitiveArrayTemplate == null) {
			try {
				primitiveArrayTemplate = IOUtil.getResourceIL("roj/config/mapper/PrimitiveArrayAdapter.class");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		byte[] b = primitiveArrayTemplate.clone();

		Type asmType = Type.from(type);
		Type methodType = asmType.opcodePrefix().equals("I") ? Type.primitive(Type.INT) : asmType;
		ClassNode c = new GenericTemplate(Type.LONG,asmType,methodType,asmType,true).generate(ByteList.wrap(b));

		switch (asmType.type) {
			case Type.LONG: addUpgrader(c, I2L); copyArrayRef(c, Type.LONG); break;
			case Type.FLOAT: addUpgrader(c, F2D); break;
			case Type.INT: copyArrayRef(c, Type.INT); break;
			case Type.BYTE: copyArrayRef(c, Type.BYTE); break;
		}

		c.name("roj/config/mapper/PAS$"+asmType);
		return (TypeAdapter) ClassDefiner.newInstance(c);
	}
	private static void copyArrayRef(ClassNode c, char type) {
		CodeWriter cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "read", "(Lroj/config/mapper/MappingContext;["+type+")V");
		cw.visitSize(2,3);
		cw.insn(ALOAD_1);
		cw.insn(ALOAD_2);
		cw.field(PUTFIELD, "roj/config/mapper/MappingContext", "ref", "Ljava/lang/Object;");
		cw.insn(ALOAD_1);
		cw.insn(ICONST_1);
		cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/MappingContext", "popd", "(Z)V");
		cw.insn(RETURN);

		c.methods.remove(c.getMethod("write"));
		c.getMethodObj("write1").name("write");
	}
	private static void addUpgrader(ClassNode c, byte code) {
		String orig = Opcodes.toString(code);
		String id = orig.replace('L', 'J');

		CodeWriter ir = c.newMethod(ACC_PUBLIC|ACC_FINAL, "read", "(Lroj/config/mapper/MappingContext;"+id.charAt(0)+")V");
		ir.visitSize(3,3);
		ir.insn(ALOAD_0);
		ir.insn(ALOAD_1);
		ir.insn((byte) opcodeByName().getInt(orig.charAt(0)+"LOAD_2"));
		ir.insn(code);
		ir.invoke(DIRECT_IF_OVERRIDE, c.name(), "read", "(Lroj/config/mapper/MappingContext;"+id.charAt(2)+")V");
		ir.insn(RETURN);
	}
	// endregion

	/**
	 * 包装用户提供的序列化器
 	 */
	private TypeAdapter user(ClassNode data, MethodNode writer, MethodNode reader, boolean isStatic) {
		String klassIn = writer.parameters().get(0).getActualClass();
		if (klassIn == null)
			throw new IllegalArgumentException("无法覆盖"+reader.returnType()+"的序列化");

		begin("roj/config/mapper/UserSer");

		CodeWriter cw;
		Type type = writer.returnType();
		String klassOut = type.getActualClass();

		int ua = isStatic ? -1 : c.newField(ACC_PRIVATE, "Sa", "L"+ data.name() +";");
		int ser;
		if (klassOut != null && !klassOut.equals("java/lang/String")) {
			String genSig;
			Signature signature = writer.getAttribute(data.cp, Attribute.SIGNATURE);
			if (signature != null) {
				IType ret = signature.values.get(signature.values.size() - 1);
				genSig = ret.toDesc();
			} else genSig = null;

			ser = ser(klassOut, genSig);
		} else ser = -1;

		// region read()
		String methodType = type.getActualType() == Type.CLASS ? "Ljava/lang/Object;" : type.toDesc();
		cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "read", "(Lroj/config/mapper/MappingContext;"+methodType+")V");
		cw.visitSize(3,3);

		if (ser >= 0) {
			CodeWriter ps = c.newMethod(ACC_PUBLIC|ACC_FINAL, "push", "(Lroj/config/mapper/MappingContext;)V");
			ps.visitSize(2,3);

			ps.insn(ALOAD_1);
			ps.insn(ALOAD_0);
			ps.field(GETFIELD, c, ser);
			ps.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/MappingContext", "push", "(Lroj/config/mapper/TypeAdapter;)V");
			ps.insn(RETURN);
			ps.finish();
		}

		cw.insn(ALOAD_1);
		if (ua >= 0) {
			cw.insn(ALOAD_0);
			cw.field(GETFIELD, c, ua);
		}
		cw.varLoad(type, 2);
		var clz = reader.parameters().get(0).getActualClass();
		if (clz != null) cw.clazz(CHECKCAST, clz);
		cw.invoke(ua >= 0 ? INVOKEVIRTUAL : INVOKESTATIC, reader);
		cw.field(PUTFIELD, "roj/config/mapper/MappingContext", "ref", "Ljava/lang/Object;");

		cw.insn(ALOAD_1);
		cw.insn(ICONST_1);
		cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/MappingContext", "popd", "(Z)V");

		cw.insn(RETURN);
		cw.finish();

		// endregion
		// region write()
		cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "write", "(Lroj/config/ValueEmitter;Ljava/lang/Object;)V");
		cw.visitSizeMax(3,3);

		if (ua >= 0) {
			cw.insn(ALOAD_0);
			cw.field(GETFIELD, c, ua);
		}
		cw.insn(ALOAD_2);
		cw.clazz(CHECKCAST, klassIn);
		cw.invoke(ua >= 0 ? INVOKEVIRTUAL : INVOKESTATIC, writer);
		cw.varStore(type, 2);

		if (ser >= 0) {
			parentSer = ser;
			invokeParent(cw, ALOAD);
		} else {
			cw.insn(ALOAD_1);
			cw.varLoad(type, 2);
			cw.invokeItf("roj/config/ValueEmitter", "value", "("+type.toDesc()+")V");
		}

		cw.insn(RETURN);
		cw.finish();
		// endregion

		serializerId.putInt("",0);
		if (!isStatic) {
			copy.insn(ALOAD_0);
			copy.insn(ALOAD_2);
			copy.clazz(CHECKCAST, data.name());
			copy.field(PUTFIELD, c, ua);
		}

		return build(classLoader);
	}

	// region 对象序列化器
	private static final byte DIRECT_IF_OVERRIDE = MAGIC_ADAPTER && Reflection.JAVA_VERSION < 22 ? INVOKESPECIAL : INVOKEVIRTUAL;
	/**
	 * 对象序列化器
	 */
	private TypeAdapter klass(Class<?> o, int flag, SerializeSetting fp) {
		int t = CHECK_PARENT| SERIALIZE_PARENT;
		if ((flag&t) == t) throw new IllegalArgumentException("CHECK_PARENT SERIALIZE_PARENT 不能同时为真");

		if ((o.getModifiers()&ACC_PUBLIC) == 0 && (flag& SERIALIZE_PUBLIC_ONLY) != 0) throw new IllegalArgumentException("类"+o.getName()+"不是公共的");
		ClassNode data = ClassNode.fromType(o);
		if (data == null) throw new IllegalArgumentException("无法获取"+o.getName()+"的类文件");

		int _init = data.getMethod("<init>", "()V");
		if (_init < 0) {
			if ((flag & SERIALIZE_ONLY) == 0) throw new IllegalArgumentException("找不到无参构造器"+o.getName());
		} else if ((data.methods.get(_init).modifier() & ACC_PUBLIC) == 0) {
			if (!MAGIC_ADAPTER) throw new IllegalArgumentException("MagicAdapter没有激活,不能跳过无参构造器生成对象"+o.getName());
			if ((flag & SERIALIZE_PUBLIC_ONLY) != 0) throw new IllegalArgumentException("UNSAFE: "+o.getName()+".<init>");
		}

		fieldIds.clear();
		parentExist.clear();
		TypeAdapter parentSerInst;
		int optional = 0;
		optionalEx = null;
		if ((flag & SERIALIZE_PARENT) != 0 && !"java/lang/Object".equals(data.parent()) && data.parent() != null) {
			parentSerInst = make(o.getSuperclass(), data.parent(), perClassFlag.applyAsInt(o.getSuperclass()));
			assert parentSerInst instanceof GA;

			begin(null);
			currentObject = o.getName().replace('.', '/');

			parentSer = c.newField(ACC_PRIVATE, "Sp", "Lroj/config/mapper/TypeAdapter;");
			serializerId.putInt(null, parentSer);
			copy.insn(ALOAD_0);
			copy.insn(ALOAD_1);
			copy.ldc(new CstClass(data.parent()));
			copy.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/Factory", "gpa", "(Ljava/lang/Class;)Lroj/config/mapper/TypeAdapter;");
			copy.field(PUTFIELD, c, parentSer);

			IntBiMap<String> oldFieldIds = ((GA) parentSerInst).fn();
			fieldIds.putAll(oldFieldIds);
			if (oldFieldIds.size() > 32) optionalEx = new BitSet();
			optional = parentSerInst.plusOptional(0, optionalEx);
			for (FieldNode field : data.fields) {
				if (oldFieldIds.containsValue(field.name()))
					throw new IllegalArgumentException("重复的字段名称:"+field+", 位于"+data.name()+",先前位于（或它的父类）:"+data.parent());
			}

			for (Method mn : parentSerInst.getClass().getDeclaredMethods()) {
				if (mn.getName().equals("read")) {
					int type = Type.from(mn.getParameterTypes()[1]).getActualType();
					parentExist.add(type);
				}
			}
		} else {
			parentSer = -1;
			parentSerInst = null;

			begin(null);
			currentObject = o.getName().replace('.', '/');
		}

		c.addAttribute(new StringAttribute("SourceFile", o.getName()));
		var cw = c.newMethod(ACC_PUBLIC | ACC_FINAL, "toString", "()Ljava/lang/String;");
		cw.visitSize(1, 1);
		cw.ldc(o.getName());
		cw.insn(ARETURN);
		cw.finish();

		int fieldIdKey = c.newField(ACC_PRIVATE|ACC_STATIC, "FIELD_ID", "Lroj/collect/IntBiMap;");
		// region fieldId
		cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "fn", "()Lroj/collect/IntBiMap;");
		cw.visitSize(1,1);
		cw.field(GETSTATIC, c, fieldIdKey);
		cw.insn(ARETURN);
		cw.finish();
		// endregion
		// region key函数
		if ((flag& NO_SCHEMA) != 0) {
			cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "key", "(Lroj/config/mapper/MappingContext;Ljava/lang/String;)V");
			cw.visitSize(3,3);

			cw.insn(ALOAD_0);
			cw.insn(ALOAD_1);
			cw.insn(ALOAD_2);
			cw.invokeS("java/lang/Integer", "parseInt", "(Ljava/lang/String;)I");
			cw.invokeV(c.name(), "key", "(Lroj/config/mapper/MappingContext;I)V");
			cw.insn(RETURN);
			cw.finish();

			cw = keyCw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "key", "(Lroj/config/mapper/MappingContext;I)V");
			cw.visitSize(3,3);
			cw.insn(ILOAD_2);
		} else {
			cw = keyCw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "key", "(Lroj/config/mapper/MappingContext;Ljava/lang/String;)V");
			cw.visitSize(3,4);

			cw.field(GETSTATIC, c, fieldIdKey);
			cw.insn(ALOAD_2);
			cw.insn(ICONST_M1);
			cw.invoke(DIRECT_IF_OVERRIDE, "roj/collect/IntBiMap", "getByValueOrDefault", "(Ljava/lang/Object;I)I");
			cw.insn(DUP);
			cw.insn(ISTORE_3);
		}

		keyPrimitive = null;
		keySwitch = SwitchBlock.ofAuto();
		cw.addSegment(keySwitch);
		keySwitch.def = cw.label();

		if (parentSerInst != null) {
			invokeParent(cw, (flag& NO_SCHEMA) != 0 ? ILOAD : ALOAD);
		} else {
			cw.insn(ALOAD_1);
			cw.field(GETSTATIC, "roj/config/mapper/Skip", "INST", "Lroj/config/mapper/TypeAdapter;");
			cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/MappingContext", "push", "(Lroj/config/mapper/TypeAdapter;)V");
		}
		cw.insn(RETURN);
		// endregion
		// region write()
		write = c.newMethod(ACC_PUBLIC|ACC_FINAL, "writeMap", "(Lroj/config/ValueEmitter;Ljava/lang/Object;)V");
		write.visitSizeMax(3,3);

		write.insn(ALOAD_2);
		write.clazz(CHECKCAST, data.name());
		write.insn(ASTORE_2);

		if (parentSerInst != null) invokeParent(write, ALOAD);
		// endregion

		int fieldId = parentSerInst == null ? 0 : parentSerInst.fieldCount();

		CstInt count = new CstInt(fieldId);
		// region map()
		cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "map", "(Lroj/config/mapper/MappingContext;I)V");
		cw.visitSize(3,3);
		cw.insn(ALOAD_1);
		if (_init < 0) cw.clazz(NEW, data.name());
		else cw.newObject(data.name());
		cw.invokeV("roj/config/mapper/MappingContext", "setRef", "(Ljava/lang/Object;)V");
		fieldIdM1(cw);
		cw.insn(RETURN);
		// endregion
		// region fieldCount()
		cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "fieldCount", "()I");
		cw.visitSize(1,1);
		cw.ldc(count);
		cw.insn(IRETURN);
		// endregion

		String defaultWriteMode = "ALWAYS";
		String defaultReadMode = "REQUIRED";

		ArrayList<FieldNode> fields = data.fields;
		var list1 = Annotations.getAnnotations(data.cp, data, false);
		list1 = fp.customizeFieldAnnotations(data, null, list1);
		for (int i1 = 0; i1 < list1.size(); i1++) {
			var opt = list1.get(i1);
			switch (opt.type()) {
				case "roj/config/mapper/FieldOrder" -> {
					var nodeName = opt.getList("value");
					FieldNode[] nodes = new FieldNode[nodeName.size()];
					for (int i = 0; i < nodes.length; i++) {
						int fieldId1 = data.getField(nodeName.getString(i));
						if (fieldId1 < 0)
							throw new IllegalStateException("在" + data.name() + "中找不到名为" + nodeName.get(i) + "的字段");
						nodes[i] = fields.get(fieldId1);
					}
					fields = ArrayList.asModifiableList(nodes);
				}
				case "roj/config/mapper/Optional" -> {
					defaultWriteMode = opt.getEnumValue("write", "NON_NULL");
					defaultReadMode = opt.getEnumValue("read", "OPTIONAL");
				}
			}
		}

		for (int i = 0; i < fields.size(); i++) {
			FieldNode f = fields.get(i);
			List<Annotation> list = Annotations.getAnnotations(data.cp, f, false);
			list = fp.customizeFieldAnnotations(data, f, list);

			if ((f.modifier() & (ACC_TRANSIENT|ACC_STATIC|ACC_SYNTHETIC)) != 0) continue;

			int unsafe = 0;
			if ((f.modifier() & ACC_PUBLIC) == 0) unsafe |= 3; // 1|2
			if ((f.modifier() & ACC_FINAL) != 0) unsafe |= 5; // 1|4

			String name = f.name();
			MethodNode get = null, set = null;
			String writeMode = defaultWriteMode;
			String readMode = defaultReadMode;
			AsType as = null;
			String comment = null;

			for (int j = 0; j < list.size(); j++) {
				Annotation anno = list.get(j);
				switch (anno.type()) {
					case "roj/config/mapper/Comment": comment = anno.getString("value"); break;
					case "roj/config/mapper/Name": name = anno.getString("value"); break;
					case "roj/config/mapper/Via": {
						String sid = anno.getString("get", null);
						if (sid != null) {
							int id = data.getMethod(sid, "()".concat(f.rawDesc()));
							if (id < 0) throw new IllegalArgumentException("无法找到get方法" + anno);
							get = data.methods.get(id);
							unsafe &= 5;
						}
						sid = anno.getString("set", null);
						if (sid != null) {
							int id = data.getMethod(sid, "("+f.rawDesc()+")V");
							if (id < 0) throw new IllegalArgumentException("无法找到set方法" + anno);
							set = data.methods.get(id);
							unsafe &= 2;
						}
						break;
					}
					case "roj/config/mapper/Optional":
						writeMode = anno.getEnumValue("write", "NON_NULL");
						readMode = anno.getEnumValue("read", "OPTIONAL");
						break;
					case "roj/config/mapper/As":
						as = asTypes.get(anno.getString("value"));
						if (as == null) throw new IllegalArgumentException("Unknown as " + anno);
				}
			}

			boolean noRead = readMode.equals("IGNORED");
			if (!noRead) {
				if (readMode.equals("OPTIONAL")) {
					// add this to read optional
					if (fieldId < 32) {
						optional |= 1 << fieldId;
					} else {
						if (optionalEx == null) optionalEx = new BitSet();
						optionalEx.add(fieldId-32);
					}
					// writeMode==ALWAYS的处理在value函数里
				}

				fieldIds.put(fieldId, name);
			}

			if (unsafe != 0 ||
				set != null && (set.modifier()&ACC_PUBLIC) == 0 ||
				get != null && (get.modifier()&ACC_PUBLIC) == 0) {
				if ((flag & SERIALIZE_PUBLIC_ONLY) != 0)
					throw new RuntimeException("无权访问"+data.name()+"."+f+" ("+unsafe+")\n" +
					"解决方案:\n" +
					" 1. 开启UNSAFE模式\n" +
					" 2. 定义setter/getter\n" +
					" 3. 为字段或s/g添加public");
				if (!MAGIC_ADAPTER)
					throw new RuntimeException("无权访问"+data.name()+"."+f+" ("+unsafe+")\n" +
						"解决方案: 换用支持的JVM");
			}

			if ((flag & NO_SCHEMA) != 0) name = null;
			asmStruct(fieldId, data, f, name, get, set, writeMode, noRead, as, (unsafe&4) != 0 ? o : null, comment);

			if (!noRead) fieldId++;
		}

		count.value = fieldId;

		write.insn(RETURN);
		write.finish();
		write = null;

		CodeWriter init = c.newMethod(ACC_PUBLIC|ACC_FINAL, "init", "(Lroj/collect/IntBiMap;Lroj/collect/BitSet;)V");
		init.visitSize(1, 3);
		init.insn(ALOAD_1);
		init.field(PUTSTATIC, c, fieldIdKey);

		if (optional != 0 || optionalEx != null) {
			cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "isOptional", "()Z");
			cw.visitSize(1, 1);
			cw.insn(ICONST_1);
			cw.insn(IRETURN);

			// region optional
			cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "plusOptional", "(ILroj/collect/BitSet;)I");
			cw.visitSize(2,3);
			if (parentSer >= 0) {
				invokeParent(cw, ILOAD);
				cw.insn(ISTORE_1);
			}
			if (optionalEx != null) {
				int fid = c.newField(ACC_PRIVATE|ACC_STATIC, "OPTIONAL", "Lroj/collect/BitSet;");
				cw.insn(ALOAD_2);
				cw.field(GETSTATIC, c, fid);
				cw.invoke(DIRECT_IF_OVERRIDE, "roj/collect/BitSet", "addAll", "(Lroj/collect/BitSet;)Lroj/collect/BitSet;");
				cw.insn(POP);

				init.insn(ALOAD_2);
				init.field(PUTSTATIC, c, fid);
			}
			cw.insn(ILOAD_1);
			if (optional != 0) {
				cw.ldc(new CstInt(optional));
				cw.insn(IOR);
			}
			cw.insn(IRETURN);
			// endregion
		}

		for (IntIterator itr = parentExist.iterator(); itr.hasNext(); ) {
			createReadMethod(data, itr.nextInt());
		}

		init.insn(RETURN);
		init.finish();

		return build(o.getClassLoader());
	}

	private String currentObject;
	private int parentSer;
	private final BitSet parentExist = new BitSet();
	private void invokeParent(CodeWriter cw, byte opcode) {
		cw.insn(ALOAD_0);
		cw.field(GETFIELD, c, parentSer);
		cw.insn(ALOAD_1);
		cw.vars(opcode, 2);
		cw.invoke(INVOKEVIRTUAL, "roj/config/mapper/TypeAdapter", cw.mn.name(), cw.mn.rawDesc());
	}

	private void asmStruct(final int fieldId, final ClassNode data, final FieldNode fn,
						   final String actualName, final MethodNode get, final MethodNode set,
						   final String writeMode, final boolean noRead, final AsType as,
						   final Class<?> unsafePut,
						   final String comment) {
		Type type = as != null ? as.output : fn.fieldType();
		int actualType = type.getActualType();
		int methodType = switch (actualType) {
			// char序列化成字符串
			case Type.CHAR -> Type.CLASS;
			case Type.BYTE, Type.SHORT -> Type.INT;
			//case Type.FLOAT: methodType = Type.DOUBLE; break;
			default -> actualType;
		};

		CodeWriter cw;
		int asId = -1;

		byte myCode = type.getOpcode(ILOAD);
		if (!noRead) while (true) {
		Tmp2 t = readMethods.get(methodType);
		if (t == null) t = createReadMethod(data, methodType);

		cw = t.cw;
		t.seg.branch(fieldId, cw.label());

		cw.insn(ALOAD_1);
		cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/MappingContext", "setFieldHook", "()V");

		cw.vars(ALOAD, t.pos);

		if (as != null && as.ref != null) {
			String asName = "as$"+as.type().replace('/', '`');
			asId = c.getField(asName);
			if (asId < 0) {
				asId = c.newField(ACC_PRIVATE, asName, "L"+as.type()+";");

				copy.insn(ALOAD_0);
				copy.insn(ALOAD_1);
				copy.ldc(new CstString(as.name));
				copy.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/Factory", "gas", "(Ljava/lang/String;)Ljava/lang/Object;");
				copy.clazz(CHECKCAST, as.type());
				copy.field(PUTFIELD, c, asId);
			}

			cw.insn(ALOAD_0);
			cw.field(GETFIELD, c, asId);
		}

		if (actualType == Type.CHAR) {
			// 特殊处理: ((String)o).charAt(0);
			cw.insn(ALOAD_2);
			cw.clazz(CHECKCAST, "java/lang/String");
			cw.insn(ICONST_0);
			cw.invoke(DIRECT_IF_OVERRIDE, "java/lang/String", "charAt", "(I)C");
		} else {
			if (myCode == -1) {
				cw.vars(ALOAD, 2);
				cw.invoke(INVOKESTATIC, "roj/config/mapper/Factory", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
				switch (actualType) { // BOOLEAN = 'Z', BYTE = 'B', CHAR = 'C', SHORT
					case Type.BOOLEAN: cw.invoke(INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z"); break;
					case Type.BYTE: cw.invoke(INVOKESTATIC, "java/lang/Byte", "parseByte", "(Ljava/lang/String;)B"); break;
					case Type.SHORT: cw.invoke(INVOKESTATIC, "java/lang/Short", "parseShort", "(Ljava/lang/String;)S"); break;
					case Type.INT: cw.invoke(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I"); break;
					case Type.LONG: cw.invoke(INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;)J"); cw.visitSizeMax(4,0); break;
					case Type.FLOAT: cw.invoke(INVOKESTATIC, "java/lang/Float", "parseFloat", "(Ljava/lang/String;)F"); break;
					case Type.DOUBLE: cw.invoke(INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D"); cw.visitSizeMax(4,0); break;
				}
			} else {
				cw.vars(myCode, 2);
				if (actualType == Type.CLASS) {
					String type1 = type.getActualClass();
					if (type1.equals("java/lang/String")) {
						cw.invoke(INVOKESTATIC, "roj/config/mapper/Factory", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
					} else {
						cw.clazz(CHECKCAST, type1);
					}
				}
			}
		}

		if (methodType != actualType && myCode != -1) {
			switch (actualType) {
				case Type.FLOAT -> cw.insn(methodType == Type.DOUBLE ? D2F : methodType == Type.LONG ? L2F : I2F);
				case Type.DOUBLE -> cw.insn(methodType == Type.LONG ? L2F : I2F);
				case Type.LONG -> {cw.insn(I2L);cw.visitSizeMax(4, 0);}
			}
		}

		if (as != null) {
			if (as.fieldType) cw.ldc(fn.rawDesc());
			cw.invoke(as.ref == null ? INVOKESTATIC : INVOKEVIRTUAL, as.reader);
		}

		if (set == null) {
			if (unsafePut != null) {
				//noinspection MagicConstant
				Type fType = Type.primitive(actualType);
				cw.varStore(fType, 2);
				cw.field(GETSTATIC, "roj/reflect/Unaligned", "U", "Lroj/reflect/Unaligned;");
				cw.insn(ALOAD_3);
				cw.ldc(Unaligned.fieldOffset(unsafePut, fn.name()));
				cw.varLoad(fType, 2);
				cw.invokeItf("roj/reflect/Unaligned", "put"+Reflection.capitalizedType(fType), "(Ljava/lang/Object;J"+(fType.isPrimitive() ? fType.toDesc() : "Ljava/lang/Object;")+")V");
				cw.visitSizeMax(5+fType.length(), 0);
			} else {
				cw.field(PUTFIELD, data.name(), fn.name(), fn.rawDesc());
			}
		} else cw.invoke(INVOKEVIRTUAL, set);

		cw.insn(RETURN);
		switch (methodType) {
			case Type.FLOAT: methodType = Type.DOUBLE; myCode = DLOAD; continue;
			case Type.DOUBLE: methodType = Type.LONG; myCode = LLOAD; cw.visitSizeMax(4,0); continue;
			case Type.LONG: methodType = Type.INT; myCode = ILOAD; cw.visitSizeMax(4,0); continue;
			// parseInt | parseFloat
			case Type.INT: methodType = Type.CLASS; myCode = -1; continue;
		}
		break;
		}

		Label skip = null;
		if (!"SKIP".equals(writeMode)) {
			cw = write;
			// 如果可选并且不是基本类型
			_ActualTypeIsClass:
			if (!"ALWAYS".equals(writeMode)) {
				skip = new Label();

				cw.insn(ALOAD_2);
				if (get == null) cw.field(GETFIELD, data.name(), fn.name(), fn.rawDesc());
				else cw.invoke(INVOKEVIRTUAL, get);

				switch (actualType) {
					case Type.CLASS -> {
						cw.insn(DUP);

						if (writeMode.equals("CUSTOM")) {
							assert false : "未实现";
							// cw.field(GETFIELD, "");
							// Nullable
							cw.invokeItf("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z");
							cw.jump(IFNE, skip);
							break _ActualTypeIsClass;
						}

						cw.vars(ASTORE, 4);
						cw.visitSizeMax(0, 5);
						cw.jump(IFNULL, skip);

						if (writeMode.equals("NON_BLANK")) {
							var actualTypeName = type.getActualClass();
							if (actualTypeName.startsWith("[")) {
								cw.vars(ALOAD, 4);
								cw.insn(ARRAYLENGTH);
								cw.jump(IFEQ, skip);
							} else {
								var inferrer = genericInferrer();
								if ("java/lang/String".equals(actualTypeName)) {
									cw.vars(ALOAD, 4);
									cw.invokeV("java/lang/String", "isEmpty", "()Z");
									cw.jump(IFNE, skip);
								} else if (inferrer.instanceOf(actualTypeName, "java/lang/CharSequence")) {
									cw.vars(ALOAD, 4);
									cw.invokeItf("java/lang/CharSequence", "length", "()I");
									cw.jump(IFEQ, skip);
								} else if (inferrer.instanceOf(actualTypeName, "java/util/Map")) {
									cw.vars(ALOAD, 4);
									cw.invokeItf("java/util/Map", "isEmpty", "()Z");
									cw.jump(IFNE, skip);
								} else if (inferrer.instanceOf(actualTypeName, "java/util/Collection")) {
									cw.vars(ALOAD, 4);
									cw.invokeItf("java/util/Collection", "isEmpty", "()Z");
									cw.jump(IFNE, skip);
								} else if ("java/util/Optional".equals(actualTypeName)) {
									cw.vars(ALOAD, 4);
									cw.invokeV("java/util/Optional", "isPresent", "()Z");
									cw.jump(IFEQ, skip);
								}
							}
						}

						break _ActualTypeIsClass;
					}
					case Type.LONG -> {
						cw.insn(LCONST_0);
						cw.insn(LCMP);
						cw.visitSizeMax(4, 0);
					}
					case Type.FLOAT -> {
						cw.insn(FCONST_0);
						cw.insn(FCMPG);
					}
					case Type.DOUBLE -> {
						cw.insn(DCONST_0);
						cw.insn(DCMPG);
						cw.visitSizeMax(4, 0);
					}
				}
				cw.jump(IFEQ, skip);
			}

			if (comment != null) {
				cw.insn(ALOAD_1);
				cw.ldc(comment);
				cw.invokeItf("roj/config/ValueEmitter", "comment", "(Ljava/lang/String;)V");
			}
			cw.insn(ALOAD_1);
			if (actualName == null) {
				cw.ldc(fieldId);
				cw.invokeItf("roj/config/ValueEmitter", "intKey", "(I)V");
			} else {
				cw.ldc(new CstString(actualName));
				cw.invokeItf("roj/config/ValueEmitter", "key", "(Ljava/lang/String;)V");
			}
		}

		cw = keyCw;
		block:
		if (actualType == Type.CLASS && !"java/lang/String".equals(type.getActualClass())) {
			int id;
			String serType = type.getActualClass();
			Signature serSig = fn.getAttribute(data.cp, Attribute.SIGNATURE);
			id = ser(serType, serSig != null ? typeParamToRaw(serSig) : null);

			keySwitch.branch(fieldId, cw.label());
			cw.insn(ALOAD_1);
			cw.insn(actualName == null ? ILOAD_2 : ILOAD_3);
			cw.insn(ALOAD_0);
			cw.field(GETFIELD, c, id);
			cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/MappingContext", "pushHook", "(ILroj/config/mapper/TypeAdapter;)V");

			cw.insn(RETURN);

			if ("SKIP".equals(writeMode)) return;
			cw = write;
			cw.insn(ALOAD_0);
			cw.field(GETFIELD, c, id);
			cw.insn(ALOAD_1);
			if (as != null && as.ref != null) {
				assert !as.user;
				cw.visitSizeMax(4,0);
				cw.insn(ALOAD_0);
				cw.field(GETFIELD, c, asId);
			}

			if (skip != null) cw.vars(ALOAD, 4);
			else {
				cw.insn(ALOAD_2);
				if (get == null) cw.field(GETFIELD, data.name(), fn.name(), fn.rawDesc());
				else cw.invoke(INVOKEVIRTUAL, get);
			}

			if (as != null) cw.invoke(as.ref == null ? INVOKESTATIC : INVOKEVIRTUAL, as.writer);

			cw.invoke(INVOKEVIRTUAL, "roj/config/mapper/TypeAdapter", "write", "(Lroj/config/ValueEmitter;Ljava/lang/Object;)V");
		} else {
			if (keyPrimitive == null) {
				keyPrimitive = cw.label();
				cw.insn(ALOAD_1);
				cw.insn(actualName == null ? ILOAD_2 : ILOAD_3);
				cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/MappingContext", "setKeyHook", "(I)V");

				cw.insn(RETURN);
			}
			keySwitch.branch(fieldId, keyPrimitive);

			if ("SKIP".equals(writeMode)) return;
			cw = write;
			if (as != null) {
				if (!as.user) cw.insn(ALOAD_1);
				if (as.ref != null) {
					cw.insn(ALOAD_0);
					cw.field(GETFIELD, c, asId);
				}
			} else {
				cw.insn(ALOAD_1);
			}

			// String
			if (skip != null && actualType == Type.CLASS) cw.vars(ALOAD, 4);
			else {
				cw.insn(ALOAD_2);
				if (get == null) cw.field(GETFIELD, data.name(), fn.name(), fn.rawDesc());
				else cw.invoke(INVOKEVIRTUAL, get);
			}

			if (as != null) {
				if (as.user) cw.insn(ALOAD_1);
				cw.invoke(as.ref == null ? INVOKESTATIC : INVOKEVIRTUAL, as.writer);
				if (as.user) break block;
			}

			if ("java/lang/String".equals(type.getActualClass())) {
				cw.invokeS("roj/config/mapper/TypeAdapter", "value", "(Lroj/config/ValueEmitter;Ljava/lang/String;)V");
			} else {
				cw.invokeItf("roj/config/ValueEmitter", "value", "("+(char)actualType+")V");
			}
		}

		if (skip != null) cw.label(skip);
	}
	private String typeParamToRaw(Signature sig) {
		var mapper = new AbstractMap<String, IType>() {
			@Override public Set<Entry<String, IType>> entrySet() {return Collections.emptySet();}
			@Override public IType get(Object key) {return Signature.any();}
		};

		List<IType> values = sig.values;
		for (int i = 0; i < values.size(); i++)
			values.set(i, Inferrer.clearTypeParam(values.get(i), mapper, null));
		return sig.toDesc();
	}

	private Tmp2 createReadMethod(ClassNode data, int methodType) {
		String desc;
		int size = 1;
		switch (methodType) {
			case Type.CLASS: desc = "(Lroj/config/mapper/MappingContext;Ljava/lang/Object;)V"; break;
			case Type.INT: desc = "(Lroj/config/mapper/MappingContext;I)V"; break;
			case Type.FLOAT: desc = "(Lroj/config/mapper/MappingContext;F)V"; break;
			case Type.DOUBLE: desc = "(Lroj/config/mapper/MappingContext;D)V"; size = 2; break;
			case Type.LONG: desc = "(Lroj/config/mapper/MappingContext;J)V"; size = 2; break;
			case Type.BOOLEAN: desc = "(Lroj/config/mapper/MappingContext;Z)V"; break;
			default: throw new IllegalStateException("Unexpected value: " + methodType);
		}

		Tmp2 t = new Tmp2();
		readMethods.put(methodType,t);
		CodeWriter cw = t.cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "read", desc);

		t.seg = SwitchBlock.ofAuto();
		t.seg.def = new Label();

		cw.visitSize(3, size+3);

		cw.insn(ALOAD_1);
		cw.field(GETFIELD, "roj/config/mapper/MappingContext", "ref", "Ljava/lang/Object;");
		cw.clazz(CHECKCAST, data.name());
		cw.vars(ASTORE, t.pos = (byte) (size+2));

		cw.insn(ALOAD_1);
		cw.field(GETFIELD, "roj/config/mapper/MappingContext", "fieldId", "I");
		cw.addSegment(t.seg);

		cw.label(t.seg.def);
		if (parentExist.remove(methodType)) {
			invokeParent(cw, Type.methodDesc(desc).get(1).getOpcode(ILOAD));
			cw.insn(RETURN);
		} else {
			cw.insn(ALOAD_1);
			cw.insn(ALOAD_0);
			cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/MappingContext", "ofIllegalType", "(Lroj/config/mapper/TypeAdapter;)V");
			cw.insn(RETURN);
		}

		if (methodType == Type.CLASS) {
			t.seg.branch(-2, cw.label());
			cw.insn(ALOAD_1);
			cw.insn(ICONST_1);
			cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/mapper/MappingContext", "popd", "(Z)V");
			cw.insn(RETURN);
		}
		return t;
	}

	private CodeWriter keyCw;
	private Label keyPrimitive;
	private SwitchBlock keySwitch;

	private static class Tmp2 {
		CodeWriter cw;
		SwitchBlock seg;
		byte pos;
	}
	private final IntMap<Tmp2> readMethods = new IntMap<>();
	private final IntBiMap<String> fieldIds = new IntBiMap<>();

	private BitSet optionalEx;

	private static void fieldIdM1(CodeWriter c) {
		c.insn(ALOAD_1);
		c.insn(ICONST_M1);
		c.field(PUTFIELD, "roj/config/mapper/MappingContext", "fieldId", "I");
	}
	// endregion

	private ClassNode c;
	private CodeWriter copy, write;
	private final ToIntMap<String> serializerId = new ToIntMap<>();

	private int ser(String type, String generic) {
		String _name = generic == null ? type : type.concat(generic);
		int id = serializerId.getOrDefault(_name,-1);
		if (id >= 0) return id;

		id = c.newField(ACC_PRIVATE, "S"+c.fields.size(), "Lroj/config/mapper/TypeAdapter;");
		serializerId.putInt(_name,id);

		copy.insn(ALOAD_0);
		if (!type.equals(currentObject)) {
			copy.insn(ALOAD_1);
			copy.ldc(new CstClass(type));
			if (generic == null || generic.equals("java/lang/Object")) {
				copy.invoke(INVOKEVIRTUAL, "roj/config/mapper/Factory", "get", "(Ljava/lang/Class;)Lroj/config/mapper/TypeAdapter;");
			} else {
				copy.ldc(new CstString(generic));
				copy.invoke(INVOKEVIRTUAL, "roj/config/mapper/Factory", "gsa", "(Ljava/lang/Class;Ljava/lang/String;)Lroj/config/mapper/TypeAdapter;");
			}
		} else {
			copy.insn(ALOAD_0);
		}
		copy.field(PUTFIELD, c, id);
		return id;
	}
	private void begin(String ua) {
		c = new ClassNode();
		c.modifier = ACC_PUBLIC|ACC_SUPER|ACC_FINAL;
		c.name((ua==null? "roj/config/mapper/GA$" :ua+"$")+Reflection.uniqueId());
		c.parent("roj/config/mapper/TypeAdapter");
		c.cloneable();
		c.itfList().clear();
		c.addInterface("roj/config/mapper/GA");

		copy = c.newMethod(ACC_PUBLIC|ACC_FINAL, "init2", "(Lroj/config/mapper/Factory;Ljava/lang/Object;)V");
		copy.visitSize(4, 3);
	}
	private TypeAdapter build(ClassLoader cl) {
		if (LOADER_CHAIN.contains(cl)) cl = ClassDefiner.APP_LOADER;

 		ClassNode c1 = c;
		c = null;
		// 2024/7/23 让ConstantPool可以释放
		keyCw = null;
		keySwitch = null;
		keyPrimitive = null;

		if (serializerId.isEmpty()) {
			c1.methods.remove(c1.getMethod("init2"));
		} else {
			copy.insn(RETURN);
			copy.finish();
		}
		copy = null;

		readMethods.clear();
		serializerId.clear();

		return (TypeAdapter) ClassDefiner.newInstance(c1, cl);
	}
	// endregion
}