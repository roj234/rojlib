package roj.asm.frame;

import roj.collect.BitSet;
import roj.collect.HashSet;
import roj.util.function.Flow;
import roj.util.FastFailException;
import roj.util.ArrayUtil;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/17 12:55
 */
public class BasicBlock {
	public int bci;
	private String desc;
	private final HashSet<BasicBlock> successor = new HashSet<>();

	public boolean isFrame;

	// 异常处理器使用到的
	private BitSet reassignedLocal;
	public void reassignedLocal(int i) {
		if (reassignedLocal == null) reassignedLocal = new BitSet();
		reassignedLocal.add(i);
	}

	// 基本块内部状态
	Var2[]
		consumedStack = new Var2[2],
		outStack = new Var2[4],
		usedLocal = new Var2[8],
		assignedLocal = new Var2[8];
	int consumedStackSize, outStackSize, usedLocalCount, assignedLocalCount;

	// frame计算时状态
	Var2[] startLocal = Frame.NONE, startStack = Frame.NONE;
	Var2[] endLocal = Frame.NONE, endStack = Frame.NONE;
	boolean reachable;

	public BasicBlock(int bci, String desc) {
		this.bci = bci;
		this.desc = desc;
	}
	public void merge(String desc) {this.desc = this.desc+" | "+desc;}

	public void to(BasicBlock bb) {successor.add(bb);}

	public void initFirst(Var2[] initLocal) {
		assert bci == 0;
		reachable = true;

		if (consumedStackSize != 0) throw new IllegalStateException("Illegal first block: "+this);

		startLocal = Arrays.copyOf(initLocal, initLocal.length);
		combineLocal(initLocal);
		endStack = Arrays.copyOf(outStack, outStackSize);
	}
	public void traverse(HashSet<BasicBlock> visited) {
		if (!visited.add(this)) return;

		for (BasicBlock block : successor) {
			try {
				block.comeFrom(this);
				block.traverse(visited);
			} catch (Exception e) {
				throw new IllegalStateException(block.toString(), e);
			}
		}

		visited.remove(this);
	}

	private static final Var2[] NOT_FIRST = new Var2[0];
	private boolean combineLocal(Var2[] newStartLocal) {
		boolean isFirst = startLocal == Frame.NONE;
		if (startLocal.length < newStartLocal.length) startLocal = Arrays.copyOf(startLocal, newStartLocal.length);
		else if (startLocal == Frame.NONE) startLocal = NOT_FIRST;

		int endLocalCount = Math.max(Math.max(usedLocalCount, assignedLocalCount), startLocal.length);
		if (endLocal.length < endLocalCount) endLocal = Arrays.copyOf(endLocal, endLocalCount);
		var endLocal = this.endLocal;

		var changed = false;

		// 把起始状态互相合并
		for (int i = 0; i < newStartLocal.length; i++) {
			Var2 existing = startLocal[i];
			Var2 newType = newStartLocal[i];
			if (newType != null) {
				if (existing == null) {
					startLocal[i] = isFirst ? newType : Var2.TOP;
					changed = true;
				} else {
					Var2 change = existing.uncombine(newType);
					if (change != null) {
						startLocal[i] = change;
						changed = true;
					}
				}
			} else {
				// 如果某一个来源没有赋值
				if (startLocal[i] != Var2.TOP) changed = true;
				startLocal[i] = Var2.TOP;
			}
		}
		// 如果某一个来源没有赋值
		for (int i = newStartLocal.length; i < startLocal.length; i++) {
			if (startLocal[i] != Var2.TOP) changed = true;
			startLocal[i] = Var2.TOP;
		}

		System.arraycopy(startLocal, 0, endLocal, 0, startLocal.length);

		// 进入时的变量必须符合该基本块用到的变量的类型
		for (int i = 0; i < usedLocalCount; i++) {
			Var2 except = usedLocal[i];
			if (except != null) {
				Var2 type = startLocal[i];
				if (type == null) {
					//可能是未完成遍历
					endLocal[i] = except;
				} else {
					type = type.copy();
					type.verify(except);
					startLocal[i] = type;
				}
			}
		}

		// 覆盖赋值过的变量
		for (int i = 0; i < assignedLocalCount; i++) {
			Var2 assigned = assignedLocal[i];
			if (assigned != null) {
				var prev = endLocal[i];
				endLocal[i] = assigned;

				// 如果usedType是long或double的话，assignedLocal[i+1]不会为null
				if (prev != null && (prev.type == Var2.T_LONG || prev.type == Var2.T_DOUBLE)) {
					endLocal[i+1] = null;
				}
			}
		}

		return changed;
	}

	public void combineExceptionLocals(BasicBlock block) {
		combineLocal(block.endLocal);
		if (block.reassignedLocal != null) {
			// 这里省去了uncombine的过程并直接赋值为TOP
			// 基于以下推导
			// 1. 若BasicBlock A存在至少一个重赋值，则它与之前的赋值类型至少有一次不同
			// 也就是说明显是TOP了，啊哈哈，不会有人去研究的
			for (var itr = block.reassignedLocal.iterator(); itr.hasNext(); ) {
				startLocal[itr.nextInt()] = Var2.TOP;
			}
		}
		combineLocal(block.startLocal);
	}

	private boolean comeFrom(BasicBlock parent) {
		var changed = combineLocal(parent.endLocal);

		Var2[] parentEndState = parent.endStack;
		int parentEndCount = parentEndState.length;

		Var2[] consumed = consumedStack;
		if (parentEndCount < consumedStackSize) throw new FastFailException("Attempt to pop empty stack.");
		for (int i = 0; i < consumedStackSize; i++) {
			Var2 except = parentEndState[--parentEndCount];
			Var2 current = consumed[i];
			if (current.bci() != 0) except.bci = current.bci();
			// todo REVERSE??
			except.verify(current);
		}

		// first time
		if (!reachable) {
			reachable = true;

			startStack = Arrays.copyOf(parentEndState, parentEndState.length);

			var stackOut = new Var2[parentEndCount + outStackSize];
			System.arraycopy(parentEndState, 0, stackOut, 0, parentEndCount);
			System.arraycopy(outStack, 0, stackOut, parentEndCount, outStackSize);
			endStack = stackOut;

			return true;
		} else {
			if (startStack.length != parentEndState.length)
				throw new FastFailException("Stack size not match between "+parent+" and initial source\nLen1="+startStack.length+", Len2="+parentEndCount+"\n\nSelf="+this);

			for (int i = 0; i < parentEndState.length; i++) {
				Var2 change = startStack[i].uncombine(parentEndState[i]);
				if (change != null) {
					if (change == Var2.TOP) throw new FastFailException("Cannot commonify "+startStack[i]+" and "+parentEndState[i]);

					startStack[i] = change;
					changed = true;
				}
			}

			for (int i = 0; i < parentEndCount; i++) {
				Var2 change = endStack[i].uncombine(parentEndState[i]);
				if (change != null) {
					if (change == Var2.TOP) throw new FastFailException("Cannot commonify "+endStack[i]+" and "+parentEndState[i]);

					endStack[i] = change;
					changed = true;
				}
			}

			return changed;
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append(desc).append(" #").append(bci);
		if (!reachable) sb.append("\n    ！不可达的代码！");
		sb.append("\n    后续: ").append(Flow.of(successor).map(x -> "#"+x.bci).toList());
		sb.append("\n    调试:");
		if (usedLocalCount > 0) sb.append("\n     读变量").append(ArrayUtil.toString(usedLocal, 0, usedLocalCount));
		if (assignedLocalCount > 0) sb.append("\n     写变量").append(ArrayUtil.toString(assignedLocal, 0, assignedLocalCount));
		if (consumedStackSize > 0) sb.append("\n     出栈").append(ArrayUtil.toString(consumedStack, 0, consumedStackSize));
		if (outStackSize > 0) sb.append("\n     入栈").append(ArrayUtil.toString(outStack, 0, outStackSize));
		sb.append("\n    起始:");
		if (startLocal.length > 0) sb.append("\n     变量状态").append(Arrays.toString(startLocal));
		if (startStack.length > 0) sb.append("\n       栈状态").append(Arrays.toString(startStack));
		sb.append("\n    结束:");
		if (endLocal.length > 0) sb.append("\n     变量状态").append(Arrays.toString(endLocal));
		if (endStack.length > 0) sb.append("\n       栈状态").append(Arrays.toString(endStack));
		return sb.toString();
	}
}