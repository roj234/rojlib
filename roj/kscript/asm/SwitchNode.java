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
package roj.kscript.asm;

import roj.collect.IntBiMap;
import roj.collect.Unioner;
import roj.kscript.util.SwitchMap;
import roj.kscript.util.VInfo;
import roj.kscript.util.Variable;
import roj.util.Helpers;

import java.util.List;

/**
 * @author Roj234
 * @since  2020/9/27 23:02
 */
public final class SwitchNode extends Node {
    public Node def;
    private VInfo diff;
    private final SwitchMap map;

    public SwitchNode(Node def, SwitchMap map) {
        this.def = def;
        this.map = Helpers.cast(map);
    }

    @Override
    public Opcode getCode() {
        return Opcode.SWITCH;
    }

    @Override
    protected void compile() {
        if(def.getClass() != LabelNode.class)
            return;
        def = def.next;
        map.stripLabels();
    }

    @Override
    protected void genDiff(Unioner<Unioner.Wrap<Variable>> var, IntBiMap<Node> idx) {
        List<Unioner.Wrap<Variable>> self = var.collect(idx.getInt(this)),
            dest = var.collect(idx.getInt(def));
        if(self != dest) {
            diff = NodeUtil.calcDiff(self, dest);
        }
        map.genDiff(self, var, idx);
    }

    @Override
    public Node exec(Frame frame) {
        return map.getAndApply(frame, def, diff);
    }

    @Override
    public String toString() {
        return "Switch " + map + " or: " + def;
    }
}
