package roj.compiler.api_rt.candy;

import roj.asm.Opcodes;
import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.Label;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.api_rt.ExprApi;
import roj.compiler.api_rt.LavaApi;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Constant;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.MethodResult;
import roj.config.word.Word;
import roj.util.Helpers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Roj234
 * @since 2024/2/20 0020 1:28
 */
public class CandyTestServer {
	public void register(LavaApi api) {
		ExprApi rtApi = api.getExprApi();
		// <minecraft:stone>
		rtApi.addExprStart("<", lexer -> {
			try {
				String nsKey = lexer.except(Word.LITERAL).val();
				lexer.except(JavaLexer.colon);
				String nsVal = lexer.except(Word.LITERAL).val();
				lexer.except(JavaLexer.gtr);

				MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "roj/compiler/api/candy/CandyTestServer$Item", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
				return Invoke.constructor(mn, Constant.valueOf(nsKey), Constant.valueOf(nsVal));
			} catch (Exception e) {
				Helpers.athrow(e);
				return null;
			}
		});

		// <minecraft:stone> * 5
		MethodNode mn = new MethodNode(Opcodes.ACC_STATIC, "roj/compiler/api/candy/CandyTestServer$Item", "stack", "(Lroj/compiler/api/candy/CandyTestServer$Item;I)Lroj/compiler/api/candy/CandyTestServer$ItemStack;");
		rtApi.onBinary("*", new Type("roj/compiler/api/candy/CandyTestServer$Item"), Type.std(Type.INT), mn, true);

		// 5 day + 3 hour
		rtApi.addExprTerminal("day", (lexer, node) -> new ExprNode() {
			@Override
			public String toString() { return node+" day"; }
			@Override
			public IType type() { return Type.std(Type.LONG); }

			@Override
			public void write(MethodWriter cw, boolean noRet) {
				long millis = TimeUnit.DAYS.toMillis(1);
				node.writeDyn(cw, cw.ctx1.castTo(node.type(), type(), 0));
				cw.ldc(millis);
				cw.one(Opcodes.LMUL);
			}
		});

		api.getResolveApi().addTypeResolver(999, name -> {
			if (name.equals("MyString")) return api.getClassContext().getClassInfo("java/lang/String");
			return null;
		});
		MethodNode mn2 = new MethodNode(Opcodes.ACC_STATIC, "roj/compiler/api/candy/CandyTestServer", "stringHooker", "(Ljava/lang/String;)I");
		api.getResolveApi().addIdentifierResolver("java/lang/String", false, (type, identifier, method, staticEnv, list) -> {
			if (!staticEnv && identifier.equals("myFakeMethod")) {
				// TODO check arg (MethodList builder)
				return new ComponentList() {
					@Override
					public MethodResult findMethod(CompileContext ctx, IType generic, SimpleList<IType> params, Map<String, IType> namedType, int flags) {
						return new MethodResult(mn2);
					}
				};
			}
			return list;
		});


		String s = "roj/compiler/api/candy/ComparisonChain";

		rtApi.newStreamChain(false, ch -> {
			MethodWriter cw = ch.writer();

			if (ch.targetType() == 2) {
				ch.fail("结果不能忽略");
				return;
			}

			Label smaller = new Label();
			Label greater = new Label();

			if (ch.sourceType() == null) {
				cw.ldc(0);
			} else {
				ch.sourceType().write(cw, false);
			}

			List<Invoke> methods = ch.chain();
			for (Invoke m : methods) {
				switch (m.getMethodNode().name()) {
					case "compare":
					case "result":
				}
			}
		})
			 .typeMask(s, Type.std(Type.INT))
			 .startOp(new MethodNode(Opcodes.ACC_STATIC, s, "start", "()Lroj/compiler/api/candy/ComparisonChain;"), false)
			 .intermediateOp(new MethodNode(0, s, "compare", "(DD)L"+s+";"))
			 .terminalOp(new MethodNode(0, s, "result", "()I"));
	}

	public static class Item {
		final String nsKey, nsVal;

		public Item(String nsKey, String nsVal) {
			this.nsKey = nsKey;
			this.nsVal = nsVal;
		}

		public static ItemStack stack(Item item, int count) {
			return new ItemStack(item, count);
		}
	}
	public static class ItemStack {
		Item item;
		int count;

		public ItemStack(Item item, int count) {
			this.item = item;
			this.count = count;
		}
	}
}