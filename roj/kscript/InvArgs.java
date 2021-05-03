package roj.kscript;

import roj.kscript.api.IArguments;
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
public final class InvArgs extends IArguments {
    public final Frame caller;
    public final InvokeNode callNode;

    /**
     * call by {@link InvokeNode}
     */
    public InvArgs(InvokeNode callNode, Frame frame, List<KType> argv) {
        this.callNode = callNode;
        this.caller = frame;
        this.argv = argv == null ? Collections.emptyList() : argv;
    }

    @Override
    @Nullable
    public KFunction caller() {
        return caller.owner();
    }

    @Override
    public void trace(List<StackTraceElement> collector) {
        caller.trace(callNode, collector);
    }
}
