package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.context.LocalContext;

/**
 * @author Roj234
 * @since 2024/1/28 0028 16:27
 */
public final class LocalVariable extends VarNode {
	Variable v;

	public LocalVariable(Variable v) { super(0); this.v = v; }

	@Override public String toString() { return v.name + (v.constantValue == null ? "" : "( = "+v.constantValue+")"); }
	@Override public IType type() { return v.type; }
	public Variable getVariable() {return v;}

	@Override public boolean isConstant() { return v.constantValue != null; }
	@Override public Object constVal() { v.ignoreUnusedCheck = true; return v.constantValue; }
	@Override public void write(MethodWriter cw, boolean noRet) {mustBeStatement(noRet);preLoadStore(cw);}

	@Override public boolean isFinal() { return v.isFinal; }
	@Override public void preStore(MethodWriter cw) {}
	@Override public void preLoadStore(MethodWriter cw) {cw.load(v); LocalContext.get().loadVar(v);}
	@Override public void postStore(MethodWriter cw, int state) {cw.store(v); LocalContext.get().storeVar(v);}
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