package roj.asm.frame;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstDynamic;
import roj.asm.cp.CstNameAndType;
import roj.asm.cp.CstRef;
import roj.asm.insn.JumpTo;
import roj.asm.insn.Segment;
import roj.asm.insn.SwitchBlock;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.collect.IntMap;
import roj.collect.RingBuffer;
import roj.collect.ToIntMap;
import roj.util.DynByteBuf;
import roj.util.FastFailException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.asm.frame.Var2.T_REFERENCE;
import static roj.asm.frame.Var2.of;
/**
 * 一个逆向访问者类，用于计算Java方法的最大局部变量大小（maxLocalSize）和最大操作数栈深度（maxStackSize）。
 * 该类会解析方法的字节码，构建控制流图（CFG），并进行数据流分析来确定这些值。
 *
 * <p>主要功能包括：
 * <ul>
 *     <li>解析字节码指令，识别分支、跳转和方法调用。</li>
 *     <li>构建基本块（{@link BasicBlock}）和控制流图。</li>
 *     <li>计算局部变量和操作数栈的需求。</li>
 * </ul>
 * @see FrameVisitor
 * @author Roj234
 * @since 2025/9/15 21:11
 */
public class SizeVisitor {
	MethodNode method;

	public int maxStackSize, maxLocalSize;

	/**
	 * 已处理的基本块列表（按访问顺序）。
	 */
	protected final List<BasicBlock> processed = new ArrayList<>();
	/**
	 * 异常处理器信息列表，每三个元素为一组：起始块、结束块、处理器块。
	 */
	protected final List<BasicBlock> exceptionHandlers = new ArrayList<>();

	/**
	 * 按字节码偏移量索引的基本块映射。
	 */
	final IntMap<BasicBlock> byPos = new IntMap<>();
	/**
	 * 分支目标映射：键为分支指令的偏移量，值为目标基本块列表。
	 */
	private final IntMap<List<BasicBlock>> branchTargets = new IntMap<>();

	/**
	 * 当前正在处理的字节码偏移量（BCI）。
	 */
	int pc;
	BasicBlock current;
	/**
	 * 指示当前基本块是否已终止控制流（例如，遇到return、throw或goto指令）。
	 */
	private boolean controlFlowTerminated;

	final ArrayList<Type> tmpList = new ArrayList<>();

	/**
	 * 初始化处理状态，遍历代码中的所有分支目标（如跳转指令 {@code GOTO}、条件跳转 {@code IF_*}、{@code SWITCH} 等），
	 * 并为每个目标 BCI 创建或获取相应的 {@link BasicBlock}。
	 * 该方法在 {@code CodeWriter} 的第二阶段之前（satisfySegments）调用，此时代码已经确定，能使用绝对偏移。
	 *
	 * @param method 方法节点。
	 * @param blocks 方法中包含的 {@link Segment}（例如 {@link JumpTo} 或 {@link SwitchBlock}）列表。
	 */
	public void init(MethodNode method, List<Segment> blocks) {
		this.method = method;

		processed.clear();
		exceptionHandlers.clear();
		byPos.clear();
		branchTargets.clear();
		current = add(0, "begin", false);

		for (int i = 0; i < blocks.size(); i++) {
			Segment block = blocks.get(i);
			if (block instanceof JumpTo node) {
				List<BasicBlock> targets;

				BasicBlock jumpTarget = add(node.target.getValue(), "jump target", true);
				// normal execution
				if (node.code != GOTO && node.code != GOTO_W) {
					BasicBlock ifNext = add(node.bci()+3, "if fail", false);
					targets = Arrays.asList(jumpTarget, ifNext);
				} else {
					targets = Collections.singletonList(jumpTarget);
				}
				branchTargets.put(node.bci(), targets);
			} else if (block instanceof SwitchBlock node) {
				List<BasicBlock> targets = Arrays.asList(new BasicBlock[node.cases.size()+1]);

				targets.set(0, add(node.def.getValue(), "switch default", true));
				for (int j = 0; j < node.cases.size(); j++) {
					targets.set(j+1, add(node.cases.get(j).bci(), "switch branch", true));
				}
				branchTargets.put(node.bci(), targets);
			}
		}
	}
	/**
	 * 访问异常处理器条目，记录异常处理块和受保护范围。
	 * 它会创建并链接代表异常处理范围（startBci, endBci）和异常处理器（handlerBci）的基本块。
	 * 该方法在 {@code CodeWriter} 的第二阶段之前（satisfySegments）调用，此时代码已经确定，能使用绝对偏移。
	 *
	 * @param startBci   受保护代码块的起始偏移量
	 * @param endBci     受保护代码块的结束偏移量
	 * @param handlerBci 异常处理器的偏移量
	 * @param type     捕获的异常类型（全限定名），如果为 {@code null}，则表示捕获所有 {@code Throwable}。
	 */
	public final void visitException(int startBci, int endBci, int handlerBci, String type) {
		var handler = add(handlerBci, "exception["+startBci+','+endBci+"=>"+handlerBci+"].handler", true);

		if (handler.reachable) { // 已存在
			Var2 generalized = handler.enterStack[0].join(of(T_REFERENCE, type == null ? "java/lang/Throwable" : type));
			if (generalized != null) handler.enterStack[0] = generalized;
		} else {
			handler.reachable = true;
			handler.enterStack = new Var2[] {of(T_REFERENCE, type == null ? "java/lang/Throwable" : type)};
			handler.pushedStackSpace = handler.pushedStackSpaceMax = 1;
		}

		var start = add(startBci, "exception["+startBci+','+endBci+"=>"+handlerBci+"].start", false);
		var end = add(endBci, "exception["+startBci+','+endBci+"=>"+handlerBci+"].end", false);

		exceptionHandlers.add(start);
		exceptionHandlers.add(end);
		exceptionHandlers.add(handler);
	}
	/**
	 * 添加或获取指定位置的基本块。如果基本块已存在，则合并描述信息。
	 *
	 * @param pos     字节码偏移量
	 * @param desc    基本块描述 (仅用于debug)
	 * @param isFrame 是否作为栈帧位置（需要生成帧信息）
	 * @return 对应位置的基本块
	 */
	private BasicBlock add(int pos, String desc, boolean isFrame) {
		BasicBlock target = byPos.get(pos);
		if (target == null) byPos.put(pos, target = new BasicBlock(pos, desc));
		else target.combine(desc);
		if (isFrame) target.isFrame = true;
		return target;
	}

	/**
	 * 仅供调试
	 * @return 当前字节码偏移，仅出错时有意义
	 */
	public int bci() {return pc;}

	/**
	 * 计算方法的最大局部变量大小和最大操作数栈深度。
	 * 此方法是整个大小计算流程的入口。
	 *
	 * @param code  方法字节码
	 * @param cp    方法的常量池
	 * @param flags 指示是否需要生成StackMapTable帧。如果为 {@code false}，则不生成帧，该方法返回 {@code null}。
	 * @return 如果 {@code generateFrames} 为 {@code true}，则返回生成的 {@link Frame} 列表；否则返回 {@code null}。
	 * @throws FastFailException             如果字节码解析或数据流分析过程中发生严重错误。
	 * @throws UnsupportedOperationException 如果 {@code generateFrames} 为 {@code true}，因为此方法仅用于计算大小，不生成帧。
	 */
	@Nullable
	@Contract("_, _, false -> null")
	public List<Frame> finish(DynByteBuf code, ConstantPool cp, int flags) {
		if ((flags & ~FrameVisitor.COMPUTE_SIZES) != 0) throw new UnsupportedOperationException();

		maxLocalSize = TypeHelper.paramSize(method.rawDesc())+((method.modifier&ACC_STATIC) == 0 ? 1 : 0);

		current = null;
		controlFlowTerminated = true;

		int begin = code.rIndex;
		visitCode(code, cp, begin);
		if (!byPos.isEmpty()) throw new FastFailException("无效的偏移: "+byPos.values());

		maxStackSize = computeMaxStackSize(code, cp, begin);
		return null;
	}

	/**
	 * 使用工作列表算法（Worklist algorithm）来计算方法的最大操作数栈深度。
	 * 该算法迭代地处理基本块，直到数据流（栈状态）达到不动点，以此确定栈的大小。
	 */
	private int computeMaxStackSize(DynByteBuf code, ConstantPool cp, int begin) {
		Deque<BasicBlock> changeset = RingBuffer.unbounded();
		List<BasicBlock> blocks = processed;

		ToIntMap<BasicBlock> maxEntryHeights = new ToIntMap<>();

		var first = blocks.get(0);
		maxEntryHeights.put(first, 0);
		changeset.add(first);

		for (int i = 0; i < exceptionHandlers.size(); i += 3) {
			var handler = exceptionHandlers.get(i+2);
			maxEntryHeights.put(handler, 0);
			changeset.add(handler);
		}

		// 更新并找到不动点
		while (!changeset.isEmpty()) {
			var currentBlock = changeset.poll();

			int entryHeight = maxEntryHeights.getOrDefault(currentBlock, 0);
			int exitHeight = entryHeight - currentBlock.poppedStackSpace + currentBlock.pushedStackSpace;

			for (var successor : currentBlock.successors) {
				int oldSuccEntryHeight = maxEntryHeights.getOrDefault(successor, -1); // 用-1表示从未见过

				if (exitHeight > 0xFFFF) throw new FastFailException("堆栈不对齐 "+currentBlock);

				if (exitHeight > oldSuccEntryHeight) {
					maxEntryHeights.put(successor, exitHeight);

					if (!changeset.contains(successor)) {
						changeset.add(successor);
					}
				}
			}
		}

		int windex = code.wIndex();
		// trick 让#jump不抛出异常
		maxStackSize = -1;
		// 找出全局最大值
		int maxStackSize = 0;
		for (int i = 0; i < blocks.size(); i++) {
			var block = blocks.get(i);
			int entryHeight = maxEntryHeights.getOrDefault(block, 0);

			// 这个简单公式可能会多计算几个stack，但是性能会比下面的好
			// maxStackSize = Math.max(maxStackSize, entryHeight + block.pushedStackSpaceMax);

			block.pushedStackSpace = entryHeight;
			block.pushedStackSpaceMax = entryHeight;
			block.poppedStackSpace = 0;

			maxStackSize = Math.max(maxStackSize, entryHeight);

			code.rIndex = block.pc;
			code.wIndex(i == blocks.size() - 1 ? windex : begin + blocks.get(i+1).pc);

			current = block;
			controlFlowTerminated = false;

			visitCode(code, cp, begin);

			maxStackSize = Math.max(maxStackSize, block.pushedStackSpaceMax);
		}

		code.rIndex = begin;
		return maxStackSize;
	}

	// region Updating stack/locals & link BasicBlock
	private static final String STACK = "EFFFFFFFFGGFFFGGFFFFGFGFGFFFFFGGGGFFFFGGGGFFFFDEDEDDDDDCDCDDDDDCCCCDDDDCCCCDDDDBABABBBBDCFFFGGGEDCDCDCDCDCDCDCDCDCDCEEEEDDDDDDDCDCDCEFEFDDEEFFDEDEEEBDDBBDDDDDDCCCCCCCCEFEDDDCDCDEEEDDEEEEEFEEEDEEDDEDDDEF";

	/**
	 * 遍历字节码，解析指令并更新栈和局部变量的状态。
	 * 同时，根据指令类型（如跳转、分支）建立基本块之间的链接。
	 * @param rBegin 方法字节码的起始读取位置。
	 */
	protected final void visitCode(DynByteBuf r, ConstantPool cp, int rBegin) {
		byte prev = 0, code;
		while (r.isReadable()) {
			int bci = r.rIndex - rBegin;

			BasicBlock next = byPos.remove(bci);
			if (next != null) {
				// if_xxx
				if (!controlFlowTerminated) current.to(next);
				else controlFlowTerminated = false;
				current = next;
				current.reachable = true;
				processed.add(next);
			}
			if (controlFlowTerminated) {
				current = new BasicBlock(bci, Opcodes.toString(prev)+" #"+bci);
				controlFlowTerminated = false;
				processed.add(current);
			}

			this.pc = bci;
			code = Opcodes.validateOpcode(r.readByte());

			boolean widen = prev == Opcodes.WIDE;

			int stackDelta = STACK.charAt(code & 0xFF) - 'E';
			if (stackDelta != 0) stack(stackDelta);

			switch (code) {
				default -> {
					if (code >= ISTORE_0 && code <= ASTORE_3) {
						int slot = (code - ISTORE_0) & 3;
						code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
						maxLocalSize = Math.max(maxLocalSize, slot + 1 + (code&1));
						continue;
					}
				}

				case JSR, JSR_W, RET -> ret();

				case BIPUSH, LDC, NEWARRAY -> r.rIndex += 1;
				case SIPUSH, LDC_W, LDC2_W, NEW, CHECKCAST, INSTANCEOF, ANEWARRAY -> r.rIndex += 2;
				case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> r.rIndex += widen ? 2 : 1;
				case ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> {
					int slot = widen ? r.readUnsignedShort() : r.readUnsignedByte();
					maxLocalSize = Math.max(maxLocalSize, slot + 1 + (code&1));
				}
				case IINC -> r.rIndex += widen ? 4 : 2;

				case MULTIANEWARRAY -> {
					r.rIndex += 2;
					int arrayDepth = r.readUnsignedByte();
					stack(-arrayDepth);
				}
				case PUTFIELD, GETFIELD, PUTSTATIC, GETSTATIC -> {
					CstRef field = cp.getRef(r, true);
					char c = field.rawDesc().charAt(0);
					int len = c == Type.LONG || c == Type.DOUBLE ? 2 : 1;
					stack((code & 1) != 0 ? -len : len);
				}
				case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC -> invoke(code, cp.getRef(r, false));
				case INVOKEINTERFACE -> {
					invoke(code, cp.get(r));
					r.rIndex += 2;
				}
				case INVOKEDYNAMIC -> invoke(cp.get(r), r.readUnsignedShort());

				case IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN, ATHROW -> controlFlowTerminated = true;

				case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL,
					 IF_icmpeq, IF_icmpne, IF_icmplt, IF_icmpge, IF_icmpgt, IF_icmple, IF_acmpeq, IF_acmpne -> {
					r.rIndex += 2;
					jump();
				}
				case GOTO -> {
					r.rIndex += 2;
					controlFlowTerminated = true;
					jump();
				}
				case GOTO_W -> {
					r.rIndex += 4;
					controlFlowTerminated = true;
					jump();
				}
				case TABLESWITCH, LOOKUPSWITCH -> {
					// align
					r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
					// default
					r.rIndex += 4;
					// branches
					if ((code&1) == 0) {
						int low = r.readInt();
						int hig = r.readInt();
						int count = hig - low + 1;

						r.rIndex += count << 2;
					} else {
						int count = r.readInt();

						r.rIndex += count << 3;
					}

					controlFlowTerminated = true;
					jump();
				}
			}

			prev = code;
		}
	}

	private void stack(int delta) {
		var block = current;
		int value = block.pushedStackSpace + delta;
		if (value >= 0) {
			block.pushedStackSpace = value;
			if (block.pushedStackSpaceMax < block.pushedStackSpace)
				block.pushedStackSpaceMax = block.pushedStackSpace;
		} else {
			block.pushedStackSpace = 0;
			block.poppedStackSpace -= value;
		}
	}

	private void invoke(CstDynamic dyn, int reserved) {invoke1(INVOKESTATIC, dyn.desc());}
	private void invoke(byte code, CstRef method) {invoke1(code, method.nameAndType());}
	private void invoke1(byte code, CstNameAndType desc) {
		ArrayList<Type> arguments = tmpList; arguments.clear();
		Type returnType = Type.getArgumentTypes(desc.rawDesc().str(), arguments);

		for (int i = arguments.size() - 1; i >= 0; i--) {
			stack(-arguments.get(i).length());
		}

		if (code != INVOKESTATIC) stack(-1);
		stack(returnType.length());
	}

	private void jump() {
		if (maxStackSize != 0) return;

		var branches = branchTargets.remove(pc);
		if (branches == null) throw new FastFailException("在#"+ pc +"处预期分支, 实际: "+branchTargets);

		for (int i = 0; i < branches.size(); i++) current.to(branches.get(i));
	}

	private static void ret() { throw new FastFailException("不允许使用JSR/RET"); }
	// endregion
}