package roj.asm.util;

import roj.asm.visitor.Label;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class TryCatchEntry {
	public Label start, end, handler;
	public String type;

	public static final String ANY = null;

	public TryCatchEntry() {}
	public TryCatchEntry(Label start, Label end, Label handler, String catchType) {
		this.start = start;
		this.end = end;
		this.handler = handler;
		this.type = catchType;
	}

	@Override
	public String toString() {
		return "TryCatchEntry{" + "start=" + start.getValue() + ", end=" + end.getValue() + ", handler=" + handler.getValue() + ", type='" + type + '\'' + '}';
	}
}
