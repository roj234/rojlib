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

import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
public class SpecialMapSerializer implements Serializer<Map<?, ?>> {
    final Supplier<Map<?, ?>> inst;

    public SpecialMapSerializer(Supplier<Map<?, ?>> inst) {
        this.inst = inst;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<?, ?> deserialize(CObject<?> object) {
        Map<String, Object> caster = (Map<String, Object>) inst.get();
        for (Map.Entry<String, CEntry> entry : object.raw().entrySet()) {
            caster.put(entry.getKey(), entry.getValue().unwrap());
        }
        return caster;
    }

    @Override
    public void serialize(CObject<?> base, Map<?, ?> object) {
        Map<String, CEntry> map = base.raw();
        for (Map.Entry<?, ?> entry : object.entrySet()) {
            map.put(entry.getKey().toString(), CEntry.wrap(entry.getValue()));
        }
    }

    @Override
    public CEntry serializeRc(Map<?, ?> t) {
        return CEntry.wrap(t);
    }

    @Override
    public Map<?, ?> deserializeRc(CEntry o) {
        return (Map<?, ?>) o.unwrap();
    }
}
