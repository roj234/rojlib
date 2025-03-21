package roj.asm.frame;

import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.Flow;
import roj.io.FastFailException;
import roj.util.ArrayUtil;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:55
 */
public class BasicBlock {
	public int bci;
	private String desc;
	private final MyHashSet<BasicBlock> successor = new MyHashSet<>();
	public boolean noFrame;

	Var2[]
		inStack = new Var2[8],
		outStack = new Var2[8],
		inLocal = new Var2[8],
		outLocal = new Var2[8];
	int inStackSize, outStackSize, inLocalSize, outLocalSize;

	int maxStackSize;

	Var2[] startLocal, startStack;
	int startLocalSize, startStackSize;
	Var2[] endLocal, endStack;
	int endLocalSize, endStackSize;
	boolean reachable;

	int refCount;

	public BasicBlock(int bci, String desc) {
		this.bci = bci;
		this.desc = desc;
	}

	public void to(BasicBlock fs) {
		// add successor (previous block)
		successor.add(fs);
	}

	public void merge(String desc, boolean noFrame) {
		this.desc = this.desc + " | " + desc;
		if (!noFrame) this.noFrame = false;
		refCount++;
	}

	public void ensureCapacity(int maxStackSize, int maxLocalSize, Var2[] initLocal) {
		startLocal = Frame.NONE;
		startLocalSize = 0;
		startStack = Frame.NONE;
		startStackSize = 0;

		if (bci == 0) {
			reachable = true;

			startLocal = Arrays.copyOf(initLocal, initLocal.length);
			startLocalSize = initLocal.length;

			endLocal = new Var2[initLocal.length];
			combineLocal(initLocal, maxLocalSize);
			if (inStackSize != 0) throw new IllegalStateException("Illegal first block: "+this);
			endStack = Arrays.copyOf(outStack, outStackSize);
			endStackSize = outStackSize;
		}
	}
	public void traverse(SimpleList<BasicBlock> route, int maxDepth) {
		if (route.contains(this)) return;
		route.add(this);

		maxDepth--;
		for (BasicBlock block : successor) {
			try {
				block.comeFrom(this);
				if (maxDepth > 0) block.traverse(route, maxDepth);
			} catch (Exception e) {
				throw new IllegalStateException(block.toString(), e);
			}
		}

		route.remove(route.size()-1);
	}

	private void combineLocal(Var2[] local, int localSize) {
		if (startLocal.length < local.length) {
			startLocal = Arrays.copyOf(startLocal, local.length);
			startLocalSize = startLocal.length;
		}

		uncombineIO(local, startLocal);

		int endLocalSize1 = Math.max(inLocalSize, outLocalSize);
		endLocalSize1 = Math.max(localSize, endLocalSize1);
		var endLocal1 = Arrays.copyOf(startLocal, endLocalSize1);

		// 限制local必须符合要求
		for (int i = 0; i < inLocalSize; i++) {
			Var2 combined = endLocal1[i];
			Var2 usedType = inLocal[i];
			if (combined == null) {
				//System.out.println("Error missing input type");
				endLocal1[i] = usedType;
			} else if (usedType != null) {
				Var2 copy = combined.copy();
				//endLocal1[i] = usedType;
				copy.verify(usedType);
			}
		}
		for (int i = 0; i < outLocalSize; i++) {
			Var2 usedType = outLocal[i];
			if (usedType != null) {
				var prev = endLocal1[i];
				endLocal1[i] = usedType;

				if (prev != null && (prev.type == Var2.T_LONG || prev.type == Var2.T_DOUBLE)) {
					endLocal1[i+1] = null;
				}
			}
		}

		if (endLocal == null) {
			endLocal = endLocal1;
		} else {
			if (endLocal.length < endLocal1.length) endLocal = Arrays.copyOf(endLocal, endLocal1.length);
			uncombineIO(endLocal1, endLocal);
		}
		endLocalSize = endLocal.length;
	}

	private static void uncombineIO(Var2[] updated, Var2[] storage) {
		for (int i = 0; i < updated.length; i++) {
			Var2 item = storage[i];
			if (updated[i] != null) {
				if (item == null) storage[i] = updated[i];
				else {
					Var2 uncombine = item.copy().uncombine(updated[i]);
					if (uncombine != null)
						storage[i] = uncombine;
				}
			} else {
				storage[i] = Var2.TOP;
			}
		}
		for (int i = updated.length; i < storage.length; i++) {
			storage[i] = Var2.TOP;
		}
	}

	private void comeFrom(BasicBlock parent) {
		startStack = parent.endStack;
		startStackSize = parent.endStackSize;
		combineLocal(parent.endLocal, parent.endLocalSize);

		Var2[] 前驱节点的栈 = parent.endStack;
		int parentSize = parent.endStackSize;
		Var2[] 我用掉的栈 = inStack;
		if (parentSize < inStackSize) throw new FastFailException("Attempt to pop empty stack.");
		for (int i = 0; i < inStackSize; i++) {
			Var2 item = 前驱节点的栈[--parentSize].copy();
			item.verify(我用掉的栈[i]);
		}

		if (!reachable) {
			var stackOut = new Var2[parentSize + outStackSize];
			System.arraycopy(前驱节点的栈, 0, stackOut, 0, parentSize);
			System.arraycopy(outStack, 0, stackOut, parentSize, outStackSize);
			endStack = stackOut;
			endStackSize = stackOut.length;
		} else {
			for (int i = 0; i < Math.min(endStackSize, inStackSize); i++) {
				Var2 uncombine = endStack[i].uncombine(前驱节点的栈[i]);
				if (uncombine != null) endStack[i] = uncombine;
			}
		}

		reachable = true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append(desc).append(" #").append(bci);
		if (!reachable) sb.append("\n    ！不可达的代码！");
		sb.append("\n    后续: ").append(Flow.of(successor).map(x -> "#"+x.bci).toList());
		sb.append("\n    调试:");
		if (inLocalSize > 0) sb.append("\n     读变量").append(ArrayUtil.toString(inLocal, 0, inLocalSize));
		if (outLocalSize > 0) sb.append("\n     写变量").append(ArrayUtil.toString(outLocal, 0, outLocalSize));
		if (inStackSize > 0) sb.append("\n     出栈").append(ArrayUtil.toString(inStack, 0, inStackSize));
		if (outStackSize > 0) sb.append("\n     入栈").append(ArrayUtil.toString(outStack, 0, outStackSize));
		sb.append("\n    起始:");
		if (startLocalSize > 0) sb.append("\n     变量状态").append(ArrayUtil.toString(startLocal, 0, startLocalSize));
		if (startStackSize > 0) sb.append("\n       栈状态").append(ArrayUtil.toString(startStack, 0, startStackSize));
		sb.append("\n    结束:");
		if (endLocalSize > 0) sb.append("\n     变量状态").append(ArrayUtil.toString(endLocal, 0, endLocalSize));
		if (endStackSize > 0) sb.append("\n       栈状态").append(ArrayUtil.toString(endStack, 0, endStackSize));
		return sb.toString();
	}
}