package roj.kscript.ast;

import roj.collect.ReuseStack;
import roj.kscript.Arguments;
import roj.kscript.api.IGettable;
import roj.kscript.ast.api.TryCatchInfo;
import roj.kscript.func.KFuncAST;
import roj.kscript.func.KFunction;
import roj.kscript.type.Context;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.util.ExceptionInfo;
import roj.kscript.util.Stack;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 12:41
 */
public final class Frame {
    public KType result;

    public final ReuseStack<ExceptionInfo> exceptionStack = new ReuseStack<>();
    public final ReuseStack<TryCatchInfo> tryCatch = new ReuseStack<>();
    public final Context ctx;
    public final KFuncAST self;

    private IGettable $this;
    private Arguments args;
    private int line;

    public final Stack stack = new Stack();

    public Frame(KFuncAST ast, Context ctx) {
        this.ctx = ctx;
        this.self = ast;
    }

    public Frame reset(IGettable $this, Arguments list) {
        this.ctx.enterRegion(0);
        this.$this = $this;
        this.args = list;
        this.line = -1;
        return this;
    }

    public KType returnVal() {
        KType result = this.result;
        cleanup();
        return result == null ? KUndefined.UNDEFINED : result;
    }

    public void cleanup() {
        stack.clear();
        tryCatch.clear();
        exceptionStack.clear();
        ctx.reset();
        $this = null;
        args = null;
        result = null;
    }

    public IGettable getThis() {
        return $this;
    }

    public Arguments getArgs() {
        return args;
    }

    public StackTraceElement[] trace(KFunction func) {
        final StackTraceElement self = new StackTraceElement(func.getClassName(), func.getName(), func.getSource(), func.getSource() == null ? -1 : line);
        if (args == null)
            return new StackTraceElement[] {
                    self
            };

        StackTraceElement[] arr = args.trace();
        arr[0] = self;

        return arr;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getLine() {
        return line;
    }
}
