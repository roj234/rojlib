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

import roj.collect.IntBiMap;
import roj.collect.Unioner;
import roj.kscript.util.VInfo;
import roj.kscript.util.Variable;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/27 18:50
 */
public class GotoNode extends Node {
    Node target;
    VInfo diff;

    public GotoNode(LabelNode label) {
        this.target = label;
    }

    @Override
    public Opcode getCode() {
        return Opcode.GOTO;
    }

    @Override
    protected void compile() {
        if(target.getClass() == LabelNode.class) {
            target = target.next;
            if(target instanceof VarGNode && ((VarGNode) target).name instanceof Node) {
                target = (Node) ((VarGNode) target).name;
            } else
            if(target instanceof IncrNode && ((IncrNode) target).name instanceof Node) {
                target = (Node) ((IncrNode) target).name;
            }
        }
    }

    @Override
    protected void genDiff(Unioner<Unioner.Wrap<Variable>> var, IntBiMap<Node> idx) {
        List<Unioner.Wrap<Variable>> self = var._collect_modifiable_int_(idx.getByValue(this)),
            dest = var._collect_modifiable_int_(idx.getByValue(target));
        if(self != dest) {
            diff = NodeUtil.calcDiff(self, dest);
        }
    }

    @Override
    public Node exec(Frame frame) {
        frame.applyDiff(diff);
        return target;
    }

    @Override
    public String toString() {
        return "Goto " + target;
    }

    public void checkNext(Node prev) {
        if(target == next)
            prev.next = target;
    }
}
