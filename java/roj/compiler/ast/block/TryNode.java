package roj.compiler.ast.block;

import roj.asm.visitor.Label;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.ExprNode;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/4/30 0030 16:47
 */
public class TryNode {
	boolean hasDefer;
	final List<Object> vars = new SimpleList<>();
	final MyBitSet exception = new MyBitSet();
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