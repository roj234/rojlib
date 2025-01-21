package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.ResolveException;

/**
 * @author Roj234
 * @since 2024/1/28 0028 16:27
 */
public final class LocalVariable extends VarNode {
	Variable v;

	public LocalVariable(Variable v) { super(0); this.v = v; }

	@Override public String toString() { return v.name; }
	@Override public IType type() { return v.type; }
	public Variable getVariable() {return v;}

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		if (v.constantValue != null) return new Constant(v.type, v.constantValue);
		return this;
	}

	@Override public boolean isConstant() { return v.constantValue != null; }
	@Override public Object constVal() { return v.constantValue; }
	@Override public void write(MethodWriter cw, boolean noRet) {mustBeStatement(noRet);preLoadStore(cw);}

	@Override public boolean isFinal() { return v.isFinal; }
	@Override public void preStore(MethodWriter cw) {}
	@Override public void preLoadStore(MethodWriter cw) {LocalContext.get().loadVar(v); cw.load(v); v.endPos = cw.bci();}
	@Override public void postStore(MethodWriter cw, int state) {LocalContext.get().storeVar(v); cw.store(v); v.endPos = cw.bci();}
	@Override public int copyValue(MethodWriter cw, boolean twoStack) {cw.one(twoStack?Opcodes.DUP2:Opcodes.DUP);return 0;}
	@Override public boolean canBeReordered() {return true;}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof LocalVariable lv)) return false;
		return v.equals(lv.v);
	}
	@Override public int hashCode() { return v.hashCode(); }
}