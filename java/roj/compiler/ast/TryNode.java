package roj.compiler.ast;

import roj.asm.insn.Label;
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
	 * true:
	 * 对于Variable：变量可能是null
	 * 对于ExprNode：表达式可能抛出异常
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
		exception.add(vars.size());
		vars.add(node);
		pos.add(null);
	}
}