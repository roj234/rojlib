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
package ilib.asm.nixim;

import ilib.Config;
import ilib.util.PinyinUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.client.util.SearchTree;
import net.minecraft.util.ResourceLocation;
import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.concurrent.OperationDone;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/1 1:17
 */
public class FastSearchTree<T> extends SearchTree<T> {
    private final Map<String, TrieTree<T>> idMap = new MyHashMap<>(4, 1);
    private final TrieTree<T> tree = new TrieTree<>();

    public FastSearchTree(Function<T, Iterable<String>> nameFunc, Function<T, Iterable<ResourceLocation>> idFunc) {
        super(nameFunc, idFunc);
        this.nameFunc = nameFunc;
        this.idFunc = idFunc;

        this.byId = null;
        this.byName = null;
        this.numericContents = Object2IntMaps.emptyMap();
    }

    public void recalculate() {
        tree.clear();
        idMap.clear();
        for (T t : this.contents) {
            index(t);
        }
    }

    public void add(T t) {
        this.contents.add(t);
        this.index(t);
    }

    private void index(T t) {
        this.idFunc.apply(t).forEach((x) -> this.idMap.computeIfAbsent(x.getNamespace().toLowerCase(), (y) -> new TrieTree<>()).put(x.getPath().toLowerCase(), t));
        try {
            StringBuilder sb = new StringBuilder();
            this.nameFunc.apply(t).forEach((x) -> {
                this.tree.put(x, t);
                if (Config.searchNameOnly)
                    throw OperationDone.INSTANCE;
                if (Config.enablePinyinSearch) {
                    this.tree.put(PinyinUtil.pinyin().toPinyin(x, sb, 24).toString(), t);
                    sb.delete(0, sb.length());
                }
            });
        } catch (OperationDone ignored) {
        }
    }

    public List<T> search(String key) {
        int i;
        key = key.toLowerCase();
        if ((i = key.indexOf(':')) < 0)
            return tree.valueMatches(key, 1000);
        else {
            TrieTree<T> tree = idMap.get(key.substring(0, i));
            return tree == null ? Collections.emptyList() : tree.valueMatches(key.substring(i + 1), 1000);
        }
    }
}
