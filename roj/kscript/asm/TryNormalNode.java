package roj.kscript.asm;

import roj.kscript.vm.ScriptException;

/**
 * @author Roj234
 * @since 2021/6/15 18:29
 */
public class TryNormalNode extends GotoNode {
	public boolean gotoFinal;

	public TryNormalNode() {
		super(null);
	}

	public void setTarget(LabelNode node) {
		target = node;
	}

	@Override
	public Node exec(Frame f) {
		f.applyDiff(diff);
		if (gotoFinal) { // try肯定配对了，不用担心
			throw ScriptException.TRY_EXIT;
		} else {
			f.popTry();
			return target;
		}
	}

	@Override
	public Opcode getCode() {
		return Opcode.TRY_EXIT;
	}

	@Override
	public String toString() {
		return "end of try block from " + target + ", finally: " + gotoFinal;
	}
}
