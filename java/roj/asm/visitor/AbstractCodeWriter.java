package roj.asm.visitor;

import roj.asm.cp.*;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.RawNode;
import roj.asm.tree.insn.SwitchEntry;
import roj.asm.type.Desc;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.Hasher;
import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.*;
import static roj.asm.util.InsnHelper.ToPrimitiveArrayId;

/**
 * @author Roj234
 * @since 2023/10/2 0002 0:57
 */
public abstract class AbstractCodeWriter extends CodeVisitor {
	protected DynByteBuf codeOb;
	protected List<Segment> segments = Collections.emptyList();

	protected final MyHashSet<Label> labels = new MyHashSet<>(Hasher.identity());

	IntMap<Label> bciR2W;
	final void validateBciRef() {
		for (IntMap.Entry<Label> entry : bciR2W.selfEntrySet()) {
			if (!entry.getValue().isValid()) throw new IllegalArgumentException("BCI #"+entry.getIntKey()+" 不存在");
		}
	}

	// region visitor
	protected final void multiArray(CstClass clz, int dimension) { multiArray(clz.name().str(), dimension); }
	protected final void clazz(byte code, CstClass clz) { clazz(code, clz.name().str()); }
	protected final void ldc(byte code, Constant c) { if (code == LDC2_W) ldc2(c); else ldc1(code, c); }
	protected final void invokeDyn(CstDynamic dyn, int type) { invokeDyn(dyn.tableIdx, dyn.desc().name().str(), dyn.desc().getType().str(), type); }
	protected final void invokeItf(CstRefItf itf, short argc) {
		CstNameAndType desc = itf.desc();
		invokeItf(itf.className(), desc.name().str(), desc.getType().str());
	}
	protected final void invoke(byte code, CstRef method) {
		CstNameAndType desc = method.desc();
		invoke(code, method.className(), desc.name().str(), desc.getType().str(), method.type() == Constant.INTERFACE);
	}
	protected final void field(byte code, CstRefField field) {
		CstNameAndType desc = field.desc();
		field(code, field.className(), desc.name().str(), desc.getType().str());
	}
	protected final void jump(byte code, int offset) { jump(code, _rel(offset)); }
	protected final void smallNum(byte code, int value) { ldc(value); }
	protected final void tableSwitch(DynByteBuf r) {
		int def = r.readInt();
		int low = r.readInt();
		int hig = r.readInt();
		int count = hig - low + 1;

		SimpleList<SwitchEntry> map = new SimpleList<>(count);

		if (count > 100000) throw new IllegalArgumentException("length > 100000");

		int i = 0;
		while (count > i) {
			map.add(new SwitchEntry(i++ + low, _rel(r.readInt())));
		}

		addSegment(new SwitchSegment(TABLESWITCH, _rel(def), map, bci));
	}
	protected final void lookupSwitch(DynByteBuf r) {
		int def = r.readInt();
		int count = r.readInt();

		SimpleList<SwitchEntry> map = new SimpleList<>(count);

		if (count > 100000) throw new IllegalArgumentException("length > 100000");

		while (count-- > 0) {
			map.add(new SwitchEntry(r.readInt(), _rel(r.readInt())));
		}

		addSegment(new SwitchSegment(LOOKUPSWITCH, _rel(def), map, bci));
	}
	abstract Label _rel(int pos);
	// endregion

	// region basic instruction
	public abstract void clazz(byte code, String clz);
	public void newArray(byte type) { codeOb.put(NEWARRAY).put(type); }
	public abstract void multiArray(String clz, int dimension);

	public final void invoke(byte code, String owner, String name, String desc) { invoke(code, owner, name, desc, false); }
	public abstract void invoke(byte code, String owner, String name, String desc, boolean isInterfaceMethod);
	public abstract void invokeItf(String owner, String name, String desc);
	public abstract void invokeDyn(int idx, String name, String desc, int type);

	public abstract void field(byte code, String owner, String name, String type);

	public void iinc(int id, int count) {
		DynByteBuf ob = codeOb;
		if (id >= 0xFF || (byte)count != count) ob.putShort(((WIDE&0xFF)<<8) | (IINC&0xFF)).putShort(id).putShort(count);
		else ob.put(IINC).put(id).put(count);
	}

	public final void ldc(Constant c) {
		switch (c.type()) {
			case Constant.DYNAMIC:
				String dyn = ((CstDynamic) c).desc().getType().str();
				if (dyn.charAt(0) == DOUBLE || dyn.charAt(0) == LONG) {
					ldc2(c);
					return;
				}
				break;
			case Constant.STRING:
			case Constant.FLOAT:
			case Constant.INT:
			case Constant.CLASS:
			case Constant.METHOD_HANDLE:
			case Constant.METHOD_TYPE:
				break;
			case Constant.DOUBLE:
			case Constant.LONG:
				ldc2(c);
				return;
			default: throw new IllegalStateException("Constant "+c+" is not loadable");
		}
		ldc1(LDC, c);
	}
	protected abstract void ldc1(byte code, Constant c);
	protected abstract void ldc2(Constant c);

	public void one(byte code) { assertTrait(code, TRAIT_ZERO_ADDRESS); codeOb.put(code); }

	public final void jump(Label target) { jump(GOTO, target); }
	public void jump(byte code, Label target) { assertTrait(code, TRAIT_JUMP); addSegment(new JumpSegment(code, target)); }
	public void vars(byte code, int value) {
		assertCate(code, CATE_LOAD_STORE_LEN);
		DynByteBuf ob = codeOb;
		if ((value&0xFF00) != 0) {
			ob.put(WIDE).put(code).putShort(value);
		} else {
			if (value <= 3) {
				byte c = code >= ISTORE ? ISTORE : ILOAD;
				ob.put((((code-c) << 2) + c + 5 + value));
			} else {
				ob.put(code).put(value);
			}
		}
	}

	public void ret(int value) {
		if (((byte)value != value)) codeOb.put(WIDE).put(RET).putShort(value);
		else codeOb.put(RET).put(value);
	}

	// endregion
	// region complex
	public final void newArraySized(Type t, int size) {
		ldc(size);

		if (t.array() == 0) {
			switch (t.type) {
				case BYTE: case SHORT: case CHAR: case BOOLEAN:
				case DOUBLE: case INT: case FLOAT: case LONG:
					newArray(ToPrimitiveArrayId(t.type));
				return;
			}
		}

		clazz(ANEWARRAY, t.getActualClass());
	}
	public final void newArraySized(Type t, int... size) {
		if (t.array()+1 < size.length) throw new IllegalArgumentException("t.array()+1 < size.length");
		for (int i = 0; i < size.length; i++) ldc(size[i]);
		multiArray(t.getActualClass(), size.length);
	}

	public final void clazz(byte code, Type clz) {
		if (clz.isPrimitive()) throw new IllegalArgumentException(clz.toString());
		clazz(code, clz.getActualClass());
	}

	public final void invoke(byte code, MethodNode m) { invoke(code, m.ownerClass(), m.name(), m.rawDesc()); }
	public final void invoke(byte code, IClass cz, int id) {
		RawNode node = cz.methods().get(id);
		invoke(code, cz.name(), node.name(), node.rawDesc());
	}
	public final void invoke(byte code, Desc desc) { invoke(code, desc.owner, desc.name, desc.param); }

	public final void invokeV(String owner, String name, String desc) { invoke(INVOKEVIRTUAL, owner, name, desc); }
	public final void invokeS(String owner, String name, String desc) { invoke(INVOKESTATIC, owner, name, desc); }
	public final void invokeD(String owner, String name, String desc) { invoke(INVOKESPECIAL, owner, name, desc); }

	public final void field(byte code, IClass cz, int id) {
		RawNode node = cz.fields().get(id);
		field(code, cz.name(), node.name(), node.rawDesc());
	}
	public final void field(byte code, String desc) {
		int cIdx = desc.indexOf('.');
		String owner = desc.substring(0, cIdx++);

		int nIdx = desc.indexOf(':', cIdx);
		String name = desc.substring(cIdx, nIdx);
		if (name.charAt(0) == '"') name = name.substring(1, name.length()-1);

		field(code, owner, name, desc.substring(nIdx+1));
	}
	public final void field(byte code, String owner, String name, Type type) { field(code, owner, name, TypeHelper.getField(type)); }

	public final void ldc(int value) {
		DynByteBuf ob = codeOb;
		if (value >= -1 && value <= 5) ob.put((value+3));
		else if ((byte)value == value) ob.put(BIPUSH).put(value);
		else if ((short)value == value) ob.put(SIPUSH).putShort(value);
		else ldc(new CstInt(value));
	}
	public final void ldc(String c) { ldc1(LDC, new CstString(c)); }
	public final void ldc(long n) {
		if (n != 0 && n != 1) ldc2(new CstLong(n));
		else one((byte) (LCONST_0 + (int)n));
	}
	public final void ldc(float n) {
		if (n != 0 && n != 1 && n != 2) ldc1(LDC, new CstFloat(n));
		else one((byte) (FCONST_0 + (int)n));
	}
	public final void ldc(double n) {
		if (n != 0 && n != 1) ldc2(new CstDouble(n));
		else one((byte) (DCONST_0 + (int)n));
	}

	public final void return_(Type type) { one((byte) (IRETURN+type.getShift())); }
	public final void varLoad(Type type, int id) { vars(type.shiftedOpcode(ILOAD), id); }
	public final void varStore(Type type, int id) { vars(type.shiftedOpcode(ISTORE), id); }

	public final void newObject(String name) {
		clazz(NEW, name);
		one(DUP);
		invoke(INVOKESPECIAL, name, "<init>", "()V");
	}
	public final void unpackArray(int slot, Class<?>... types) {
		Type[] types1 = new Type[types.length];
		for (int i = 0; i < types.length; i++) types1[i] = TypeHelper.class2type(types[i]);
		unpackArray(slot, 0, types1);
	}
	public final void unpackArray(int slot, int begin, Type... types) { unpackArray(slot, begin, Arrays.asList(types)); }
	public final void unpackArray(int slot, int begin, List<Type> types) {
		for (int i = 0; i < types.size(); i++) {
			vars(ALOAD, slot);
			ldc(begin+i);
			one(AALOAD);

			Type klass = types.get(i);
			if (klass.isPrimitive()) {
				switch (klass.type) {
					case BOOLEAN:
						clazz(CHECKCAST, "java/lang/Boolean");
						invoke(INVOKESPECIAL, "java/lang/Boolean", "booleanValue", "()Z");
						break;
					case CHAR:
						clazz(CHECKCAST, "java/lang/Character");
						invoke(INVOKESPECIAL, "java/lang/Character", "charValue", "()C");
						break;
					default:
						clazz(CHECKCAST, "java/lang/Number");
						invoke(INVOKEVIRTUAL, "java/lang/Number", Type.toString(klass.type)+"Value", "()"+(char)klass.type);
						break;
				}
			} else {
				clazz(CHECKCAST, klass.getActualClass());
			}
		}
	}
	public final void stdOut(String s) {
		field(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
		ldc(new CstString(s));
		invoke(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
	}
	// endregion

	private static final Supplier<Label> _NEWLABEL = Label::new;
	public final Label _monitor(int bci) { return bciR2W.computeIfAbsentIntS(bci, _NEWLABEL); }

	public static Label newLabel() { return new Label(); }
	public final Label label() {
		if (bciR2W != null) {
			Label label = bciR2W.get(bci);
			if (label != null) return label;
		}
		Label l = newLabel(); label(l); return l;
	}
	public abstract void label(Label x);

	final boolean updateOffset(Collection<Label> labels, int[] offSum, int len) {
		sumOffset(segments, offSum);

		boolean changed = false;
		for (Label label : labels) {
			changed |= label.update(offSum, len);
		}
		return changed;
	}
	static void sumOffset(List<Segment> segments, int[] offSum) {
		int i = 0;
		offSum[0] = 0;
		do {
			Segment c = segments.get(i);
			offSum[++i] = offSum[i-1] + c.length();
		} while (i != segments.size());
	}

	public abstract int bci();
	public static SwitchSegment newSwitch(byte code) { return new SwitchSegment(code); }

	int offset;
	public abstract void addSegment(Segment c);
	final void endSegment() {
		if (!codeOb.isReadable()) segments.remove(segments.size()-1);
		else {
			StaticSegment prev = (StaticSegment) segments.get(segments.size()-1);
			prev.setData(codeOb);
			offset += prev.length();
			codeOb = null;
		}
	}
}