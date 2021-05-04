package roj.kscript.type;

import roj.kscript.util.ScriptException;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/28 12:16
 */
public final class KError extends KBase {
    private final ScriptException ex;

    public KError(ScriptException e) {
        super(Type.ERROR);
        this.ex = e;
    }

    @Override
    public boolean canCastTo(Type type) {
        switch (type) {
            case ERROR:
            case STRING:
                return true;
        }
        return false;
    }

    @Override
    public KError asKError() {
        return this;
    }

    public Throwable getCause() {
        return ex.getCause();
    }

    public StackTraceElement[] getRealTrace() {
        return ex.getStackTrace();
    }

    @Nonnull
    @Override
    public String asString() {
        return toString0(new StringBuilder(), 0).toString();
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("Error{").append(ex.getLocalizedMessage()).append('}');
    }

    @Override
    public boolean equalsTo(KType b) {
        return b == this;
    }

    public ScriptException getOrigin() {
        return ex;
    }
}
