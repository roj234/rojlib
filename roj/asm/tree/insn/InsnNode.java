package roj.asm.tree.insn;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.OpcodeUtil;
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

	@Deprecated
	public static final int T_OTHER = OpcodeUtil.CATE_MISC;
	public static final int T_LOAD_STORE = OpcodeUtil.CATE_LOAD_STORE;
	public static final int T_INVOKE_DYNAMIC = 2;
	public static final int T_IINC = 3;
	public static final int T_LDC = OpcodeUtil.CATE_LDC;
	public static final int T_MULTIANEWARRAY = 5;
	public static final int T_SWITCH = 6;
	public static final int T_GOTO_IF = OpcodeUtil.CATE_IF;
	public static final int T_CLASS = OpcodeUtil.CATE_CLASS;
	public static final int T_INVOKE = OpcodeUtil.CATE_METHOD;
	public static final int T_FIELD = OpcodeUtil.CATE_FIELD;

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

	public void preSerialize(Map<InsnNode, Label> labels) {}
	public abstract void serialize(CodeWriter cw);

	public int nodeSize(int prevBci) { return 0; }

	@Override
	public int hashCode() {
		return bci*code;
	}

	public String toString() {
		return OpcodeUtil.toString0(code);
	}
}