package roj.compiler.api_rt;

import roj.asm.tree.MethodNode;
import roj.asm.type.Type;

/**
 * @author Roj234
 * @since 2024/2/20 0020 13:32
 */
public interface StreamChain {
	StreamChain typeMask(String name, Type realType);
	StreamChain typeMask(String name, Type... realType);
	StreamChain startOp(MethodNode node, boolean checkParent);
	StreamChain intermediateOp(MethodNode node);
	StreamChain terminalOp(MethodNode node);
}