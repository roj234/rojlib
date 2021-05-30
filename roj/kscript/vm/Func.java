package roj.kscript.vm;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.Frame;
import roj.kscript.ast.Node;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 12:28
 */
public class Func extends KFunction {
    public Func(Node begin, Frame frame) {
        this.begin = begin;
        this.frame0 = frame.init(this);
    }

    public Node begin;
    public Frame frame0, curr;

    @Override
    public KType invoke(@Nonnull IObject $this, ArgList param) {
        Frame f;
        if(frame0.working()) {
            f = frame0.duplicate();
        } else {
            f = frame0;
        }
        curr = f;

        f.init($this, param);

        Node p = begin;

        while (p != null) {
            f.linear(p);
            try {
                p = p.execute(f);
            } catch (Throwable e) {
                ScriptException se = collect(f, p, e);

                f.reset();
                throw se;
            }
        }

        return f.returnVal();
    }

    protected ScriptException collect(Frame f, Node p, Throwable e) {
        ScriptException se;
        if (e instanceof ScriptException) {
            se = (ScriptException) e;
        } else {
            ArrayList<StackTraceElement> trace = new ArrayList<>();
            f.trace(p, trace);
            se = new ScriptException("Node#" + nodeId(begin, p) + ": " + p + "\n\n" + f.toString(), trace.toArray(new StackTraceElement[trace.size()]), e);
        }
        return se;
    }

    static int nodeId(Node begin, Node target) {
        int i = 0;
        do {
            if (begin == target) {
                return i;
            }
            i++;
        } while ((begin = begin.next) != null);
        return -1;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return ASTree.toString(begin, sb.append("function ").append(getName()).append("() {\n")).append('}');
    }

    public KFunction onReturn(Frame frame) {
        return new Func(begin, curr.closure()).set(source, name, clazz);
    }
}
