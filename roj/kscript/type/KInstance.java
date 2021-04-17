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
package roj.kscript.type;

import roj.kscript.func.BindThis;
import roj.kscript.func.KFunction;
import roj.kscript.util.opm.ObjectPropMap;

import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/27 23:41
 */
public final class KInstance extends KObject {
    final KFunction constructor;

    @Override
    public boolean canCastTo(Type type) {
        return type == Type.OBJECT;
    }

    public KInstance(KFunction cst, KObject proto) {
        super(new ObjectPropMap(), proto);
        for (Map.Entry<String, KType> entry : proto.getInternal().entrySet()) {
            String key = entry.getKey();
            KType value = entry.getValue();

            if (value.getType() == Type.FUNCTION)
                map.put(key, new BindThis(this, value.asFunction()));
        }

        this.constructor = cst;
    }

    @Override
    public KType getOr(String key, KType def) {
        return key.equals("constructor") ? constructor : super.getOr(key, def);
    }
}
