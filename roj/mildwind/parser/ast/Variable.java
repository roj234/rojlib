package roj.mildwind.parser.ast;

import roj.config.word.NotStatementException;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.parser.ParseContext;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

/**
 * @author Roj234
 * @since 2020/10/30 19:14
 */
final class Variable implements LoadExpression {
	String name;
	private JsObject v;

	public Variable(String name) { this.name = name; }

	public Type type() { return v == null ? Type.OBJECT : v.type(); }

	@Override
	public void write(JsMethodWriter tree, boolean noRet) {
		if (noRet) throw new NotStatementException();

		if (v == null) tree.variables.get(name);
		else {
			int fid = tree.sync(v);
			tree.load(fid);
		}
	}

	public boolean isConstant() { return v != null; }
	public JsObject constVal() { return v; }

	public void writeLoad(JsMethodWriter tree) { throw new IllegalStateException("is variable"); }
	public void writeExecute(JsMethodWriter tree, boolean noRet) { throw new IllegalStateException("is variable"); }
	public boolean setDeletion() { return false; }

	public JsObject compute(JsObject ctx) { return ctx.get(name); }
	public void computeAssign(JsObject ctx, JsObject val) { ctx.put(name, val); }

	@Override
	@Deprecated
	public void var_op(ParseContext ctx, int op_type) {
		if (op_type == 1) v = ctx.maybeConstant(name);
	}

	@Override
	public boolean isEqual(Expression o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Variable v = (Variable) o;
		return v.name.equals(name);
	}

	@Override
	public String toString() { return "<lvt>."+name; }
}
