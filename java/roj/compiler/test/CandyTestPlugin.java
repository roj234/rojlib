package roj.compiler.test;

import roj.asm.Opcodes;
import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.Label;
import roj.compiler.JavaLexer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Constant;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.context.LocalContext;
import roj.compiler.plugins.api.ExprApi;
import roj.compiler.plugins.api.LavaApi;
import roj.config.Word;
import roj.util.Helpers;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Roj234
 * @since 2024/2/20 0020 1:28
 */
public class CandyTestPlugin {
	public void register(LavaApi api) {
		ExprApi rtApi = api.getExprApi();
		// <minecraft:stone>
		rtApi.addExprStart("<", (lexer, lc) -> {
			try {
				String nsKey = lexer.except(Word.LITERAL).val();
				lexer.except(JavaLexer.colon);
				String nsVal = lexer.except(Word.LITERAL).val();
				lexer.except(JavaLexer.gtr);

				MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "roj/compiler/test/CandyTestPlugin$Item", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
				return Invoke.constructor(mn, Constant.valueOf(nsKey), Constant.valueOf(nsVal));
			} catch (Exception e) {
				Helpers.athrow(e);
				return null;
			}
		});

		// <minecraft:stone> * 5
		MethodNode mn = new MethodNode(Opcodes.ACC_STATIC, "roj/compiler/test/CandyTestPlugin$Item", "stack", "(Lroj/compiler/test/CandyTestPlugin$Item;I)Lroj/compiler/test/CandyTestPlugin$ItemStack;");
		rtApi.onBinary("*", new Type("roj/compiler/test/CandyTestPlugin$Item"), Type.std(Type.INT), mn, true);

		// 5 day + 3 hour
		rtApi.addExprTerminal("day", (lexer, node, lc) -> new ExprNode() {
			@Override
			public String toString() { return node+" day"; }
			@Override
			public IType type() { return Type.std(Type.LONG); }

			@Override
			public void write(MethodWriter cw, boolean noRet) {
				mustBeStatement(noRet);
				long millis = TimeUnit.DAYS.toMillis(1);
				node.writeDyn(cw, LocalContext.get().castTo(node.type(), type(), 0));
				cw.ldc(millis);
				cw.one(Opcodes.LMUL);
			}
		});
		rtApi.addExprTerminal("hour", (lexer, node, lc) -> new ExprNode() {
			@Override
			public String toString() { return node+" hour"; }
			@Override
			public IType type() { return Type.std(Type.LONG); }

			@Override
			public void write(MethodWriter cw, boolean noRet) {
				mustBeStatement(noRet);
				long millis = TimeUnit.HOURS.toMillis(1);
				node.writeDyn(cw, LocalContext.get().castTo(node.type(), type(), 0));
				cw.ldc(millis);
				cw.one(Opcodes.LMUL);
			}
		});


		String s = "roj/compiler/test/ComparisonChain";

		rtApi.newStreamChain(s, false, ch -> {
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
				switch (m.getMethod().name()) {
					case "compare":
					case "result":
				}
			}
		}, Type.std(Type.INT))
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

		@Override
		public String toString() {
			return "Item{" + "nsKey='" + nsKey + '\'' + ", nsVal='" + nsVal + '\'' + '}';
		}
	}
	public static class ItemStack {
		Item item;
		int count;

		public ItemStack(Item item, int count) {
			this.item = item;
			this.count = count;
		}

		@Override
		public String toString() {
			return "ItemStack{" + "item=" + item + ", count=" + count + '}';
		}
	}
}