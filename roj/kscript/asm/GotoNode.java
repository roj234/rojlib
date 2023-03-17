package roj.kscript.asm;

import roj.collect.IntBiMap;
import roj.collect.RSegmentTree;
import roj.kscript.util.VInfo;
import roj.kscript.util.Variable;

import java.util.List;

/**
 * @author Roj234
 * @since 2020/9/27 18:50
 */
public class GotoNode extends Node {
	Node target;
	VInfo diff;

	public GotoNode(LabelNode label) {
		this.target = label;
	}

	@Override
	public Opcode getCode() {
		return Opcode.GOTO;
	}

	@Override
	protected void compile() {
		if (target.getClass() == LabelNode.class) {
			target = target.next.replacement();
		}
	}

	@Override
	protected void genDiff(RSegmentTree<RSegmentTree.Wrap<Variable>> var, IntBiMap<Node> idx) {
		List<RSegmentTree.Wrap<Variable>> self = var.collect(idx.getInt(this)), dest = var.collect(idx.getInt(target));
		if (self != dest) {
			diff = NodeUtil.calcDiff(self, dest);
		}
	}

	@Override
	public Node exec(Frame frame) {
		frame.applyDiff(diff);
		return target;
	}

	@Override
	public String toString() {
		return "Goto " + target;
	}

	public void checkNext(Node prev) {
		if (target == next) prev.next = target;
	}
}
