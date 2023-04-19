package roj.config.serial;

import org.jetbrains.annotations.Nullable;
import roj.asm.OpcodeUtil;
import roj.asm.Parser;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstInt;
import roj.asm.cst.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.AttrUTF;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.SwitchEntry;
import roj.asm.type.*;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.LongByeBye;
import roj.asm.visitor.SwitchSegment;
import roj.collect.*;
import roj.config.data.CEntry;
import roj.config.data.CNull;
import roj.io.IOUtil;
import roj.reflect.FastInit;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.ToIntFunction;

import static roj.asm.Opcodes.*;
import static roj.asm.util.AccessFlag.*;

/**
 * @author Roj233
 * @version 2.0
 * @since 2022/1/11 17:49
 */
public final class SerializerManager {
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

		public AsType(String name, MethodNode writer, MethodNode reader, Object o) {
			this.name = name;
			this.ref = o;
			this.reader = reader;
			this.writer = writer;
			this.output = writer.returnType();
		}

		public String klass() { return reader.ownerClass(); }
	}

	public static final int
		GENERATE = 1, CHECK_INTERFACE = 2, CHECK_PARENT = 4,
		DYNAMIC = 8, IGNORE_UNKNOWN = 16, NO_CONSTRUCTOR = 32;
	public int flag;
	public ToIntFunction<Class<?>> flagGetter;
	private int flagFor(Class<?> className) {
		return flagGetter == null ? flag : flagGetter.applyAsInt(className);
	}

	@Deprecated
	public CEntry serialize(Object o) {
		if (o == null) return CNull.NULL;
		ToEntry c = new ToEntry();
		adapter(o.getClass()).write(c, Helpers.cast(o));
		return c.get();
	}

	@Deprecated
	public <T> T deserialize(Class<T> type, CEntry entry) {
		CAdapter<T> des = adapter(type);
		entry.forEachChild(des);
		return des.result();
	}

	public SerializerManager() {
		this(GENERATE|CHECK_INTERFACE|CHECK_PARENT);
	}
	public SerializerManager(int flag) {
		this.flag = flag;

		ObjAny any = new ObjAny(this);

		//localRegistry.put(Object.class, any);
		localRegistry.put("java/util/Map", new MapSer(any));
		ListSer LIST = new ListSer(any, false);
		localRegistry.put("java/util/List", LIST);
		localRegistry.put("java/util/Set", new ListSer(any, true));
		localRegistry.put("java/util/Collection", LIST);
	}

	public SerializerManager register(Class<?> cls, Object o) {
		if (cls.isPrimitive() || cls == String.class) throw new IllegalStateException("你覆盖啥呢(input不能是基本类型)");

		String name = "U|"+cls.getName().replace('.', '/');
		Adapter ser = GENERATED.get(name);
		if (ser == null) {
			ConstantData data = Parser.parse(o.getClass());
			if (data == null) throw new IllegalArgumentException("无法获取"+o+"的类文件");

			Type clsType = TypeHelper.class2type(cls);
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

		ser = ((GenAdapter) ser).copy(this, o);

		synchronized (localRegistry) {
			localRegistry.put(cls.getName().replace('.', '/'), ser);
		}
		return this;
	}
	public SerializerManager registerAsType(String type, Class<?> cls, Object o) {
		ConstantData data = Parser.parse(o.getClass());
		if (data == null) throw new IllegalArgumentException("无法获取"+o+"的类文件");

		Type clsType = TypeHelper.class2type(cls);
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
			asTypes.put(type, new AsType(type, writer, reader, o));
		}
		return this;
	}

	@SuppressWarnings("unchecked")
	public <T> CAdapter<T> adapter(Class<T> type) {
		return (CAdapter<T>) new AdaptContext(make(null, type, null, false));
	}
	public CAdapter<?> adapter(IType generic) {
		Adapter root = get(generic);
		if (root == null) return null;
		return new AdaptContext(root);
	}
	public <T> CAdapter<List<T>> listOf(Class<T> content) {
		Adapter root = get(Generic.parameterized(List.class, content));
		assert root != null;
		return Helpers.cast(new AdaptContext(root));
	}
	public <T> CAdapter<Map<String, T>> mapOf(Class<T> content) {
		Adapter root = get(Generic.parameterized(Map.class, String.class, content));
		assert root != null;
		return Helpers.cast(new AdaptContext(root));
	}

	final Object getAsType(String as) {
		AsType a = asTypes.get(as);
		if (a == null) throw new IllegalStateException("Missing as-type adapter " + as);
		return a.ref;
	}
	final Adapter get(Class<?> type) { return make(null, type, null, false); }
	final Adapter get(Class<?> type, String generic) {
		IType genericType = null;

		if ((flagFor(type) & DYNAMIC) != 0) {
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
			return null;
		}

		if (type == generic || (flagFor(klass) & DYNAMIC) != 0) {
			return make(null, klass, null, true);
		} else {
			return make(generic.toDesc(), klass, (Generic) generic, true);
		}
	}
	final Adapter getByName(String name) { return make(name, null, null, false); }

	private static boolean isInvalid(Class<?> type) {
		if (type.getComponentType() != null) return false;
		if ((type.getModifiers() & (ABSTRACT|INTERFACE)) != 0) return true;
		return type == Object.class;
	}

	private Adapter make(String name, Class<?> type, Generic generic, boolean checking) {
		Adapter ser = localRegistry.get(name!=null?name:(name=type.getName().replace('.', '/')));
		if (ser != null) return ser;

		if (type == null) {
			try {
				type = Class.forName(name, false, SerializerManager.class.getClassLoader());
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
			CharList sb = IOUtil.getSharedCharBuf();
			Class<?> c = type;
			while (true) {
				if ((flag & CHECK_INTERFACE) != 0) {
					for (Class<?> itf : c.getInterfaces()) {
						sb.clear();
						ser = localRegistry.get(sb.append(itf.getName()).replace('.', '/'));
						if (ser != null) break findExist;
					}
				}

				c = c.getSuperclass();
				if (c == null) break;

				if ((flag & CHECK_PARENT) != 0) {
					sb.clear();
					ser = localRegistry.get(sb.append(c.getName()).replace('.', '/'));
					if (ser != null) break findExist;
				}
			}
		}

		if (ser != null) {
			if (generic != null) ser = ser.withGenericType(this, generic.children);
			synchronized (localRegistry) {
				localRegistry.put(name, ser);
			}
			return ser;
		}

		if ((flag & GENERATE) == 0) throw new IllegalArgumentException("未找到"+name+"的序列化器");

		if (isInvalid(type)) {
			if (checking) return null;
			throw new IllegalArgumentException(name+"无法被序列化");
		}

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
			ser = ((GenAdapter) ser).copy(this, null);
		}

		synchronized (localRegistry) {
			localRegistry.put(name, ser);
		}

		return ser;
	}

	private Adapter array(Class<?> type) {
		type = type.getComponentType();
		if (type.isPrimitive()) {
			byte[] b;
			try {
				b = IOUtil.readRes("roj/config/serial/PrimArr.class");
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

			c.putAttr(new AttrUTF("SourceFile", "Adapter@"+asmType));
			c.name("roj/config/serial/PAS$"+asmType);

			FastInit.prepare(c);
			return (Adapter) FastInit.make(c);
		} else {
			return new ObjArr(type, make(null, type, null, false));
		}
	}

	private void copyArrayRef(ConstantData c, char type) {
		CodeWriter cw = c.newMethod(PUBLIC|FINAL, "read", "(Lroj/config/serial/AdaptContext;["+type+")Z");
		cw.visitSize(2,3);
		cw.one(ALOAD_1);
		cw.one(ALOAD_2);
		cw.field(PUTFIELD, "roj/config/serial/AdaptContext", "ref", "Ljava/lang/Object;");
		cw.one(ALOAD_1);
		cw.one(ICONST_1);
		cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/AdaptContext", "popd", "(Z)V");
		cw.one(ICONST_1);
		cw.one(IRETURN);
	}

	private void addUpgrader(ConstantData c, byte code) {
		String orig = OpcodeUtil.toString0(code);
		String id = orig.replace('L', 'J');

		CodeWriter ir = c.newMethod(PUBLIC|FINAL, "read", "(Lroj/config/serial/AdaptContext;"+id.charAt(0)+")V");
		ir.visitSize(3,3);
		ir.one(ALOAD_0);
		ir.one(ALOAD_1);
		ir.one((byte) OpcodeUtil.getByName().getInt(orig.charAt(0)+"LOAD_1"));
		ir.one(code);
		ir.invoke(DIRECT_IF_OVERRIDE, c.name, "read", "(Lroj/config/serial/AdaptContext;"+id.charAt(2)+")V");
		ir.one(RETURN);
	}

	private Adapter user(ConstantData data, MethodNode writer, MethodNode reader) {
		String klassIn = reader.returnType().getActualClass();
		if (klassIn == null) throw new IllegalArgumentException("覆盖"+reader.returnType()+"的序列化");

		begin();

		// region toString
		CodeWriter cw = c.newMethod(PUBLIC|FINAL, "toString", "()Ljava/lang/String;");
		cw.visitSize(1,1);
		String toString = "UserAdapter@"+writer.ownerClass();
		cw.ldc(new CstString(toString));
		c.putAttr(new AttrUTF("SourceFile", toString));
		cw.one(ARETURN);
		cw.finish();
		// endregion
		Type type = writer.returnType();
		String klassOut = type.getActualClass();

		int ua = c.newField(PRIVATE, "userAdapter", "L"+data.name+";");
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
		cw = c.newMethod(PUBLIC|FINAL, "read", "(Lroj/config/serial/AdaptContext;"+methodType+")V");
		cw.visitSize(3,3);

		if (ser >= 0) {
			CodeWriter ps = c.newMethod(PUBLIC|FINAL, "push", "(Lroj/config/serial/AdaptContext;)V");
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
		cw.var(type.shiftedOpcode(ILOAD, false), 2);
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
		cw = c.newMethod(PUBLIC|FINAL, "write", "(Lroj/config/serial/CVisitor;Ljava/lang/Object;)V");
		cw.visitSizeMax(3,3);

		cw.one(ALOAD_0);
		cw.field(GETFIELD, c, ua);
		cw.one(ALOAD_2);
		cw.clazz(CHECKCAST, klassIn);
		cw.invoke(INVOKEVIRTUAL, writer);
		cw.var(type.shiftedOpcode(ISTORE, false), 2);

		if (ser >= 0) {
			cw.one(ALOAD_0);
			cw.field(GETFIELD, c, ser);
			cw.one(ALOAD_1);
			cw.one(ALOAD_2);
			cw.invoke(INVOKEVIRTUAL, "roj/config/serial/Adapter", "write", "(Lroj/config/serial/CVisitor;Ljava/lang/Object;)V");
		} else {
			cw.one(ALOAD_1);
			cw.var(type.shiftedOpcode(ILOAD, false), 2);
			cw.invokeItf("roj/config/serial/CVisitor", "value", "("+type.toDesc()+")V");
		}

		cw.one(RETURN);
		cw.finish();
		// endregion

		copy.one(ALOAD_0);
		copy.one(ALOAD_2);
		copy.field(PUTFIELD, c, ua);

		return build();
	}

	// region object serializer
	private static final byte DIRECT_IF_OVERRIDE = AdapterOverride.have ? INVOKESPECIAL : INVOKEVIRTUAL;
	private Adapter klass(Class<?> o, int flag) {
		ConstantData data = Parser.parse(o);
		if (data == null) throw new IllegalArgumentException("无法获取"+o.getName()+"的类文件");
		if (data.fields.size() == 0) throw new IllegalArgumentException("这"+o.getName()+"味道不对啊,怎么一个字段都没有");

		int _init = data.getMethod("<init>", "()V");
		if (_init < 0 || (data.methods.get(_init).modifier() & PUBLIC) == 0) {
			if (!AdapterOverride.have)
				throw new IllegalArgumentException(o.getName()+"没有public <init>()且AdapterOverride没有激活");
			_init = -1;
		}

		begin();
		// region toString
		CodeWriter cw = c.newMethod(PUBLIC|FINAL, "toString", "()Ljava/lang/String;");
		cw.visitSize(1,1);
		String toString = "Adapter@"+o.getName();
		cw.ldc(new CstString(toString));
		c.putAttr(new AttrUTF("SourceFile", toString));
		cw.one(ARETURN);
		cw.finish();
		// endregion
		int fieldIdKey = c.newField(PRIVATE|STATIC, "ser$fieldIds", "Lroj/collect/IntBiMap;");
		fieldIds.clear();
		// region fieldId
		cw = c.newMethod(PUBLIC|FINAL, "fieldNames", "()Lroj/collect/IntBiMap;");
		cw.visitSize(1,1);
		cw.field(GETSTATIC, c, fieldIdKey);
		cw.one(ARETURN);
		cw.finish();
		// endregion
		// region key函数
		cw = keyCw = c.newMethod(PUBLIC|FINAL, "key", "(Lroj/config/serial/AdaptContext;Ljava/lang/String;)V");
		cw.visitSize(3,4);

		cw.field(GETSTATIC, c, fieldIdKey);
		cw.one(ALOAD_2);
		cw.one(ICONST_M1);
		cw.invoke(DIRECT_IF_OVERRIDE, "roj/collect/IntBiMap", "getValueOrDefault", "(Ljava/lang/Object;I)I");
		cw.one(DUP);
		cw.one(ISTORE_3);

		keyPrimitive = null;
		keySwitch = new SwitchSegment(TABLESWITCH);
		cw.switches(keySwitch);
		keySwitch.def = cw.label();

		if ((flag & IGNORE_UNKNOWN) == 0) {
			cw.clazz(NEW, "java/lang/NoSuchFieldException");
			cw.one(DUP);
			cw.one(ALOAD_2);
			cw.invoke(INVOKESPECIAL, "java/lang/NoSuchFieldException", "<init>", "(Ljava/lang/String;)V");
			cw.one(ATHROW);
		} else {
			cw.one(ALOAD_1);
			cw.field(GETSTATIC, "roj/config/serial/SkipSer", "INST", "Lroj/config/serial/Adapter;");
			cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/AdaptContext", "push", "(Lroj/config/serial/Adapter;)V");
			cw.one(RETURN);
		}

		// endregion
		// region write()
		write = c.newMethod(PUBLIC|FINAL, "writeMap", "(Lroj/config/serial/CVisitor;Ljava/lang/Object;)V");
		write.visitSizeMax(3,3);

		write.one(ALOAD_2);
		write.clazz(CHECKCAST, data.name);
		write.one(ASTORE_2);
		// endregion

		CstInt count = new CstInt(0);
		// region map()
		cw = c.newMethod(PUBLIC|FINAL, "map", "(Lroj/config/serial/AdaptContext;I)V");
		cw.visitSize(3,3);
		cw.one(ALOAD_1);
		if (_init < 0) cw.clazz(NEW, data.name);
		else cw.newObject(data.name);
		cw.field(PUTFIELD, "roj/config/serial/AdaptContext", "ref", "Ljava/lang/Object;");
		fieldIdM1(cw);
		cw.one(RETURN);
		// endregion
		// region fieldCount()
		cw = c.newMethod(PUBLIC|FINAL, "fieldCount", "()I");
		cw.visitSize(1,1);
		cw.ldc(count);
		cw.one(IRETURN);
		// endregion

		int fieldId = 0;

		int optional = 0;
		optionalEx = null;

		SimpleList<FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			FieldNode f = fields.get(i);
			if ((f.modifier() & (TRANSIENT|STATIC)) != 0) continue;
			if ((f.modifier() & FINAL) != 0) {
				throw new IllegalArgumentException("无法修改"+f);
			} else if ((f.modifier() & PUBLIC) == 0) {
				if (!AdapterOverride.have) throw new IllegalArgumentException("Adapter未成功继承MagicAccessor,不能访问"+f);
			}

			String name = f.name();
			MethodNode get = null, set = null;
			boolean optional1 = false;
			AsType as = null;

			Annotations attr = f.parsedAttr(data.cp, Attribute.ClAnnotations);
			if (attr != null) {
				List<Annotation> list = attr.annotations;
				for (int j = 0; j < list.size(); j++) {
					Annotation anno = list.get(j);
					switch (anno.clazz) {
						case "roj/config/serial/Name": name = anno.getString("value"); break;
						case "roj/config/serial/Via": {
							String sid = anno.getString("get");
							if (sid != null) {
								int id = data.getMethod(sid, "()".concat(f.rawDesc()));
								if (id < 0) throw new IllegalArgumentException("无法找到get方法" + anno);
								get = data.methods.get(id);
							}
							sid = anno.getString("set");
							if (sid != null) {
								int id = data.getMethod(sid, "(" + f.rawDesc() + ")V");
								if (id < 0) throw new IllegalArgumentException("无法找到set方法" + anno);
								set = data.methods.get(id);
							}
							break;
						}
						case "roj/config/serial/Optional":
							if (fieldId < 32) {
								optional |= 1 << fieldId;
							} else {
								if (optionalEx == null) optionalEx = new MyBitSet();
								optionalEx.add(fieldId-32);
							}
							optional1 = true;
							break;
						case "roj/config/serial/As":
							as = asTypes.get(anno.getString("value"));
							if (as == null) throw new IllegalArgumentException("Unknown as " + anno);
					}
				}
			}

			value(fieldId, data, f, name, get, set, optional1, as);
			fieldIds.putInt(fieldId, name);

			fieldId++;
		}

		count.value = fieldId;

		write.one(RETURN);
		write.finish();
		write = null;

		CodeWriter init = c.newMethod(PUBLIC, "init", "(Lroj/collect/IntBiMap;Lroj/collect/MyBitSet;)V");
		init.visitSize(1, 3);
		init.one(ALOAD_1);
		init.field(PUTSTATIC, c, fieldIdKey);

		if (optional != 0 || optionalEx != null) {
			// region optional
			cw = c.newMethod(PUBLIC|FINAL, "plusOptional", "(ILroj/collect/MyBitSet;)I");
			cw.visitSize(2,3);
			if (optionalEx != null) {
				int fid = c.newField(PRIVATE|STATIC, "ser$optEx", "Lroj/collect/MyBitSet;");
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

		init.one(RETURN);
		init.finish();

		return build();
	}
	private void value(int fieldId, ConstantData data, FieldNode fn,
					   String actualName, MethodNode get, MethodNode set,
					   boolean optional1, AsType as) {
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

		byte myCode = type.shiftedOpcode(ILOAD, false);
		while (true) {
		Tmp2 t = readMethods.get(methodType);
		if (t == null) t = createReadMethod(data, methodType);

		cw = t.cw;
		t.seg.targets.add(new SwitchEntry(fieldId, cw.label()));

		cw.one(ALOAD_1);
		cw.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/AdaptContext", "setFieldHook", "()V");

		cw.var(ALOAD, t.pos);

		if (as != null) {
			asId = c.getField("as$"+as.klass());
			if (asId < 0) asId = c.newField(PRIVATE, "as$"+as.klass(), "L"+as.klass()+";");

			copy.one(ALOAD_0);
			copy.one(ALOAD_1);
			copy.ldc(new CstString(as.name));
			copy.invoke(DIRECT_IF_OVERRIDE, "roj/config/serial/SerializerManager", "getAsType", "(Ljava/lang/String;)Ljava/lang/Object;");
			copy.clazz(CHECKCAST, as.klass());
			copy.field(PUTFIELD, c, asId);

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
			cw.var(myCode, 2);
			if (actualType == Type.CLASS) {
				String type1 = type.getActualClass();
				if (type1.equals("java/lang/String")) {
					cw.invoke(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
				} else {
					cw.clazz(CHECKCAST, type1);
				}
			}
		}

		if (as != null) {
			cw.invoke(INVOKEVIRTUAL, as.reader);
		}

		if (methodType != actualType) {
			switch (actualType) {
				case Type.FLOAT: cw.one(D2F); break;
				case Type.LONG: cw.one(I2L); break;
			}
		}
		if (set == null) cw.field(PUTFIELD, data.name, fn.name(), fn.rawDesc());
		else cw.invoke(INVOKEVIRTUAL, set);

		cw.one(RETURN);
		// 这里改改可以实现short char之类的——不过int2这些没有损失/也不会很慢(吧)
		switch (methodType) {
			case Type.FLOAT: methodType = Type.DOUBLE; myCode = DLOAD; continue;
			case Type.LONG: methodType = Type.INT; myCode = ILOAD; continue;
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
		if (actualType == Type.CLASS && !"java/lang/String".equals(type.owner)) {
			int id;
			String serType = type.owner == null ? type.toDesc() : type.owner;
			Signature serSig = fn.parsedAttr(data.cp, Attribute.SIGNATURE);
			if (serSig != null) {
				id = ser(serType, serSig.toDesc());
			} else {
				id = ser(serType, null);
			}

			keySwitch.targets.add(new SwitchEntry(fieldId, cw.label()));
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
			keySwitch.targets.add(new SwitchEntry(fieldId, keyPrimitive));

			cw = write;
			cw.one(ALOAD_1);
			if (as != null) {
				cw.one(ALOAD_0);
				cw.field(GETFIELD, c, asId);
			}
			cw.one(ALOAD_2);

			if (get == null) cw.field(GETFIELD, data.name, fn.name(), fn.rawDesc());
			else cw.invoke(INVOKEVIRTUAL, get);

			if (as != null) {
				cw.invoke(INVOKEVIRTUAL, as.writer);
			}

			String c = "java/lang/String".equals(type.owner) ? "Ljava/lang/String;" : Type.toDesc(actualType);

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
		CodeWriter cw = t.cw = c.newMethod(PUBLIC|FINAL, "read", desc);

		t.seg = new SwitchSegment();
		t.seg.def = new Label();

		cw.visitSize(3, size+3);

		cw.one(ALOAD_1);
		cw.field(GETFIELD, "roj/config/serial/AdaptContext", "ref", "Ljava/lang/Object;");
		cw.clazz(CHECKCAST, data.name);
		cw.var(ASTORE, t.pos = (byte) (size+2));

		cw.one(ALOAD_1);
		cw.field(GETFIELD, "roj/config/serial/AdaptContext", "fieldId", "I");
		cw.switches(t.seg);

		cw.label(t.seg.def);
		cw.clazz(NEW, "java/lang/IllegalStateException");
		cw.one(DUP);
		cw.one(ALOAD_1);
		cw.field(GETFIELD, "roj/config/serial/AdaptContext", "fieldId", "I");
		cw.invoke(INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;");
		cw.invoke(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V");
		cw.one(ATHROW);
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

	private static int classId;

	private ConstantData c;
	private CodeWriter copy, write;
	private final ToIntMap<String> serializerId = new ToIntMap<>();

	private int ser(String type, String generic) {
		int id = serializerId.getOrDefault(type,-1);
		if (id >= 0) return id;

		id = c.newField(PRIVATE, "ser$"+c.fields.size(), "Lroj/config/serial/Adapter;");
		serializerId.putInt(type,id);

		copy.one(ALOAD_0);
		copy.one(ALOAD_1);
		copy.ldc(new CstClass(type));
		if (generic == null || generic.equals("java/lang/Object")) {
			copy.invoke(INVOKEVIRTUAL, "roj/config/serial/SerializerManager", "get", "(Ljava/lang/Class;)Lroj/config/serial/Adapter;");
		} else {
			copy.ldc(new CstString(generic));
			copy.invoke(INVOKEVIRTUAL, "roj/config/serial/SerializerManager", "get", "(Ljava/lang/Class;Ljava/lang/String;)Lroj/config/serial/Adapter;");
		}
		copy.field(PUTFIELD, c, id);
		return id;
	}
	private void begin() {
		c = new ConstantData();
		c.access = PUBLIC|SUPER|FINAL;
		c.name("roj/config/serial/GA$"+classId++);
		c.parent("roj/config/serial/Adapter");
		c.interfaces.add(new CstClass("roj/config/serial/GenAdapter"));
		FastInit.prepare(c);

		copy = c.newMethod(PUBLIC, "copy", "(Lroj/config/serial/SerializerManager;Ljava/lang/Object;)Lroj/config/serial/Adapter;");
		copy.visitSize(4, 3);
		copy.newObject(c.name);
		copy.one(ASTORE_0);
	}
	private Adapter build() {
		ConstantData c1 = c;
		c = null;

		readMethods.clear();
		serializerId.clear();

		copy.one(ALOAD_0);
		copy.one(ARETURN);
		copy.finish();
		copy = null;

		return (Adapter) FastInit.make(c1);
	}
}