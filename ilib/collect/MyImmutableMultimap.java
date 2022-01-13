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
package ilib.collect;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class MyImmutableMultimap implements Multimap<String, Object> {
    public MyImmutableMultimap(Multimap<String, Object> map) {
        this.map = map;
    }

    private final Multimap<String, Object> map;

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(@Nullable Object o) {
        return map.containsKey(o);
    }

    @Override
    public boolean containsValue(@Nullable Object o) {
        return map.containsValue(o);
    }

    @Override
    public boolean containsEntry(@Nullable Object o, @Nullable Object o1) {
        return map.containsEntry(o, o1);
    }

    @Override
    public boolean put(@Nullable String s, @Nullable Object o) {
        return false;
    }

    @Override
    public boolean remove(@Nullable Object o, @Nullable Object o1) {
        return false;
    }

    @Override
    public boolean putAll(@Nullable String s, @Nonnull Iterable<?> iterable) {
        return false;
    }

    @Override
    public boolean putAll(@Nonnull Multimap<? extends String, ?> multimap) {
        return false;
    }

    @Override
    public Collection<Object> replaceValues(@Nullable String s, @Nonnull Iterable<?> iterable) {
        return null;
    }

    @Override
    public Collection<Object> removeAll(@Nullable Object o) {
        return null;
    }

    @Override
    public void clear() {

    }

    @Override
    public Collection<Object> get(@Nullable String s) {
        return map.get(s);
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public Multiset<String> keys() {
        return map.keys();
    }

    @Override
    public Collection<Object> values() {
        return map.values();
    }

    @Override
    public Collection<Map.Entry<String, Object>> entries() {
        return map.entries();
    }

    @Override
    public Map<String, Collection<Object>> asMap() {
        return map.asMap();
    }
}
