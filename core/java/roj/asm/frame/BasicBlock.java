package roj.asm.frame;

import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.util.FastFailException;
import roj.util.function.Flow;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
/**
 * 表示一个基本块，是控制流图 (CFG) 中的一个节点。
 * 每个基本块包含一系列连续执行的字节码指令，没有中间的控制流转移。
 * <p>
 * 此类也存储了在进入和退出时（例如，局部变量和操作数栈的状态）与帧相关的信息，
 * 使得它能够被用于各种静态分析，如栈大小计算和帧的生成。
 * <p>
 * 属性包括：
 * <ul>
 *     <li>{@code bci}: 基本块的起始字节码偏移量（BCI）。</li>
 *     <li>{@code desc}: 基本块的描述，通常用于调试。</li>
 *     <li>{@code successors}: 指向前导基本块的列表。</li>
 *     <li>{@code isFrame}: 指示此基本块是否需要生成StackMapTable帧。</li>
 *     <li>{@code enterLocals}, {@code enterStack}: 进入基本块时的局部变量和操作数栈状态。</li>
 *     <li>{@code exitLocals}, {@code exitStack}: 离开基本块时的局部变量和操作数栈状态。</li>
 *     <li>{@code reassignedLocal}: 一个 {@link BitSet}，记录在此基本块中哪些局部变量被重新赋值。</li>
 *     <li>{@code reachable}: 指示此基本块是否可以从方法入口处到达。</li>
 * </ul>
 * @author Roj234
 * @since 2022/11/17 12:55
 */
public final class BasicBlock {
	private static final BitSet EMPTY = new BitSet();

	/**
	 * 基本块的起始字节码偏移量（BCI）。
	 */
	public int pc;
	/**
	 * 描述，用于调试，例如"if fail"
	 */
	private String desc;
	/**
	 * 指向前导基本块的列表，表示控制流的下一个可能路径。
	 * 初始化容量为2，以适应常见的分支场景(average 2.6 successors)。
	 */
	Collection<BasicBlock> successors = new ArrayList<>(2);

	/**
	 * 指示此基本块是否是StackMapTable中的帧位置。
	 * 如果为 {@code true}，则需要为该基本块的入口生成帧信息。
	 */
	public boolean isFrame;

	/**
	 * 指示此基本块是否可以从方法入口处到达。
	 */
	boolean reachable;
	/**
	 * 此基本块结束时推入操作数栈的总空间。
	 */
	int pushedStackSpace,
	/**
	 * 在此基本块中可能推入的最大操作数栈空间。
	 */
	pushedStackSpaceMax,
	/**
	 * 从(incoming)操作数栈中弹出的总空间。
	 */
	poppedStackSpace;

	// frame计算时状态
	Var2[] enterLocals = Frame.NONE, enterStack = Frame.NONE;
	Var2[] exitLocals = Frame.NONE, exitStack = Frame.NONE;
	BitSet reassignedLocal = EMPTY;

	public BasicBlock(int pc, String desc) {
		this.pc = pc;
		this.desc = desc;
	}
	/**
	 * 将此基本块的描述合并到现有的描述字段中。
	 * 用于将多个描述（例如，来自不同控制流路径）合并成一个。
	 *
	 * @param desc 要合并的新描述。
	 */
	public void combine(String desc) {this.desc = this.desc+" | "+desc;}

	/**
	 * 添加一个后继基本块。
	 * 如果该后继块已存在，则不进行任何操作。
	 *
	 * @param block 要添加的后继基本块。
	 */
	public void to(BasicBlock block) {
		if (!successors.contains(block)) {
			if (successors.size() == 8)
				successors = new LinkedHashSet<>(successors);
			successors.add(block);
		}
	}

	/**
	 * 记录一个局部变量索引被重新赋值。
	 * 在异常处理器处，局部变量的状态需要特殊处理。
	 *
	 * @param i 被重新赋值的局部变量的索引。
	 */
	public void reassignedLocal(int i) {
		if (reassignedLocal == EMPTY) reassignedLocal = new BitSet();
		reassignedLocal.add(i);
	}

	/**
	 * 入口状态合并自另一个基本块。
	 * 主要用于合并来自正常控制流路径的分析结果。
	 *
	 * @param incoming 要合并的源基本块。
	 * @return 如果状态发生改变，则返回 {@code true}；否则返回 {@code false}。
	 */
	public boolean merge(BasicBlock incoming) {return mergeLocals(incoming.exitLocals) | mergeStack(incoming);}

	/**
	 * 局部变量合并自另一个基本块。
	 * 用于异常处理器。
	 *
	 * @param incoming 要合并的源基本块。
	 * @return 如果状态发生改变，则返回 {@code true}；否则返回 {@code false}。
	 */
	public boolean mergeException(BasicBlock incoming) {
		var changed = mergeLocals(incoming.exitLocals);
		if (incoming.reassignedLocal != null) {
			// 若Block A存在至少一个重赋值，则它与之前的赋值类型至少有一次不同
			// see FrameVisitor#set
			// 也就是说最终合并结果只能是TOP
			for (var itr = incoming.reassignedLocal.iterator(); itr.hasNext(); ) {
				enterLocals[itr.nextInt()] = Var2.TOP;
			}
		}
		return mergeLocals(incoming.enterLocals) | changed;
	}

	private boolean mergeLocals(Var2[] incomingLocals) {
		if (enterLocals == Frame.NONE) {
			enterLocals = incomingLocals.clone();
			return true;
		}

		int incomingLocalSize = incomingLocals.length;
		if (enterLocals.length < incomingLocalSize)
			enterLocals = Arrays.copyOf(enterLocals, incomingLocalSize);

		var changed = false;
		for (int i = 0; i < incomingLocalSize; i++) {
			Var2 enterLocal = enterLocals[i];
			if (enterLocal == null) enterLocal = Var2.TOP;

			Var2 incomingLocal = incomingLocals[i];
			if (incomingLocal == null) incomingLocal = Var2.TOP;

			if (enterLocal == incomingLocal) continue;

			Var2 change = enterLocal.join(incomingLocal);
			if (change != null) {
				enterLocals[i] = change;
				changed = true;
			}
		}
		for (int i = incomingLocalSize; i < enterLocals.length; i++) {
			if (enterLocals[i] == Var2.TOP) continue;
			enterLocals[i] = Var2.TOP;
			changed = true;
		}

		return changed;
	}

	private boolean mergeStack(BasicBlock incoming) {
		Var2[] incomingStack = incoming.exitStack;

		if (enterStack == Frame.NONE) {
			enterStack = incomingStack.clone();
			return true;
		}

		int incomingStackSize = incomingStack.length;
		if (enterStack.length != incomingStackSize)
			throw new FastFailException("Inconsistent stack size "+enterStack.length+" != "+incomingStackSize);

		var changed = false;
		for (int i = 0; i < incomingStackSize; i++) {
			Var2 change = enterStack[i].join(incomingStack[i]);
			if (change != null) {
				if (change == Var2.TOP) throw new FastFailException("Cannot join "+enterStack[i]+" and "+ incomingStack[i]);

				enterStack[i] = change;
				changed = true;
			}
		}
		return changed;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append(desc).append(" #").append(pc);

		sb.append("\n ");
		if (pc == 0) sb.append(" ENTRY");
		if (isFrame) sb.append(" FRAME");
		if (!reachable) sb.append(" UNREACHABLE");
		if (enterLocals == Frame.NONE) sb.append(" UNINITIALIZED");

		int exitStackHeight = -poppedStackSpace + pushedStackSpace;
		sb.append(" StackMax=").append(pushedStackSpaceMax).append(" ExitHeight=").append(exitStackHeight);

		if (enterLocals != Frame.NONE) {
			sb.append("\n  Enter:");
			if (enterLocals.length > 0) sb.append("\n   Locals").append(Arrays.toString(enterLocals));
			if (enterStack.length > 0) sb.append("\n     Stack").append(Arrays.toString(enterStack));
		}
		if (exitLocals != Frame.NONE) {
			sb.append("\n  Exit:");
			if (exitLocals.length > 0) sb.append("\n   Locals").append(Arrays.toString(exitLocals));
			if (exitStack.length > 0) sb.append("\n     Stack").append(Arrays.toString(exitStack));
		}

		if (!successors.isEmpty()) sb.append("\n  Successors: ").append(Flow.of(successors).map(x -> "#"+x.pc).toList());
		return sb.toString();
	}
}