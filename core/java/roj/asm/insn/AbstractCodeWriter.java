package roj.asm.insn;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;
import roj.RojLib;
import roj.asm.*;
import roj.asm.cp.*;
import roj.asm.type.Type;
import roj.collect.*;
import roj.text.logging.Logger;
import roj.util.DynByteBuf;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2023/10/2 0:57
 */
public abstract class AbstractCodeWriter extends CodeVisitor {
	protected DynByteBuf codeOb;
	protected List<Segment> segments = Collections.emptyList();

	protected final HashSet<Label> labels = new HashSet<>(Hasher.identity());

	IntMap<Label> bciR2W;
	final void validateBciRef() {
		for (IntMap.Entry<Label> entry : bciR2W.selfEntrySet()) {
			if (!entry.getValue().isValid()) {
				if (RojLib.isDev("asm")) {
					Logger.FALLBACK.error("找不到标签引用的BCI @"+entry.getIntKey());
				} else {
					throw new IllegalArgumentException("BCI @"+entry.getIntKey()+" 不存在");
				}
			}
		}
	}

	// region visitor
	protected final void multiArray(CstClass clz, int dimension) { multiArray(clz.value().str(), dimension); }
	protected final void clazz(byte code, CstClass clz) { clazz(code, clz.value().str()); }
	protected final void ldc(byte code, Constant c) { if (code == LDC2_W) ldc2(c); else ldc1(code, c); }
	protected final void invokeDyn(CstDynamic dyn, int type) { invokeDyn(dyn.tableIdx, dyn.desc().name().str(), dyn.desc().rawDesc().str(), type); }
	protected final void invokeItf(CstRef method, short argc) {
		CstNameAndType desc = method.nameAndType();
		invokeItf(method.owner(), desc.name().str(), desc.rawDesc().str());
	}
	protected final void invoke(byte code, CstRef method) {
		CstNameAndType desc = method.nameAndType();
		invoke(code, method.owner(), desc.name().str(), desc.rawDesc().str(), method.type() == Constant.INTERFACE);
	}
	protected final void field(byte code, CstRef field) {
		CstNameAndType desc = field.nameAndType();
		field(code, field.owner(), desc.name().str(), desc.rawDesc().str());
	}
	protected final void jump(byte code, int offset) { jump(code, _rel(offset)); }
	protected final void smallNum(byte code, int value) { ldc(value); }
	protected final void tableSwitch(DynByteBuf r) {
		int def = r.readInt();
		int low = r.readInt();
		int hig = r.readInt();
		int count = hig - low + 1;

		ArrayList<SwitchTarget> map = new ArrayList<>(count);

		if (count > 100000) throw new IllegalArgumentException("length > 100000");

		int i = 0;
		while (count > i) {
			map.add(new SwitchTarget(i++ + low, _rel(r.readInt())));
		}

		addSegment(new SwitchBlock(TABLESWITCH, _rel(def), map, bci));
	}
	protected final void lookupSwitch(DynByteBuf r) {
		int def = r.readInt();
		int count = r.readInt();

		ArrayList<SwitchTarget> map = new ArrayList<>(count);

		if (count > 100000) throw new IllegalArgumentException("length > 100000");

		while (count-- > 0) {
			map.add(new SwitchTarget(r.readInt(), _rel(r.readInt())));
		}

		addSegment(new SwitchBlock(LOOKUPSWITCH, _rel(def), map, bci));
	}
	abstract Label _rel(int pos);
	// endregion

	// region basic instruction
	public abstract void clazz(@MagicConstant(intValues = {NEW, ANEWARRAY, INSTANCEOF, CHECKCAST}) byte code, String clz);
	public void newArray(@Range(from = 4, to = 12) byte type) { codeOb.put(NEWARRAY).put(type); }
	public abstract void multiArray(String clz, int dimension);

	public final void invoke(@MagicConstant(intValues = {INVOKESTATIC,INVOKEVIRTUAL,INVOKESPECIAL}) byte code, String owner, String name, String desc) { invoke(code, owner, name, desc, false); }
	public abstract void invoke(@MagicConstant(intValues = {INVOKESTATIC,INVOKEVIRTUAL,INVOKESPECIAL,INVOKEINTERFACE}) byte code, String owner, String name, String desc, boolean isInterfaceMethod);
	public abstract void invokeItf(String owner, String name, String desc);
	public final void invokeDyn(int idx, String name, String desc) {invokeDyn(idx, name, desc, 0);}
	public abstract void invokeDyn(int idx, String name, String desc, @Range(from = 0, to = 0) int reserved);

	public abstract void field(@MagicConstant(intValues = {GETFIELD, GETSTATIC, PUTFIELD, PUTSTATIC}) byte code, String owner, String name, String type);

	public void iinc(int id, int count) {
		DynByteBuf ob = codeOb;
		if (id >= 0xFF || (byte)count != count) ob.putShort(((WIDE&0xFF)<<8) | (IINC&0xFF)).putShort(id).putShort(count);
		else ob.put(IINC).put(id).put(count);
	}

	public final void ldc(Constant c) {
		switch (c.type()) {
			case Constant.DYNAMIC:
				String dyn = ((CstDynamic) c).desc().rawDesc().str();
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
	protected abstract void ldc1(@MagicConstant(intValues = {Opcodes.LDC, Opcodes.LDC_W}) byte code, Constant c);
	protected abstract void ldc2(Constant c);

	public void insn(@MagicConstant(valuesFromClass = Opcodes.class) byte code) { assertTrait(code, TRAIT_ZERO_ADDRESS); codeOb.put(code); }

	public final void jump(Label target) { jump(GOTO, target); }
	public void jump(@MagicConstant(intValues = {
			IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
			IF_icmpeq, IF_icmpne, IF_icmplt, IF_icmpge, IF_icmpgt, IF_icmple,
			IF_acmpeq, IF_acmpne,
			IFNULL, IFNONNULL,
			GOTO, GOTO_W
	}) byte code, Label target) { assertTrait(code, TRAIT_JUMP); addSegment(new JumpTo(code, target)); }
	public void vars(@MagicConstant(intValues = {ILOAD,LLOAD,FLOAD,DLOAD,ALOAD,ISTORE,LSTORE,FSTORE,DSTORE,ASTORE}) byte code, int value) {
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
	public final void newArray(Type type) {ToPrimitiveArrayId(type.getActualType());}
	public final void newArrayP(int type) {newArray(ToPrimitiveArrayId(type));}

	public final void newArraySized(Type t, int size) {
		ldc(size);
		int type = t.getActualType();
		if (type == CLASS) clazz(ANEWARRAY, t.getActualClass());
		else newArray(ToPrimitiveArrayId(type));
	}
	public final void newArraySized(Type t, int... size) {
		if (t.array()+1 < size.length) throw new IllegalArgumentException("t.array()+1 < size.length");
		for (int i = 0; i < size.length; i++) ldc(size[i]);
		multiArray(t.getActualClass(), size.length);
	}

	private static final String PRIMITIVE_TYPE_TABLE = "ZCFDBSIJ";
	private static final Int2IntMap PRIMITIVE_ARRAY_TABLE = new Int2IntMap(8);
	static {
		for (int i = 0; i < PRIMITIVE_TYPE_TABLE.length(); i++) {
			PRIMITIVE_ARRAY_TABLE.putInt(PRIMITIVE_TYPE_TABLE.charAt(i), i+4);
		}
	}
	public static byte ToPrimitiveArrayId(int descType) {
		int id = PRIMITIVE_ARRAY_TABLE.getOrDefaultInt(descType, 0);
		if (id == 0) throw new IllegalArgumentException(String.valueOf((char) descType));
		return (byte) id;
	}
	public static char FromPrimitiveArrayId(int opType) {return PRIMITIVE_TYPE_TABLE.charAt(opType-4);}

	public final void clazz(@MagicConstant(intValues = {NEWARRAY, INSTANCEOF, CHECKCAST}) byte code, Type clz) {
		if (clz.isPrimitive()) throw new IllegalArgumentException(clz.toString());
		clazz(code, clz.getActualClass());
	}

	public final void invoke(@MagicConstant(intValues = {INVOKESTATIC,INVOKEVIRTUAL,INVOKESPECIAL}) byte code, MemberDescriptor desc) { invoke(code, desc.owner, desc.name, desc.rawDesc); }
	public final void invoke(@MagicConstant(intValues = {INVOKESTATIC,INVOKEVIRTUAL,INVOKESPECIAL}) byte code, MethodNode m) { invoke(code, m.owner(), m.name(), m.rawDesc()); }
	public final void invoke(@MagicConstant(intValues = {INVOKESTATIC,INVOKEVIRTUAL,INVOKESPECIAL}) byte code, ClassDefinition cz, int id) {
		Member node = cz.methods().get(id);
		invoke(code, cz.name(), node.name(), node.rawDesc());
	}

	public final void invokeV(String owner, String name, String desc) { invoke(INVOKEVIRTUAL, owner, name, desc); }
	public final void invokeS(String owner, String name, String desc) { invoke(INVOKESTATIC, owner, name, desc); }
	public final void invokeD(String owner, String name, String desc) { invoke(INVOKESPECIAL, owner, name, desc); }

	public final void field(@MagicConstant(intValues = {GETFIELD, GETSTATIC, PUTFIELD, PUTSTATIC}) byte code, MemberDescriptor desc) { field(code, desc.owner, desc.name, desc.rawDesc); }
	public final void field(@MagicConstant(intValues = {GETFIELD, GETSTATIC, PUTFIELD, PUTSTATIC}) byte code, String owner, String name, Type type) { field(code, owner, name, type.toDesc()); }
	public final void field(@MagicConstant(intValues = {GETFIELD, GETSTATIC, PUTFIELD, PUTSTATIC}) byte code, ClassDefinition cz, int id) {
		Member node = cz.fields().get(id);
		field(code, cz.name(), node.name(), node.rawDesc());
	}
	public final void field(@MagicConstant(intValues = {GETFIELD, GETSTATIC, PUTFIELD, PUTSTATIC}) byte code, String desc) {
		int cIdx = desc.indexOf('.');
		String owner = desc.substring(0, cIdx++);

		int nIdx = desc.indexOf(':', cIdx);
		String name = desc.substring(cIdx, nIdx);
		if (name.charAt(0) == '"') name = name.substring(1, name.length()-1);

		field(code, owner, name, desc.substring(nIdx+1));
	}

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
		else insn((byte) (LCONST_0 + (int)n));
	}
	public final void ldc(float n) {
		if (n != 0 && n != 1 && n != 2) ldc1(LDC, new CstFloat(n));
		else insn((byte) (FCONST_0 + (int)n));
	}
	public final void ldc(double n) {
		if (n != 0 && n != 1) ldc2(new CstDouble(n));
		else insn((byte) (DCONST_0 + (int)n));
	}

	public final void return_(Type type) { insn(type.getOpcode(IRETURN)); }
	public final void varLoad(Type type, int id) { vars(type.getOpcode(ILOAD), id); }
	public final void varStore(Type type, int id) { vars(type.getOpcode(ISTORE), id); }
	public final void arrayLoad(Type type) {insn(ArrayLoad(type));}
	public final void arrayStore(Type type) {insn(ArrayStore(type));}
	public final void arrayLoadP(int type) {insn((byte) (ArrayLoadP(type)+33));}
	public final void arrayStoreP(int type) {insn(ArrayLoadP(type));}

	public static byte ArrayStore(Type type) {return (byte) (ArrayLoad(type)+33);}
	public static byte ArrayLoad(Type type) {return ArrayLoadP(type.type);}
	public static byte ArrayLoadP(int type) {
		return switch (type) {
			case 'I' -> IALOAD;
			case 'J' -> LALOAD;
			case 'F' -> FALOAD;
			case 'D' -> DALOAD;
			case 'L' -> AALOAD;
			case 'Z', 'B' -> BALOAD;
			case 'C' -> CALOAD;
			case 'S' -> SALOAD;
			default -> throw new IllegalArgumentException(String.valueOf((char)type));
		};
	}

	public final void newObject(String name) {
		clazz(NEW, name);
		insn(DUP);
		invoke(INVOKESPECIAL, name, "<init>", "()V");
	}
	public final void unpackArray(int slot, Class<?>... types) {
		Type[] types1 = new Type[types.length];
		for (int i = 0; i < types.length; i++) types1[i] = from(types[i]);
		unpackArray(slot, 0, types1);
	}
	public final void unpackArray(int slot, int begin, Type... types) { unpackArray(slot, begin, Arrays.asList(types)); }
	public final void unpackArray(int slot, int begin, List<Type> types) {
		for (int i = 0; i < types.size(); i++) {
			vars(ALOAD, slot);
			ldc(begin+i);
			insn(AALOAD);

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
						invoke(INVOKEVIRTUAL, "java/lang/Number", Type.getName(klass.type)+"Value", "()"+(char)klass.type);
						break;
				}
			} else {
				clazz(CHECKCAST, klass.getActualClass());
			}
		}
	}
	public final void println(String s) {
		field(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
		ldc(new CstString(s));
		invoke(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
	}
	// endregion

	private static final Supplier<Label> _NEWLABEL = Label::new;
	@ApiStatus.Internal
	public final Label _monitor(int bci) { return bciR2W.computeIfAbsentS(bci, _NEWLABEL); }

	public static Label newLabel() { return new Label(); }
	public final Label label() {
		if (bciR2W != null) {
			Label label = bciR2W.get(bci);
			if (label != null) return label;
		}
		Label l = newLabel(); label(l); return l;
	}
	protected boolean skipFirstSegmentLabels() {return true;}
	public final void label(Label x) {
		if (!x.isUnset()) throw new IllegalStateException("标签的状态不是<unset>: "+x);

		if (segments.isEmpty()) {
			x.setFirst(bci());
			if (skipFirstSegmentLabels()) return;
		} else {
			x.block = (short) (segments.size()-1);
			x.offset = (char) codeOb.wIndex();
			x.value = (char) (x.offset + offset);
		}

		labels.add(x);
	}
	public final void _addLabel(Label x) {labels.add(x);}

	final boolean updateOffset(Collection<Label> labels, int[] offSum, int len) {
		int i = 0;
		offSum[0] = 0;
		if (i != segments.size()) {
			do {
				Segment c = segments.get(i);
				offSum[++i] = offSum[i-1] + c.length();
			} while (i != segments.size());
		} else {
			offSum[1] = bci();
		}

		boolean changed = false;
		for (Label label : labels) {
			changed |= label.update(offSum, len);
		}
		return changed;
	}

	public abstract int bci();

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