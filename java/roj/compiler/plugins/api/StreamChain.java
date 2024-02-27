package roj.compiler.plugins.api;

import roj.asm.tree.MethodNode;

/**
 * @author Roj234
 * @since 2024/2/20 0020 13:32
 */
public interface StreamChain {
	StreamChain startOp(MethodNode node, boolean checkParent);
	StreamChain intermediateOp(MethodNode node);
	StreamChain terminalOp(MethodNode node);
}