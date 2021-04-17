package ilib.math;

import roj.collect.IntBiMap;

/**
 * @author Roj234
 * @since 2020/9/19 20:00
 */
public class UnionFindSet {
	public static class Node {
		public int parent = -1, depth;
		final Object v;

		public Node(Object v) {
			this.v = v;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Node node = (Node) o;

			return v.equals(node.v);
		}

		@Override
		public int hashCode() {
			return v.hashCode();
		}
	}

	IntBiMap<Node> nodes = new IntBiMap<>();

	public void addNode(Node self) {
		nodes.putInt(nodes.size(), self);
	}

	public Node find(Node x) {
		Node parent = nodes.get(x.parent);
		if (parent == null) return x;

		if (x.parent != parent.parent) {
			parent = find(parent);
			x.parent = parent.parent;
			x.depth += parent.depth;
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
