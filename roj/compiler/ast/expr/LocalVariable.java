package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.config.word.NotStatementException;

/**
 * @author Roj234
 * @since 2024/1/28 0028 16:27
 */
public class LocalVariable implements VarNode {
	Variable v;

	public LocalVariable(Variable v) { this.v = v; }

	@Override
	public String toString() { return v.name; }

	@Override
	public IType type() { return v.type; }

	// todo => replace final local variable

	@Override
	public void write(MethodWriter cw, boolean noRet) throws NotStatementException {
		mustBeStatement(noRet);
		cw.load(v);
	}

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (!(o instanceof LocalVariable lv)) return false;
		return v.equals(lv.v);
	}

	@Override
	public boolean isFinal() { return v.constant; }
	@Override
	public void preStore(MethodWriter cw) {}
	@Override
	public void preLoadStore(MethodWriter cw) { cw.load(v); }
	@Override
	public void postStore(MethodWriter cw) { cw.store(v); }
	@Override
	public void copyValue(MethodWriter cw, boolean twoStack) { cw.one(twoStack?Opcodes.DUP2:Opcodes.DUP); }
	@Override
	public boolean canBeReordered() { return true; }
}