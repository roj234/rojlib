package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

/**
 * 强制类型转换
 *
 * @author Roj234
 * @since 2022/2/24 19:48
 */
public class Cast implements ASTNode {
	Type type;
	ASTNode right;

	public Cast(Type type, ASTNode right) {
		this.type = type;
		this.right = right;
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) throws NotStatementException {
		right.write(tree, false);
		if (!tree.ctx.canInstanceOf(tree.stackTop().owner, type.owner, 0)) {
			throw new IllegalStateException("Unable cast " + tree.stackTop().owner + " to " + type.owner);
		}
		tree.cast(type);
	}

	@Override
	public Type type() {
		return type;
	}

	@Override
	public boolean isEqual(ASTNode o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Cast cast = (Cast) o;

		if (!type.equals(cast.type)) return false;
		return right.isEqual(cast.right);
	}
}
