package roj.compiler.ast;

import roj.asm.insn.Label;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.Expr;

import java.util.List;

/**
 * 存放try-with-resource或defer的数据
 * @author Roj234
 * @since 2024/4/30 16:47
 */
final class TryNode extends FlowHook {
	/** 是否有任何的defer */
	boolean hasDefer;
	/**
	 * 表达式 => Variable
	 * defer => ExprNode
	 */
	final List<Object> vars = new ArrayList<>();
	/**
	 * true:
	 * 对于Variable：变量可能是null
	 * 对于ExprNode：表达式可能抛出异常
	 */
	final BitSet exception = new BitSet();
	/** 表达式刚执行完的位置 (defer => null) */
	final List<Label> pos = new ArrayList<>();

	public void add(Variable closeable, boolean nullable, Label defineAt) {
		if (nullable) this.exception.add(vars.size());
		vars.add(closeable);
		pos.add(defineAt);
	}
	public void defer(Expr node) {
		hasDefer = true;
		exception.add(vars.size());
		vars.add(node);
		pos.add(null);
	}
}