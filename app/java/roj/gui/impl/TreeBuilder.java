package roj.gui.impl;

import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.text.TextUtil;
import roj.util.TreeNodeImpl;

import java.util.Collections;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/1/17 18:37
 */
public class TreeBuilder<V> {
	private final Node<V> root;

	public TreeBuilder() {
		// 初始化根节点
		this.root = new Node<>("", "");
	}

	/**
	 * 添加路径到树中
	 * @param path 路径，例如 "a/b/c"
	 * @param value 关联的值
	 */
	public void add(String path, V value, boolean isDirectory) {
		if (path == null || path.isEmpty()) return;

		var parts = TextUtil.split(path, "/");
		Node<V> current = root;
		StringBuilder currentFullPath = new StringBuilder();

		for (String part : parts) {
			if (currentFullPath.length() > 0) {
				currentFullPath.append("/");
			}
			currentFullPath.append(part);

			Node<V> next = current.childrenMap.get(part);
			if (next == null) {
				if (current.childrenMap.isEmpty()) {
					current.childrenMap = new HashMap<>();
					current.children = new ArrayList<>();
				}
				current.childrenMap.put(part, next = new Node<>(part, currentFullPath.toString()));
				current.children.add(next);
			}

			current = next;
		}
		current.value = value;
	}

	public Node<V> build(String rootName) {
		root.name = root.fullName = rootName;
		return root;
	}

	public static class Node<V> extends TreeNodeImpl<Node<V>> {
		public V value;
		public String fullName, name;

		public Node(String name, String fullName) {
			this.name = name;
			this.fullName = fullName;
		}

		private Map<String, Node<V>> childrenMap = Collections.emptyMap();

		public Map<String, Node<V>> getChildrenMap() {return childrenMap;}

		@Override
		public String toString() {return name;}
	}
}