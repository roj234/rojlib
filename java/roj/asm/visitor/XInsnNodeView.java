package roj.asm.visitor;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.type.Desc;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.InsnMatcher;
import roj.text.CharList;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2023/8/25 0025 18:10
 */
public final class XInsnNodeView {
	private final XInsnList owner;
	private final boolean shared;

	private byte code;

	private Label pos;
	private int len;

	private Object ref;
	private int id;
	private short number;

	// fastSwitch
	public static final byte[] OPLENGTH = ArrayUtil.unpackB(
	"\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\6\24B\1a<\23J%S*\25!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1AK&\23J%R\1A!\21\t\5\3\2\1A!\21" +
		"\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!8\t\5\3\2\1" +
		"A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\n!\1\1A!\21\t\5\3\4\nEc2\32\rG&\23Mi'\33\5\3\4\"Q!\21\1\23A\1\1\1\1\21");

	XInsnNodeView(XInsnList owner, boolean shared) {
		this.owner = owner;
		this.shared = shared;
	}

	public XInsnNodeView unshared() {
		if (shared) {
			XInsnNodeView copy = new XInsnNodeView(owner, false);
			copy.code = code;
			copy.ref = ref;
			copy.id = id;
			copy.number = number;
			owner.labels.add(copy.pos = new Label(pos));
			copy.len = len;
			return copy;
		}
		return this;
	}

	final void _init(Label pos, Segment seg) {
		this.pos = pos;

		if (seg.getClass() == StaticSegment.class) {
			DynByteBuf r = seg.getData();

			int i = pos.offset;
			code = r.get(i++);

			boolean wide = code == WIDE;
			if (wide) {
				code = r.get(i++);
				CodeVisitor.checkWide(code);
			}

			byte data = OPLENGTH[code&0xFF];
			if (data == 0) throw new IllegalStateException("unknown opcode " + showOpcode(code));

			len = data>>>4;
			assert len > 0;

			if (wide) len *= 2;

			ref = null;
			switch (data&0xF) {
				case 1: case 2: case 3: case 4: case 11: ref = owner.getNodeData(pos); break;
				case 5: id = wide ? r.readUnsignedShort(i) : r.getU(i); break;
				case 8: id = r.readShort(i); break;
				case 6: case 9: id = r.get(i); break;
				case 7:
					id = wide ? r.readUnsignedShort(i) : r.getU(i);
					number = wide ? r.readShort(i+2) : r.get(i+1);
					break;
				case 10:
					ref = owner.getNodeData(pos);
					id = r.getU(i+2);
					break;
			}

			return;
		} else if (seg instanceof LdcSegment) {
			code = seg.length() == 3 ? LDC_W : LDC;
		} else if (seg instanceof JumpSegment) {
			code = ((JumpSegment) seg).code;
		} else if (seg instanceof SwitchSegment) {
			code = ((SwitchSegment) seg).code;
		} else {
			assert false;
		}

		ref = seg;
		id = number = 0;
		len = seg.length();
	}

	public final String opName() { return showOpcode(opcode()); }
	public final byte opcode() {
		if (!pos.isValid()) throw new IllegalStateException("referent Node was removed");
		return code;
	}
	public void setOpcode(int newCode) {
		if ((code&0xFF) == (newCode&0xFF)) return;

		byte oldData = OPLENGTH[code&0xFF];
		byte data = OPLENGTH[newCode&0xFF];
		if (data != oldData) throw new IllegalStateException("只能修改类型与长度相同的: "+ showOpcode(code)+" =X> "+ showOpcode(newCode));

		DynByteBuf data1 = getData();
		if (data1.get(pos.offset) == WIDE) throw new UnsupportedOperationException("对wide的处理暂未实现");
		data1.put(pos.offset, code = (byte) newCode);
	}

	public final int bci() { return pos.getValue(); }
	public final Label pos() { return pos; }
	public final Label end() { return new Label(pos.getValue()+len); }
	public final int length() { return len; }

	private DynByteBuf getData() { return owner.segments.get(pos.block).getData(); }

	/** 直接改，但是不要动flag | owner为null时，是invokeDynamic */
	@NotNull
	public final Desc desc() {
		byte code = opcode();
		if (!(ref instanceof Desc)) invalidArg(code);
		return (Desc) ref;
	}
	public final Desc descOrNull() {
		opcode();
		return ref instanceof Desc ? (Desc) ref : null;
	}

	/**
	 * 不能修改Constant类型，LDC LDC_W LDC2_W
	 */
	@NotNull
	public final Constant constant() {
		byte code = opcode();
		return switch (code) {
			case LDC, LDC_W -> ((LdcSegment) ref).c;
			case LDC2_W -> (Constant) ref;
			default -> invalidArg(code);
		};
	}
	public final Constant constantOrNull() {
		byte code = opcode();
		return switch (code) {
			case LDC, LDC_W -> ((LdcSegment) ref).c;
			case LDC2_W -> (Constant) ref;
			default -> null;
		};
	}

	@NotNull
	public final String type() {
		byte code = opcode();
		// includes 0xC5 (MultiANewArray)
		if (!(ref instanceof String)) invalidArg(code);
		return (String) ref;
	}
	public final String typeOrNull() {
		opcode();
		return ref instanceof String ? (String) ref : null;
	}
	public final void setType(String type) {
		byte code = opcode();
		if (!(ref instanceof String)) invalidArg(code);
		doSetData(type);
	}

	public final int id() {
		byte code = opcode();
		return switch (OPLENGTH[code & 0xFF] & 0xF) {
			case 5, 7 -> id; // vs,vl,ret iinc
			case 6 -> number; // newarray
			default -> invalidArg(code);
		};
	}
	public final void setId(int id) {
		byte code = opcode();
		switch (OPLENGTH[code&0xFF]&0xF) {
			case 5:
				if (id < 0) throw new IllegalArgumentException();
				DynByteBuf data = getData();
				boolean wide = data.get(pos.offset) == WIDE;
				if (id > 255 && !wide) throw new IllegalArgumentException();
				if (wide) data.putShort(pos.offset+1, id);
				else data.put(pos.offset+1, id);
			break;
			case 6:
				//InsnHelper.Type2PrimitiveArray()
				data = getData();
				data.put(pos.offset+1, id);
			break;
			case 7:
				data = getData();
				wide = data.get(pos.offset) == WIDE;
				if ((byte) id != id && !wide) throw new IllegalArgumentException();
				if (wide) data.putShort(pos.offset+1, id);
				else data.put(pos.offset+1, id);
			break;
			default:
				invalidArg(code);
		}
	}

	public final int getVarId() {
		byte opcode = opcode();
		switch (category(opcode)) {
			case CATE_LOAD_STORE:
				String name = Opcodes.showOpcode(opcode);
				return name.charAt(name.length()-1)-'0';
			case CATE_LOAD_STORE_LEN: return id();
		}
		return -1;
	}

	public final int getNumberExact() {
		byte code = opcode();
		return switch (OPLENGTH[code & 0xFF] & 0xF) {
			case 8, 9 -> id; // bipush sipush
			case 7 -> number; // iinc
			default -> invalidArg(code);
		};
	}
	public final void setNumberExact(int num) {
		byte code = opcode();
		switch (OPLENGTH[code&0xFF]&0xF) {
			case 8:
				if ((byte) num != num) throw new IllegalArgumentException();
				getData().put(pos.offset+1, num);
			break;
			case 9:
				if ((short) num != num) throw new IllegalArgumentException();
				getData().putShort(pos.offset+1, num);
			break;
			case 7:
				DynByteBuf data = getData();
				boolean wide = data.get(pos.offset) == WIDE;
				if ((byte) num != num && !wide) throw new IllegalArgumentException();
				if (wide) data.putShort(pos.offset+3, num);
				else data.put(pos.offset+2, num);
			break;
			default: invalidArg(code);
		}
	}

	public final int getAsInt() {
		byte code = opcode();
		if (code>=2&&code<=8) return code-3;
		return switch (code) {
			case BIPUSH, SIPUSH -> id;
			case LDC, LDC_W -> ((CstInt) ref).value;
			default -> invalidArg(code);
		};
	}
	public final long getAsLong() {
		byte code = opcode();
		return switch (code) {
			case LCONST_0 -> 0;
			case LCONST_1 -> 1;
			case LDC2_W -> ((CstLong) ref).value;
			default -> invalidArg(code);
		};

	}
	public final float getAsFloat() {
		byte code = opcode();
		return switch (code) {
			case FCONST_0 -> 0;
			case FCONST_1 -> 1;
			case FCONST_2 -> 2;
			case LDC, LDC_W -> ((CstFloat) ref).value;
			default -> invalidArg(code);
		};
	}
	public final double getAsDouble() {
		byte code = opcode();
		return switch (code) {
			case DCONST_0 -> 0;
			case DCONST_1 -> 1;
			case LDC2_W -> ((CstDouble) ref).value;
			default -> invalidArg(code);
		};
	}

	public final int MultiANewArray_initDimension() {
		byte code = opcode();
		if (code != MULTIANEWARRAY) invalidArg(code);
		return number;
	}
	@NotNull
	public final Type arrayType() {
		byte code = opcode();
		return switch (code) {
			case NEWARRAY -> Type.std(AbstractCodeWriter.FromPrimitiveArrayId(number));
			case ANEWARRAY, MULTIANEWARRAY -> TypeHelper.parseField(type());
			default -> invalidArg(code);
		};
	}

	@NotNull
	public Label target() {
		byte code = opcode();
		if (!(ref instanceof JumpSegment)) invalidArg(code);
		return ((JumpSegment) ref).target;
	}
	public Label targetOrNull() {
		opcode();
		if (!(ref instanceof JumpSegment)) return null;
		return ((JumpSegment) ref).target;
	}
	public void setTarget(Label label) {
		byte code = opcode();
		if (!(ref instanceof JumpSegment)) invalidArg(code);
		((JumpSegment) ref).target = label;
	}
	@NotNull
	public SwitchSegment switchTargets() {
		byte code = opcode();
		if (code != LOOKUPSWITCH && code != TABLESWITCH) invalidArg(code);
		return (SwitchSegment) ref;
	}

	private void doSetData(Object type) {
		ref = type;
		owner.setNodeData(pos, type);
	}

	public XInsnNodeView prev() {
		int bci = owner.getPcMap().prevTrue(pos.getValue()-1);
		return bci < 0 ? Helpers.nonnull() : owner.getNodeAt(bci);
	}
	public XInsnNodeView next() {
		int bci = pos.getValue() + len;
		return bci == owner.bci() ? Helpers.nonnull() : owner.getNodeAt(bci);
	}

	@Deprecated
	public InsnMod replace() { return new InsnMod(owner, pos(), end()); }
	public static class InsnMod {
		XInsnList owner;
		Label from, to;
		public XInsnList list = new XInsnList();

		public InsnMod(XInsnList list, Label from, Label to) {
			this.owner = list;
			this.from = from;
			this.to = to;
		}

		public void commit() {
			if (owner == null) throw new IllegalStateException("committed");
			owner.replaceRange(from, to, list, true);
			owner = null;
		}
	}

	@Deprecated
	public void remove() {replace(new XInsnList(), false);}
	public void replace(XInsnList list, boolean clone) {owner.replaceRange(pos(), end(), list, clone);}
	public void insertBefore(XInsnList list, boolean clone) {Label start = pos();owner.replaceRange(start, start, list, clone);}
	public void insertAfter(XInsnList list, boolean clone) {Label end = end();owner.replaceRange(end, end, list, clone);}

	@SuppressWarnings("fallthrough")
	public boolean isSimilarTo(XInsnNodeView b, InsnMatcher context) {
		if (normalize(code) == normalize(b.code)) {
			switch (OPLENGTH[code&0xFF]&0xF) {
				case 6: case 7: case 8: case 9:
					if (number == b.number) return true;
				default:
					if (ref != null) {
						if (ref.equals(b.ref)) {
							return true;
						}/* else if (ref instanceof Segment) {
							System.out.println(ref);
							return true;
						}*/
					} else {
						int id1 = getVarId();
						int id2 = b.getVarId();
						return id1<0&&id2<0 || context.checkId(id1, id2);
					}
			}
		}
		return opName().startsWith("Ldc") && b.opName().startsWith("Ldc") && ref.equals(b.ref);
	}
	private static int normalize(int code) {
		code &= 0xFF;
		if (code >= ILOAD && code <= ALOAD_3) {
			if (code >= ILOAD_0) code = ((code - ILOAD_0) / 4) + ILOAD;
		} else {
			if (code >= ISTORE_0) code = ((code - ISTORE_0) / 4) + ISTORE;
		}
		return code;
	}

	@Override
	public String toString() {
		CharList sb = new CharList();

		if (pos.isValid()) sb.append("#").append(pos.getValue()).append(' ');
		else sb.append("#<invalid>: ");

		sb.append(showOpcode(code)).append(' ');
		return myToString(sb, false).toStringAndFree();
	}

	public CharList myToString(CharList sb, boolean simple) {
		switch (OPLENGTH[code&0xFF]&0xF) {
			case 1:
				Desc d = desc();
				sb.append(parseOwner(d.owner, simple)).append('.').append(d.name).append(" // ");
				TypeHelper.parseField(d.param).toString(sb);
			break;
			case 2:
				d = desc();
				TypeHelper.humanize(TypeHelper.parseMethod(d.param), parseOwner(d.owner, simple)+'.'+d.name, simple, sb);
			break;
			case 3:
				d = desc();
				TypeHelper.humanize(TypeHelper.parseMethod(d.param), "*."+d.name, simple, sb).append(" // [#").append((int)d.flags).append(']');
			break;
			case 6:
				//noinspection MagicConstant
				sb.append(Type.std(AbstractCodeWriter.FromPrimitiveArrayId(id))); break;
			case 5: case 8: case 9: sb.append(id); break;
			case 7: sb.append('#').append(id).append(number >= 0 ? " += " : " -= ").append(Math.abs(number)); break;
			case 10: TypeHelper.parseField(ref.toString()).toString(sb); sb.append(" // [维度=").append(id).append(']'); break;
			default:
				if (ref instanceof LdcSegment) sb.append(((LdcSegment) ref).c.getEasyReadValue());
				else if (ref instanceof JumpSegment) sb.append(((JumpSegment) ref).target);
				else if (ref instanceof SwitchSegment) sb.append(ref);
				else if (ref instanceof Constant) {
					sb.append(((Constant) ref).getEasyReadValue());
				} else if (ref != null) sb.append(parseOwner((String) ref, simple));
			break;
		}
		return sb;
	}

	private static String parseOwner(String owner, boolean simple) {
		String val = owner.startsWith("[")?TypeHelper.parseField(owner).toString():owner;
		return simple ? val.substring(val.lastIndexOf('/')+1) : val;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		XInsnNodeView view = (XInsnNodeView) o;

		if (opcode() != view.opcode()) return false;
		if (id != view.id) return false;
		if (number != view.number) return false;
		return ref != null ? ref.equals(view.ref) : view.ref == null;
	}

	@Override
	public int hashCode() {
		int result = id;
		result = 31 * result + number;
		result = 31 * result + opcode();
		result = 31 * result + (ref != null ? ref.hashCode() : 0);
		return result;
	}

	public void appendTo(XInsnList list) {
		opcode();

		if (ref instanceof Segment) {
			list.addSegment((Segment) ref);
			return;
		} else if (ref != null) {
			list.addRef(ref);
		}

		list.codeOb.put(getData(), pos.offset, len);
	}

	private static <T> T invalidArg(byte code) { throw new UnsupportedOperationException(showOpcode(code)+"不支持该操作"); }
}