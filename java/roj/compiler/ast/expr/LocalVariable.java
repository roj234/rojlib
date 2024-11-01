package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;

/**
 * @author Roj234
 * @since 2024/1/28 0028 16:27
 */
public final class LocalVariable extends VarNode {
	Variable v;

	public LocalVariable(Variable v) { this.v = v; }

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

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		v.endPos = wordEnd;
		mustBeStatement(noRet);

		var ctx = LocalContext.get();
		// TODO var type merge
		//if (v.isVar) v.type = ctx.getCommonParent(v.type, /*Target.Type?*/);

		if (!ctx.hasValue(v)) ctx.report(Kind.ERROR, "var.notAssigned", v.name);
		cw.load(v);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof LocalVariable lv)) return false;
		return v.equals(lv.v);
	}
	@Override public int hashCode() { return v.hashCode(); }

	@Override public boolean isFinal() { return v.isFinal; }
	@Override public void preStore(MethodWriter cw) {}
	@Override
	public void preLoadStore(MethodWriter cw) {
		var ctx = LocalContext.get();
		if (!ctx.hasValue(v)) ctx.report(Kind.ERROR, "var.notAssigned", v.name); cw.load(v); }
	@Override public void postStore(MethodWriter cw) {LocalContext.get().assign(v); cw.store(v);}
	@Override public void copyValue(MethodWriter cw, boolean twoStack) {cw.one(twoStack?Opcodes.DUP2:Opcodes.DUP);}
	@Override public boolean canBeReordered() {return true;}
}