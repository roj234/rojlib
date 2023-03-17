package roj.kscript.asm;

/**
 * @author Roj234
 * @since 2020/9/27 18:50
 */
public final class TryEnterNode extends Node {
	public Node handler, fin, end;

	public TryEnterNode(LabelNode handler, LabelNode fin, LabelNode end) {
		this.handler = handler;
		this.fin = fin;
		this.end = end;
	}

	@Override
	public Opcode getCode() {
		return Opcode.TRY_ENTER;
	}

	@Override
	protected void compile() {
		// since handler is Nullable
		if (!(handler instanceof LabelNode)) return;

		handler = handler.next;
		fin = fin.next;
		end = end.next;
	}

	@Override
	public Node exec(Frame frame) {
		frame.pushTry(this);
		return next;
	}

}
