package roj.lavac.expr;

import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.compiler.ast.expr.ExprNode;
import roj.config.word.NotStatementException;
import roj.lavac.parser.CompileUnit;
import roj.lavac.parser.MethodWriterL;
import roj.text.CharList;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 操作符 - 获取定名字的量
 *
 * @author Roj233
 * @since 2022/2/27 20:27
 */
public class DotGet implements LoadNode {
	@Nullable
	ExprNode parent;
	SimpleList<String> names;
	CompileUnit ctx;

	public DotGet(@Nullable ExprNode parent, String name, int flag) {
		this.parent = parent;
		this.names = new SimpleList<>(4);
		this.names.add(name);
	}

	@Override
	public Type type() {
		return new Type("");
	}

	@Nonnull
	@Override
	public ExprNode resolve() {
		if (parent != null) {
			parent = parent.resolve();
			assert !parent.isConstant();
		}
		return this;
	}

	@Override
	public void preResolve(CompileUnit ctx, int flags) {
		this.ctx = ctx;
	}

	@Override
	public void write(MethodWriterL cw, boolean noRet) {
		if (noRet) throw new NotStatementException();

		parent.write(cw, false);
	}

	public DotGet add(String name, int flag) { names.add(name); return this; }

	@Override
	public void writeLoad(MethodWriterL tree) { parent.write(tree, false); }

	@Override
	public String toString() {
		CharList sb = new CharList();
		if (parent != null) sb.append(parent).append('.');
		return sb.append(TextUtil.join(names, ".")).toStringAndFree();
	}

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DotGet get = (DotGet) o;

		if (parent != null ? !parent.equalTo(get.parent) : get.parent != null) return false;
		return names.equals(get.names);
	}

	@Override
	public int hashCode() {
		int result = parent != null ? parent.hashCode() : 0;
		result = 31 * result + names.hashCode();
		return result;
	}
}
