package roj.kscript.asm;

/**
 * @author Roj234
 * @since 2021/4/28 22:19
 */
public final class TryEndNode extends GotoNode {
	public TryEndNode(LabelNode end) {
		super(end);
	}

	@Override
	public Node exec(Frame frame) {
		frame.applyDiff(diff);
		return null;
	}

	@Override
	public Opcode getCode() {
		return Opcode.TRY_EXIT;
	}

	@Override
	public String toString() {
		return "end of catch/finally block, to " + target;
	}
}
