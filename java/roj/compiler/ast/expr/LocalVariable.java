package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.compiler.CompileContext;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;

/**
 * @author Roj234
 * @since 2024/1/28 16:27
 */
public final class LocalVariable extends LeftValue {
	Variable v;

	public LocalVariable(Variable v) { super(0); this.v = v; }

	@Override public String toString() { return v.name + (v.value == null ? "" : "( = "+v.value +")"); }
	@Override public IType type() { return v.type; }
	public Variable getVariable() {return v;}

	@Override public boolean isConstant() { return v.value != null; }
	@Override public Object constVal() { v.forceUsed = true; return v.value; }
	@Override public void write(MethodWriter cw, boolean noRet) {mustBeStatement(noRet);preLoadStore(cw);}

	@Override public boolean isFinal() { return v.isFinal; }
	@Override public void preStore(MethodWriter cw) {}
	@Override public void preLoadStore(MethodWriter cw) {cw.load(v); CompileContext.get().loadVar(v);}
	@Override public void postStore(MethodWriter cw, int state) {cw.store(v); CompileContext.get().storeVar(v);}
	@Override public int copyValue(MethodWriter cw, boolean twoStack) {cw.insn(twoStack?Opcodes.DUP2:Opcodes.DUP);return 0;}
	@Override public boolean hasSideEffect() {return false;}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof LocalVariable lv)) return false;
		return v.equals(lv.v);
	}
	@Override public int hashCode() { return v.hashCode(); }
}