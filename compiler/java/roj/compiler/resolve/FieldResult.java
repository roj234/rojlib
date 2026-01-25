package roj.compiler.resolve;

import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.compiler.diagnostic.IText;

/**
 * @author Roj234
 * @since 2024/2/6 3:25
 */
public final class FieldResult {
	public ClassNode owner;
	public FieldNode field;
	public IText error;

	public FieldResult(IText error) { this.error = error; }
	public FieldResult(ClassNode owner, FieldNode fn) {
		this.owner = owner;
		this.field = fn;
	}
}