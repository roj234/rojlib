package roj.asm.frame;

import roj.collect.BitSet;
import roj.collect.HashSet;
import roj.util.ArrayUtil;
import roj.util.FastFailException;
import roj.util.function.Flow;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/17 12:55
 */
public final class BasicBlock {
	public int bci;
	private String desc;
	private final HashSet<BasicBlock> successors = new HashSet<>();

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
		usedLocals = new Var2[8],
		assignedLocals = new Var2[8];
	int consumedStackSize, outStackSize, usedLocalCount, assignedLocalCount;

	// only for stack size compute
	int outStackInts, outStackIntsMax, consumedStackInts;

	// frame计算时状态
	Var2[] startLocals = Frame.NONE, startStack = Frame.NONE;
	Var2[] endLocals = Frame.NONE, endStack = Frame.NONE;
	boolean reachable;

	public BasicBlock(int bci, String desc) {
		this.bci = bci;
		this.desc = desc;
	}
	public void merge(String desc) {this.desc = this.desc+" | "+desc;}

	public void to(BasicBlock bb) {successors.add(bb);}

	public int computeMaxStackSize(HashSet<BasicBlock> visited, int entryHeight) {
		if (!visited.add(this)) return 0;

		int localMax = entryHeight - consumedStackInts + outStackIntsMax;
		int overallMax = localMax;

		int exitHeight = entryHeight - consumedStackInts + outStackInts;

		for (BasicBlock block : successors) {
			int succMax = block.computeMaxStackSize(visited, exitHeight);
			overallMax = Math.max(overallMax, succMax);
		}

		visited.remove(this);
		return overallMax;
	}

	public void initFirst(Var2[] initLocal) {
		assert bci == 0;
		reachable = true;

		if (consumedStackSize != 0) throw new IllegalStateException("Illegal first block: "+this);

		startLocals = Arrays.copyOf(initLocal, initLocal.length);
		combineLocal(initLocal);
		endStack = Arrays.copyOf(outStack, outStackSize);
	}
	public void traverse(HashSet<BasicBlock> visited) {
		if (!visited.add(this)) return;

		for (BasicBlock block : successors) {
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
		boolean isFirst = startLocals == Frame.NONE;
		if (startLocals.length < newStartLocal.length) startLocals = Arrays.copyOf(startLocals, newStartLocal.length);
		else if (startLocals == Frame.NONE) startLocals = NOT_FIRST;

		int endLocalCount = Math.max(Math.max(usedLocalCount, assignedLocalCount), startLocals.length);
		if (endLocals.length < endLocalCount) endLocals = Arrays.copyOf(endLocals, endLocalCount);
		var endLocal = this.endLocals;

		var changed = false;

		// 把起始状态互相合并
		for (int i = 0; i < newStartLocal.length; i++) {
			Var2 existing = startLocals[i];
			Var2 newType = newStartLocal[i];
			if (newType != null) {
				if (existing == null) {
					startLocals[i] = isFirst ? newType : Var2.TOP;
					changed = true;
				} else {
					Var2 change = existing.generalize(newType);
					if (change != null) {
						startLocals[i] = change;
						changed = true;
					}
				}
			} else {
				// 如果某一个来源没有赋值
				if (startLocals[i] != Var2.TOP) changed = true;
				startLocals[i] = Var2.TOP;
			}
		}
		// 如果某一个来源没有赋值
		for (int i = newStartLocal.length; i < startLocals.length; i++) {
			if (startLocals[i] != Var2.TOP) changed = true;
			startLocals[i] = Var2.TOP;
		}

		System.arraycopy(startLocals, 0, endLocal, 0, startLocals.length);

		// 进入时的变量必须符合该基本块用到的变量的类型
		for (int i = 0; i < usedLocalCount; i++) {
			Var2 except = usedLocals[i];
			if (except != null) {
				Var2 type = startLocals[i];
				if (type == null) {
					//可能是未完成遍历
				} else {
					type = type.copy();
					type.verify(except);
					startLocals[i] = type;
				}
				endLocal[i] = except;
			}
		}

		// 覆盖赋值过的变量
		for (int i = 0; i < assignedLocalCount; i++) {
			Var2 assigned = assignedLocals[i];
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
		combineLocal(block.endLocals);
		if (block.reassignedLocal != null) {
			// 这里省去了generalize的过程并直接赋值为TOP
			// 基于以下推导
			// 1. 若BasicBlock A存在至少一个重赋值，则它与之前的赋值类型至少有一次不同
			// 也就是说明显是TOP了，啊哈哈，不会有人去研究的
			for (var itr = block.reassignedLocal.iterator(); itr.hasNext(); ) {
				startLocals[itr.nextInt()] = Var2.TOP;
			}
		}
		combineLocal(block.startLocals);
	}

	private boolean comeFrom(BasicBlock parent) {
		var changed = combineLocal(parent.endLocals);

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
				throw new FastFailException("Inconsistent stack size "+startStack.length+" != "+parentEndCount+"\nFrom="+parent+"\nTo="+this);

			for (int i = 0; i < parentEndState.length; i++) {
				Var2 change = startStack[i].generalize(parentEndState[i]);
				if (change != null) {
					if (change == Var2.TOP) throw new FastFailException("Cannot generalize "+startStack[i]+" and "+parentEndState[i]);

					startStack[i] = change;
					changed = true;
				}
			}

			for (int i = 0; i < parentEndCount; i++) {
				Var2 change = endStack[i].generalize(parentEndState[i]);
				if (change != null) {
					if (change == Var2.TOP) throw new FastFailException("Cannot generalize "+endStack[i]+" and "+parentEndState[i]);

					endStack[i] = change;
					changed = true;
				}
			}

			return changed;
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append(desc).append(" @").append(bci);

		sb.append("\n ");
		if (bci == 0) sb.append(" ENTRY");
		if (isFrame) sb.append(" FRAME");
		if (!reachable) sb.append(" UNREACHABLE");
		if (startLocals == Frame.NONE) sb.append(" UNINITIALIZED");

		if (usedLocalCount > 0) sb.append("\n   Read").append(ArrayUtil.toString(usedLocals, 0, usedLocalCount));
		if (assignedLocalCount > 0) sb.append("\n   Write").append(ArrayUtil.toString(assignedLocals, 0, assignedLocalCount));
		if (consumedStackSize > 0) sb.append("\n   Pop").append(ArrayUtil.toString(consumedStack, 0, consumedStackSize));
		if (outStackSize > 0) sb.append("\n   Push").append(ArrayUtil.toString(outStack, 0, outStackSize));

		if (startLocals != Frame.NONE) {
			sb.append("\n  Enter:");
			if (startLocals.length > 0) sb.append("\n   Locals").append(Arrays.toString(startLocals));
			if (startStack.length > 0) sb.append("\n     Stack").append(Arrays.toString(startStack));
		}
		if (startLocals != Frame.NONE) {
			sb.append("\n  Exit:");
			if (endLocals.length > 0) sb.append("\n   Locals").append(Arrays.toString(endLocals));
			if (endStack.length > 0) sb.append("\n     Stack").append(Arrays.toString(endStack));
		}

		if (!successors.isEmpty()) sb.append("\n  Successors: ").append(Flow.of(successors).map(x -> "@"+x.bci).toList());
		return sb.toString();
	}
}