/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.config.serial;

import roj.config.data.CEntry;
import roj.config.data.CObject;
import roj.config.data.CString;

/**
 * @author Roj233
 * @since 2022/1/12 0:01
 */
class UnableSerializer implements Serializer<Object> {
    public static final Serializer<?> INSTANCE = new UnableSerializer();

    @Override
    public Object deserialize(CObject<?> o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object deserializeRc(CEntry o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CEntry serializeRc(Object t) {
        return CString.valueOf("FAILURE: 无法序列化这个对象...");
    }

    @Override
    public void serialize(CObject<?> o, Object t) {
        o.put("//FAILURE", "无法序列化这个对象...");
    }
}
