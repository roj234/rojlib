package roj.config.serial;

import roj.asm.Parser;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.collect.MyHashMap;
import roj.collect.ToIntMap;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CNull;
import roj.config.data.CString;
import roj.config.exch.*;
import roj.reflect.*;
import roj.util.ByteList;
import roj.util.EmptyArrays;
import roj.util.Helpers;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.*;
import static roj.asm.util.AccessFlag.*;

/**
 * @author Roj233
 * @since 2022/1/11 17:49
 */
public final class Serializers {
	public static final Serializer<Byte> BYTE_FACTORY = new Serializer<Byte>() {
		public Byte deserializeRc(CEntry o) { return (byte) o.asInteger(); }
		public CEntry serializeRc(Byte t) { return TByte.valueOf(t); }
	};
	public static final Serializer<Short> SHORT_FACTORY = new Serializer<Short>() {
		public Short deserializeRc(CEntry o) { return (short) o.asInteger(); }
		public CEntry serializeRc(Short t) { return TShort.valueOf(t); }
	};
	public static final Serializer<Character> CHARACTER_FACTORY = new Serializer<Character>() {
		public Character deserializeRc(CEntry o) { return (char) o.asInteger(); }
		public CEntry serializeRc(Character t) { return TShort.valueOf((short) t.charValue()); }
	};
	public static final Serializer<Float> FLOAT_FACTORY = new Serializer<Float>() {
		public Float deserializeRc(CEntry o) { return (float) o.asDouble(); }
		public CEntry serializeRc(Float t) { return TFloat.valueOf(t); }
	};
	public static final Serializer<String> STRING_FACTORY = new Serializer<String>() {
		public String deserializeRc(CEntry o) { return o.getType() == roj.config.data.Type.NULL ? null : o.asString(); }
		public CEntry serializeRc(String t) { return t == null ? CNull.NULL : CString.valueOf(t); }
	};

	interface Init {
		void init(Serializers instance);
	}

	final Map<String, Serializer<?>> registry = new MyHashMap<>();
	final ConcurrentLinkedQueue<Init> pending = new ConcurrentLinkedQueue<>();
	final ClassDefiner loadedClassesRef = ClassDefiner.getFor(Serializers.class.getClassLoader());

	public static final int FINAL = 1, DYNAMIC = 2, GENERATE = 4, NO_INHERIT_FIELDS = 8, NO_CHECK_INHERITANCE = 16;
	public int defaultFlag;

	public CEntry serialize(Object o) {
		if (o == null) return CNull.NULL;
		return find(o.getClass()).serializeRc(Helpers.cast(o));
	}

	public <T> T deserialize(Class<T> type, CEntry entry) {
		return find(type).deserializeRc(entry);
	}

	@SuppressWarnings("unchecked")
	public <T> Serializer<T> find(Class<T> cls) {
		Serializer<?> ser = registry.get(cls.getName());
		if (ser == null) {
			if ((defaultFlag & GENERATE) == 0) {
				throw new UnsupportedOperationException("Not found serializer for " + cls.getName());
			}
			register(cls, defaultFlag);
			ser = registry.get(cls.getName());
			if (ser == null) throw new IllegalStateException("Recursive finding " + cls.getName());
		}

		return (Serializer<T>) ser;
	}

	public void register(Class<?> cls, int flag) {
		if (registry.containsKey(cls.getName())) return;
		synchronized (registry) {
			if (registry.containsKey(cls.getName())) return;
		}

		Serializer<?> ser;
		if ((flag & NO_CHECK_INHERITANCE) == 0) {
			List<Class<?>> clz = ReflectionUtils.getAllParentsWithSelfOrdered(cls);
			for (int i = 1; i < clz.size(); i++) {
				ser = registry.get(clz.get(i).getName());
				if (ser != null) {
					synchronized (registry) {
						registry.putIfAbsent(cls.getName(), ser);
					}
					return;
				}
			}
		}

		synchronized (registry) {
			if (null != registry.putIfAbsent(cls.getName(), null)) return;
		}
		if (cls.isEnum()) {
			ser = new EnumSerializer(cls);
		} else if (cls.getComponentType() != null) {
			ser = makeArray(cls, flag);
		} else {
			ser = make(cls, flag);
		}
		synchronized (registry) {
			registry.put(cls.getName(), ser);
		}
		if (!pending.isEmpty()) {
			try {
				Init r;
				do {
					r = pending.peek();
					if (r != null) {
						r.init(this);
						pending.poll();
					}
				} while (r != null);
			} catch (IllegalStateException ignored) {}
		}
		if (ser instanceof Init) {
			try {
				((Init) ser).init(this);
			} catch (IllegalStateException e) {
				pending.add((Init) ser);
			}
		}
	}

	public void register(Class<?> cls, Serializer<?> ser) {
		synchronized (registry) {
			registry.put(cls.getName(), ser);
		}
	}

	public Serializers(int flag) {
		this();
		this.defaultFlag = flag;
	}

	public Serializers() {
		WrapSerializer s = new WrapSerializer(this);
		register(Object.class, s);
		register(Boolean.class, s);
		register(CharSequence.class, STRING_FACTORY);
		register(Number.class, s);
		register(Map.class, s);
		register(List.class, s);
		register(Collection.class, s);

		register(String.class, STRING_FACTORY);
		register(Float.class, FLOAT_FACTORY);
		register(Character.class, CHARACTER_FACTORY);
		register(Byte.class, BYTE_FACTORY);
		register(Short.class, SHORT_FACTORY);
		register(Set.class, new SetSerializer(this));
	}

	private static final AtomicInteger ordinal = new AtomicInteger();

	private static volatile boolean inited = false;

	private static void blackMagic() {
		synchronized (Serializers.class) {
			if (!inited) {
				try {
					ConstantData d = new ConstantData();
					d.name("roj/config/serial/GenSer");
					d.parent(DirectAccessor.MAGIC_ACCESSOR_CLASS);
					d.interfaces.add(new CstClass("roj/config/serial/Serializer"));

					CodeWriter w = d.newMethod(PUBLIC, "<init>", "()V");
					w.visitSize(1, 1);
					w.one(ALOAD_0);
					w.invoke(INVOKESPECIAL, d.parent, "<init>", "()V");
					w.one(RETURN);
					w.finish();

					d.newMethod(ABSTRACT, "serialize0", "(Lroj/config/data/CMapping;Ljava/lang/Object;)V");
					d.newMethod(PUBLIC|ABSTRACT, "deserializeRc", "(Lroj/config/data/CEntry;)Ljava/lang/Object;");

					w = d.newMethod(PUBLIC|AccessFlag.FINAL, "serializeRc", "(Ljava/lang/Object;)Lroj/config/data/CEntry;");
					w.visitSize(3, 3);
					w.one(ALOAD_1);

					Label nonNull = CodeWriter.newLabel();
					w.jump(IFNONNULL, nonNull);
					w.field(GETSTATIC, "roj/config/data/CNull", "NULL", "Lroj/config/data/CNull;");
					w.one(ARETURN);

					w.label(nonNull);
					w.newObject("roj/config/data/CMapping");
					w.one(ASTORE_2);
					w.one(ALOAD_0);
					w.one(ALOAD_2);
					w.one(ALOAD_1);
					w.invoke(INVOKEVIRTUAL, "roj/config/serial/GenSer", "serialize0", "(Lroj/config/data/CMapping;Ljava/lang/Object;)V");

					w.one(ALOAD_2);
					w.one(ARETURN);
					w.finish();

					ByteList t = Parser.toByteArrayShared(d);
					ClassDefiner.INSTANCE.defineClassC(d.name.replace('/', '.'), t);
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
			inited = true;
		}
	}

	private Serializer<?> make(Class<?> owner, int flag) {
		if (owner.isEnum() || owner.isPrimitive() || owner.getComponentType() != null) throw new AssertionError();
		if (owner.isInterface()) throw new IllegalArgumentException(owner.getName() + "不是实体类");
		try {
			owner.getConstructor(EmptyArrays.CLASSES);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException(owner.getName() + "没有public无参构造器");
		}

		if (!inited) blackMagic();

		String className = owner.getName().replace('.', '/');

		ConstantData c = new ConstantData();
		DirectAccessor.makeHeader("roj/config/serial/GenSer$" + ordinal.getAndIncrement(), "roj/config/serial/Serializer", c);
		c.interfaces.add(new CstClass("roj/config/serial/Serializers$Init"));
		c.parent("roj/config/serial/GenSer");
		FastInit.prepare(c);

		CodeWriter init = c.newMethod(PUBLIC, "init", "(Lroj/config/serial/Serializers;)V");
		init.interpretFlags = AttrCode.COMPUTE_SIZES;
		init.visitSize(10,10);

		CodeWriter rcSer = c.newMethod(PUBLIC, "serialize0", "(Lroj/config/data/CMapping;Ljava/lang/Object;)V");
		rcSer.visitSize(10,10);
		rcSer.interpretFlags = AttrCode.COMPUTE_SIZES;

		rcSer.one(ALOAD_2);
		rcSer.clazz(CHECKCAST, className);
		rcSer.one(ASTORE_2);

		rcSer.one(ALOAD_1);
		rcSer.invoke(INVOKEVIRTUAL, "roj/config/data/CMapping", "raw", "()Ljava/util/Map;");

		CodeWriter rcDes = c.newMethod(PUBLIC, "deserializeRc", "(Lroj/config/data/CEntry;)Ljava/lang/Object;");
		rcDes.visitSize(3, 3);

		Label next = CodeWriter.newLabel();
		rcDes.one(ALOAD_1);
		rcDes.invoke(INVOKEVIRTUAL, "roj/config/data/CEntry", "getType", "()Lroj/config/data/Type;");
		rcDes.field(GETSTATIC, "roj/config/data/Type", "NULL", new Type("roj/config/data/Type"));
		rcDes.jump(IF_acmpne, next);
		rcDes.one(ACONST_NULL);
		rcDes.one(ARETURN);
		rcDes.label(next);
		rcDes.one(ALOAD_1);
		rcDes.clazz(CHECKCAST, "roj/config/data/CMapping");
		rcDes.field(GETFIELD, "roj/config/data/CMapping", "map", new Type("java/util/Map"));
		rcDes.one(ASTORE_1);

		rcDes.clazz(NEW, className);
		rcDes.one(DUP);
		rcDes.invoke(INVOKESPECIAL, className, "<init>", "()V");
		rcDes.one(ASTORE_2);

		ToIntMap<Type> storedSer = new ToIntMap<>(4);
		for (Field field : (flag & NO_INHERIT_FIELDS) != 0 ? Arrays.asList(owner.getDeclaredFields()) : ReflectionUtils.getFields(owner)) {
			if ((field.getModifiers() & (AccessFlag.TRANSIENT_OR_VARARGS | AccessFlag.STATIC)) != 0) continue;

			FieldInsnNode getFA;
			if ((field.getModifiers() & AccessFlag.FINAL) != 0) {
				if ((flag & FINAL) == 0) continue;
				c.newField(AccessFlag.STATIC, "a" + c.fields.size(), "Lroj/reflect/FieldAccessor;");
				int accId;

				init.ldc(new CstClass(className));
				init.ldc(new CstString(field.getName()));
				init.invoke(INVOKESTATIC, "roj/config/serial/Serializers", "acc", "(Ljava/lang/Class;Ljava/lang/String;)Lroj/reflect/FieldAccessor;");
				init.field(PUTSTATIC, c, accId = c.fields.size() - 1);
				getFA = new FieldInsnNode(GETSTATIC, c, accId);
			} else getFA = null;

			Type type = TypeHelper.parseField(TypeHelper.class2asm(field.getType()));

			// ser: dup, ldc [name], aload_2, getfield, convert, put
			rcSer.one(DUP);
			rcSer.ldc(new CstString(field.getName()));
			rcSer.mark();
			rcSer.one(ALOAD_2);
			rcSer.field(GETFIELD, className, field.getName(), type);

			// rcDes: dup, aload_1, ldc [name], get, convert, putfield
			if (getFA != null) {
				rcDes.add(getFA);
				rcDes.one(ALOAD_2);
				rcDes.invoke(INVOKEVIRTUAL, "roj/reflect/FieldAccessor", "setInstance", "(Ljava/lang/Object;)V");
				rcDes.add(getFA);
			} else {
				rcDes.one(ALOAD_2);
			}
			rcDes.mark();
			rcDes.one(ALOAD_1);
			rcDes.ldc(new CstString(field.getName()));
			rcDes.invoke_interface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");

			// convert jfield -> cobject
			a:
			{
				if (type.owner == null) {
					if (type.array() == 0) {
						rcDes.clazz(CHECKCAST, "roj/config/data/CEntry");
						switch (type.type) {
							case BOOLEAN:
								rcSer.invoke(INVOKESTATIC, "roj/config/data/CBoolean", "valueOf", "(Z)Lroj/config/data/CEntry;");
								rcDes.invoke(INVOKEVIRTUAL, "roj/config/data/CEntry", "asBool", "()Z");
								break a;
							case BYTE:
							case CHAR:
							case SHORT:
							case INT:
								rcSer.invoke(INVOKESTATIC, "roj/config/data/CInteger", "valueOf", "(I)Lroj/config/data/CInteger;");
								rcDes.invoke(INVOKEVIRTUAL, "roj/config/data/CEntry", "asInteger", "()I");
								switch (type.type) {
									case BYTE: rcDes.one(I2B); break;
									case CHAR: rcDes.one(I2C); break;
									case SHORT: rcDes.one(I2S); break;
								}
								break a;
							case FLOAT:
								rcSer.one(F2D);
								rcSer.invoke(INVOKESTATIC, "roj/config/data/CDouble", "valueOf", "(D)Lroj/config/data/CDouble;");
								rcDes.invoke(INVOKEVIRTUAL, "roj/config/data/CEntry", "asDouble", "()D");
								rcDes.one(D2F);
								break a;
							case DOUBLE:
								rcSer.invoke(INVOKESTATIC, "roj/config/data/CDouble", "valueOf", "(D)Lroj/config/data/CDouble;");
								rcDes.invoke(INVOKEVIRTUAL, "roj/config/data/CEntry", "asDouble", "()D");
								break a;
							case LONG:
								rcSer.invoke(INVOKESTATIC, "roj/config/data/CLong", "valueOf", "(J)Lroj/config/data/CLong;");
								rcDes.invoke(INVOKEVIRTUAL, "roj/config/data/CEntry", "asLong", "()J");
								break a;
						}
					} else if (type.array() == 1) {
						rcSer.invoke(INVOKESTATIC, "roj/config/serial/Serializers", "wArray", "([" + (char) type.type + ")Lroj/config/data/CEntry;");
						rcDes.clazz(CHECKCAST, "roj/config/data/CList");
						rcDes.invoke(INVOKESTATIC, "roj/config/serial/Serializers", "rArray" + (char) type.type, "(Lroj/config/data/CList;)[" + (char) type.type);
						break a;
					}
				}
				serField(flag, storedSer, c, rcSer, rcDes, type);
			}

			rcSer.invoke_interface("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
			rcSer.one(POP);

			if (getFA == null) {
				rcDes.field(PUTFIELD, className, field.getName(), type);
			} else {
				StringBuilder name = new StringBuilder().append("set");
				int pos = name.length();
				name.append(Type.toString(type.type));
				name.setCharAt(pos, Character.toUpperCase(name.charAt(0)));

				StringBuilder param = new StringBuilder().append("(");
				if (type.owner == null && type.array() == 0) {
					param.append((char) type.type);
				} else {
					param.append("Ljava/lang/Object;");
				}
				rcDes.invoke(INVOKEVIRTUAL, "roj/reflect/FieldAccessor", name.toString(), param.append(")V").toString());
				rcDes.add(getFA);
				rcDes.invoke(INVOKEVIRTUAL, "roj/reflect/FieldAccessor", "clearInstance", "()V");
			}
		}

		rcSer.one(POP);
		rcSer.one(RETURN);

		rcDes.one(ALOAD_2);
		rcDes.one(ARETURN);

		if (!init.hasCode()) {
			c.methods.remove(2);
			c.interfaces.remove(c.interfaces.size() - 1);
		} else {
			init.one(RETURN);
			init.finish();
		}

		c.version = 49 <<16;
		return (Serializer<?>) FastInit.make(c, loadedClassesRef);
	}

	@SuppressWarnings("fallthrough")
	private void serField(int flag, ToIntMap<Type> storedSer, ConstantData cz, CodeWriter rcSer, CodeWriter rcDes, Type type) {
		/*if (type.array() == 0) {
			switch (type.owner) {
				case "java/lang/CharSequence":
				case "java/lang/String":
					rcSer.invoke(INVOKESTATIC, "roj/config/data/CString", "valueOf1", "(Ljava/lang/CharSequence;)Lroj/config/data/CEntry;");
					rcDes.invoke(INVOKEVIRTUAL, "roj/config/data/CEntry", "asString", "()Ljava/lang/String;");
					return;
				// Primitive wrappers may be null, using Specified Serializer is simpler
			}
		}*/
		// rest: 'standard' object
		Class<?> cType;
		try {
			cType = type.toJavaClass();
			register(cType, flag); // 前向递归
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("A necessary class can not be found: " + type, e);
		}

		boolean st = cType != Object.class && ((flag & DYNAMIC) == 0 || type.array() > 0 ||
			// Not inheritable
			(cType.getModifiers() & (AccessFlag.ANNOTATION | AccessFlag.FINAL | AccessFlag.ENUM)) != 0);
		if (st) {
			int id = storedSer.getOrDefault(type, -1);
			if (id == -1) {
				id = cz.fields.size();
				storedSer.putInt(type, id);

				cz.newField(STATIC, "s"+id, "Lroj/config/serial/Serializer;");

				// init(), 这是为了支持递归
				CodeWriter init = ((AttrCodeWriter)cz.methods.get(2).attrByName("Code")).cw;
				init.one(ALOAD_1);
				init.ldc(new CstClass(cType.getName().replace('.', '/')));
				init.invoke(INVOKESPECIAL, "roj/config/serial/Serializers", "find", "(Ljava/lang/Class;)Lroj/config/serial/Serializer;");
				init.field(PUTSTATIC, cz, id);
			}
			FieldInsnNode GET = new FieldInsnNode(GETSTATIC, cz, id);
			rcSer.addMark(GET);
			rcSer.invoke_interface("roj/config/serial/Serializer", "serializeRc", "(Ljava/lang/Object;)Lroj/config/data/CEntry;");

			rcDes.addMark(GET);
			rcDes.invoke_interface("roj/config/serial/Serializer", "deserializeRc", "(Lroj/config/data/CEntry;)Ljava/lang/Object;");
		} else {
			// todo invoke Serializer.unwrap/wrap
			// There are not any CObjects more
			rcSer.invoke(INVOKESTATIC, "roj/config/data/CEntry", "wrap", "(Ljava/lang/Object;)Lroj/config/data/CEntry;");
			rcDes.invoke(INVOKEVIRTUAL, "roj/config/data/CEntry", "unwrap", "()Ljava/lang/Object;");
		}

		rcDes.clazz(CHECKCAST, type.owner);
	}

	private Serializer<?> makeArray(Class<?> owner, int flag) {
		ConstantData c = new ConstantData();
		DirectAccessor.makeHeader("roj/config/serial/GenArraySer$" + ordinal.getAndIncrement(), "roj/config/serial/Serializer", c);
		c.interfaces.add(new CstClass("roj/config/serial/Serializers$Init"));
		c.parent("roj/config/serial/GenSer");
		FastInit.prepare(c);

		CodeWriter init = c.newMethod(PUBLIC, "init", "(Lroj/config/serial/Serializers;)V");
		init.interpretFlags = AttrCode.COMPUTE_SIZES;

		CodeWriter rcSer = c.newMethod(PUBLIC, "serializeRc", "(Ljava/lang/Object;)Lroj/config/data/CEntry;");
		rcSer.visitSize(4, 4);

		CodeWriter rcDes = c.newMethod(PUBLIC, "deserializeRc", "(Lroj/config/data/CEntry;)Ljava/lang/Object;");
		rcDes.visitSize(4, 4);

		// 初始化 SER

		Label after = CodeWriter.newLabel();

		// if (o == null) return CNull.NULL;
		rcSer.one(ALOAD_1);
		rcSer.jump(IFNONNULL, after);
		rcSer.field(GETSTATIC, "roj/config/data/CNull", "NULL", "Lroj/config/data/CNull;");

		rcSer.label(after);
		rcSer.clazz(NEW, "java/util/ArrayList");
		rcSer.one(DUP);

		rcSer.one(ALOAD_1);
		rcSer.clazz(CHECKCAST, owner.getName().replace('.', '/'));
		rcSer.one(DUP);
		rcSer.one(ASTORE_2);
		rcSer.one(ARRAYLENGTH);

		rcSer.invoke(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(I)V");
		rcSer.one(ASTORE_1);

		rcSer.one(ICONST_0); // int i = 0;
		rcSer.one(ISTORE_3);

		// 初始化 DES

		rcDes.one(ALOAD_1);
		rcDes.clazz(CHECKCAST, "roj/config/data/CList");
		rcDes.invoke(INVOKEVIRTUAL, "roj/config/data/CList", "raw()", "()Ljava/util/List;");
		rcDes.one(DUP);
		rcDes.one(ASTORE_1);

		rcDes.invoke_interface("java/util/List", "size", "()I");
		rcDes.clazz(ANEWARRAY, TypeHelper.class2asm(owner));
		rcDes.one(ASTORE_2);

		rcDes.one(ICONST_0); // int i = 0;
		rcDes.one(ISTORE_3);

		// 循环头

		Label cycleBegin = rcSer.label();
		Label cycleExit = CodeWriter.newLabel();

		Label cycleBegin2 = rcDes.label();
		Label cycleExit2 = CodeWriter.newLabel();

		// if (i < array.length) {
		rcSer.one(ILOAD_3);
		rcSer.one(ALOAD_2);
		rcSer.one(ARRAYLENGTH);
		rcSer.jump(IF_icmpge, cycleExit);

		rcDes.one(ILOAD_3);
		rcDes.one(ALOAD_2);
		rcDes.one(ARRAYLENGTH);
		rcDes.jump(IF_icmpge, cycleExit2);

		// local -> list, array, i

		// list.add(obj[i]);
		rcSer.one(ALOAD_1);
		rcSer.mark();
		rcSer.one(ALOAD_2);
		rcSer.one(ILOAD_3);
		rcSer.one(AALOAD);

		// obj[i] = list.get(i);
		rcDes.one(ALOAD_2);
		rcDes.one(ILOAD_3);

		rcDes.mark();
		rcDes.one(ALOAD_1);
		rcDes.one(ILOAD_3);
		rcDes.invoke_interface("java/util/List", "get", "(I)Ljava/lang/Object;");

		Class<?> upper = owner.getComponentType();
		serField(0x03000000 | flag, new ToIntMap<>(), c, rcSer, rcDes, new Type(upper.getName().replace('.', '/')));

		rcSer.invoke_interface("java/util/List", "add", "(Ljava/lang/Object;)Z");
		rcSer.one(POP);
		rcDes.one(AASTORE);

		// i++;
		rcSer.increase(3, 1);
		rcDes.increase(3, 1);

		// 循环结束 SER

		rcSer.jump(GOTO, cycleBegin);
		rcSer.label(cycleExit);

		rcSer.clazz(NEW, "roj/config/data/CList");
		rcSer.one(DUP);
		rcSer.one(ALOAD_1);
		rcSer.invoke(INVOKESPECIAL, "roj/config/data/CList", "<init>", "(Ljava/util/List;)V");
		rcSer.one(ARETURN);

		// 循环结束 DES

		rcDes.jump(GOTO, cycleBegin2);
		rcDes.label(cycleExit2);
		rcDes.one(ALOAD_2);
		rcDes.one(ARETURN);

		if (!init.hasCode()) {
			c.methods.remove(2);
			c.interfaces.remove(c.interfaces.size() - 1);
		} else {
			init.one(RETURN);
			init.finish();
		}

		return (Serializer<?>) FastInit.make(c, loadedClassesRef);
	}

	static FieldAccessor acc(Class<?> objClass, String fieldName) {
		try {
			return ReflectionUtils.access(ReflectionUtils.getField(objClass, fieldName));
		} catch (NoSuchFieldException e) {
			throw new NoSuchFieldError(objClass.getName() + '.' + fieldName);
		}
	}

	public static CEntry wArray(int[] arr) {
		return new TIntArray(arr);
	}

	public static CEntry wArray(short[] arr) {
		CList dst = new CList(arr.length);
		for (int o1 : arr) {
			dst.add(o1);
		}
		return dst;
	}

	public static CEntry wArray(char[] arr) {
		CList dst = new CList(arr.length);
		for (int o1 : arr) {
			dst.add(o1);
		}
		return dst;
	}

	public static CEntry wArray(byte[] arr) {
		return new TByteArray(arr);
	}

	public static CEntry wArray(boolean[] arr) {
		CList dst = new CList(arr.length);
		for (boolean o1 : arr) {
			dst.add(o1);
		}
		return dst;
	}

	public static CEntry wArray(double[] arr) {
		CList dst = new CList(arr.length);
		for (double o1 : arr) {
			dst.add(o1);
		}
		return dst;
	}

	public static CEntry wArray(float[] arr) {
		CList dst = new CList(arr.length);
		for (float o1 : arr) {
			dst.add(o1);
		}
		return dst;
	}

	public static CEntry wArray(long[] arr) {
		return new TLongArray(arr);
	}

	public static int[] rArrayI(CList list) {
		int[] arr = new int[list.size()];
		List<CEntry> raw = list.raw();
		for (int i = 0; i < raw.size(); i++) {
			arr[i] = raw.get(i).asInteger();
		}
		return arr;
	}

	public static boolean[] rArrayZ(CList list) {
		boolean[] arr = new boolean[list.size()];
		List<CEntry> raw = list.raw();
		for (int i = 0; i < raw.size(); i++) {
			arr[i] = raw.get(i).asBool();
		}
		return arr;
	}

	public static short[] rArrayS(CList list) {
		short[] arr = new short[list.size()];
		List<CEntry> raw = list.raw();
		for (int i = 0; i < raw.size(); i++) {
			arr[i] = (short) raw.get(i).asInteger();
		}
		return arr;
	}

	public static char[] rArrayC(CList list) {
		char[] arr = new char[list.size()];
		List<CEntry> raw = list.raw();
		for (int i = 0; i < raw.size(); i++) {
			arr[i] = (char) raw.get(i).asInteger();
		}
		return arr;
	}

	public static byte[] rArrayB(CList list) {
		byte[] arr = new byte[list.size()];
		List<CEntry> raw = list.raw();
		for (int i = 0; i < raw.size(); i++) {
			arr[i] = (byte) raw.get(i).asInteger();
		}
		return arr;
	}

	public static long[] rArrayJ(CList list) {
		long[] arr = new long[list.size()];
		List<CEntry> raw = list.raw();
		for (int i = 0; i < raw.size(); i++) {
			arr[i] = raw.get(i).asLong();
		}
		return arr;
	}

	public static float[] rArrayF(CList list) {
		float[] arr = new float[list.size()];
		List<CEntry> raw = list.raw();
		for (int i = 0; i < raw.size(); i++) {
			arr[i] = (float) raw.get(i).asDouble();
		}
		return arr;
	}

	public static double[] rArrayD(CList list) {
		double[] arr = new double[list.size()];
		List<CEntry> raw = list.raw();
		for (int i = 0; i < raw.size(); i++) {
			arr[i] = raw.get(i).asDouble();
		}
		return arr;
	}
}