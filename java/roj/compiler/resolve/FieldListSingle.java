package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassDefinition;
import roj.asm.FieldNode;
import roj.compiler.CompileContext;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2024/2/6 3:02
 */
final class FieldListSingle extends ComponentList {
	FieldListSingle(ClassDefinition owner, FieldNode node) {
		this.owner = owner;
		this.node = node;
	}

	final ClassDefinition owner;
	final FieldNode node;

	@NotNull
	public FieldResult findField(CompileContext ctx, int flag) {
		var tmp = new CharList();
		ctx.errorCapture = makeErrorCapture(tmp);

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