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
package ilib.util.energy;

import roj.collect.IntBiMap;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/19 20:00
 */
public class UnionFindSet {
    public static class Node {
        public int parent = -1, value;
        final Object identifier;

        public Node(Object identifier) {
            this.identifier = identifier;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Node node = (Node) o;

            return identifier.equals(node.identifier);
        }

        @Override
        public int hashCode() {
            return identifier.hashCode();
        }
    }

    IntBiMap<Node> nodes = new IntBiMap<>();

    public void addNode(Node self) {
        nodes.put(nodes.size(), self);
    }

    public Node find(Node x) {
        Node parent = nodes.get(x.parent);
        if (parent == null)
            return x;

        if (x.parent != parent.parent) {
            parent = find(parent);
            x.parent = parent.parent;
            x.value += parent.value;
        }
        return parent;
    }

    public void merge(Node a, Node b) {
        Node px = find(a);
        Node py = find(b);
        if (px != py) {
            px.parent = nodes.getInt(py);
        }
    }
}
