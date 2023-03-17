package roj.lavac.expr;

import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

/**
 * @author Roj234
 * @since 2022/2/24 19:55
 */
public final class InstanceOf extends Cast {
	public InstanceOf(Type type, ASTNode right) {
		super(type, right);
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) throws NotStatementException {
		right.write(tree, false);
		if (type.owner == null || tree.stackTop().owner == null)
			throw new IllegalStateException("Primitive type");
		if (!tree.ctx.canInstanceOf(tree.stackTop().owner, type.owner, 0)) {
			tree.instanceof1(type.array() > 0 ? TypeHelper.getField(type) : type.owner);
		}
		// absolute is
	}
}
