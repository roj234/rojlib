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
package roj.kscript.ast;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.kscript.vm.KScriptVM;
import roj.kscript.vm.TCOException;

import java.util.List;

/**
 * Invocation
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/27 23:27
 */
class InvokeNode extends Node {
    final short argc;
    final byte flag;

    public InvokeNode(boolean staticCall, int argCount, boolean noRet) {
        this.flag = (byte) ((staticCall ? 0 : 1) | (noRet ? 2 : 0));
        if(argCount > 65535 || argCount < 0)
            throw new IndexOutOfBoundsException("KScript only support at most 65535 parameters, got " + argCount);
        this.argc = (short) argCount;
    }

    @Override
    public Opcode getCode() {
        return Opcode.INVOKE;
    }

    @Override
    public Node exec(Frame f) {
        KScriptVM.get().pushStack(0);

        List<KType> args;
        int argc = this.argc;
        if (argc != 0) {
            args = KScriptVM.retainArgHolder(argc, false);

            for (int i = argc - 1; i >= 0; i--) {
                args.set(i, f.pop()/*.setFlag(1)*/);
            }
        } else {
            args = null;
        }

        KFunction fn = f.last().asFunction();
        ArgList argList = KScriptVM.retainArgList(this, f, args);

        IObject $this;
        int v = this.flag;
        if((v & 1) != 0) {
            KType tmp = fn.createInstance(argList);
            if(!(tmp instanceof IObject)) {
                System.out.println(tmp);
                if ((v & 2) == 0)
                    f.setLast(tmp);
                else
                    f.pop();

                KScriptVM.releaseArgList(argList);
                KScriptVM.get().popStack();

                return next;
            }
            $this = (IObject) tmp;
        } else {
            $this = f.$this;
        }

        do {
            try {
                KType result = fn.invoke($this, argList);

                if ((v & 2) == 0)
                    f.setLast(((v & 1) == 0) ? result : $this);
                else
                    f.pop();

                KScriptVM.releaseArgList(argList);
                KScriptVM.get().popStack();

                return next;
            } catch (TCOException e) {
                KScriptVM.releaseArgList(argList);

                argList = e.argList;
                fn = e.fn;
                $this = e.$this;
                v = e.flag;
            } catch (Throwable e) { // fake finally
                KScriptVM.releaseArgList(argList);
                KScriptVM.get().popStack();
                throw e;
            }
        } while (true);
    }

    @Override
    public String toString() {
        String b = "";
        switch (flag) {
            case 0:
                b = "invoke ";
                break;
            case 1:
                b = "new ";
                break;
            case 2:
                b = "void invoke ";
                break;
            case 3:
                b = "void new ";
                break;
        }
        return b + argc;
    }

}
