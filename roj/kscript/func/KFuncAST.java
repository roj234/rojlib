package roj.kscript.func;

import roj.collect.ReuseStack;
import roj.kscript.api.IArguments;
import roj.kscript.api.IObject;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.Frame;
import roj.kscript.ast.Node;
import roj.kscript.ast.TryNode;
import roj.kscript.type.KError;
import roj.kscript.type.KType;
import roj.kscript.util.ExcpInfo;
import roj.kscript.util.ScriptException;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 12:28
 */
public final class KFuncAST extends KFunction {
    public KFuncAST(Node begin, Frame frame) {
        this.begin = begin;
        this.frame0 = frame.init(this);
    }

    public final Node begin;
    public final Frame frame0;

    @Override
    public KType invoke(@Nonnull IObject $this, IArguments param) {
        Frame f;
        if(frame0.working()) {
            f = frame0.duplicate();
        } else {
            f = this.frame0;
        }

        f.reset($this, param);

        Node p = begin;

        TryNode info = null;
        ScriptException se = null;
        /**
         * 0 : none, 1: caught, 2: finally_throw, 3: finally_eat
         */
        int stg = 0;

        ReuseStack<ExcpInfo> excs = f.exInfo;
        while (true) {
            if (p == null) {
                if (info != null) {
                    switch (stg) {
                        case 1: // caught
                            p = info.fin();
                            if (p == null) {
                                p = info.getEnd();

                                /** if stack is empty, then return {@link ExcpInfo#NONE} **/
                                ExcpInfo stack = excs.pop();
                                info = stack.info;
                                se = stack.e;
                                stg = stack.stage;
                            } else {
                                stg = 3;
                            }
                            break;
                        case 2: // finally - nocatch
                            f.cleanup();
                            throw se;
                        case 3: // finally - caught
                            p = info.getEnd(); // out of block

                            /** if stack is empty, then return {@link ExcpInfo#NONE} **/
                            ExcpInfo stack = excs.pop();
                            info = stack.info;
                            se = stack.e;
                            stg = stack.stage;
                            break;
                    }
                } else {
                    break;
                }
            }
            try {
                p = p.execute(f);
            } catch (Throwable e) {
                if(e instanceof ScriptException) {
                    se = (ScriptException) e;
                } else {
                    ArrayList<StackTraceElement> trace = new ArrayList<>();
                    f.trace(begin, trace);
                    se = new ScriptException("Node#" + nodeId(begin, p) + ": " + p, trace.toArray(new StackTraceElement[trace.size()]), e);
                }

                ReuseStack<TryNode> tc = f.tryCatch;

                if (e == ScriptException.TRY_EXIT) {
                    // try block run normal
                    p = (info = tc.pop()).fin();
                    stg = 3;
                } else if (tc.isEmpty()) {
                    // an uncaught exception
                    f.cleanup();
                    throw se;
                } else {
                    // try block caught exception
                    f.stackClear();

                    if (info != null) {
                        // save last exception
                        excs.push(new ExcpInfo(stg, info, se));
                    }
                    // load current try block descriptor
                    info = tc.pop();

                    // 'caught' node
                    Node node = info.getHandler();
                    if (node != null) {
                        p = node;

                        f.push(new KError(se));

                        stg = 1;
                    } else {
                        // 'finally' node
                        node = info.fin();
                        if (node != null) { // finally
                            p = node;
                            stg = 2;
                        }
                    }
                }
            }
        }

        return f.returnVal();
    }

    private static int nodeId(Node begin, Node target) {
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

    public KFuncAST export(Frame frame) {
        return (KFuncAST) new KFuncAST(begin, this.frame0.staticize(frame)).set(source, name, clazz);
    }
}
