package roj.compiler.ast;

import roj.collect.RingBuffer;
import roj.collect.ToIntMap;
import roj.compiler.ast.block.VarDefNode;
import roj.lavac.block.Node;
import roj.util.Helpers;

import java.util.function.IntFunction;

/**
 * @author Roj234
 * @since 2023/11/4 0004 17:38
 */
public class AST {
	private static void register(Class<? extends Node> type) { nodeTypes.putInt(type, nodeTypes.size()); }
	private static final ToIntMap<Class<? extends Node>> nodeTypes = new ToIntMap<>();
	private static IntFunction<Node> nodeGenerator;

	static {
		register(VarDefNode.class);
	}

	private static final ThreadLocal<RingBuffer<Node>[]> TLB = new ThreadLocal<>();
	private static RingBuffer<Node>[] buf() {
		RingBuffer<Node>[] b = TLB.get();
		if (b != null) return b;
		b = Helpers.cast(new RingBuffer<?>[nodeTypes.size()]);
		for (int i = 0; i < b.length; i++) {
			b[i] = new RingBuffer<>(10, 64);
		}
		TLB.set(b);
		return b;
	}
	public static void free(RingBuffer<Node>[] buf, Node node) {
		int slot = nodeTypes.getInt(node.getClass());
		buf[slot].ringAddLast(node);
		node.free(buf);
	}
	public static <T extends Node> T get(RingBuffer<Node>[] buf, Class<T> type) {
		int slot = nodeTypes.getInt(type);
		Node node = buf[slot].pollFirst();
		if (node == null) node = nodeGenerator.apply(slot);
		return Helpers.cast(node);
	}

	Node head;
	public void accept(NodeVisitor nv) {

	}
}
