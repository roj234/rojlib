/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.kscript.vm;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.Frame;
import roj.kscript.ast.Node;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/27 12:28
 */
public class Func extends KFunction {
    public Func(Node begin, Frame frame) {
        this.begin = begin;
        this.frame0 = frame.init(this);
    }

    public Node begin;
    public Frame frame0, curr;

    protected ArrayList<WeakReference<Frame>> unused;
    protected ReferenceQueue<Frame> queue;

    @Override
    public KType invoke(@Nonnull IObject $this, ArgList param) {
        Frame f = curr = getIdleFrame();
        f.init($this, param);

        Node p = begin;

        while (p != null) {
            f.linear(p);
            try {
                p = p.exec(f);
            } catch (Throwable e) {
                ScriptException se = collect(f, p, e);

                f.reset();
                throw se;
            }
        }

        return f.returnVal();
    }

    Frame getIdleFrame() {
        Frame f = frame0;
        if(!f.working())
            return f;
        f = f.duplicate();
        /*if(unused == null) {
            unused = new ArrayList<>();
            queue = InstantEnqueuer.set(this);
        }

        ArrayList<WeakReference<Frame>> unused = this.unused;
        synchronized (unused) {
            if(!unused.isEmpty()) {
                do {
                    f = unused.remove(unused.size() - 1).get();
                } while (f == null && !unused.isEmpty());
            }
            if(f == null)
                unused.add(new WeakReference<>(f = frame0.duplicate(), queue));
        }*/

        return f;
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
        sb.append("function ").append(getName()).append("() {");
        return ASTree.DEBUG ? ASTree.toString(begin, sb.append("\n")) : sb.append(" ... ").append('}');
    }

    public KFunction onReturn(Frame frame) {
        return new Func(begin, curr.closure()).set(source, name, clazz);
    }
}
