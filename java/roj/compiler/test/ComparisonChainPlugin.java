package roj.compiler.test;

import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.insn.Label;
import roj.asm.type.Type;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugin.ExprApi;
import roj.compiler.plugin.LavaApi;
import roj.compiler.plugin.LavaPlugin;
import roj.compiler.plugins.stc.StreamChain;
import roj.compiler.plugins.stc.StreamChainExpr;
import roj.compiler.resolve.TypeCast;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/12/4 13:26
 */
@LavaPlugin(name = "comparisonTest", desc = "StreamChain测试插件")
public class ComparisonChainPlugin {
	public void pluginInit(LavaApi api) {
		ExprApi rtApi = api.getExprApi();

		String name = "roj/compiler/test/ComparisonChain";

		StreamChain chain = rtApi.newStreamChain(name, false, ch -> {
			if (ch.targetType() == StreamChainExpr.IGNORE) {
				ch.context().report(Kind.ERROR, "结果不能忽略");
				return;
			}

			var cw = ch.writer();

			Label EndLoc = new Label();
			int t = ch.targetType() == 0 ? 2 : 1;

			if (ch.sourceType() != null) {
				ch.sourceType().write(cw, false);
				cw.one(Opcodes.DUP);
				cw.jump(Opcodes.IFNE, EndLoc);
				cw.one(Opcodes.POP);
			}

			List<Invoke> methods = ch.chain();
			for (int j = 0; j < methods.size(); j++) {
				Invoke m = methods.get(j);
				MethodNode mn = m.getMethod();
				if (mn.name().startsWith("compare")) {
					Type myParamType = mn.parameters().get(0);

					List<ExprNode> arguments = m.getArguments();
					Type wrapper = TypeCast.getWrapper(myParamType);
					if (wrapper != null) {
						for (int i = 0; i < arguments.size(); i++) {
							ExprNode argument = arguments.get(i);
							argument.write(cw, LocalContext.get().castTo(argument.type(), myParamType, 0));
						}
						cw.invokeS(wrapper.owner(), "compare", "("+(char)myParamType.type+(char)myParamType.type+")I");
						if (mn.name().equals("compareFalseFirst")) cw.one(Opcodes.INEG);
					} else {
						if (myParamType.owner.equals("java/lang/Object")) {
							//public abstract <T> ComparisonChain compare(@Nullable T a, @Nullable T b, Comparator<T> cmp);
							arguments.get(2).write(cw);
							arguments.get(0).write(cw);
							arguments.get(1).write(cw);
							cw.invokeItf("java/util/Comparator", "compare", "(Ljava/lang/Object;Ljava/lang/Object;)I");
						} else {
							//public abstract ComparisonChain compare(Comparable<?> a, Comparable<?> b);
							arguments.get(0).write(cw);
							arguments.get(1).write(cw);
							cw.invokeItf("java/lang/Comparable", "compareTo", "(Ljava/lang/Object;)I");
						}
					}

					// -1 is Result
					if (j == methods.size() - t) {
						cw.label(EndLoc);

						return;
					}

					cw.one(Opcodes.DUP);
					cw.jump(Opcodes.IFNE, EndLoc);
					cw.one(Opcodes.POP);
				}
			}

			assert false;
		}, Type.primitive(Type.INT));

		for(var c : "IJFDZ".toCharArray()) {
			chain.intermediateOp(new MethodNode(Opcodes.ACC_PUBLIC, name, "compare", "("+c+c+")L"+name+";"));
		}
		chain.intermediateOp(new MethodNode(Opcodes.ACC_PUBLIC, name, "compare", "(Ljava/lang/Comparable;Ljava/lang/Comparable;)L"+name+";"))
			 .intermediateOp(new MethodNode(Opcodes.ACC_PUBLIC, name, "compare", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/Comparator;)L"+name+";"))
			 .startOp(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "start", "()L" + name + ";"))
			 .terminalOp(new MethodNode(Opcodes.ACC_PUBLIC, name, "result", "()I"));

	}
}