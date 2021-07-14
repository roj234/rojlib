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
package roj.kscript.ast;

import roj.collect.MyHashSet;
import roj.collect.Unioner;
import roj.kscript.util.VInfo;
import roj.kscript.util.Variable;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/4/28 18:32
 */
public class NodeUtil {
    public static VInfo calcDiff(List<Unioner.Wrap<Variable>> self, List<Unioner.Wrap<Variable>> dest) {
        MyHashSet<Variable> cvHas = new MyHashSet<>(self.size());
        VInfo root = new VInfo(), curr = root;
        // Pass 0
        for (int i = 0; i < self.size(); i++) {
            Unioner.Wrap<Variable> wrap = self.get(i);
            cvHas.add(wrap.sth);
        }
        // Pass 1: check dest.add
        for (int i = 0; i < dest.size(); i++) {
            Unioner.Wrap<Variable> wrap = dest.get(i);
            if(!cvHas.remove(wrap.sth)) {
                // append variable
                curr.id = wrap.sth.index;
                curr.v = wrap.sth.def;
                curr.next = new VInfo();
                curr = curr.next;
            //} else {
            //     duplicate variable, pass
            }
        }
        // Pass 2: check dest.remove
        for (Variable var : cvHas) {
            curr.id = var.index;
            curr.next = new VInfo();
            curr = curr.next;
        }
        // Pass 3: remove useless curr
        if(curr == root)
            return null;
        VInfo end = curr;
        curr = root;
        while (true) {
            if(curr.next == end) {
                curr.next = null;
                break;
            }
            curr = curr.next;
        }

        return root;
    }
}
