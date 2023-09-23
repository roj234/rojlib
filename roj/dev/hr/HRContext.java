package roj.dev.hr;

import roj.asm.OpcodeUtil;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstClass;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.SwitchSegment;
import roj.collect.*;
import roj.mapper.MapUtil;
import roj.mapper.util.Desc;
import roj.reflect.DirectAccessor;
import roj.util.ByteList;
import roj.util.NativeMemory;
import sun.misc.Unsafe;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.ref.ReferenceQueue;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.*;
import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2023/9/24 0024 1:54
 */
public final class HRContext extends ClassLoader {
	public HRContext(ClassLoader parent) { super(parent); }

	Map<String, Structure> structure = new MyHashMap<>();
	Map<String, Structure> dirty = new MyHashMap<>();

	public void update(ConstantData data) {
		Desc d = MapUtil.getInstance().sharedDC;
		d.owner = data.name;

		Structure s = structure.get(data.name);
		if (s == null) {
			s = new Structure();
			s.name = data.name;
			s.codeName = data.name.replace('/', '.');
			s.code = data;
			structure.put(s.name, s);
			structure.put(s.codeName, s);

			SimpleList<MethodNode> nodes = data.methods;
			for (int i = 0; i < nodes.size(); i++) {
				MethodNode n = nodes.get(i);
				d.name = n.name();
				d.param = n.rawDesc();
				d.flags = n.modifier();

				Desc copy = d.copy();
				s.firstMethods.add(copy);
				s.allMethods.add(copy);
			}

			updateMethods(data, s, new MyBitSet());

			MyHashSet<Desc> x = new MyHashSet<>();
			checkFieldMove(data, s, false, d, x);
			checkFieldMove(data, s, true, d, x);

			createFieldStorage(data, s);
			return;
		}

		MyBitSet newAdd = new MyBitSet();
		MyHashSet<Desc> removed = new MyHashSet<>(s.allMethods);

		// todo only modifier change?
		SimpleList<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode n = methods.get(i);
			d.name = n.name();
			d.param = n.rawDesc();
			d.flags = n.modifier();

			if (!s.allMethods.contains(d)) s.allMethods.add(d.copy());
			if (!s.firstMethods.contains(d)) newAdd.add(i);
			removed.remove(d);
		}

		s.dirty = 0;
		if (newAdd.size() > 0 || !removed.isEmpty()) {
			s.code = data;
			s.dirty = 1;
			dirty.put(s.codeName, s);

			updateMethods(data, s, newAdd);
			for (Desc d1 : removed) {
				MethodNode m = new MethodNode(d1.flags, d1.owner, d1.name, d1.param);

				methods.add(m);
				AttrCodeWriter cw1 = new AttrCodeWriter(data.cp, m);
				m.putAttr(cw1);

				CodeWriter c = cw1.cw;
				c.clazz(NEW, "java/lang/NoSuchMethodError");
				c.ldc("该方法已被移除");
				c.invoke(INVOKESPECIAL, "java/lang/NoSuchMethodError", "<init>", "(Ljava/lang/String;)V");
				c.one(ATHROW);
				c.finish();
			}
		}

		removed.clear(); removed.addAll(s.fieldIndex.keySet());

		checkFieldMove(data, s, false, d, removed);
		checkFieldMove(data, s, true, d, removed);

		for (Desc d1 : removed) s.fieldIndex.remove(d1);

		if ((s.dirty&2) != 0) dirty.put(s.codeName, s);

		createFieldStorage(data, s);
	}
	private void createFieldStorage(ConstantData data, Structure s) {
		data.fields.clear();
		if ((s.o_size| s.p_size) != 0) {
			data.newField(AccessFlag.PUBLIC, "fs_i", "Lroj/dev/hr/HRFieldStorage;");
		}
		if ((s.o_size_static| s.p_size_static) != 0) {
			data.newField(AccessFlag.PUBLIC|AccessFlag.STATIC|AccessFlag.FINAL, "fs_s", "Lroj/dev/hr/HRFieldStorage;");
			data.methodIter_tmpName_("<clinit>", "()V", new CodeWriter() {
				@Override
				protected void begin() {

				}
			});
		}
	}
	private void checkFieldMove(ConstantData data, Structure s, boolean _static,
								Desc d, MyHashSet<Desc> removed) {
		Int2IntMap moved = _static ? s.fieldsMovedStatic : s.fieldsMoved; moved.clear(); removed.clear();
		ToIntMap<Desc> idx = s.fieldIndex;

		int p_pos = 0, o_pos = 0;
		SimpleList<FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			FieldNode n = fields.get(i);
			d.name = n.name();
			d.param = n.rawDesc();
			d.flags = n.modifier();
			if (((d.flags&AccessFlag.STATIC) == 0) == _static) continue;

			removed.remove(d);
			Type type = n.fieldType();
			if (type.isPrimitive()) {
				int prevId = idx.getOrDefault(d, 0);
				if (-prevId - 1 != p_pos) {
					idx.putInt(d.copy(), -(p_pos+1));

					if (prevId != 0) {
						moved.put(prevId, p_pos);
						if (type.length() > 1) moved.put(prevId-1, p_pos+1);
					}
				}

				p_pos += type.length();
			} else {
				int prevId = idx.getOrDefault(d, -1);
				if (prevId != o_pos) {
					idx.putInt(d.copy(), o_pos);

					if (prevId != -1) moved.put(prevId, o_pos);
				}

				o_pos++;
			}
		}

		if (!moved.isEmpty()) s.dirty |= 2;
		if (_static) {
			if (o_pos != s.o_size_static || p_pos != s.p_size_static)
				s.dirty |= 2;
			s.o_size_static = o_pos;
			s.p_size_static = p_pos;
		} else {
			if (o_pos != s.o_size || p_pos != s.p_size)
				s.dirty |= 2;
			s.o_size = o_pos;
			s.p_size = p_pos;
		}
	}

	public synchronized void commit(Instrumentation instrumentation) throws UnmodifiableClassException, ClassNotFoundException {
		// todo suspend all threads
		// 字段因为挂在链表上处理就行
		HRFieldStorage fs = head;
		while (fs != null) {
			fs = fs.next;

			Structure s = dirty.get(fs.type);
			if ((s.dirty&2) != 0) fs.update(s);
		}

		// 方法增加->使用invokeVirtual (必然会走到这里！)
		for (Structure s : dirty.values()) {
			transform(s.code);
			transform(s.methodImpl);
		}

		Class<?>[] classes = instrumentation.getAllLoadedClasses();
		MyHashMap<String, Class<?>> byName = new MyHashMap<>();
		for (Class<?> c : classes) byName.put(c.getName(), c);

		List<ClassDefinition> toTransform = new SimpleList<>();
		for (Structure s : dirty.values()) {
			if ((s.dirty&1) != 0) {
				// by using private static methods, they can be added or removed

				Class<?> type = byName.get(s.methodImpl.name.replace('/', '.'));
				byte[] code = Parser.toByteArray(s.methodImpl);
				if (type != null) toTransform.add(new ClassDefinition(type, code));
				else defineClass(null,code,0,code.length);

				type = byName.get(s.codeName);
				code = Parser.toByteArray(s.code);
				if (type != null) toTransform.add(new ClassDefinition(type, code));
				else defineClass(null,code,0,code.length);
			}
		}
		dirty.clear();

		System.out.println(toTransform);
		instrumentation.redefineClasses(toTransform.toArray(new ClassDefinition[toTransform.size()]));
	}

	private void transform(ConstantData data) {
		Desc d = new Desc();
		new Context("", data).forEachMethod(new CodeWriter() {
			@Override
			public void invoke(byte code, String owner, String name, String desc) {

			}

			@Override
			public void field(byte code, String owner, String name, String type) {
				Structure s = structure.get(owner);
				if (s != null) {
					d.owner = owner;
					d.name = name;
					d.param = type;

					int id = s.fieldIndex.getOrDefault(d, -1);

					return;
				}

				super.field(code, owner, name, type);
			}
		});
	}

	public static final Unsafe UNSAFE_REF = u;
	private static final CodeWriter EMPTY_CW = new CodeWriter();
	private void updateMethods(ConstantData data, Structure s, MyBitSet newAdd) {
		ConstantData impl = s.methodImpl;
		if (impl == null) {
			s.methodImpl = impl = new ConstantData();
			impl.name("roj/dev/hr/HRMethodStorage$"+methodId);
			impl.parent(DirectAccessor.MAGIC_ACCESSOR_CLASS);
			impl.interfaces().add("roj/dev/hr/HRContext$Method");
			impl.newField(AccessFlag.PRIVATE|AccessFlag.STATIC, "OFFSET", "I");
		} else {
			impl.methods.clear();
		}

		CodeWriter cWrap = impl.newMethod(AccessFlag.PUBLIC|AccessFlag.FINAL, "invoke", "(ILroj/dev/hr/HRContext$Stack;)V");
		SwitchSegment seg = new SwitchSegment(true);

		cWrap.visitSize(2, 4);
		cWrap.one(ILOAD_1);

		cWrap.one(ALOAD_2);
		cWrap.field(GETFIELD, "roj/dev/hr/HRContext$Stack", "p_arr", "J");
		cWrap.one(LSTORE_0);

		cWrap.one(ALOAD_2);
		cWrap.field(GETFIELD, "roj/dev/hr/HRContext$Stack", "o_arr", "[Ljava/lang/Object;");
		cWrap.one(ASTORE_3);

		cWrap.switches(seg);

		SimpleList<MethodNode> nodes = data.methods;
		for (int i = 0; i < nodes.size(); i++) {
			MethodNode m = nodes.get(i);
			if ((m.modifier() & (AccessFlag.NATIVE|AccessFlag.ABSTRACT)) != 0 ||
				m.name().equals("<clinit>")) continue;
			if (m.name().equals("<init>")) continue;

			MethodNode copy = m.copy();

			copy.owner = impl.name;
			impl.methods.add(copy);
			seg.branch(methodId, seg.def = cWrap.label());

			List<Type> params = copy.parameters();
			if ((copy.modifier()&AccessFlag.STATIC) == 0) {
				params.add(0, new Type(data.name));
				copy.modifier(copy.modifier()|AccessFlag.STATIC);
			}
			copy.modifier(copy.modifier()&(~(AccessFlag.PUBLIC|AccessFlag.PROTECTED))|AccessFlag.PRIVATE);

			int maxSize = 0;
			for (int j = 0; j < params.size(); j++) maxSize += params.get(j).length();

			CodeWriter cDisp;
			if (newAdd.contains(i)) {
				nodes.remove(i--);
				cDisp = EMPTY_CW;
				cDisp.init(new ByteList(), new ConstantPool());
			} else {
				AttrCodeWriter cw1 = new AttrCodeWriter(data.cp, m);
				m.putAttr(cw1);
				cDisp = cw1.cw;
			}

			cDisp.invoke(INVOKESTATIC, "roj/dev/hr/HRContext", "stack", "()Lroj/dev/hr/HRContext$Stack;");
			cDisp.var(ASTORE, maxSize);

			cDisp.var(ALOAD, maxSize);
			cDisp.field(GETFIELD, "roj/dev/hr/HRContext$Stack", "o_arr", "[Ljava/lang/Object;");
			cDisp.var(ASTORE, maxSize+1);

			cDisp.var(ALOAD, maxSize);
			cDisp.field(GETFIELD, "roj/dev/hr/HRContext$Stack", "p_arr", "J");
			cDisp.var(LSTORE, maxSize+2);

			cDisp.visitSize(3, maxSize+4);

			int oid = 0, pid = 0, size = 0;
			for (int j = 0; j < params.size(); j++) {
				Type type = params.get(j);

				if (type.isPrimitive()) {
					cDisp.field(GETSTATIC, "roj/dev/hr/HRContext", "UNSAFE_REF", "Lsun/misc/Unsafe;");
					cDisp.var(LLOAD, maxSize+2);
					cDisp.ldc((long) pid);
					cDisp.one(LADD);
					cDisp.var(type.shiftedOpcode(ILOAD, false), size);
					cDisp.invoke(INVOKESPECIAL, "sun/misc/Unsafe", "set"+upper(type.toString()), "(J"+type.toDesc()+")V");

					cDisp.visitSizeMax(5, 0);

					cWrap.field(GETSTATIC, "roj/dev/hr/HRContext", "UNSAFE_REF", "Lsun/misc/Unsafe;");
					cWrap.one(LLOAD_0);
					cWrap.ldc((long) pid);
					cWrap.one(LADD);
					cWrap.invoke(INVOKESPECIAL, "sun/misc/Unsafe", "get"+upper(type.toString()), "(J)".concat(type.toDesc()));

					cWrap.visitSizeMax(maxSize+5, 0);

					pid++;
				} else {
					cDisp.var(ALOAD, maxSize+1);
					cDisp.var(ALOAD, size);
					cDisp.ldc(oid);
					cDisp.one(AASTORE);

					cWrap.one(ALOAD_3);
					cWrap.ldc(oid);
					cWrap.one(AALOAD);
					cWrap.clazz(CHECKCAST, type);

					cWrap.visitSizeMax(maxSize+2, 0);

					oid++;
				}

				size += type.length();
			}

			cDisp.var(ALOAD, maxSize);
			cDisp.ldc(new CstClass(data.name));
			cDisp.ldc(methodId);
			cDisp.invoke(INVOKESTATIC, "roj/dev/hr/HRContext", "invoke", "(Lroj/dev/hr/HRContext$Stack;Ljava/lang/Class;I)V");

			cWrap.invoke(INVOKESTATIC, copy);
			Type type = copy.returnType();
			if (type.type == Type.VOID) {
				cDisp.one(RETURN);
				cWrap.one(RETURN);
			} else {
				cDisp.var(ALOAD, maxSize);
				cDisp.one(type.shiftedOpcode(IRETURN, false));

				int b = OpcodeUtil.getByName().getInt(type.nativeName()+"STORE_0");
				// lengthunknown's swapground
				cWrap.one((byte) b);
				cWrap.one(ALOAD_2);
				cWrap.one((byte) (b+33));
				if (type.isPrimitive()) {
					cDisp.invoke(INVOKESPECIAL, "roj/dev/hr/HRContext$Stack", "get"+type.toDesc(), "()"+type.toDesc());
					cWrap.invoke(INVOKESPECIAL, "roj/dev/hr/HRContext$Stack", "set"+type.toDesc(), "("+type.toDesc()+")V");
				} else {
					cDisp.invoke(INVOKESPECIAL, "roj/dev/hr/HRContext$Stack", "getL", "()Ljava/lang/Object;");
					cDisp.clazz(CHECKCAST, type);
					cWrap.invoke(INVOKESPECIAL, "roj/dev/hr/HRContext$Stack", "setL", "(Ljava/lang/Object;)V");
				}
			}

			methodId++;
		}

		impl.dump();
	}
	private static String upper(String s) {
		char[] tmp = new char[s.length()];
		s.getChars(0, s.length(), tmp, 0);
		tmp[0] = Character.toUpperCase(tmp[0]);
		return new String(tmp);
	}

	public final ReferenceQueue<Object> queue = new ReferenceQueue<>();
	private HRFieldStorage head, tail;
	final synchronized void addFieldRef(HRFieldStorage ref) {
		cleanFieldRef();

		if (head == null) head = ref;
		if (tail != null) {
			tail.next = ref;
			ref.prev = tail;
		}
		tail = ref;
	}
	final synchronized void cleanFieldRef() {
		while (true) {
			HRFieldStorage ref = (HRFieldStorage) queue.poll();
			if (ref == null) break;

			if (ref.prev == null) head = ref.next;
			else ref.prev.next = ref.next;

			if (ref.next == null) tail = ref.prev;
			else ref.next.prev = ref.prev;
		}
	}

	int methodId;
	IntMap<Method> methods = new IntMap<>();

	private static final ThreadLocal<Stack> STACK = ThreadLocal.withInitial(Stack::new);
	private static final int maxArgumentCount = 99;
	public static Stack stack() { return STACK.get(); }

	public static void invokeVirtual(Stack param, Object o, int methodId) {
		// 新增的方法被$Method调用时触发
	}
	// 让JVM处理继承
	public static void invoke(Stack param, Class<?> o, int methodId) {
		HRContext ctx = (HRContext) o.getClassLoader();

		Method m = ctx.methods.get(methodId);
		if (m == null) throw new NoSuchMethodError();

		try {
			m.invoke(methodId, param);
		} finally {
			param.setO_len(0);
		}
	}

	public static final class Structure {
		public int dirty;

		public String name, codeName;
		ConstantData code;

		final ToIntMap<Desc> fieldIndex = new ToIntMap<>();
		final Int2IntMap
			fieldsMoved = new Int2IntMap(),
			fieldsMovedStatic = new Int2IntMap();
		int p_size, o_size,
			p_size_static, o_size_static;

		ConstantData methodImpl;
		final MyHashSet<Desc>
			firstMethods = new MyHashSet<>(),
			allMethods = new MyHashSet<>();

		public ToIntMap<Desc> methodAdded;
	}

	public static final class Stack extends NativeMemory {
		public final Object[] o_arr = new Object[maxArgumentCount];
		private int o_len;
		public final long p_arr = allocate(maxArgumentCount*8);

		public final void setO_len(int len) {
			for (int i = len; i < o_len; i++) o_arr[i] = null;
			o_len = len;
		}

		private Object out_o;

		public final void setB(byte   v) { u.putByte  (p_arr,v); }
		public final void setC(char   v) { u.putChar  (p_arr,v); }
		public final void setS(short  v) { u.putShort (p_arr,v); }
		public final void setI(int    v) { u.putInt   (p_arr,v); }
		public final void setF(float  v) { u.putFloat (p_arr,v); }
		public final void setD(double v) { u.putDouble(p_arr,v); }
		public final void setJ(long   v) { u.putLong  (p_arr,v); }
		public final void setL(Object v) {            out_o = v; }

		public final byte   getB() { return u.getByte(p_arr); }
		public final char   getC() { return u.getChar(p_arr); }
		public final short  getS() { return u.getShort(p_arr); }
		public final int    getI() { return u.getShort(p_arr); }
		public final float  getF() { return u.getFloat(p_arr); }
		public final double getD() { return u.getDouble(p_arr); }
		public final long   getJ() { return u.getLong(p_arr); }
		public final Object getL() { return out_o; }
	}

	interface Method {
		void invoke(int methodId, Stack stack);
	}
}
