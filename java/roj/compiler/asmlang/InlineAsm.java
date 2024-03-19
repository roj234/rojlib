package roj.compiler.asmlang;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.compiler.api_rt.Evaluable;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.GlobalContext;
import roj.util.Helpers;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/2/6 0006 12:47
 */
public class InlineAsm extends ExprNode {
	public InlineAsm(String str) {

	}

	@Override
	public String toString() {
		return "";
	}

	@Override
	public IType type() {
		return null;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {

	}

	public static void register(GlobalContext ctx) {
		IClass ci = ctx.getClassInfo("roj/compiler/asmlang/ASM");
		List<MethodNode> methods = Helpers.cast(ci.methods()); methods.clear();

		MethodNode mn;

		Evaluable asmHook = new Evaluable() {
			@Override
			public ExprNode eval(@Nullable ExprNode self, List<ExprNode> args) {
				if (!args.get(0).isConstant()) {
					throw new IllegalArgumentException("__asm()的参数必须是常量字符串");
				}
				return new InlineAsm(args.get(0).constVal().toString());
			}

			@Override
			public String toString() {return "__asm hook";}
		};

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, ci.name(), "__asm", "(Ljava/lang/String;)Z");
		mn.putAttr(asmHook);
		methods.add(mn);

		Evaluable i2zHook = new Evaluable() {
			@Override
			public ExprNode eval(@Nullable ExprNode self, List<ExprNode> args) {return args.get(0);}
			@Override
			public String toString() {return "i2z or z2i hook";}
		};

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, ci.name(), "i2z", "(I)Z");
		mn.putAttr(i2zHook);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, ci.name(), "z2i", "(Z)I");
		mn.putAttr(i2zHook);
		methods.add(mn);
	}
}