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
import roj.kscript.api.IObject;
import roj.kscript.type.KType;
import roj.kscript.vm.KScriptVM;
import roj.kscript.vm.TCOException;

import java.util.List;

/**
 * Tail Call Optimization Part 2
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/6/16 23:14
 */
public class TailCall extends Node {
    final short argc;
    final byte  flag;

    public TailCall(InvokeNode self) {
        this.flag = self.flag;
        this.argc = self.argc;
    }

    @Override
    public Opcode getCode() {
        return Opcode.USELESS;
    }

    @Override
    public Node exec(Frame f) {
        // self
        if (f.last(argc) == f.owner) {
            IObject t = f.$this;
            ArgList al;

            if(argc > 0) {
                List<KType> args = KScriptVM.resetArgList(al = f.args, this, f, argc);

                for (int i = argc - 1; i >= 0; i--) {
                    args.set(i, f.pop()/*.memory(1)*/);
                }
            } else {
                al = null;
            }

            f.reset();
            if (!f.init(t, al))
                throw TCOException.TCO_RESET;
            return f.owner.begin;
        }

        List<KType> args;
        int argc = this.argc;
        if (argc != 0) {
            args = KScriptVM.retainArgHolder(argc, false);

            for (int i = argc - 1; i >= 0; i--) {
                args.set(i, f.pop());
            }
        } else {
            args = null;
        }

        KType fn = f.last();
        ArgList argList = KScriptVM.retainArgList(this, f, args);

        if ((flag & 1) != 0) {
            fn = fn.asFunction().invoke(f.$this, argList);
        }

        // 把控制权返回调用者
        throw KScriptVM.get().localTCOInit.reset(f.$this, argList, fn.asFunction(), flag);
    }

    @Override
    public String toString() {
        String b = "";
        switch (flag) {
            case 0:
                b = "T invoke ";
                break;
            case 1:
                b = "T new ";
                break;
            case 2:
                b = "T void invoke ";
                break;
            case 3:
                b = "T void new ";
                break;
        }
        return b + (argc & 32767);
    }

}