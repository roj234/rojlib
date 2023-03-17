package roj.kscript.asm;

import roj.collect.IntBiMap;
import roj.collect.RSegmentTree;
import roj.kscript.util.Variable;

/**
 * JS Op Nodes
 *
 * @author Roj234
 * @since 2020/9/27 12:30
 */
public abstract class Node {
	public Node next;

	protected Node() {}

	public abstract Node exec(Frame frame);

	protected void compile() {}

	@Override
	public String toString() {
		return getCode().toString();
	}

	public abstract Opcode getCode();

	Node replacement() {
		return this;
	}

	protected void genDiff(RSegmentTree<RSegmentTree.Wrap<Variable>> var, IntBiMap<Node> idx) {}
}
