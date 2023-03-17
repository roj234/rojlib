package roj.lavac.expr;

import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValArray;
import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

/**
 * 操作符 - 定义数组
 *
 * @author Roj233
 * @since 2022/2/27 19:43
 */
public final class ArrayDef implements ASTNode {
	Type type;
	List<ASTNode> expr;

	public ArrayDef(List<ASTNode> args) {
		this.type = type;
		this.expr = args;
	}

	@Override
	public boolean isEqual(ASTNode left) {
		if (this == left) return true;
		if (left == null || getClass() != left.getClass()) return false;
		ArrayDef define = (ArrayDef) left;
		return arrayEq(expr, define.expr);
	}

	static boolean arrayEq(List<ASTNode> as, List<ASTNode> bs) {
		if (as.size() != bs.size()) return false;

		if (as.isEmpty()) return true;

		for (int i = 0; i < as.size(); i++) {
			ASTNode a = as.get(i);
			ASTNode b = bs.get(i);
			if (a == null) {
				if (b != null) return false;
			} else if (!a.isEqual(b)) return false;
		}
		return true;
	}

	@Nonnull
	@Override
	public ASTNode compress() {
		for (int i = 0; i < expr.size(); i++) {
			if (!expr.get(i).isConstant()) return this;
		}
		List<AnnVal> arrayEntry = Arrays.asList(new AnnVal[expr.size()]);
		for (int i = 0; i < expr.size(); i++) {
			arrayEntry.set(i, expr.get(i).asCst().val());
		}
		AnnValArray val = new AnnValArray(arrayEntry);
		return new LDC(val);
	}

	@Override
	public Type type() {
		return type;
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) {
		if (noRet) throw new NotStatementException();

		tree.const1(expr.size()).newArray(type);
		for (int i = 0; i < expr.size(); i++) {
			ASTNode expr = this.expr.get(i);
			if (expr != null) {
				tree.dup().const1(i);
				expr.write(tree, false);
				tree.arrayStore();
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append("new ").append(type).append("{");
		int i = 0;
		while (true) {
			sb.append(expr.get(i++).toString());
			if (i == expr.size()) break;
			sb.append(',');
		}
		return sb.append("}").toString();
	}
}
