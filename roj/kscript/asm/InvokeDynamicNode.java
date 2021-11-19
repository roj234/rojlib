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
package roj.kscript.asm;

import roj.kscript.api.ArgList;
import roj.kscript.api.IArray;
import roj.kscript.api.IObject;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.kscript.vm.KScriptVM;
import roj.kscript.vm.TCOException;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/6/18 10:06
 */
public final class InvokeDynamicNode extends InvokeNode {
    final long activeBits;

    public InvokeDynamicNode(boolean staticCall, int argCount, boolean noRet, long activeBits) {
        super(staticCall, argCount, noRet);
        this.activeBits = activeBits;
    }

    @Override
    public Opcode getCode() {
        return Opcode.USELESS;
    }

    @Override
    public Node exec(Frame f) {
        KScriptVM.get().pushStack(0);

        List<KType> args;
        int argc = this.argc;
        if (argc != 0) {
            args = KScriptVM.retainArgHolder(argc, true);

            for (int i = argc - 1; i >= 0; i--) {
                KType t = f.pop();
                if((activeBits & (1L << i)) != 0) {
                    IArray array = t.asArray();
                    for (int j = 0; j < array.size(); j++) {
                        args.add(array.get(j));
                    }
                } else {
                    args.add(t);
                }
            }
        } else {
            args = null;
        }

        KFunction fn = f.last().asFunction();
        ArgList argList = KScriptVM.retainArgList(this, f, args);

        IObject $this;
        int v = this.flag;
        if((v & 1) == 0) {
            KType tmp = fn.createInstance(argList);
            if(!(tmp instanceof IObject)) {
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
        v &= 2;

        do {
            try {
                KType result = fn.asFunction().invoke($this, argList);

                if (v == 0)
                    f.setLast(result);
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
            } catch (Throwable e) { // fake finally
                KScriptVM.releaseArgList(argList);
                KScriptVM.get().popStack();
                throw e;
            }
        } while (true);
    }
}
