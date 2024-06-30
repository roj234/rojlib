package roj.compiler.resolve;

import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;

/**
 * @author Roj234
 * @since 2024/2/6 3:25
 */
public final class FieldResult {
	public IClass owner;
	public FieldNode field;
	public String error;

	public FieldResult(String error) { this.error = error; }
	public FieldResult(IClass owner, FieldNode fn) {
		this.owner = owner;
		this.field = fn;
	}
}