package roj.kscript.vm;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/28 13:39
 */
public class ScriptException extends RuntimeException {
    public static final ScriptException TRY_EXIT = new ScriptException();

    private ScriptException() {
        super("", null, false, false);
        this.traces = null;
    }

    static final long serialVersionUID = 1L;

    private final StackTraceElement[] traces;

    public ScriptException(String msg, StackTraceElement[] stackTrace, Throwable e) {
        super(msg, e);
        this.setStackTrace(traces = stackTrace);
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
