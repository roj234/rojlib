package roj.asm.insn;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class TryCatchBlock {
	public Label start, end, handler;
	public String type;

	public static final String ANY = null;

	public TryCatchBlock() {}
	public TryCatchBlock(Label start, Label end, Label handler, String catchType) {
		this.start = start;
		this.end = end;
		this.handler = handler;
		this.type = catchType;
	}

	@Override
	public String toString() {
		return "TryCatchBlock{" +
				"start=" + start +
				", end=" + end +
				", handler=" + handler +
				", type=" + (type == ANY ? "<any>" : "'"+type+"'") +
				'}';
	}
}