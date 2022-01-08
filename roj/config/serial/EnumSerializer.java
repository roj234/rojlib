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
import roj.reflect.EnumHelper;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
class EnumSerializer implements Serializer<Enum<?>> {
    final Class<? extends Enum<?>> enumc;

    @SuppressWarnings("unchecked")
    EnumSerializer(Class<?> enumc) {
        if (!enumc.isEnum()) throw new ClassCastException();
        this.enumc = (Class<? extends Enum<?>>) enumc;
    }

    @Override
    public Enum<?> deserialize(CObject<?> o) {
        return (Enum<?>) EnumHelper.H.getEnumConstantDirectory(enumc).get(o.get("").asString());
    }

    @Override
    public void serialize(CObject<?> o, Enum<?> t) {
        o.put("", t.name());
    }

    @Override
    public CEntry serializeRc(Enum<?> t) {
        return CString.valueOf(t.name());
    }

    @Override
    public Enum<?> deserializeRc(CEntry o) {
        return (Enum<?>) EnumHelper.H.getEnumConstantDirectory(enumc).get(o.asString());
    }
}
