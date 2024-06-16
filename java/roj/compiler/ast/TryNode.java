package roj.compiler.ast;

import roj.asm.visitor.Label;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.ExprNode;

import java.util.List;

/**
 * 存放try-with-resource或defer的数据
 * @author Roj234
 * @since 2024/4/30 0030 16:47
 */
public final class TryNode {
	/** 是否有任何的defer */
	boolean hasDefer;
	/**
	 * 表达式 => Variable
	 * defer => ExprNode
	 */
	final List<Object> vars = new SimpleList<>();
	/**
	 * 表面上：表达式的返回值有没有可能是null
	 * 实际上：是否要为close或defer创建异常处理器
	 */
	final MyBitSet exception = new MyBitSet();
	/** 表达式刚执行完的位置 (defer => null) */
	final List<Label> pos = new SimpleList<>();

	public void add(Variable var, boolean nullable, Label defined) {
		if (nullable) this.exception.add(vars.size());
		vars.add(var);
		pos.add(defined);
	}
	public void add(ExprNode node) {
		hasDefer = true;
		// TODO remove useless exception handler on demand
		exception.add(vars.size());
		vars.add(node);
		pos.add(null);
	}
}