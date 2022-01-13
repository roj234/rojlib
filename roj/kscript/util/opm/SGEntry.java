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
package roj.kscript.util.opm;

import roj.kscript.Arguments;
import roj.kscript.func.KFunction;
import roj.kscript.type.KNull;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/4/26 18:10
 */
public final class SGEntry extends KOEntry {
    public KFunction set;
    Arguments arg;

    // get = value
    SGEntry(String k, KType v) {
        super(k, v);
    }

    @Override
    public KType getValue() {
        if(v == null)
            return KUndefined.UNDEFINED;

        if(arg == null) {
            arg = new Arguments(new OneList<>(null));
        }
        ((OneList<KType>) arg.argv).setEmpty(true);

        return v.asFunction().invoke(KNull.NULL, arg);
    }

    @Override
    public KType setValue(KType latest) {
        if(set == null)
            return getValue();

        if(arg == null) {
            arg = new Arguments(new OneList<>(null));
        }

        OneList<KType> argv = (OneList<KType>) arg.argv;
        argv.setEmpty(false);
        argv.set(0, latest);

        return set.invoke(KNull.NULL, arg);
    }
}
