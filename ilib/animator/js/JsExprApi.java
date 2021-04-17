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


import roj.collect.MyHashMap;
import roj.kscript.api.Computer;
import roj.kscript.api.MethodsAPI;
import roj.kscript.type.KType;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/27 23:03
 */
public class JsExprApi extends MethodsAPI {
    static MyHashMap<String, Computer> dm = new MyHashMap<>();

    static {
        dm.put("ceil", new Ceil());
        dm.put("floor", new Floor());
    }

    @Override
    protected Computer getDedicated0(CharSequence name) {
        return dm.get(name);
    }

    @Override
    protected KType preCompute0(CharSequence name, List<KType> cst) {
        Computer cp = dm.get(name);
        if(cp != null)
            return cp.compute(cst);
        return null;
    }
}