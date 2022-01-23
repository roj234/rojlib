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

import roj.kscript.vm.ScriptException;

/**
 * @author Roj234
 * @since  2021/6/15 18:29
 */
public class TryNormalNode extends GotoNode {
    public boolean gotoFinal;

    public TryNormalNode() {
        super(null);
    }

    public void setTarget(LabelNode node) {
        target = node;
    }

    @Override
    public Node exec(Frame f) {
        f.applyDiff(diff);
        if(gotoFinal) { // try肯定配对了，不用担心
            throw ScriptException.TRY_EXIT;
        } else {
            f.popTry();
            return target;
        }
    }

    @Override
    public Opcode getCode() {
        return Opcode.TRY_EXIT;
    }

    @Override
    public String toString() {
        return "end of try block from " + target + ", finally: " + gotoFinal;
    }
}
