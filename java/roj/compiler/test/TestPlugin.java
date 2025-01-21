package roj.compiler.test;

import roj.asm.Opcodes;
import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.JavaLexer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Constant;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.UnaryPreNode;
import roj.compiler.context.LocalContext;
import roj.compiler.plugin.ExprApi;
import roj.compiler.plugin.LavaApi;
import roj.config.Word;
import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2024/2/20 0020 1:28
 */
public class TestPlugin {
	public void pluginInit(LavaApi api) {
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
		rtApi.onBinary(new Type("roj/compiler/test/CandyTestPlugin$Item"), "*", Type.std(Type.INT), mn, true);

		api.getExprApi().addUnaryPre("__TypeOf", (lexer, node) -> new UnaryPreNode() {
			ExprNode node;
			@Override public String setRight(ExprNode node) {this.node = node;return null;}
			@Override public String toString() {return null;}
			@Override public IType type() {return null;}
			@Override public void write(MethodWriter cw, boolean noRet) {
				throw new UnsupportedOperationException("[\n  表达式="+node+"\n  解析="+(node = node.resolve(LocalContext.get()))+"\n  返回类型="+node.type()+"\n]");
			}
		});
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