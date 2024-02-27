package roj.compiler.plugins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.tree.MethodNode;
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
import roj.compiler.plugins.api.*;
import roj.config.Word;
import roj.util.Helpers;

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

	final IntMap<List<SimpleOp>> operators = new IntMap<>();
	static record SimpleOp(Type left, Type right, MethodNode node) {
		public ExprNode exec(GlobalContextApi.LCImpl api, IType left, IType right, ExprNode e1, ExprNode e2) {
			if (api.castTo(left, this.left, -8).type == 0 && right == null ? this.right == null : api.castTo(right, this.right, -8).type == 0)
				return Invoke.staticMethod(node, e1, e2);
			return null;
		}
	}

	{custom.add(null);}

	ExprNode override_getOperatorOverride(GlobalContextApi.LCImpl api, @NotNull ExprNode e1, @Nullable ExprNode e2, int operator) {
		IType left = e1.type(), right = e2 == null ? /*deal with null check*/Helpers.maybeNull() : e2.type();

		for (SimpleOp op : operators.getOrDefault(operator, Collections.emptyList())) {
			ExprNode result = op.exec(api, left, right, e1, e2);
			if (result != null) return result;
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

	@Override
	public void addUnaryPre(String token, BiFunction<JavaLexer, @Nullable UnaryPreNode, UnaryPreNode> fn) {register(ExprParser.SM_UnaryPre, token, fn, 0);}
	@Override public void addExprGen(String token, LEG fn) {register(ExprParser.SM_UnaryPre, token, fn, ExprParser.CU_TerminateFlag);}
	@Override public void addExprStart(String token, LEG fn) {register(ExprParser.SM_ExprStart, token, fn, 0);}
	@Override public void addExprConv(String token, LEC fn) {register(ExprParser.SM_ExprNext, token, fn, 0);}
	@Override public void addExprTerminal(String token, LEC fn) {register(ExprParser.SM_ExprNext, token, fn, ExprParser.CU_TerminateFlag);}

	@Override
	public void addBinary(String token, int priority, LEC callback) {
		throw new UnsupportedOperationException("not implemented yet");
	}

	@Override
	public void onUnary(String operator, Type type, MethodNode node, int side) {
		operators.computeIfAbsentInt(tokenId(operator), x -> new SimpleList<>()).add(new SimpleOp(type, null, node));
	}

	@Override
	public void onBinary(String operator, Type left, Type right, MethodNode node, boolean swap) {
		operators.computeIfAbsentInt(tokenId(operator), x -> new SimpleList<>()).add(new SimpleOp(left, right, node));
	}

	@Override
	public StreamChain newStreamChain(String chainType, boolean existInRuntime, Consumer<StreamChainExpr> fn, Type... exactType) {
		return new StreamChain() {
			@Override
			public StreamChain startOp(MethodNode node, boolean checkChild) {
				return this;
			}

			@Override
			public StreamChain intermediateOp(MethodNode node) {
				return this;
			}

			@Override
			public StreamChain terminalOp(MethodNode node) {
				return this;
			}
		};
	}
}