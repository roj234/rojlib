package roj.kscript.parser.ast;

import roj.config.word.NotStatementException;
import roj.kscript.asm.CompileContext;
import roj.kscript.asm.KS_ASM;
import roj.kscript.parser.ParseContext;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * @author Roj234
 * @since 2020/10/30 19:14
 */
public final class Variable extends Field {
	private static final Expression fakeParent = new Expression() {
		@Override
		public void write(KS_ASM tree, boolean noRet) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void toVMCode(CompileContext ctx, boolean noRet) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			return "$$";
		}
	};

	private Constant cst;
	private byte spec_op_type;
	private ParseContext ctx;

	public Variable(String name) {
		super(fakeParent, name);
	}

	@Override
	public void mark_spec_op(ParseContext ctx, int op_type) {
		if (op_type == 1) {
			KType t = ctx.maybeConstant(name);
			if (t != null) {
				cst = Constant.valueOf(t);
			}
		}

		spec_op_type |= op_type;
		this.ctx = ctx;
	}

	@Override
	public boolean isEqual(Expression o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Variable v = (Variable) o;

		return v.name.equals(name);
	}

	@Override
	public int hashCode() {
		int result = cst != null ? cst.hashCode() : 0;
		result = 31 * result + (int) spec_op_type;
		result = 31 * result + (ctx != null ? ctx.hashCode() : 0);
		return result;
	}

	@Override
	public boolean setDeletion() {
		return false;
	}

	@Override
	public boolean isConstant() {
		return cst != null;
	}

	@Override
	public Constant asCst() {
		return cst == null ? super.asCst() : cst;
	}

	@Override
	public byte type() {
		return cst == null ? -1 : cst.type();
	}

	@Nonnull
	@Override
	public Expression compress() {
		return cst == null ? this : cst;
	}

	public String getName() {
		return name;
	}

	@Override
	public KType compute(Map<String, KType> param) {
		return param.getOrDefault(name, KUndefined.UNDEFINED);
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		if (noRet) throw new NotStatementException();

		if (cst == null) {tree.Get(name);} else cst.write(tree, false);

		_after_write_op();
	}

	@Override
	public void toVMCode(CompileContext ctx, boolean noRet) {
		if (noRet) throw new NotStatementException();

		ctx.loadVar(name, spec_op_type);
	}

	void _after_write_op() {
		if ((spec_op_type & 1) != 0) {
			ctx.useVariable(name);
		}
		if ((spec_op_type & 2) != 0) {
			ctx.assignVariable(name);
		}
		spec_op_type = 0;
	}
}
