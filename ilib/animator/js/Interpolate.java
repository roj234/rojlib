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
package ilib.animator.js;

import roj.collect.DoubleList;
import roj.kscript.type.KDouble;
import roj.kscript.type.KType;
import roj.math.MathUtils;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/27 23:04
 */
public class Interpolate implements IFunction {
    @Override
    public int argumentCount() {
        return 5;
    }

    @Override
    public double compute(DoubleList args) {
        return MathUtils.interpolate(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4));
    }

    @Override
    public KType compute(List<KType> args) {
        return KDouble.valueOf(MathUtils.interpolate(args.get(0).asDouble(), args.get(1).asDouble(), args.get(2).asDouble(), args.get(3).asDouble(), args.get(4).asDouble()));
    }
}
