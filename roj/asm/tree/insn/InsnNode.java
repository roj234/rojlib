package roj.asm.tree.insn;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.OpcodeUtil;
import roj.asm.util.InsnList;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.util.Helpers;

import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/27 1:12
 */
public abstract class InsnNode implements Helpers.Node {
	public char bci;

	public static final int T_OTHER = 0;
	public static final int T_LOAD_STORE = 1;
	public static final int T_CLASS = 2;
	public static final int T_FIELD = 3;
	public static final int T_INVOKE = 4;
	public static final int T_INVOKE_DYNAMIC = 5;
	public static final int T_GOTO_IF = 6;
	public static final int T_LABEL = 7;
	public static final int T_LDC = 8;
	public static final int T_IINC = 9;
	public static final int T_SWITCH = 10;
	public static final int T_MULTIANEWARRAY = 11;

	protected InsnNode() {}
	protected InsnNode(byte code) {
		setOpcode(code);
	}

	public byte code;

	public void setOpcode(byte code) {
		byte o = this.code;
		this.code = code;
		if (!validate()) {
			this.code = o;
			throw new IllegalArgumentException("Unsupported opcode 0x" + Integer.toHexString(code & 0xFF));
		}
	}

	public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {}

	public final InsnNode next() {
		return next;
	}

	/**
	 * 保证这是一个连接在表内的节点
	 */
	public static InsnNode validate(InsnNode node) {
		int i = 0;
		while (node.next != null) {
			node = node.next;

			if (i++ == 10) {
				if (Helpers.hasCircle(node)) {
					throw new IllegalStateException("Circular reference: " + node);
				}
			}
		}

		return node;
	}

	protected InsnNode next = null;

	/**
	 * 替换
	 */
	@Internal
	public void _i_replace(InsnNode now) {
		if (now != this) this.next = now;
	}

	public final byte getOpcode() {
		return code;
	}

	public final int getOpcodeInt() {
		return code & 0xFF;
	}

	protected boolean validate() {
		return true;
	}

	public int nodeType() {
		return T_OTHER;
	}

	public void preSerialize(CodeWriter c, Map<InsnNode, Label> labels) {}
	public abstract void serialize(CodeWriter cw);

	public abstract int nodeSize(int prevBci);

	public String toString() {
		return OpcodeUtil.toString0(code);
	}
}