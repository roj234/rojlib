package roj.kscript.vm;

import roj.kscript.asm.TryEnterNode;

/**
 * @author Roj234
 * @since 2021/1/13 22:36
 */
public final class ErrorInfo {
	public static final ErrorInfo NONE = new ErrorInfo(0, null, null);

	public ScriptException e;
	public TryEnterNode info;
	public byte stage;

	public ErrorInfo(int stage, TryEnterNode info, ScriptException ex) {
		this.stage = (byte) stage;
		this.info = info;
		this.e = ex;
	}

	public void reset() {
		e = null;
		info = null;
		stage = 0;
	}
}
