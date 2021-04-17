package roj.kscript.func;

import roj.kscript.Arguments;
import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.Frame;
import roj.kscript.ast.api.TryCatchInfo;
import roj.kscript.ast.node.Node;
import roj.kscript.type.Context;
import roj.kscript.type.KError;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.util.ExceptionInfo;
import roj.kscript.util.ScriptException;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 12:28
 */
public final class KFuncAST extends KFunction {
    public KFuncAST(KFuncAST ast) {
        this((KObject) ast.getPrototype(), ast.frame0.ctx.copy(), ast.begin, ast.clazz);
    }

    public KFuncAST(KObject parent, Context ctx, Node begin, String clazz) {
        super(parent);
        this.begin = begin;
        this.clazz = clazz;
        this.frame0 = new Frame(this, ctx);
    }

    private final String clazz;
    private final Node begin;
    private final Frame frame0;

    @Override
    public KType invoke(@Nonnull IGettable $this, Arguments param) {
        Frame frame;
        if(frame0.getArgs() != null) {
            frame = new Frame(this, frame0.ctx.copy());
        } else {
            frame = this.frame0;
        }

        frame.reset($this, param);

        Node next = begin;

        TryCatchInfo info = null;
        ScriptException se = null;
        int stg = 0;

        while (true) {
            if (next == null) {
                if (info != null) {
                    switch (stg) {
                        case 1: // caught
                            next = info.getFin();
                            if (next == null) {
                                next = info.getEnd();
                                if(frame.exceptionStack.isEmpty()) {
                                    info = null; // out of block
                                    se = null;
                                    stg = 0;
                                } else {
                                    ExceptionInfo stack = frame.exceptionStack.pop();
                                    info = stack.info;
                                    se = stack.exception;
                                    stg = stack.stage;
                                }
                            } else {
                                stg = 3;
                            }
                            break;
                        case 2: // finally - nocatch
                            frame.cleanup();
                            throw se;
                        case 3: // finally - caught
                            next = info.getEnd(); // out of block
                            if(frame.exceptionStack.isEmpty()) {
                                info = null;
                                se = null;
                                stg = 0;
                            } else {
                                ExceptionInfo stack = frame.exceptionStack.pop();
                                info = stack.info;
                                se = stack.exception;
                                stg = stack.stage;
                            }
                            break;
                    }
                } else {
                    break;
                }
            }
            try {
                next = next.execute(frame);
            } catch (Throwable e) {
                se = e instanceof ScriptException ? (ScriptException) e : new ScriptException("Node#" + nodeId(begin, next) + ": " + next, frame.trace(this), e);

                if (e == ScriptException.TRY_EXIT) {
                    next = (info = frame.tryCatch.pop()).getFin();
                    stg = 3;
                    System.out.println("nz-try_exit " + frame.tryCatch);
                    continue;
                }

                if (frame.tryCatch.isEmpty()) {
                    frame.cleanup();
                    throw se;
                }
                frame.stack.clear();

                if(info != null) {
                    frame.exceptionStack.push(new ExceptionInfo(stg, info, se));
                }
                info = frame.tryCatch.pop();

                Node node = info.getHandler();
                if (node != null) { // catch
                    next = node;

                    frame.stack.push(new KError(se));

                    stg = 1;
                    continue;
                }

                node = info.getFin();
                if (node != null) { // finally
                    next = node;
                    stg = 2;
                }
            }
        }

        return frame.returnVal();
    }

    private static int nodeId(Node begin, Node target) {
        int i = 0;
        do {
            if (begin == target) {
                return i;
            }
            i++;
        } while ((begin = begin.next()) != null);
        return -1;
    }

    @Override
    public String getClassName() {
        return clazz;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return ASTree.toString(begin, sb.append("function ").append(getName()).append("() {\n")).append('}');
    }
}
