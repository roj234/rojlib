package roj.compiler.resolve;

import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;

/**
 * @author Roj234
 * @since 2024/2/6 3:25
 */
public final class FieldResult {
	public IClass owner;
	public FieldNode field;
	// TODO private synthetic accessor (或者伪装权限？或者直接报错？)
	public MethodNode accessorMethod;
	public String error;

	public FieldResult(String error) { this.error = error; }
	public FieldResult(IClass owner, FieldNode fn) {
		this.owner = owner;
		this.field = fn;
	}
}