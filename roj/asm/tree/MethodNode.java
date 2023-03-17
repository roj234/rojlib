package roj.asm.tree;

import roj.asm.type.Type;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public interface MethodNode extends MoFNode {
	String ownerClass();
	List<Type> parameters();
	Type getReturnType();

	default String info() {
		return ownerClass()+"."+name()+rawDesc();
	}
}
