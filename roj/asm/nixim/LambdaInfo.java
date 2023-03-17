package roj.asm.nixim;

import roj.asm.tree.attr.BootstrapMethods;
import roj.asm.tree.insn.InvokeDynInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author solo6975
 * @since 2021/10/3 23:25
 */
final class LambdaInfo {
	final BootstrapMethods.BootstrapMethod bootstrapMethod;
	final List<InvokeDynInsnNode> nodes = new ArrayList<>();

	LambdaInfo(BootstrapMethods.BootstrapMethod bm) {
		this.bootstrapMethod = bm;
	}
}
