package roj.compiler.resolve;

import roj.asm.ClassDefinition;
import roj.asm.FieldNode;

/**
 * @author Roj234
 * @since 2024/2/6 3:25
 */
public final class FieldResult {
	public ClassDefinition owner;
	public FieldNode field;
	public String error;

	public FieldResult(String error) { this.error = error; }
	public FieldResult(ClassDefinition owner, FieldNode fn) {
		this.owner = owner;
		this.field = fn;
	}
}