package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassDefinition;
import roj.asm.FieldNode;
import roj.compiler.CompileContext;

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
		ctx.enableErrorCapture();
		try {
			if (ctx.canAccessSymbol(owner, node, (flag&IN_STATIC) != 0, true)) {
				FieldList.checkBridgeMethod(ctx, owner, node);
				checkDeprecation(ctx, owner, node);
				return new FieldResult(owner, node);
			}
			return new FieldResult(ctx.getCapturedError().replace('/', '.').toString());
		} finally {
			ctx.disableErrorCapture();
		}
	}
}