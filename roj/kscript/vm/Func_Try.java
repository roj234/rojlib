package roj.kscript.vm;

import roj.collect.ReuseStack;
import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.ast.Frame;
import roj.kscript.ast.Node;
import roj.kscript.ast.TryNode;
import roj.kscript.func.KFunction;
import roj.kscript.type.KError;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 12:28
 */
public final class Func_Try extends Func {
    public Func_Try(Node begin, Frame frame) {
        super(begin, frame);
    }

    @Override
    @SuppressWarnings("fallthrough")
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

        TryNode info = null;
        ScriptException se = null;
        /**
         * 0 : none, 1: caught, 2: finally_throw, 3: finally_eat
         */
        int stg = 0;

        /** if stack is empty, then return {@link ErrorInfo#NONE} **/
        ReuseStack<ErrorInfo> excs = f.exInfo;

        execution:
        while (true) {
            // 当前流程走完了
            if (p == null) {
                switch (stg) {
                    case 0: // 不在try中
                        break execution;
                    case 1: // catch执行完毕
                        if ((p = info.fin) != null) { // 若有finally执行finally
                            stg = 3;
                            break;
                        }
                    case 3: // 有catch或者正常执行的finally完毕
                        p = info.end;

                        ErrorInfo stack = excs.pop();
                        info = stack.info;
                        se = stack.e;
                        stg = stack.stage;
                        break;
                    case 2: // 没有catch的finally
                        f.reset();
                        throw se;
                }
            }
            f.linear(p);
            try {
                p = p.execute(f);
            } catch (Throwable e) {
                if (info != null) {
                    // try-catch中又碰到了try且给你送了个异常
                    excs.push(new ErrorInfo(stg, info, se));
                }

                se = collect(f, p, e);

                ReuseStack<TryNode> tc = f.tryCatch;
                if (tc.isEmpty()) {
                    // 未捕捉的异常
                    f.reset();
                    throw se;
                }
                info = tc.pop();

                if (e == ScriptException.TRY_EXIT) {
                    // try正常执行，进入finally
                    p = info.fin;
                    stg = 3;
                } else {
                    // try遇到异常
                    f.stackClear();

                    // 如果有 catch
                    if ((p = info.handler) != null) {
                        // 将转换后的KType错误对象压入栈顶
                        f.push(new KError(se));
                        stg = 1;
                    } else {
                        // finally
                        // 无需检测是否null, 一个try不可能既没有catch,也没有finally
                        p = info.fin;
                        stg = 2;
                    }
                }
            }
        }

        return f.returnVal();
    }

    public KFunction onReturn(Frame frame) {
        return new Func_Try(begin, curr.closure()).set(source, name, clazz);
    }
}
