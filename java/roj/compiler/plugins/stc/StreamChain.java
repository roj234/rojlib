package roj.compiler.plugins.stc;

import roj.asm.tree.MethodNode;

/**
 * @author Roj234
 * @since 2024/2/20 0020 13:32
 */
public interface StreamChain {
	StreamChain startOp(MethodNode node);
	StreamChain intermediateOp(MethodNode node);
	StreamChain terminalOp(MethodNode node);
}