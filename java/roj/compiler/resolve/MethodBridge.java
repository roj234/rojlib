package roj.compiler.resolve;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.compiler.api.Evaluable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.context.CompileUnit;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/7/4 0004 14:22
 */
final class MethodBridge extends Evaluable {
	private final CompileUnit owner;
	private final Evaluable prev;
	private final int accessor;

	MethodBridge(CompileUnit owner, MethodNode mn, Evaluable prev) {
		this.owner = owner;
		this.prev = prev;

		// <init> will also have a delegation
		accessor = this.owner.methods.size();
		MethodNode delegate = new MethodNode(0, "", this.owner.getNextAccessorName(), mn.rawDesc());
		boolean invokeSpecial = mn.name().equals("<init>");
		if ((owner.modifier&Opcodes.ACC_STATIC) != 0 || invokeSpecial) {
			if (invokeSpecial) delegate.setReturnType(Type.klass(owner.name()));
			this.owner.createDelegation(Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC, mn, delegate, true, invokeSpecial);
		} else {
			this.owner.createDelegation(Opcodes.ACC_FINAL|Opcodes.ACC_SYNTHETIC, mn, delegate, true, false);
		}
	}

	@Override
	public String toString() {return "Evaluable<Generic MethodBridge>";}

	@Override
	public ExprNode eval(MethodNode owner, @Nullable ExprNode self, List<ExprNode> args, Invoke node) {
		if (prev != null) {
			ExprNode eval = prev.eval(owner, self, args, node);
			if (eval != null) return eval;
		}

		MethodNode mn = this.owner.methods.get(accessor);
		if ((owner.modifier&Opcodes.ACC_STATIC) != 0) return Invoke.staticMethod(mn, args);
		else return Invoke.virtualMethod(mn, self, args);
	}
}