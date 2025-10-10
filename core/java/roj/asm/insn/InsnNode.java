package roj.asm.insn;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.asm.MemberDescriptor;
import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.compiler.runtime.RtUtil;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import static roj.asm.Opcodes.*;

/**
 * <code>InsnNode</code>是<code>InsnList</code>中单个指令的视图对象，提供对指令操作码、操作数、常量池引用和标签等属性的访问和修改。
 * 每个节点绑定到其所属的指令列表（owner），并通过位置标签（Label）定位在字节码流中的确切位置。
 * 节点支持直接修改底层字节码，同时提供类型安全的访问方法来处理不同指令类型的特定数据。
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>操作码访问和修改，支持类型检查以确保兼容性</li>
 *   <li>操作数解析：变量索引、常量值、标签目标、数组维度等</li>
 *   <li>常量池引用处理：成员描述符、常量、类名和类型</li>
 *   <li>结构化修改：插入、替换、删除指令，支持克隆选项</li>
 *   <li>迭代导航：获取前一个或下一个指令节点</li>
 *   <li>相似性比较：用于模式匹配和优化</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * InsnList list = new InsnList();
 * list.ldc(5);
 * list.vars(ILOAD,0);
 * list.insn(IADD);
 *
 * // 获取第一个指令节点
 * InsnNode node = list.first();
 * System.out.println(node.opName()); // "iconst_5"
 * System.out.println(node.getAsInt()); // 5
 *
 * // 直接修改（仅限类型和长度相同的操作码）
 * InsnNode loadNode = list.since(0).next();
 * loadNode.setOpcode(Opcodes.ALOAD_0);
 *
 * // 替换节点
 * InsnList replacement = new InsnList();
 * replacement.ldc("hello");
 * node.replace(replacement, false);
 * }</pre></p>
 *
 * <p>注意：节点的位置标签必须有效，否则操作会抛出<code>ConcurrentModificationException</code>。
 * 修改操作会直接影响所属的<code>InsnList</code>。</p>
 *
 * @author Roj234
 * @see InsnList
 * @see Label
 * @see Opcodes
 */
public final class InsnNode {
	private final InsnList owner;
	private final InsnList.NodeIterator attachTo;

	private Label pos;

	private byte code;
	private int len;
	private int dataIndex;

	private Object data;
	private int id;
	private short number;

	/**
	 * 操作码长度和类型标志的预计算表。
	 * <p>这是一个字节数组，用于快速确定每个操作码的长度（高4位）和操作数类型（低4位）。
	 */
	public static final byte[] OPLENGTH = RtUtil.unpackB(
	"\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\6\24B\1a<\23J%S*\25!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1AK&\23J%R\1A!\21\t\5\3\2\1A!\21" +
		"\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!8\t\5\3\2\1" +
		"A!\21\t\5\3\2\1A!\21\t\5\3\2\1A!\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\n!\1\1A!\21\t\5\3\4\nEc2\32\rG&\23Mi'\33\5\3\4\"Q!\21\1\23A\1\1\1\1\21");

	InsnNode(InsnList owner, InsnList.NodeIterator attachTo) {
		this.owner = owner;
		this.attachTo = attachTo;
	}

	/**
	 * 分离节点，创建独立副本。
	 *
	 * <p>如果节点当前附加到迭代器，从迭代器中分离并返回一个新的独立节点副本。
	 * 独立节点不会受迭代器状态影响，但仍绑定到同一指令列表。</p>
	 *
	 * <p>如果节点已经是独立的，直接返回自身。</p>
	 *
	 * @return 独立指令节点副本
	 */
	@Contract(pure = true)
	public InsnNode detach() {
		if (attachTo == null) return this;

		InsnNode copy = new InsnNode(owner, null);
		copy.code = code;
		copy.data = data;
		copy.id = id;
		copy.number = number;
		copy.pos = new Label(pos);
		copy.len = len;
		return copy;
	}

	final void setPos(Label pos, Segment seg) {
		this.pos = pos;

		if (seg.getClass() == StaticSegment.class) {
			DynByteBuf r = seg.getData();

			int i = pos.offset;
			code = r.getByte(i++);

			boolean wide = code == WIDE;
			if (wide) {
				code = r.getByte(i++);
				CodeVisitor.checkWide(code);
			}

			byte flags = OPLENGTH[code&0xFF];
			if (flags == 0) throw new IllegalStateException("illegal opcode " + Opcodes.toString(code));

			len = flags>>>4;
			assert len > 0;

			if (wide) len *= 2;

			this.data = null;
			switch (flags & 0xF) {
				case 1, 2, 3, 4, 11 -> {
					dataIndex = owner.findRef(pos);
					data = owner.refVal[dataIndex];
				}
				case 5 -> id = wide ? r.getUnsignedShort(i) : r.getUnsignedByte(i);
				case 8 -> id = r.getShort(i);
				case 6, 9 -> id = r.getByte(i);
				case 7 -> {
					id = wide ? r.getUnsignedShort(i) : r.getUnsignedByte(i);
					number = wide ? r.getShort(i+2) : r.getByte(i+1);
				}
				case 10 -> {
					dataIndex = owner.findRef(pos);
					data = owner.refVal[dataIndex];
					id = r.getUnsignedByte(i+2);
				}
			}

			return;
		} else if (seg instanceof Ldc ldc) {
			code = seg.length() == 3 ? LDC_W : LDC;
			data = ldc;
		} else if (seg instanceof JumpTo) {
			code = ((JumpTo) seg).code;
			data = seg;
		} else if (seg instanceof SwitchBlock) {
			code = ((SwitchBlock) seg).code;
			data = seg;
		} else {
			assert false;
		}

		id = number = 0;
		len = seg.length();
	}
	private DynByteBuf getData() {return owner.segments.get(pos.block).getData();}
	private void setData(Object newData) {
		data = newData;

		int target = (pos.block << 16) | pos.offset;
		if (dataIndex >= owner.refCount || owner.refPos[dataIndex] != target) {
			dataIndex = owner.findRef(pos);
		}
		owner.refVal[dataIndex] = newData;
	}

	/**
	 * Returns the human-readable name of the opcode (e.g., "ALoad", "InvokeVirtual").
	 *
	 * @return the opcode name as a string
	 * @see Opcodes#toString(int)
	 */
	public final String opName() { return Opcodes.toString(opcode()); }
	/**
	 * Returns the opcode byte of this instruction.
	 * Validates that the node is still valid (not removed).
	 *
	 * @return the opcode byte
	 * @throws IllegalStateException if the node was removed from its list
	 */
	@MagicConstant(valuesFromClass = Opcodes.class)
	public final byte opcode() {
		if (!pos.isBound()) throw new IllegalStateException("Node was removed");
		return code;
	}
	/**
	 * Sets the opcode to a new value, but only if it has the same length and operand type.
	 * Validates compatibility using {@link #OPLENGTH}.
	 * <p>
	 * Does nothing if the opcode is unchanged.
	 * Currently unsupported for WIDE-prefixed instructions.
	 *
	 * @param newCode the new opcode value
	 * @throws IllegalStateException if the new opcode has incompatible length or type
	 * @throws UnsupportedOperationException if the instruction uses WIDE prefix
	 */
	public void setOpcode(int newCode) {
		if ((code&0xFF) == (newCode&0xFF)) return;

		byte oldData = OPLENGTH[code&0xFF];
		byte data = OPLENGTH[newCode&0xFF];
		if (data != oldData) throw new IllegalStateException("只能修改类型与长度相同的: "+Opcodes.toString(code)+" =X> "+Opcodes.toString(newCode));

		DynByteBuf data1 = getData();
		if (data1.getByte(pos.offset) == WIDE) throw new UnsupportedOperationException("对wide的处理暂未实现");
		data1.set(pos.offset, code = (byte) newCode);
	}

	public final int bci() { return pos.getValue(); }
	public final int length() { return len; }
	public final Label pos() { return pos; }
	public final Label end() { return new Label(pos.getValue()+len); }

	//region accessor / mutator
	/**
	 * Return a <b>mutable</b> {@link MemberDescriptor} for field/method related opcodes.
	 * The {@code flags} field should not be changed.
	 * If the {@code owner} is null, indicates this is an {@code invokeDynamic} instruction.
	 *
	 * @return the member descriptor (field or method reference)
	 * @throws UnsupportedOperationException if the opcode does not support member descriptors
	 */
	@NotNull
	@Contract(pure = true)
	public final MemberDescriptor desc() {
		byte code = opcode();
		if (!(data instanceof MemberDescriptor)) invalidArg(code);
		return (MemberDescriptor) data;
	}
	/**
	 * @see #desc()
	 */
	@Contract(pure = true)
	public final MemberDescriptor descOrNull() {
		opcode();
		return data instanceof MemberDescriptor ? (MemberDescriptor) data : null;
	}

	/**
	 * Returns the <b>mutable</b> constant value for LDC, LDC_W, or LDC2_W instructions.
	 *
	 * @return the constant value
	 * @throws UnsupportedOperationException if the opcode is not a load constant instruction
	 */
	@NotNull
	@Contract(pure = true)
	public final Constant constant() {
		byte code = opcode();
		return switch (code) {
			case LDC, LDC_W -> ((Ldc) data).constant;
			case LDC2_W -> (Constant) data;
			default -> invalidArg(code);
		};
	}
	/**
	 * @see #constant()
	 */
	@Contract(pure = true)
	public final Constant constantOrNull() {
		byte code = opcode();
		return switch (code) {
			case LDC, LDC_W -> ((Ldc) data).constant;
			case LDC2_W -> (Constant) data;
			default -> null;
		};
	}
	/**
	 * Sets the constant value for constant-referencing instructions.
	 * Validates that the current data is a constant and its type matches.
	 *
	 * @param constant the new type descriptor
	 * @throws UnsupportedOperationException if the opcode does not reference a constant
	 */
	@ApiStatus.Experimental
	public final void setConstant(Constant constant) {
		Type oldType = constant().resolvedType();
		Type newType = constant.resolvedType();

		if (newType.isPrimitive() ? newType.getActualType() != oldType.getActualType() : oldType.isPrimitive()) {
			throw new UnsupportedOperationException("Constant type was changed from "+oldType+" => "+newType);
		}

		if (code == LDC2_W) setData(constant);
		else ((Ldc) data).constant = constant;
	}

	/**
	 * Returns the type string for instructions like CHECKCAST or MULTIANEWARRAY.
	 *
	 * @return the type descriptor string
	 * @throws UnsupportedOperationException if the opcode does not reference a type
	 */
	@NotNull
	@Contract(pure = true)
	public final String type() {
		byte code = opcode();
		// including 0xC5 (MultiANewArray)
		if (!(data instanceof String)) invalidArg(code);
		return (String) data;
	}
	/**
	 * @see #type()
	 */
	@Contract(pure = true)
	public final String typeOrNull() {
		opcode();
		return data instanceof String ? (String) data : null;
	}
	/**
	 * Sets the type string for type-referencing instructions.
	 * Validates that the current data is a string.
	 *
	 * @param type the new type descriptor
	 * @throws UnsupportedOperationException if the opcode does not reference a type
	 */
	public final void setType(String type) {
		byte code = opcode();
		if (!(data instanceof String)) invalidArg(code);
		setData(type);
	}

	/**
	 * Returns the ID (index or value) specific to the opcode category,
	 * such as local variable index (for load/store/IINC/ret), or array type code (for NEWARRAY).
	 * <b>Not applicable for compact opcode forms like ALOAD_0</b>
	 *
	 * @return the ID value
	 * @throws UnsupportedOperationException if the opcode does not have an ID
	 */
	@Contract(pure = true)
	public final int id() {
		byte code = opcode();
		return switch (OPLENGTH[code & 0xFF] & 0xF) {
			case 5, 7 -> id; // vs,vl,ret iinc
			case 6 -> number; // newarray
			default -> invalidArg(code);
		};
	}
	/**
	 * Sets the ID value, handling wide prefixes where necessary.
	 * Supports variable loads/stores, IINC, and NEWARRAY.
	 * Validates range and prefix compatibility.
	 *
	 * @param id the new ID value (must be non-negative for variables)
	 * @throws IllegalArgumentException if the ID is out of range or requires wide but not present
	 * @throws UnsupportedOperationException if the opcode does not support setting ID
	 */
	public final void setId(int id) {
		byte code = opcode();
		switch (OPLENGTH[code&0xFF]&0xF) {
			case 5:
				if (id < 0) throw new IllegalArgumentException();
				DynByteBuf data = getData();
				boolean wide = data.getByte(pos.offset) == WIDE;
				if (id > 255 && !wide) throw new IllegalArgumentException();
				if (wide) data.setShort(pos.offset+1, id);
				else data.set(pos.offset+1, id);
			break;
			case 6:
				// 这是rawid，我是否该替程序员转换？
				data = getData();
				data.set(pos.offset+1, id);
			break;
			case 7:
				data = getData();
				wide = data.getByte(pos.offset) == WIDE;
				if ((byte) id != id && !wide) throw new IllegalArgumentException();
				if (wide) data.setShort(pos.offset+1, id);
				else data.set(pos.offset+1, id);
			break;
			default:
				invalidArg(code);
		}
	}

	/**
	 * Returns the local variable index for load/store opcodes (e.g., ILOAD, ASTORE).
	 * Returns -1 for non-variable opcodes or variable-length forms (use {@link #id()} instead).
	 *
	 * @return the variable index, or -1
	 */
	@Contract(pure = true)
	public final int getVarId() {
		byte opcode = opcode();
		switch (category(opcode)) {
			case CATE_LOAD_STORE:
				String name = Opcodes.toString(opcode);
				return name.charAt(name.length()-1)-'0';
			case CATE_LOAD_STORE_LEN: return id();
		}
		return -1;
	}

	/**
	 * Returns the exact numeric value for push opcodes (BIPUSH, SIPUSH, IINC const).
	 * Validates the opcode category.
	 *
	 * @return the numeric value
	 * @throws UnsupportedOperationException if the opcode does not push an exact number
	 */
	@Contract(pure = true)
	public final int getNumberExact() {
		byte code = opcode();
		return switch (OPLENGTH[code & 0xFF] & 0xF) {
			case 8, 9 -> id; // bipush sipush
			case 7 -> number; // iinc
			default -> invalidArg(code);
		};
	}
	/**
	 * Sets the exact numeric value for push opcodes (BIPUSH, SIPUSH, IINC const).
	 * Handles wide prefixes for IINC and validates range.
	 *
	 * @param num the new numeric value
	 * @throws IllegalArgumentException if the value exceeds the opcode's range
	 * @throws UnsupportedOperationException if the opcode does not support setting number
	 */
	public final void setNumberExact(int num) {
		byte code = opcode();
		switch (OPLENGTH[code&0xFF]&0xF) {
			case 8:
				if ((byte) num != num) throw new IllegalArgumentException();
				getData().set(pos.offset+1, num);
			break;
			case 9:
				if ((short) num != num) throw new IllegalArgumentException();
				getData().setShort(pos.offset+1, num);
			break;
			case 7:
				DynByteBuf data = getData();
				boolean wide = data.getByte(pos.offset) == WIDE;
				if ((byte) num != num && !wide) throw new IllegalArgumentException();
				if (wide) data.setShort(pos.offset+3, num);
				else data.set(pos.offset+2, num);
			break;
			default: invalidArg(code);
		}
	}

	@MagicConstant(intValues = {
			Constant.INT, Constant.LONG, Constant.FLOAT, Constant.DOUBLE, Constant.DYNAMIC, Constant.METHOD_HANDLE, Constant.METHOD_TYPE,
			-1
	})
	@Contract(pure = true)
	public int getConstantType() {
		byte code = opcode();
		// noinspection MagicConstant
		return switch (code) {
			// ICONST
			case ICONST_M1,ICONST_0,ICONST_1,ICONST_2,ICONST_3,ICONST_4,ICONST_5
			,BIPUSH,SIPUSH -> Constant.INT;
			case LCONST_0, LCONST_1 -> Constant.LONG;
			case FCONST_0, FCONST_1, FCONST_2 -> Constant.FLOAT;
			case DCONST_0, DCONST_1 -> Constant.DOUBLE;
			case LDC, LDC_W -> ((Ldc) data).constant.type();
			case LDC2_W -> ((Constant) data).type();
			default -> -1;
		};
	}

	/**
	 * 检查常量值是否相等，如果该节点是常量节点的话
	 * @param constantType 常量类型
	 * @param constantRepr 这里的字符串表示为raw格式，也就是不带前后缀或escape的值
	 * @return 常量值是否相等
	 * @see Constant#getEasyCompareValue()
	 */
	@Contract(pure = true)
	public boolean constantEquals(int constantType, String constantRepr) {
		byte code = opcode();
		return switch (code) {
			case ICONST_M1,ICONST_0,ICONST_1,ICONST_2,ICONST_3,ICONST_4,ICONST_5 -> constantType == Constant.INT && String.valueOf(code-3).equals(constantRepr);
			case LCONST_0 -> constantType == Constant.LONG && "0".equals(constantRepr);
			case LCONST_1 -> constantType == Constant.LONG && "1".equals(constantRepr);
			case FCONST_0 -> constantType == Constant.FLOAT && "0".equals(constantRepr);
			case FCONST_1 -> constantType == Constant.FLOAT && "1".equals(constantRepr);
			case FCONST_2 -> constantType == Constant.FLOAT && "2".equals(constantRepr);
			case DCONST_0 -> constantType == Constant.DOUBLE && "0".equals(constantRepr);
			case DCONST_1 -> constantType == Constant.DOUBLE && "1".equals(constantRepr);
			case BIPUSH, SIPUSH -> constantType == Constant.INT && String.valueOf(id).equals(constantRepr);
			case LDC, LDC_W -> {
				Constant c = ((Ldc) data).constant;
				yield c.type() == constantType && c.toString().equals(constantRepr);
			}
			case LDC2_W -> {
				Constant c = (Constant) data;
				yield c.type() == constantType && c.toString().equals(constantRepr);
			}
			default -> false;
		};
	}

	@Contract(pure = true)
	public final int getAsInt() {
		byte code = opcode();
		return switch (code) {
			case ICONST_M1,ICONST_0,ICONST_1,ICONST_2,ICONST_3,ICONST_4,ICONST_5 -> code-3;
			case BIPUSH, SIPUSH -> id;
			case LDC, LDC_W -> ((CstInt) ((Ldc) data).constant).value;
			default -> invalidArg(code);
		};
	}
	@Contract(pure = true)
	public final long getAsLong() {
		byte code = opcode();
		return switch (code) {
			case LCONST_0 -> 0;
			case LCONST_1 -> 1;
			case LDC2_W -> ((CstLong) data).value;
			default -> invalidArg(code);
		};
	}
	@Contract(pure = true)
	public final float getAsFloat() {
		byte code = opcode();
		return switch (code) {
			case FCONST_0 -> 0;
			case FCONST_1 -> 1;
			case FCONST_2 -> 2;
			case LDC, LDC_W -> ((CstFloat) ((Ldc) data).constant).value;
			default -> invalidArg(code);
		};
	}
	@Contract(pure = true)
	public final double getAsDouble() {
		byte code = opcode();
		return switch (code) {
			case DCONST_0 -> 0;
			case DCONST_1 -> 1;
			case LDC2_W -> ((CstDouble) data).value;
			default -> invalidArg(code);
		};
	}

	/**
	 * Returns the number of dimensions for MULTIANEWARRAY instructions.
	 *
	 * @return the dimension count (1-255)
	 * @throws UnsupportedOperationException if not MULTIANEWARRAY
	 */
	@Contract(pure = true)
	public final int multiArrayDimensions() {
		byte code = opcode();
		if (code != MULTIANEWARRAY) invalidArg(code);
		return number;
	}
	/**
	 * Returns the array type for NEWARRAY, ANEWARRAY, or MULTIANEWARRAY.
	 *
	 * @return the {@link Type} of the array
	 * @throws UnsupportedOperationException if not an array creation instruction
	 */
	@NotNull
	@Contract(pure = true)
	public final Type arrayType() {
		byte code = opcode();
		return switch (code) {
			case NEWARRAY -> Type.primitive(Type.getByArrayType(number));
			case ANEWARRAY, MULTIANEWARRAY -> Type.getType(type());
			default -> invalidArg(code);
		};
	}

	@NotNull
	@Contract(pure = true)
	public Label target() {
		byte code = opcode();
		if (!(data instanceof JumpTo)) invalidArg(code);
		return ((JumpTo) data).target;
	}
	@Contract(pure = true)
	public Label targetOrNull() {
		opcode();
		if (!(data instanceof JumpTo t)) return null;
		return t.target;
	}
	/**
	 * Sets the target label for jump instructions.
	 *
	 * @param label the new target label
	 * @throws UnsupportedOperationException if not a jump instruction
	 */
	public void setTarget(Label label) {
		byte code = opcode();
		if (!(data instanceof JumpTo)) invalidArg(code);
		((JumpTo) data).target = label;
	}
	/**
	 * Returns the switch block for LOOKUPSWITCH or TABLESWITCH instructions.
	 *
	 * @return the {@link SwitchBlock} containing targets and keys
	 * @throws UnsupportedOperationException if not a switch instruction
	 */
	@NotNull
	public SwitchBlock switchTargets() {
		byte code = opcode();
		if (code != LOOKUPSWITCH && code != TABLESWITCH) invalidArg(code);
		return (SwitchBlock) data;
	}
	//endregion

	/**
	 * Returns the previous instruction node in the list.
	 * Skips over non-instruction bytes using the PC map (slow).
	 *
	 * @return the previous node, or null if at start
	 */
	@Contract(pure = true)
	@ApiStatus.Experimental
	public InsnNode prev() {
		int bci = owner.getPcMap().prevTrue(pos.getValue()-1);
		return bci < 0 ? Helpers.maybeNull() : owner.getNodeAt(bci);
	}
	/**
	 * Returns the next instruction node in the list.
	 *
	 * @return the next node, or null if at end
	 */
	@Contract(pure = true)
	@ApiStatus.Experimental
	public InsnNode next() {
		int bci = pos.getValue() + len;
		return bci == owner.length() ? Helpers.maybeNull() : owner.getNodeAt(bci);
	}

	/**
	 * Removes this instruction from its owning instruction list.
	 * <p>
	 * This operation is equivalent to replacing the instruction with an empty list.
	 * After removal, the node's position may becomes invalid and subsequent operations
	 * may throw {@link IllegalStateException}.
	 */
	public void remove() {replace(InsnList.EMPTY, true);}

	/**
	 * Replaces this instruction with the specified instruction list.
	 * This is a convenience method equivalent to {@code replace(data, false)}.
	 *
	 * @param data the instruction list to replace with (will be moved)
	 */
	public void replace(InsnList data) {replace(data, false);}
	/**
	 * Replaces this instruction with the given list of instructions at the current position.
	 * This {@code InsnNode} will now refer to {@code data.first()} if data is not empty, otherwise {@code this.next()}.
	 * If this InsnNode is the last element of owner InsnList, this InsnNode will become invalid.
	 *
	 * @param data  the new instruction list to insert
	 * @param clone {@code true} to clone the instructions, {@code false} to move them directly
	 * @throws IllegalStateException if the node has been removed or is invalid
	 */
	public void replace(InsnList data, boolean clone) {
		opcode();
		Label pos = this.pos;
		owner.replace(pos, end(), data, clone);
		Segment seg = owner.segments.get(pos.block);
		if (seg.length() == 0) {
			pos.clear();
		} else {
			if (pos.offset != 0) {
				pos.offset = 0;
				pos.block++;
			}
			setPos(pos, seg);
		}
	}

	/**
	 * Inserts the specified instruction list before this instruction.
	 * This is a convenience method equivalent to {@code insertBefore(data, false)}.
	 *
	 * @param data the instruction list to insert before this instruction (will be moved)
	 */
	public void insertBefore(InsnList data) {insertBefore(data, false);}
	/**
	 * Inserts the given list of instructions immediately before this instruction.
	 * Current {@code InsnNode}'s position is adjusted to maintain correct iterating.
	 *
	 * @param data  the instructions to insert before this node
	 * @param clone {@code true} to clone the instructions, {@code false} to move them directly
	 * @throws IllegalStateException if the node has been removed or is invalid
	 */
	public void insertBefore(InsnList data, boolean clone) {
		opcode();
		Label pos = this.pos;
		var segment = owner.segments.get(pos.block);

		int size = data.segments.size();
		owner.replace(pos, pos, data, clone);

		if (pos.offset >= segment.length()) {
			pos.block += size+1;
			pos.offset = 0;
			segment = owner.segments.get(pos.block);
		} else {
			int segmentId = owner.segments.indexOf(segment);
			pos.block = (short) segmentId;
			assert pos.offset == 0;
		}

		owner.indexLabel(pos);
		setPos(pos, segment);
	}

	/**
	 * Inserts the specified instruction list after this instruction.
	 * This is a convenience method equivalent to {@code insertAfter(data, false)}.
	 *
	 * @param data the instruction list to insert after this instruction (will be moved)
	 */
	public void insertAfter(InsnList data) {insertAfter(data, false);}
	/**
	 * Inserts the given list of instructions immediately after this instruction.
	 * The instructions are inserted at the end position of this node.
	 *
	 * @param data  the instructions to insert after this node
	 * @param clone {@code true} to clone the instructions, {@code false} to move them directly
	 * @throws IllegalStateException if the node has been removed or is invalid
	 */
	public void insertAfter(InsnList data, boolean clone) {
		Label end = end();
		owner.replace(end, end, data, clone);
	}

	/**
	 * 将当前节点追加到目标列表
	 * <b>移动，而不是复制！</b>
	 *
	 * @param target the target instruction list
	 */
	@ApiStatus.Experimental
	public void appendTo(InsnList target) {
		opcode();

		if (data instanceof Segment seg) {
			target.addSegment(seg);
			return;
		}

		if (data != null) target.addRef(data);
		target.codeOb.put(getData(), pos.offset, len);
	}

	@SuppressWarnings("fallthrough")
	boolean isSimilarTo(InsnNode other, InsnMatcher context) {
		if (normalize(code) == normalize(other.code)) {
			switch (OPLENGTH[code&0xFF]&0xF) {
				case 6, 7, 8, 9:
					if (number == other.number) return true;
				default:
					if (data != null) {
						if (data.equals(other.data)) {
							return true;
						}/* else if (ref instanceof Segment) {
							System.out.println(ref);
							return true;
						}*/
					} else {
						int id1 = getVarId();
						int id2 = other.getVarId();
						return id1<0&&id2<0 || context.checkId(id1, id2);
					}
			}
		}
		return opName().startsWith("Ldc") && other.opName().startsWith("Ldc") && data.equals(other.data);
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

		if (pos.isBound()) sb.append("#").append(pos.getValue()).append(' ');
		else sb.append("#<invalid>: ");

		sb.append(Opcodes.toString(code)).append(' ');
		try {
			return myToString(sb, false).toStringAndFree();
		} catch (Exception e) {
			e.printStackTrace();
			return sb.toStringAndFree();
		}
	}

	public CharList myToString(CharList sb, boolean simple) {
		switch (OPLENGTH[code & 0xFF] & 0xF) {
			case 1 -> {
				var d = desc();
				sb.append(parseOwner(d.owner, simple)).append('.').append(d.name).append(" // ");
				Type.getType(d.rawDesc).toString(sb);
			}
			case 2 -> {
				var d = desc();
				TypeHelper.humanize(Type.getMethodTypes(d.rawDesc), parseOwner(d.owner, simple)+'.'+d.name, simple, sb);
			}
			case 3 -> {
				var d = desc();
				TypeHelper.humanize(Type.getMethodTypes(d.rawDesc), "[#"+(int)d.modifier+"]."+d.name, simple, sb);
			}
			case 6 -> sb.append(Type.primitive(Type.getByArrayType(id)));
			case 5, 8, 9 -> sb.append(id);
			case 7 -> sb.append('#').append(id).append(number >= 0 ? " += " : " -= ").append(Math.abs(number));
			case 10 -> {
				Type.getType(data.toString()).toString(sb);
				sb.append(" // [维度=").append(id).append(']');
			}
			default -> {
				if (data instanceof Ldc) sb.append(((Ldc) data).constant.toString());
				else if (data instanceof JumpTo) sb.append(((JumpTo) data).target);
				else if (data instanceof SwitchBlock) sb.append(data);
				else if (data instanceof Constant) sb.append(data.toString());
				else if (data != null) sb.append(parseOwner((String) data, simple));
			}
		}
		return sb;
	}

	private static String parseOwner(String owner, boolean simple) {
		String val = owner.startsWith("[")? Type.getType(owner).toString():owner;
		return simple ? val.substring(val.lastIndexOf('/')+1) : val;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		InsnNode view = (InsnNode) o;

		if (opcode() != view.opcode()) return false;
		if (id != view.id) return false;
		if (number != view.number) return false;
		return data != null ? data.equals(view.data) : view.data == null;
	}

	@Override
	public int hashCode() {
		int result = id;
		result = 31 * result + number;
		result = 31 * result + opcode();
		result = 31 * result + (data != null ? data.hashCode() : 0);
		return result;
	}

	private static <T> T invalidArg(byte code) { throw new UnsupportedOperationException(Opcodes.toString(code)+"不支持该操作"); }
}