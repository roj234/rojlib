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
import roj.kscript.type.KType;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/4/26 23:16
 */
public class ConstMap extends MyHashMap<String, KType> {
    public ConstMap() {
        super(8);
    }

    @Override
    protected MyHashMap.Entry<String, KType> createEntry(String id) {
        return new KOEntry(id, null);
    }

    public boolean isConst(String key) {
        KOEntry entry = (KOEntry) getEntry(key);
        return entry != null && (entry.flags & 1) != 0;
    }

    public void markConst(String key) {
        KOEntry entry = (KOEntry) getEntry(key);

        if(entry != null)
            entry.flags |= 1;
    }
}
