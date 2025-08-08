package roj.gui;

import roj.collect.HashMap;
import roj.util.TreeNodeImpl;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2024/1/17 18:37
 */
public class TreeBuilder<V> {
	HashMap<String, V> packages = new HashMap<>();

	public void add(String path, V value, boolean isDirectory) {
		int i = path.length();
		while ((i = path.lastIndexOf('/', i - 1)) >= 0) {
			packages.put(path.substring(0, i), value);
		}
		packages.put(path, value);
	}

	public Node<V> build(String rootName) {
		Node<V> root = new Node<>(rootName, 0);
		Node<V> node = root;

		Object[] array = packages.keySet().toArray();
		Arrays.sort(array);
		int level = 0;
		for (int i = 0; i < array.length; i++) {
			String o = array[i].toString();
			int myLevel = packageLevel(o);

			while (myLevel < level) {
				node = node.parent;
				level--;
			}
			boolean inserted = false;
			while (myLevel > level) {
				node = node.children.get(node.children.size() - 1);
				Node<V> next = new Node<>(o, o.lastIndexOf('/') + 1);
				next.value = packages.get(o);
				node.insert(next, node.getChildCount());
				level++;
				inserted = true;
			}

			if (!inserted) {
				Node<V> next = new Node<>(o, o.lastIndexOf('/') + 1);
				next.value = packages.get(o);
				node.insert(next, node.getChildCount());
			}
		}

		return root;
	}

	private static int packageLevel(String o) {
		int j = 0;
		for (int i = 0; i < o.length(); i++)
			if (o.charAt(i) == '/') j++;
		return j;
	}

	public static class Node<V> extends TreeNodeImpl<Node<V>> {
		public V value;
		public final String fullName;
		public final String name;

		public Node(String name, int pos) {
			this.name = name.substring(pos);
			this.fullName = name;
		}

		@Override
		public String toString() {return name;}
	}
}