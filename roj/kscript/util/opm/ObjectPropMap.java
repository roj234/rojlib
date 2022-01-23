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

import roj.collect.MyHashMap;
import roj.kscript.api.IObject;
import roj.kscript.type.KBool;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.util.UnmodifiableException;

import java.util.Objects;

import static roj.collect.IntMap.NOT_USING;

/**
 * @author Roj234
 * @since  2021/4/25 23:26
 */
public class ObjectPropMap extends MyHashMap<String, KType> {
    public ObjectPropMap() {
        super(2);
    }

    public ObjectPropMap(int initCap) {
        super(initCap);
    }

    public ObjectPropMap(MyHashMap<String, KType> map) {
        super(map);
    }

    @Override
    protected Entry<String, KType> createEntry(String id) {
        return new KOEntry(id, null);
    }

    @Override
    public KType get(Object id) {
        Entry<String, KType> entry = getEntry((String) id);
        return entry == null ? null : entry.getValue();
    }

    @Override
    public KType getOrDefault(Object key, KType def) {
        Entry<String, KType> entry = getEntry((String) key);
        if(entry == null || entry.v == NOT_USING)
            return def;
        return entry.getValue();
    }

    @Override
    public KType put(String key, KType e) {
        if (size > length * loadFactor) {
            length <<= 1;
            resize();
        }

        Entry<String, KType> entry = getOrCreateEntry(key);
        Object old = entry.setValue(e);
        if (old == NOT_USING) {
            size++;
            return null;
        }
        return (KType) old;
    }

    public static void Object_defineProperty(KObject src, String id, IObject prop) {
        ObjectPropMap map = (ObjectPropMap) src.getInternal();

        KOEntry entry;
        if(prop.containsKey("get") || prop.containsKey("set")) {
            Entry<String, KType> chk = map.getEntryFirst(id, true);
            entry = new SGEntry(id, null);

            if(prop.containsKey("get")) {
                entry.v = prop.get("get").asFunction();
            }

            if(prop.containsKey("set")) {
                ((SGEntry)entry).set = prop.get("set").asFunction();
            }

            if (chk.v == NOT_USING || Objects.equals(id, chk.k)) {
                byte flags = ((KOEntry) chk).flags;
                if((flags & 1) == 1) {
                    throw new UnmodifiableException();
                }
                entry.flags = flags;

                map.entries[map.indexFor(id)] = entry;
            } else {
                Entry<String, KType> prev;
                while (chk.next != null) {
                    prev = chk;
                    chk = chk.next;
                    if (Objects.equals(id, chk.k)) {
                        byte flags = ((KOEntry) chk).flags;
                        if ((flags & 1) == 1) {
                            throw new UnmodifiableException();
                        }
                        entry.flags = flags;

                        prev.next = entry;
                        entry.next = chk.next;
                    }
                }
                chk.next = entry;
            }
        } else {
            entry = (KOEntry) map.getEntry(id);

            if((entry.flags & 1) == 0) {
                KType v = prop.getOrNull("value");
                if(v != null)
                    entry.v = v;
            } else {
                throw new UnmodifiableException();
            }
        }

        if(!prop.getOr("configurable", KBool.FALSE).asBool()) {
            entry.flags |= 1;
        } else {
            entry.flags &= ~1;
        }

        if(!prop.getOr("enumerable", KBool.TRUE).asBool()) {
            entry.flags |= 2;
        } else {
            entry.flags &= ~2;
        }
    }
}
