package roj.config.serial;

import roj.collect.ArrayList;
import roj.collect.XashMap;
import roj.config.data.CEntry;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 访问者模式的TreeQuery，性能也许不会很好？还有些方法没写
 * @author Roj234
 * @since 2025/4/25 23:06
 */
public class TreeQuery implements CVisitor {
	private static class PathNode {
		private static final XashMap.Builder<Object, PathNode> BUILDER = XashMap.builder(Object.class, PathNode.class, "key", "_next");

		final Object key;
		private PathNode _next;

		final XashMap<Object, PathNode> children = BUILDER.createSized(2);

		CVisitor visitor;

		PathNode(Object key) {this.key = key;}
		boolean isLeaf() {return visitor != null;}
	}

	private final PathNode root = new PathNode(null);
	private final ArrayList<PathNode> nodeStack = new ArrayList<>();
	private final ArrayList<Object> contextStack = new ArrayList<>();
	private PathNode node = root;

	private CVisitor visitor;
	private int depth;

	private String key;
	private int listSize = -1;

	public TreeQuery() {}
	@Override
	public CVisitor reset() {
		nodeStack.clear();
		contextStack.clear();
		node = root;
		visitor = root.visitor;
		depth = 0;
		key = null;
		listSize = -1;
		return this;
	}

	/**
	 * format: $.expr[arr].expr这样的格式
	 * 和{@link CEntry#query(CharSequence)}格式相同，暂不支持""和通配符
	 * @param sql
	 * @param visitor
	 */
	public void query(String sql, CVisitor visitor) {
		var node = root;
		for (Object path : splitPathExpr(sql)) {
			node = node.children.computeIfAbsent(path);
			if (node.isLeaf()) throw new IllegalArgumentException("路径之间不能存在交集！");
		}
		if (!node.children.isEmpty()) throw new IllegalArgumentException("路径之间不能存在交集！");

		if (node.visitor != null) throw new IllegalArgumentException("路径表达式已存在！");
		node.visitor = visitor;
	}

	private static final Pattern PATHEXPR = Pattern.compile("\\.([^\\[\\]]+)|\\[(\\d+)]");
	private static List<Object> splitPathExpr(String pathExpr) {
		if (!pathExpr.startsWith("$")) throw new IllegalArgumentException("Path must start with $");

		List<Object> segments = new java.util.ArrayList<>();
		Matcher matcher = PATHEXPR.matcher(pathExpr);
		while (matcher.find()) {
			if (matcher.group(1) != null) {
				segments.add(matcher.group(1));
			} else if (matcher.group(2) != null) {
				segments.add(Integer.parseInt(matcher.group(2)));
			} else {
				assert false;
			}
		}
		return segments;
	}

	@Override
	public void valueMap() {
		if (visitor == null) push(false);
		if (visitor != null) {
			visitor.valueMap();
			depth++;
		}
	}

	@Override
	public void key(String key) {
		this.key = key;
		if (visitor != null) {
			if (depth == 0) nextVisitor();
			if (visitor != null) visitor.key(key);
		}
	}

	@Override
	public void valueList() {
		if (visitor == null) push(true);
		if (visitor != null) {
			visitor.valueList();
			depth++;
		}
	}

	@Override
	public void pop() {
		if (visitor != null) {
			visitor.pop();
			if (--depth > 0) return;
		}

		visitor = null;
		popContext();
	}

	private void push(boolean isList) {
		nextVisitor();

		contextStack.add(listSize < 0 ? key : listSize);
		nodeStack.add(node);
		listSize = isList ? 0 : -1;
	}

	private void popContext() {
		node = nodeStack.pop();
		var ctx = contextStack.pop();
		if (ctx instanceof Integer) {
			listSize = (int) ctx;
		} else {
			listSize = -1;
			key = (String) ctx;
		}
	}

	private void nextVisitor() {
		var parent = nodeStack.getLast();
		if (parent == null) return;

		node = listSize >= 0 ? parent.children.get(listSize++) : parent.children.get(key);
		visitor = node != null ? node.visitor : null;
	}

	// 以下为CVisitor接口方法的转发实现，仅展示部分示例
	@Override
	public void value(boolean b) {
		if (visitor == null) nextVisitor();
		if (visitor != null) visitor.value(b);
	}
	@Override
	public void value(int i) {
		if (visitor == null) nextVisitor();
		if (visitor != null) visitor.value(i);
	}
	@Override
	public void value(long i) {
		if (visitor == null) nextVisitor();
		if (visitor != null) visitor.value(i);
	}
	@Override
	public void value(float i) {
		if (visitor == null) nextVisitor();
		if (visitor != null) visitor.value(i);
	}
	@Override
	public void value(double i) {
		if (visitor == null) nextVisitor();
		if (visitor != null) visitor.value(i);
	}
	@Override
	public void value(String s) {
		if (visitor == null) nextVisitor();
		if (visitor != null) visitor.value(s);
	}
	@Override
	public void valueNull() {
		if (visitor == null) nextVisitor();
		if (visitor != null) visitor.valueNull();
	}
	// 其他方法类似，需判断activeVisitor并转发调用
	// 如value(byte), value(short), value(char)等

	@Override
	public void close() {

	}
}