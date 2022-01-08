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
import roj.config.data.CMapping;
import roj.config.data.CNull;
import roj.config.data.CObject;
import roj.reflect.FieldAccessor;
import roj.reflect.ReflectionUtils;

/**
 * @author Roj233
 * @since 2022/1/12 1:07
 */
abstract class GenSer extends WrapSerializer implements Serializer<Object> {
    GenSer() {}

    abstract void serialize0(CMapping map, Object o);

    public abstract Object deserializeRc(CEntry e);

    public final CEntry serializeRc(Object o) {
        if (o == null) return CNull.NULL;
        CMapping map = new CMapping();
        serialize0(map, o);
        return map;
    }

    public final void serialize(CObject<?> obj, Object o) {
        serialize0(obj, o);
    }

    public final Object deserialize(CObject<?> obj) {
        return deserializeRc(obj);
    }

    static FieldAccessor acc(Class<?> objClass, String fieldName) {
        try {
            return ReflectionUtils.access(ReflectionUtils.getField(objClass, fieldName));
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(objClass.getName() + '.' + fieldName);
        }
    }
}
