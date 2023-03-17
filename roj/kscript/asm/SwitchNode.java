package roj.kscript.asm;

import roj.collect.IntBiMap;
import roj.collect.RSegmentTree;
import roj.kscript.util.SwitchMap;
import roj.kscript.util.VInfo;
import roj.kscript.util.Variable;
import roj.util.Helpers;

import java.util.List;

/**
 * @author Roj234
 * @since 2020/9/27 23:02
 */
public final class SwitchNode extends Node {
	public Node def;
	private VInfo diff;
	private final SwitchMap map;

	public SwitchNode(Node def, SwitchMap map) {
		this.def = def;
		this.map = Helpers.cast(map);
	}

	@Override
	public Opcode getCode() {
		return Opcode.SWITCH;
	}

	@Override
	protected void compile() {
		if (def.getClass() != LabelNode.class) return;
		def = def.next;
		map.stripLabels();
	}

	@Override
	protected void genDiff(RSegmentTree<RSegmentTree.Wrap<Variable>> var, IntBiMap<Node> idx) {
		List<RSegmentTree.Wrap<Variable>> self = var.collect(idx.getInt(this)), dest = var.collect(idx.getInt(def));
		if (self != dest) {
			diff = NodeUtil.calcDiff(self, dest);
		}
		map.genDiff(self, var, idx);
	}

	@Override
	public Node exec(Frame frame) {
		return map.getAndApply(frame, def, diff);
	}

	@Override
	public String toString() {
		return "Switch " + map + " or: " + def;
	}
}
