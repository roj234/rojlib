package roj.compiler.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.Int2IntMap;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.UnaryPreNode;
import roj.compiler.context.LocalContext;
import roj.compiler.plugins.stc.StreamChain;
import roj.compiler.plugins.stc.StreamChainExpr;
import roj.compiler.plugins.stc.StreamChainPlugin;
import roj.config.Word;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 这个类的设计只是为了能跑起来，之后再优化
 * @author Roj234
 * @since 2024/5/21 0021 2:23
 */
class ImplExprApi implements ExprApi {
	public ImplExprApi() {st.putAll(ExprParser.getStateMap());}

	final Int2IntMap st = new Int2IntMap();
	final List<Object> custom = new SimpleList<>();

	final IntMap<List<ExprOp>> operators = new IntMap<>();

	private static final class SimpleOp implements ExprOp {
		private final Type left;
		private final Type right;
		private final MethodNode node;

		SimpleOp(Type left, Type right, MethodNode node) {
			this.left = left;
			this.right = right;
			this.node = node;
		}

		@Override
		public @Nullable ExprNode test(LocalContext ctx, OperatorContext opctx, ExprNode left, Object right) {
			if (ctx.castTo(opctx.leftType(), this.left, -8).type >= 0 &&
				(right == null
				? this.right == null
				: ctx.castTo(opctx.rightType(), this.right, -8).type >= 0)) {

				boolean isObject = (node.modifier() & Opcodes.ACC_STATIC) == 0;
				if (isObject) return Invoke.virtualMethod(node, left, (ExprNode) right);
				return Invoke.staticMethod(node, left, (ExprNode) right);
			}
			return null;
		}
	}

	{custom.add(null);}

	ExprNode getOperatorOverride(LocalContext api, @NotNull ExprNode e1, @Nullable Object e2, int operator) {
		IType left = e1.type(), right = e2 instanceof ExprNode n ? n.type() : (IType) e2;

		var ctx = new OperatorContext() {
			@Override public short symbol() {return (short) operator;}
			@Override public IType leftType() {return left;}
			@Override public IType rightType() {return right;}
		};

		for (var op : operators.getOrDefault(operator, Collections.emptyList())) {
			var node = op.test(api, ctx, e1, e2);
			if (node != null) return node.resolve(api);
		}
		return null;
	}

	private int tokenId(String token) {
		Word w = JavaLexer.JAVA_TOKEN.get(token);
		if (w != null) return w.type();

		w = new Word().init(1000 + JavaLexer.JAVA_TOKEN.size(), 0, token);
		JavaLexer.JAVA_TOKEN.put(token, w);
		return w.type();
	}

	private void register(int mask, String token, Object fn, int terminate) {
		int i = st.putIntIfAbsent(tokenId(token) | mask, terminate |= custom.size());
		if (i != terminate) throw new IllegalStateException("token "+token+" was occupied");
		custom.add(fn);
	}

	@Override public void addUnaryPre(String token, BiFunction<JavaLexer, @Nullable UnaryPreNode, UnaryPreNode> fn) {register(ExprParser.SM_UnaryPre, token, fn, 0);}
	@Override public void addExprGen(String token, LEG fn) {register(ExprParser.SM_UnaryPre, token, fn, ExprParser.CU_TerminateFlag);}
	@Override public void addExprStart(String token, LEG fn) {register(ExprParser.SM_ExprStart, token, fn, 0);}
	@Override public void addExprConv(String token, LEC fn) {register(ExprParser.SM_ExprNext, token, fn, 0);}
	@Override public void addExprTerminal(String token, LEC fn) {register(ExprParser.SM_ExprNext, token, fn, ExprParser.CU_TerminateFlag);}

	@Override
	public void addBinary(String token, int priority, LEC callback) {
		throw new UnsupportedOperationException("not implemented yet");
	}

	@Override public void onUnary(String operator, Type type, MethodNode node, int side) {operators.computeIfAbsentInt(tokenId(operator), x -> new SimpleList<>()).add(new SimpleOp(type, null, node));}
	@Override public void onBinary(Type left, String operator, Type right, MethodNode node, boolean swap) {operators.computeIfAbsentInt(tokenId(operator), x -> new SimpleList<>()).add(new SimpleOp(left, right, node));}
	@Override public void addOpHandler(String operator, ExprOp resolver) {operators.computeIfAbsentInt(tokenId(operator), x -> new SimpleList<>()).add(resolver);}

	StreamChainPlugin scp = new StreamChainPlugin();
	@Override
	public StreamChain newStreamChain(String chainType, boolean allowFallback, Consumer<StreamChainExpr> fn, Type exactType) {
		return scp.newStreamChain(chainType, allowFallback, fn, exactType);
	}
}