package roj.kscript;

import roj.kscript.api.ArgumentList;
import roj.kscript.ast.Frame;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.reflect.J8Util;
import roj.util.ArrayUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * 调用参数列表 + 堆栈追踪
 *
 * @author Roj233
 * @since 2020/9/21 22:37
 */
public class Arguments implements ArgumentList {
    private final KFunction caller;
    private final List<KType> arguments;

    private StackTraceElement[] traces;
    private int traceLen, argc;

    public Arguments(List<KType> arguments) {
        this.caller = null;
        this.arguments = arguments;
        this.argc = arguments.size();

        StackTraceElement[] ste = J8Util.getTraces(new Throwable());
        this.traces = new StackTraceElement[ste.length + 3];
        fill(ste);
    }

    public Arguments(Frame frame, KType[] arguments, int argc) {
        KFunction caller = frame.self;
        this.caller = caller;
        this.arguments = Arrays.asList(arguments);
        this.argc = argc;

        final Arguments args = frame.getArgs();
        this.traces = args.traces;
        this.traceLen = args.traceLen;

        fill(new StackTraceElement(caller.getClassName(), caller.getName(), caller.getSource(), frame.getLine()));
    }

    public Arguments() {
        this.caller = null;
        this.arguments = null;
        StackTraceElement[] ste = J8Util.getTraces(new Throwable());
        this.traces = new StackTraceElement[ste.length + 10];
        fill(ste);
    }

    private void fill(StackTraceElement from) {
        int j = this.traceLen;

        StackTraceElement[] st = this.traces;
        if (st.length - j - 1 < 0) {
            System.arraycopy(this.traces, 0, st = new StackTraceElement[j + 1 + 10], 0, j);
            this.traces = st;
        }

        st[this.traceLen++] = from;
    }

    private void fill(StackTraceElement... from) {
        int j = this.traceLen;

        StackTraceElement[] st = this.traces;
        if (st.length - j - from.length < 0) {
            System.arraycopy(this.traces, 0, st = new StackTraceElement[j + from.length + 10], 0, j);
            this.traces = st;
        }

        StackTraceElement[] inverse = ArrayUtil.inverse(from);
        for (int i = 0, len = inverse.length - 1; i < len; i++) {
            st[j++] = inverse[i];
        }

        this.traceLen = j;
    }

    @Override
    @Nonnull
    public KType get(int i) {
        return arguments == null || argc <= i ? KUndefined.UNDEFINED : arguments.get(i);
    }

    @Override
    @Nullable
    public KFunction caller() {
        return caller;
    }

    @Override
    public int getOr(int i, int def) {
        return arguments == null || argc <= i ? def : arguments.get(i).asInteger();
    }

    @Override
    public double getOr(int i, double def) {
        return arguments == null || argc <= i ? def : arguments.get(i).asDouble();
    }

    @Override
    public String getOr(int i, String def) {
        return arguments == null || argc <= i ? def : arguments.get(i).asString();
    }

    @Override
    public boolean getOr(int i, boolean def) {
        return arguments == null || argc <= i ? def : arguments.get(i).asBoolean();
    }

    @Override
    public KType getOr(int i, KType def) {
        return arguments == null || argc <= i ? def : arguments.get(i);
    }

    @Override
    public <T> T getObject(int i, Class<T> t, T def) {
        return arguments == null || argc <= i ? def : arguments.get(i).asJavaObject(t).getOr(def);
    }

    @Override
    public <T> T getObject(int i, Class<T> t) {
        return getObject(i, t, null);
    }

    @Override
    public StackTraceElement[] trace() {
        StackTraceElement[] stes = new StackTraceElement[this.traceLen + 1];
        System.arraycopy(this.traces, 0, stes, 0, this.traceLen);

        return ArrayUtil.inverse(stes);
    }
}
