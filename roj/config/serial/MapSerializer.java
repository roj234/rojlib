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

import roj.collect.MyHashMap;
import roj.config.data.CEntry;
import roj.config.data.CObject;
import roj.util.Helpers;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
class MapSerializer implements Serializer<Map<?, ?>> {
    private final Serializers owner;

    public MapSerializer(Serializers owner) {
        this.owner = owner;
    }

    @Override
    public Map<?, ?> deserialize(CObject<?> object) {
        MyHashMap<String, Object> caster = Helpers.cast(new MyHashMap<>(object.raw()));
        for (Map.Entry<String, Object> entry : caster.entrySet()) {
            entry.setValue(((CEntry) entry.getValue()).unwrap());
        }
        return caster;
    }

    @Override
    public void serialize(CObject<?> base, Map<?, ?> object) {
        Map<String, CEntry> map = base.raw();
        for (Map.Entry<?, ?> entry : object.entrySet()) {
            map.put(entry.getKey().toString(), CEntry.wrap(entry.getValue(), owner));
        }
    }

    @Override
    public CEntry serializeRc(Map<?, ?> t) {
        return CEntry.wrap(t, owner);
    }

    @Override
    public Map<?, ?> deserializeRc(CEntry o) {
        return (Map<?, ?>) o.unwrap();
    }
}
