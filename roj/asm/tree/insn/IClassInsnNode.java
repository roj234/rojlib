package roj.asm.tree.insn;

/**
 * @author Roj234
 * @since 2021/1/1 23:12
 */
public interface IClassInsnNode {
	String owner();

	void owner(String clazz);
}