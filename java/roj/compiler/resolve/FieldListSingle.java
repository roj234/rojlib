package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import roj.asm.FieldNode;
import roj.asm.IClass;
import roj.compiler.context.LocalContext;
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

	final IClass owner;
	final FieldNode node;

	@NotNull
	public FieldResult findField(LocalContext ctx, int flag) {
		CharList tmp = new CharList();
		ctx.errorCapture = (trans, param) -> {
			tmp.clear();
			tmp.append(trans);
			for (Object o : param)
				tmp.append('\1').append(o).append('\0');
		};

		try {
			if (ctx.checkAccessible(owner, node, (flag&IN_STATIC) != 0, true)) {
				tmp._free();
				FieldList.checkBridgeMethod(ctx, owner, node);
				checkDeprecation(ctx, owner, node);
				return new FieldResult(owner, node);
			}
			return new FieldResult(tmp.replace('/', '.').toStringAndFree());
		} finally {
			ctx.errorCapture = null;
		}
	}
}