package roj.collect;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/5/14 16:04
 */
public class Graph<T> {
	final MyHashMap<T, EdgeList> edges = new MyHashMap<>();
	int edgeCount;
	static final class EdgeList {
		MyBitSet to = new MyBitSet();
		final int id;

		public EdgeList(int id) {
			this.id = id;
		}
	}

	protected SimpleList<T> elements = new SimpleList<>();
	protected MyBitSet empty = new MyBitSet();

	public boolean hasEdge(T from, T to) {
		return edges.get(from).to.contains(edges.get(to).id);
	}
	public void addEdge(T from, T to) {
		if (edges.get(from).to.add(edges.get(to).id)) {
			edgeCount++;
		}
	}
	public boolean removeEdge(T from, T to) {
		if (edges.get(from).to.remove(edges.get(to).id)) {
			edgeCount--;
			return true;
		}
		return false;
	}
	public void removeEdgeFrom(T from) {
		edges.get(from).to.clear();
	}
	public void removeEdgeTo(T to) {
		int id = edges.get(to).id;
		for (Map.Entry<T, EdgeList> entry : edges.entrySet()) {
			entry.getValue().to.remove(id);
		}
	}
	public int getEdgeCount() {
		return edgeCount;
	}

	public void addNode(T node) {
		if (edges.containsKey(node)) return;

		int free = empty.last();
		if (free < 0) {
			edges.put(node, new EdgeList(elements.size()));
			elements.add(node);
		} else {
			empty.remove(free);
			edges.put(node, new EdgeList(free));
			elements.set(free, node);
		}
	}
	public boolean removeNode(T node) {
		if (edges.containsKey(node)) return false;

		EdgeList m = edges.remove(node);
		if (m == null) return false;
		elements.set(m.id, null);
		empty.add(m.id);

		edgeCount -= m.to.size();

		for (EdgeList m1 : edges.values()) {
			m1.to.remove(m.id);
		}
		return true;
	}
	public int nodeCount() {
		return edges.size;
	}
	public int getId(T node) {
		EdgeList list = edges.get(node);
		return list==null?-1:list.id;
	}

	static class Edge {
		int from, to, value;
	}
	public Graph<T> trim() {
		return this;
/*
		int[] target, departure, value;
		int node_count = elements.size;

		UnionFindSet ufs;
		int[] union_set = new int[node_count];

		int[] depth = new int[node_count];

		int ei = 0;
		Edge[] edges = new Edge[edgeCount];
		for (EdgeList list : this.edges.values()) {
			for (IntIterator iterator = list.to.iterator(); iterator.hasNext(); ) {
				Integer v = iterator.next();

			}
		}
		for (int i = 0; i < edge_count; ++i) {
			edges[i].setValue(value[i], target[i], departure[i]);
		}
		merge_sort(edges, edge_count);

		int[] ret = new int[(node_count - 2) * 3];
		int ret_idx = 0;
		for (int i = 0; i < edgeCount; ++i) {
			int find_res_1 = find(union_set, edges[i].from);
			int find_res_2 = find(union_set, edges[i].to);
			if (find_res_1 != find_res_2) {
				if (depth[find_res_1] > depth[find_res_2]) {
					union_set[find_res_2] = find_res_1;
				} else {
					union_set[find_res_1] = find_res_2;
					if (depth[find_res_1] == depth[find_res_2])
						++depth[find_res_1];
				}
				ret[ret_idx++] = edges[i].from;
				ret[ret_idx++] = edges[i].to;
				ret[ret_idx++] = edges[i].value;
				if (ret_idx >= ret.length) break;
			}
		}
		return ret;*/
	}

	public Graph<T> reverse() {
		Graph<T> other = new Graph<>();
		if (empty.size() == 0) {
			other.elements.addAll(elements);
		} else {
			for (int i = 0; i < elements.size(); i++) {
				T t = elements.get(i);
				if (t != null) other.addNode(t);
			}
		}

		for (Map.Entry<T, EdgeList> entry : edges.entrySet()) {
			for (IntIterator itr = entry.getValue().to.iterator(); itr.hasNext(); ) {
				addEdge(elements.get(itr.nextInt()), entry.getKey());
			}
		}
		return other;
	}

	public void clear() {
		edges.clear();
		elements.clear();
		edgeCount = 0;
	}
}
