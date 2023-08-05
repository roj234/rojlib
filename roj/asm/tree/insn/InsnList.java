package roj.asm.tree.insn;

import roj.asm.OpcodeUtil;
import roj.asm.cst.Constant;
import roj.asm.cst.CstInt;
import roj.asm.util.ICodeWriter;
import roj.collect.IntBiMap;
import roj.collect.SimpleList;

import javax.annotation.Nonnull;
import java.util.ListIterator;

import static roj.asm.OpcodeUtil.assertCate;
import static roj.asm.OpcodeUtil.assertTrait;
import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2021/5/24 23:21
 */
public final class InsnList extends SimpleList<InsnNode> implements ICodeWriter {
	static final long serialVersionUID = 0L;

	InsnNode labels;

	@Override
	public boolean add(InsnNode node) {
		if (node instanceof LabelInsnNode) {
			if (labels == null) labels = node;
			else node.next = labels;
			return true;
		} else {
			if (labels != null) {
				labels.next = node;
				labels = null;
			}
			return super.add(node);
		}
	}

	@Override
	public void add(int i, InsnNode node) {
		if (node instanceof LabelInsnNode) {
			throw new IllegalArgumentException("Label insert not rewindable");
		} else {
			if (i == size) {
				if (labels != null) {
					labels.next = node;
					labels = null;
				}
			}
			super.add(i, node);
		}
	}

	public InsnNode setr(int i, InsnNode node) {
		InsnNode prev = (InsnNode) list[i];
		prev.next = node;
		list[i] = node;
		return prev;
	}

	public IntBiMap<InsnNode> getPCMap() {
		IntBiMap<InsnNode> rev = new IntBiMap<>();
		int bci = 0;
		for (int i = 0; i < size(); i++) {
			InsnNode node = get(i);
			rev.putInt(bci, node);
			node.bci = (char) bci;
			bci += node.nodeSize(node.bci);
		}
		return rev;
	}

	public void newArray(byte type) { add(new U1InsnNode(NEWARRAY, type)); }
	public void multiArray(String clz, int dimension) { add(new MDArrayInsnNode(clz, dimension)); }
	public void clazz(byte code, String clz) {
		assertCate(code, OpcodeUtil.CATE_CLASS);
		add(new ClassInsnNode(code, clz));
	}
	public void increase(int id, int count) { add(new IncrInsnNode(id, count)); }
	public void ldc(Constant c) { add(new LdcInsnNode(c)); }
	public void invokeDyn(int idx, String name, String desc, int type) {
		add(new InvokeDynInsnNode(idx, name, desc, type));
	}
	public void invokeItf(String owner, String name, String desc) {
		add(new InvokeInsnNode(INVOKEINTERFACE, owner, name, desc));
	}
	public void invoke(byte code, String owner, String name, String desc) {
		assertCate(code,OpcodeUtil.CATE_METHOD);
		add(new InvokeInsnNode(code, owner, name, desc));
	}
	public void field(byte code, String owner, String name, String type) {
		assertCate(code,OpcodeUtil.CATE_FIELD);
		add(new FieldInsnNode(code, owner, name, type));
	}
	public void one(byte code) {
		assertTrait(code,OpcodeUtil.TRAIT_ZERO_ADDRESS);
		add(new NPInsnNode(code));
	}
	public void smallNum(byte code, int value) {
		if (code == BIPUSH) add(new U1InsnNode(BIPUSH, value));
		else add(new U2InsnNode(SIPUSH, value));
	}
	public final void ldc(int value) {
		if (value >= -1 && value <= 5) {
			add(new NPInsnNode((byte) (value+3)));
		} else if ((byte) value == value) {
			add(new U1InsnNode(BIPUSH, value));
		} else if ((short)value == value) {
			add(new U2InsnNode(SIPUSH, value));
		} else {
			ldc(new CstInt(value));
		}
	}
	public void var(byte code, int value) {
		assertCate(code,OpcodeUtil.CATE_LOAD_STORE_LEN);
		if ((value&0xFF00) != 0) {
			add(new U2InsnNode(code, value));
		} else {
			if (value <= 3) {
				byte c = code >= ISTORE ? ISTORE : ILOAD;
				add(new NPInsnNode((byte) (((code-c) << 2) + c + 5 + value)));
			} else {
				add(new U1InsnNode(code, value));
			}
		}
	}
	public void jsr(int value) {
		add(new JsrInsnNode(JSR, value));
	}
	public void ret(int value) {
		throw new UnsupportedOperationException();
	}

	public void jump(InsnNode node) {
		add(new JumpInsnNode(node));
	}
	public void jump(byte code, InsnNode node) {
		assertTrait(code,OpcodeUtil.TRAIT_JUMP);
		add(new JumpInsnNode(code, node));
	}

	public void switches(SwitchInsnNode c) {
		add(c);
	}
	public LabelInsnNode label() {
		LabelInsnNode x = new LabelInsnNode();
		add(x);
		return x;
	}
	public void label(LabelInsnNode x) {
		add(x);
	}

	@Nonnull
	@Override
	public ListIterator<InsnNode> listIterator(int i) {
		if (labels != null) throw new IllegalArgumentException("label pending");
		return super.listIterator(i);
	}
}
