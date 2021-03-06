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
import roj.kscript.asm.Frame;
import roj.kscript.asm.Node;
import roj.kscript.asm.TryEnterNode;
import roj.kscript.func.KFunction;
import roj.kscript.type.KError;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since  2020/9/27 12:28
 */
public final class Func_Try extends Func {
    public Func_Try(Node begin, Frame frame) {
        super(begin, frame);
    }

    @Override
    @SuppressWarnings("fallthrough")
    public KType invoke(@Nonnull IObject $this, ArgList param) {
        Frame f = curr = getIdleFrame();
        f.init($this, param);

        Node p = begin;

        TryEnterNode info = null;
        ScriptException se = null;
        /**
         * 0 : none, 1: caught, 2: finally_throw, 3: finally_eat
         */
        int stg = 0;

        execution:
        while (true) {
            // ?????????????????????
            if (p == null) {
                switch (stg) {
                    case 0: // ??????try???
                        break execution;
                    case 1: // catch????????????
                        if ((p = info.fin) != null) { // ??????finally?????????
                            stg = 3;
                            break;
                        }
                    case 3: // catch ?????? [???????????? ??? finally] ??????
                        p = info.end;

                        /** if stack is empty, then return {@link ErrorInfo#NONE} **/
                        ErrorInfo stack = f.popError();
                        info = stack.info;
                        se = stack.e;
                        stg = stack.stage;
                        break;
                    case 2: // ??????catch???finally
                        f.reset();
                        throw se;
                }
            }
            f.linear(p);
            try {
                p = p.exec(f);
            } catch (TCOException e) {
                if(e == TCOException.TCO_RESET) {
                    p = begin;

                    info = null;
                    se = null;
                    stg = 0;
                } else {
                    throw e;
                }
            } catch (Throwable e) {
                if (e == ScriptException.TRY_EXIT) {
                    // try?????????????????????finally
                    p = info.fin;
                    stg = 3;
                } else {
                    if (info != null) {
                        // try???catch???????????????try????????????????????????
                        f.pushError(stg, info, se);
                    }

                    se = collect(f, p, e);

                    if ((info = f.popTryOrNull()) == null) {
                        // ??????????????????
                        f.reset();
                        throw se;
                    }

                    // ??????
                    f.stackClear();

                    // ????????? catch
                    if ((p = info.handler) != null) {
                        // ???????????????KType????????????????????????
                        f.push(new KError(se));
                        stg = 1;
                    } else {
                        // finally
                        // ??????????????????null, ??????try??????????????????catch,?????????finally
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
