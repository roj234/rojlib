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
package roj.kscript.util;

import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.collect.Unioner;
import roj.kscript.ast.Frame;
import roj.kscript.ast.Node;
import roj.kscript.ast.NodeUtil;
import roj.kscript.type.KType;
import roj.kscript.util.opm.SWEntry;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/4/28 18:47
 */
public class SwitchMap extends MyHashMap<KType, Node> {
    public SwitchMap() {
        super(8);
    }

    public SwitchMap(int initCap) {
        super(initCap);
    }

    @Override
    protected Entry<KType, Node> createEntry(KType id) {
        return new SWEntry(id, null);
    }

    public void stripLabels() {
        Entry<?, ?>[] entries = this.entries;
        if (entries == null)
            return;
        for (int i = 0; i < length; i++) {
            SWEntry entry = (SWEntry) entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                entry.v = entry.v.next;
                entry = (SWEntry) entry.next;
            }
        }
    }

    public void genDiff(List<Unioner.Wrap<Variable>> self, Unioner<Unioner.Wrap<Variable>> var, IntBiMap<Node> idx) {
        Entry<?, ?>[] entries = this.entries;
        if (entries == null)
            return;
        for (int i = 0; i < length; i++) {
            SWEntry entry = (SWEntry) entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                List<Unioner.Wrap<Variable>> dest = var.i_collect(idx.getInt(entry.v));
                if(dest != self) {
                    entry.diff = NodeUtil.calcDiff(self, dest);
                }
                entry = (SWEntry) entry.next;
            }
        }
    }

    public Node getAndApply(Frame frame, Node def, VInfo diff) {
        KType zero = frame.pop();
        SWEntry entry = (SWEntry) getEntryFirst(zero, false);
        while (entry != null) {
            if (zero.equalsTo(entry.k)) {
                frame.applyDiff(entry.diff);
                return entry.v;
            }
            entry = (SWEntry) entry.next;
        }

        frame.applyDiff(diff);
        return def;
    }
}
