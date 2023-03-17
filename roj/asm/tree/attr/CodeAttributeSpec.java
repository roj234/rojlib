package roj.asm.tree.attr;

import roj.asm.tree.insn.InsnNode;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;

import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/17 0017 18:09
 */
public interface CodeAttributeSpec {
	void toByteArray(CodeWriter c);
	void preToByteArray(Map<InsnNode, Label> concerned);
}
