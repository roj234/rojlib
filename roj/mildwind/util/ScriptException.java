package roj.mildwind.util;

/**
 * @author Roj234
 * @since 2020/9/28 13:39
 */
public class ScriptException extends RuntimeException {
	public static final ScriptException TRY_EXIT = new ScriptException();

	private ScriptException() {
		super("", null, false, false);
		this.traces = null;
	}

	static final long serialVersionUID = 1L;

	private StackTraceElement[] traces;

	public ScriptException(String msg, StackTraceElement[] stackTrace, Throwable e) {
		super(msg, e);
		this.setStackTrace(traces = stackTrace);
	}
	public ScriptException(String msg) {
		super(msg);
	}
	public ScriptException(String msg, Throwable e) {
		super(msg,e );
	}

	@Override
	public StackTraceElement[] getStackTrace() {
		return traces;
	}

	@Override
	public String toString() {
		return "脚本异常: " + getMessage();
	}
}
