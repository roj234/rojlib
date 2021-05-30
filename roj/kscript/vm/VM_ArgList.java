package roj.kscript.vm;

import roj.kscript.api.ArgList;
import roj.kscript.ast.Frame;
import roj.kscript.ast.InvokeNode;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;

import javax.annotation.Nullable;
import java.util.Collections;
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
public final class VM_ArgList extends ArgList {
    public Frame caller;
    public InvokeNode from;

    public VM_ArgList() {}

    public VM_ArgList reset(InvokeNode from, Frame frame, List<KType> argv) {
        this.from = from;
        this.caller = frame;
        this.argv = argv == null ? Collections.emptyList() : argv;
        return this;
    }

    @Override
    @Nullable
    public KFunction caller() {
        return caller.owner();
    }

    @Override
    public void trace(List<StackTraceElement> collector) {
        caller.trace(from, collector);
    }
}
