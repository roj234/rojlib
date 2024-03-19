package roj.compiler.api.impl;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.MethodNode;
import roj.asm.type.Type;
import roj.collect.Int2IntMap;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.api_rt.ExprApi;
import roj.compiler.api_rt.StreamChain;
import roj.compiler.api_rt.StreamChainExpr;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.UnaryPreNode;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/5/21 0021 2:23
 */
public class ExprApiImpl implements ExprApi {
	public ExprApiImpl() {st.putAll(ExprParser.DST);}

	private final Int2IntMap st = new Int2IntMap();
	private List<Object> custom = new SimpleList<>();



	private int tokenId(String token) {
		return 0;
	}

	private void register(int mask, String token, Object fn) {
		int i = st.putIntIfAbsent(mask | tokenId(token), custom.size());
		if (i != custom.size()) throw new IllegalStateException("token "+ token +" was occupied");
		custom.add(fn);
	}

	@Override
	public void addUnaryPreFirst(String token, Function<JavaLexer, UnaryPreNode> fn) {register(ExprParser.SM_UnaryPreOnce, token, fn);}
	@Override
	public void addUnaryPre(String token, BiFunction<JavaLexer, @Nullable UnaryPreNode, UnaryPreNode> fn) {register(ExprParser.SM_UnaryPreMany, token, fn);}
	@Override
	public void addExprGen(String token, Function<JavaLexer, ExprNode> fn) {
		st.put(ExprParser.SM_UnaryPreMany|tokenId(token), -4);
		register(ExprParser.SM_UserExprGen, token, fn);
	}
	@Override
	public void addExprStart(String token, Function<JavaLexer, ExprNode> fn) {register(ExprParser.SM_ExprStart, token, fn);}
	@Override
	public void addExprConv(String token, BiFunction<JavaLexer, ExprNode, ExprNode> fn) {
		st.put(ExprParser.SM_ExprNext|tokenId(token), -10);
		register(ExprParser.SM_UserContinue, token, fn);
	}
	@Override
	public void addExprTerminal(String token, BiFunction<JavaLexer, ExprNode, ExprNode> fn) {
		st.put(ExprParser.SM_ExprNext|tokenId(token), -11);
		register(ExprParser.SM_UserTerminate, token, fn);
	}

	@Override
	public void addBinary(String token, int priority, BiFunction<ExprNode, ExprNode, ExprNode> callback) {
		throw new UnsupportedOperationException("not implemented yet");
	}

	@Override
	public void onUnary(String operator, Type type, MethodNode node, int side) {

	}

	@Override
	public void onBinary(String operator, Type left, Type right, MethodNode node, boolean swap) {

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