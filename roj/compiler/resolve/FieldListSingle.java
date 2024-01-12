package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.compiler.context.CompileContext;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2024/2/6 3:02
 */
final class FieldListSingle extends ComponentList {
	FieldListSingle(IClass owner, FieldNode node) {
		this.owner = owner;
		this.node = node;
	}

	private final IClass owner;
	final FieldNode node;

	@NotNull
	public FieldResult findField(CompileContext ctx, int flag) {
		CharList tmp = new CharList();
		ctx.errorCapture = (trans, param) -> {
			tmp.clear();
			tmp.append(trans);
			for (Object o : param)
				tmp.append(':').append(o);
		};

		try {
			if (ctx.checkAccessible(owner, node, (flag&IN_STATIC) != 0, true)) {
				tmp._free();
				return new FieldResult(owner, node);
			}
			return new FieldResult(tmp.replace('/', '.').toStringAndFree());
		} finally {
			ctx.errorCapture = null;
		}
	}
}