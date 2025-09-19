package roj.compiler.plugins.stc;

import roj.asm.MethodNode;

/**
 * @author Roj234
 * @since 2024/2/20 13:32
 */
public interface StreamChain {
	StreamChain startOp(MethodNode node);
	StreamChain intermediateOp(MethodNode node);
	StreamChain terminalOp(MethodNode node);
}