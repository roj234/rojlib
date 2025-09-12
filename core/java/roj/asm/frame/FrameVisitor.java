package roj.asm.frame;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import roj.RojLib;
import roj.asm.ClassUtil;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.insn.AbstractCodeWriter;
import roj.asm.insn.JumpTo;
import roj.asm.insn.Segment;
import roj.asm.insn.SwitchBlock;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.collect.IntMap;
import roj.compiler.resolve.TypeCast;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.FastFailException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.asm.frame.Frame.*;
import static roj.asm.frame.Var2.*;

/**
 * @author Roj234
 * @since 2022/11/17 13:09
 */
public class FrameVisitor {
	private static final boolean DEADCODE_LENIENT = RojLib.isDev("asm");
	private static final String ANY_OBJECT_TYPE = "java/lang/Object";

	/**
	 * 最大操作数栈大小和最大局部变量表大小（以槽位计）。
	 * 在 {@link #finish} 方法中计算并填充。
	 */
	public int maxStackSize, maxLocalSize;

	private MethodNode method;
	/**
	 * 方法入口处的局部变量初始状态（复制自第一个基本块）。
	 */
	private Var2[] initLocal;

	/**
	 * 按字节码偏移量索引的基本块映射。
	 */
	private final IntMap<BasicBlock> byPos = new IntMap<>();
	/**
	 * 已处理的基本块列表（按访问顺序）。
	 */
	private final List<BasicBlock> processed = new ArrayList<>();
	/**
	 * 分支目标映射：键为分支指令的偏移量，值为目标基本块列表。
	 */
	private final IntMap<List<BasicBlock>> branchTargets = new IntMap<>();
	/**
	 * 异常处理器信息列表，每三个元素为一组：起始块、结束块、处理器块。
	 */
	private final List<BasicBlock> exceptionHandlers = new ArrayList<>();

	private int bci;
	private BasicBlock current;
	private boolean controlFlowTerminated;

	private final ArrayList<Type> tmpList = new ArrayList<>();
	private final CharList sb = new CharList();

	/**
	 * 初始化方法处理状态。
	 * 设置当前方法，初始化局部变量表（包括 this 引用和方法参数），并记录初始状态。
	 *
	 * @param method 要处理的方法节点
	 * @throws IllegalArgumentException 如果方法是静态构造器或参数为 void 类型
	 */
	private void setMethod(MethodNode method) {
		this.method = method;

		byPos.clear();
		processed.clear();
		branchTargets.clear();
		exceptionHandlers.clear();
		current = add(0, "begin", false);

		boolean isConstructor = method.name().equals("<init>");
		int slotBegin = 0 == (method.modifier()&ACC_STATIC) ? 1 : 0;

		if (slotBegin != 0) { // this
			set(0, new Var2(isConstructor ? T_UNINITIAL_THIS : T_REFERENCE, method.owner()));
		} else if (isConstructor) {
			throw new IllegalArgumentException("静态的构造器");
		}

		List<Type> types = method.parameters();
		for (int i = 0; i < types.size(); i++) {
			Var2 v = of(types.get(i));
			if (v == null) throw new IllegalArgumentException("参数["+i+"]是Void");
			set(slotBegin++, v);
			if (v.type == T_DOUBLE || v.type == T_LONG) set(slotBegin++, SECOND);
		}

		initLocal = Arrays.copyOf(current.assignedLocals, current.assignedLocalCount);
	}
	/**
	 * 初始化方法处理状态，并
	 * 访问代码块（跳转或 switch 块），记录分支目标信息。
	 * 由 {@code CodeWriter} 在固定偏移量（stage2）之后调用。
	 *
	 * @param method 当前方法节点
	 * @param blocks 代码块列表（{@link JumpTo} 或 {@link SwitchBlock}）
	 */
	public void visitBlocks(MethodNode method, List<Segment> blocks) {
		setMethod(method);

		for (int i = 0; i < blocks.size(); i++) {
			Segment block = blocks.get(i);
			if (block instanceof JumpTo node) {
				List<BasicBlock> list;

				BasicBlock jumpTarget = add(node.target.getValue(), "jump target", true);
				// normal execution
				if (node.code != GOTO && node.code != GOTO_W) {
					BasicBlock ifNext = add(node.fv_bci+3, "if fail", false);
					list = Arrays.asList(jumpTarget,ifNext);
				} else {
					list = Collections.singletonList(jumpTarget);
				}
				branchTargets.put(node.fv_bci, list);
			} else if (block.getClass() == SwitchBlock.class) {
				SwitchBlock node = (SwitchBlock) block;
				List<BasicBlock> list = Arrays.asList(new BasicBlock[node.targets.size()+1]);

				list.set(0, add(node.def.getValue(), "switch default", true));
				for (int j = 0; j < node.targets.size();j++) {
					list.set(j+1, add(node.targets.get(j).bci(), "switch branch", true));
				}
				branchTargets.put(node.fv_bci, list);
			}
		}
	}
	/**
	 * 访问异常处理器条目，记录异常处理块和受保护范围。
	 * 由 {@code CodeWriter} 在固定偏移量（stage2）之后调用。
	 *
	 * @param startBci   受保护代码块的起始偏移量
	 * @param endBci     受保护代码块的结束偏移量
	 * @param handlerBci 异常处理器的偏移量
	 * @param type       捕获的异常类型（全限定名），为 null 时表示捕获所有 Throwable
	 */
	public void visitException(int startBci, int endBci, int handlerBci, String type) {
		var handler = current = add(handlerBci, "exception["+startBci+','+endBci+"=>"+handlerBci+"].handler", true);

		if (handler.reachable) { // 已存在
			Var2 generalized = handler.startStack[0].generalize(of(T_REFERENCE, type == null ? "java/lang/Throwable" : type));
			if (generalized != null) handler.startStack[0] = generalized;
		} else {
			push(type == null ? "java/lang/Throwable" : type);
			handler.reachable = true;
			handler.startStack = new Var2[] {handler.outStack[0]};
		}

		// 对于任何一个在异常处理器[startBci,endBci]之间的基本块，记录每一个变量slot被assign时的类型，如果与start之前的状态不符，那么在异常处理器中它就是TOP类型
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
		else target.merge(desc);
		if (isFrame) target.isFrame = true;
		return target;
	}

	public int bci() {return bci;}

	/**
	 * 完成帧分析，生成栈映射帧列表。
	 * 遍历字节码，计算最大栈和局部变量大小，合并异常处理器状态，并生成简化帧类型。
	 *
	 * @param code           字节码缓冲区
	 * @param cp             常量池
	 * @param generateFrames 是否生成帧（如果为 false 则返回 null）
	 * @return 生成的帧列表，或 null（如果 {@code generateFrames} 为 false）
	 * @throws FastFailException      如果存在未处理的基本块
	 * @throws IllegalStateException  如果遇到未初始化对象或死代码（非宽松模式）
	 */
	@Nullable
	@Contract("_, _, false -> null")
	public List<Frame> finish(DynByteBuf code, ConstantPool cp, boolean generateFrames) {
		current = null;
		controlFlowTerminated = false;

		visitBytecode(code, cp);
		if (!byPos.isEmpty()) throw new FastFailException("这些节点不是字节码开始或不存在: "+byPos.values());

		List<BasicBlock> blocks = processed;
		var tmp = new HashSet<BasicBlock>();

		maxLocalSize = 0;
		for (int i = 0; i < blocks.size(); i++) {
			var block = blocks.get(i);
			maxLocalSize = Math.max(maxLocalSize, Math.max(block.assignedLocalCount, block.usedLocalCount));
		}

		var first = blocks.get(0);
		maxStackSize = first.computeMaxStackSize(tmp, 0);

		if (blocks.size() <= 1 || !generateFrames) return null;

		var frames = new ArrayList<Frame>();

		first.initFirst(initLocal);
		first.traverse(tmp);

		if (!exceptionHandlers.isEmpty()) {
			for (int i = 0; i < exceptionHandlers.size(); i += 3) {
				var start   = exceptionHandlers.get(i);
				var end     = exceptionHandlers.get(i+1);
				var handler = exceptionHandlers.get(i+2);

				for (int j = blocks.indexOf(start); ; j++) {
					var block = blocks.get(j);

					if (block.reachable) {
						handler.combineExceptionLocals(block);
						handler.traverse(tmp);
					}

					if (block == end) break;
				}
			}
		}

		List<Var2> out = new ArrayList<>();
		Var2[] lastLocal = initLocal;
		for (Var2 item : lastLocal) {
			if (item != SECOND) out.add(item);
		}
		lastLocal = out.toArray(new Var2[out.size()]);

		for (int i = 0; i < blocks.size(); i++) {
			var block = blocks.get(i);
			if (!block.reachable) {
				if (DEADCODE_LENIENT) {
					new IllegalStateException(block.toString()).printStackTrace();
				} else {
					throw new IllegalStateException(block.toString());
				}
			}
			else if (!block.isFrame) continue;

			Var2[] stack = block.startStack;
			Var2[] local = block.startLocals;

			int startLocalSize = 0;
			for (int j = 0; j < local.length;) {
				Var2 item = local[j++];
				if (item == null || item.type == T_TOP) continue;
				startLocalSize = j;
				if ((item.type == T_UNINITIAL || item.type == T_UNINITIAL_THIS) && block.bci > item.bci) {
					if (item.bci == 0) throw new IllegalStateException("未初始化的"+item);
					local[j-1] = Var2.of(T_REFERENCE, item.owner);
				}
			}

			out.clear();
			for (int j = 0; j < startLocalSize; j++) {
				Var2 item = local[j];
				if (item == null) item = TOP;
				if (item == SECOND) continue;
				out.add(item);
			}
			local = out.toArray(new Var2[out.size()]);

			for (int j = 0; j < stack.length; j++) {
				Var2 item = stack[j];
				if ((item.type == T_UNINITIAL || item.type == T_UNINITIAL_THIS) && block.bci > item.bci) {
					if (item.bci == 0) throw new IllegalStateException("未初始化的"+item);
					local[j] = Var2.of(T_REFERENCE, item.owner);
				}
			}

			Frame frame = new Frame();
			frame.bci = block.bci;
			frame.stacks = stack;
			frames.add(frame);

			maySimplifyFrameType: {
				if (stack.length < 2) {
					if (eq(lastLocal, lastLocal.length, local, local.length)) {
						frame.type = (stack.length == 0 ? same : same_local_1_stack);
						break maySimplifyFrameType;
					} else if (stack.length == 0) {
						int delta = local.length - lastLocal.length;
						mayNotSimplifyFrameType:
						if (delta != 0 && Math.abs(delta) <= 3) {
							int j = Math.min(local.length, lastLocal.length);

							while (j-- > 0) {
								if (local[j] == null || !local[j].eq(lastLocal[j]))
									break mayNotSimplifyFrameType;
							}

							frame.type = delta < 0 ? (byte) (chop + 1 + delta) : append;
							if (delta > 0) frame.locals = Arrays.copyOfRange(local, lastLocal.length, local.length);
							break maySimplifyFrameType;
						}

					}
				}

				frame.type = full;
				frame.locals = local;
			}

			lastLocal = local;
		}
		blocks.clear();

		//System.out.println(frames);
		return frames;
	}

	private static boolean eq(Var2[] local, int len, Var2[] local1, int len1) {
		if (len != len1) return false;
		for (int i = 0; i < len; i++) {
			if (!local[i].eq(local1[i])) return false;
		}
		return true;
	}

	// region Basic block type prediction + emulate
	@SuppressWarnings("fallthrough")
	private void visitBytecode(DynByteBuf r, ConstantPool cp) {
		int rBegin = r.rIndex;

		byte prev = 0, code;
		while (r.isReadable()) {
			int bci = r.rIndex - rBegin;

			BasicBlock next = byPos.remove(bci);
			if (next != null) {
				// if
				if (!controlFlowTerminated && current != null) current.to(next);
				current = next;
				processed.add(next);
				controlFlowTerminated = false;
			}
			if (controlFlowTerminated) {
				if (DEADCODE_LENIENT) {
					current = new BasicBlock(bci, "dead "+Opcodes.toString(prev));
					controlFlowTerminated = false;
					processed.add(current);
				} else {
					throw new IllegalStateException("无法访问的代码: #"+bci+"("+Opcodes.toString(prev)+") ");
				}
			}

			this.bci = bci;
			code = Opcodes.validateOpcode(r.readByte());

			boolean widen = prev == Opcodes.WIDE;
			//CodeVisitor.checkWide(code);

			if (code >= ILOAD_0 && code <= ALOAD_3) {
				int arg = (code - ILOAD_0) & 3;
				code = (byte) (((code - ILOAD_0) / 4) + ILOAD);
				var(code, arg);
				continue;
			} else if (code >= ISTORE_0 && code <= ASTORE_3) {
				int arg = (code - ISTORE_0) & 3;
				code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
				var(code, arg);
				continue;
			}

			Var2 t1, t2, t3;
			switch (code) {
				/*case NOP, LNEG, FNEG, DNEG, INEG, I2B, I2C, I2S -> {}*/
				case ACONST_NULL -> push(T_NULL);
				case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 -> push(T_INT);
				case LCONST_0, LCONST_1 -> push(T_LONG);
				case FCONST_0, FCONST_1, FCONST_2 -> push(T_FLOAT);
				case DCONST_0, DCONST_1 -> push(T_DOUBLE);
				case IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR -> math(T_INT);
				case FADD, FSUB, FMUL, FDIV, FREM -> math(T_FLOAT);
				case LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR -> math(T_LONG);
				case LSHL, LSHR, LUSHR -> pop(T_INT);
				case DADD, DSUB, DMUL, DDIV, DREM -> math(T_DOUBLE);
				case LDC -> ldc(cp.data().get(r.readUnsignedByte()-1));
				case LDC_W, LDC2_W -> ldc(cp.get(r));
				case BIPUSH -> {
					push(T_INT);
					r.rIndex += 1;
				}
				case SIPUSH -> {
					push(T_INT);
					r.rIndex += 2;
				}
				case IINC -> {
					int id = widen ? r.readUnsignedShort() : r.readUnsignedByte();
					get(id, T_INT);
					r.rIndex += widen ? 2 : 1;
				}
				case ISTORE, LSTORE, FSTORE, DSTORE, ASTORE,
					ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> var(code, widen ? r.readUnsignedShort() : r.readUnsignedByte());
				case FCMPL, FCMPG -> cmp(T_FLOAT);
				case DCMPL, DCMPG -> cmp(T_DOUBLE);
				case LCMP -> cmp(T_LONG);
				case F2I -> {
					pop(T_FLOAT);
					push(T_INT);
				}
				case L2I -> {
					pop(T_LONG);
					push(T_INT);
				}
				case D2I -> {
					pop(T_DOUBLE);
					push(T_INT);
				}
				case I2F -> {
					pop(T_INT);
					push(T_FLOAT);
				}
				case L2F -> {
					pop(T_LONG);
					push(T_FLOAT);
				}
				case D2F -> {
					pop(T_DOUBLE);
					push(T_FLOAT);
				}
				case I2L -> {
					pop(T_INT);
					push(T_LONG);
				}
				case F2L -> {
					pop(T_FLOAT);
					push(T_LONG);
				}
				case D2L -> {
					pop(T_DOUBLE);
					push(T_LONG);
				}
				case I2D -> {
					pop(T_INT);
					push(T_DOUBLE);
				}
				case F2D -> {
					pop(T_FLOAT);
					push(T_DOUBLE);
				}
				case L2D -> {
					pop(T_LONG);
					push(T_DOUBLE);
				}
				case ARRAYLENGTH -> {
					pop(T_ANYARRAY);
					push(T_INT);
				}
				case IALOAD -> arrayLoadI("[I");
				case BALOAD -> arrayLoadI("[B");
				case CALOAD -> arrayLoadI("[C");
				case SALOAD -> arrayLoadI("[S");
				case LALOAD -> arrayLoad("[J", T_LONG);
				case FALOAD -> arrayLoad("[F", T_FLOAT);
				case DALOAD -> arrayLoad("[D", T_DOUBLE);
				case IASTORE -> arrayStoreI("[I");
				case BASTORE -> arrayStoreI("[B");
				case CASTORE -> arrayStoreI("[C");
				case SASTORE -> arrayStoreI("[S");
				case FASTORE -> arrayStore("[F", T_FLOAT);
				case LASTORE -> arrayStore("[J", T_LONG);
				case DASTORE -> arrayStore("[D", T_DOUBLE);
				case AALOAD -> {
					pop(T_INT);
					String v = pop("[Ljava/lang/Object;").owner;
					v = v.charAt(1) == 'L' ? v.substring(2, v.length() - 1) : v.substring(1);
					push(new Var2(T_REFERENCE, v));
				}
				case AASTORE -> {
					Var2 value = pop(ANY_OBJECT_TYPE);
					pop(T_INT);
					String v = pop("[Ljava/lang/Object;").owner;
					v = v.charAt(1) == 'L' ? v.substring(2, v.length() - 1) : v.substring(1);
					value.verify(new Var2(T_REFERENCE, v));
				}
				case PUTFIELD, GETFIELD, PUTSTATIC, GETSTATIC -> field(code, cp.getRef(r, true));
				case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC -> invoke(code, cp.getRef(r, false));
				case INVOKEINTERFACE -> {
					invoke(code, cp.get(r));
					r.rIndex += 2;
				}
				case INVOKEDYNAMIC -> invokeDynamic(cp.get(r), r.readUnsignedShort());
				case JSR, JSR_W, RET -> ret();
				case NEWARRAY -> {
					pop(T_INT);
					sb.clear();
					push(sb.append('[').append(AbstractCodeWriter.FromPrimitiveArrayId(r.readByte())).toString());
				}
				case INSTANCEOF -> {
					r.rIndex += 2;
					pop(ANY_OBJECT_TYPE);
					push(T_INT);
				}
				case NEW -> push(of(T_UNINITIAL, cp.getRefName(r, Constant.CLASS)));
				case ANEWARRAY -> {
					pop(T_INT);
					String className = cp.getRefName(r, Constant.CLASS);
					if (className.endsWith(";") || className.startsWith("[")) push("[".concat(className));
					else {
						sb.clear();
						push(sb.append("[L").append(className).append(';').toString());
					}
				}
				case CHECKCAST -> {
					pop(ANY_OBJECT_TYPE);
					push(cp.getRefName(r, Constant.CLASS));
				}
				case MULTIANEWARRAY -> {
					var arrayType = cp.getRefName(r, Constant.CLASS);
					int arrayDepth = r.readUnsignedByte();
					while (arrayDepth-- > 0) pop(T_INT);
					push(arrayType);
				}
				case RETURN -> controlFlowTerminated = true;
				case IRETURN, FRETURN, LRETURN, DRETURN, ARETURN -> {
					pop(of(method.returnType()));
					controlFlowTerminated = true;
				}
				case ATHROW -> {
					pop("java/lang/Throwable");
					controlFlowTerminated = true;
				}
				case MONITORENTER, MONITOREXIT -> pop(ANY_OBJECT_TYPE);
				case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
					pop(T_INT);
					r.rIndex += 2;
					jump();
				}
				case IF_icmpeq, IF_icmpne, IF_icmplt, IF_icmpge, IF_icmpgt, IF_icmple -> {
					pop(T_INT);
					pop(T_INT);
					r.rIndex += 2;
					jump();
				}
				case IFNULL, IFNONNULL -> {
					pop(ANY_OBJECT_TYPE);
					r.rIndex += 2;
					jump();
				}
				case IF_acmpeq, IF_acmpne -> {
					pop(ANY_OBJECT_TYPE);
					pop(ANY_OBJECT_TYPE);
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
				case TABLESWITCH -> {
					// align
					r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
					tableSwitch(r);
				}
				case LOOKUPSWITCH -> {
					r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
					lookupSwitch(r);
				}
				case POP -> pop1();
				case POP2 -> {
					t1 = pop2();
					if (t1.type != T_DOUBLE && t1.type != T_LONG) {
						pop1();
					}
				}
				case DUP -> {
					t1 = pop1();
					push(t1);
					push(t1);
				}
				case DUP_X1 -> {
					t1 = pop1();
					t2 = pop1();
					push(t1);
					push(t2);
					push(t1);
				}
				case DUP_X2 -> {
					t1 = pop1();
					t2 = pop1();
					t3 = pop1();
					push(t1);
					push(t3);
					push(t2);
					push(t1);
				}
				case DUP2 -> {
					t1 = pop2();
					if (t1.type == T_DOUBLE || t1.type == T_LONG) {
						push(t1);
						push(t1);
					} else {
						t2 = pop1();
						push(t2);
						push(t1);
						push(t2);
						push(t1);
					}
				}
				case DUP2_X1 -> {
					t1 = pop2();
					t2 = t1.type == T_DOUBLE || t1.type == T_LONG ? null : pop1();
					t3 = pop1();
					if (t2 != null) {
						push(t2);
						push(t1);
						push(t3);
						push(t2);
					} else {
						push(t1);
						push(t3);
					}
					push(t1);
				}
				case DUP2_X2 -> {
					t1 = pop2();
					t2 = t1.type == T_DOUBLE || t1.type == T_LONG ? null : pop1();
					t3 = pop1();
					Var2 t4 = pop1();
					if (t2 != null) {
						push(t2);
						push(t1);
						push(t4);
						push(t3);
						push(t2);
					} else {
						push(t1);
						push(t4);
						push(t3);
					}
					push(t1);
				}
				case SWAP -> {
					t1 = pop1();
					t2 = pop1();
					push(t1);
					push(t2);
				}
			}

			prev = code;
		}
	}

	private void math(byte type) {pop(type); pop(type); push(type);}
	private void cmp(byte type) {pop(type); pop(type); push(T_INT);}

	private void arrayLoadI(String arrayClass) {pop(T_INT); pop(arrayClass); push(T_INT);}
	private void arrayLoad(String arrayClass, byte type) {pop(T_INT); pop(arrayClass); push(type);}
	private void arrayStoreI(String arrayClass) {pop(T_INT); pop(T_INT); pop(arrayClass);}
	private void arrayStore(String arrayClass, byte type) {pop(type); pop(T_INT); pop(arrayClass);}

	private void ldc(Constant c) {
		int type = c.type();
		switch (type) {
			case Constant.DYNAMIC -> {
				String typeStr = ((CstDynamic) c).desc().rawDesc().str();
				switch (TypeCast.getDataCap(typeStr.charAt(0))) {
					case 0,1,2,3,4 -> push(T_INT);
					case 5 -> push(T_LONG);
					case 6 -> push(T_FLOAT);
					case 7 -> push(T_DOUBLE);
					case 8 -> {
						if (typeStr.charAt(0) != 'L') throw new UnsupportedOperationException("非法的动态类型:"+typeStr);
						new Throwable("CONSTANT_DYNAMIC_CLASS:"+typeStr).printStackTrace();
						push(typeStr.substring(1, typeStr.length() - 1));
					}
				}
			}
			case Constant.INT -> push(T_INT);
			case Constant.LONG -> push(T_LONG);
			case Constant.FLOAT -> push(T_FLOAT);
			case Constant.DOUBLE -> push(T_DOUBLE);
			case Constant.CLASS -> push("java/lang/Class");
			case Constant.STRING -> push("java/lang/String");
			case Constant.METHOD_TYPE -> push("java/lang/invoke/MethodType");
			case Constant.METHOD_HANDLE -> push("java/lang/invoke/MethodHandle");
			default -> throw new IllegalArgumentException("不支持的常量:"+type);
		}
	}

	private void invokeDynamic(CstDynamic dyn, int type/* unused */) {invoke(INVOKESTATIC, null, dyn.desc());}
	private void invoke(byte code, CstRef method) {invoke(code, method, method.nameAndType());}
	private void invoke(byte code, CstRef method, CstNameAndType desc) {
		ArrayList<Type> arguments = tmpList; arguments.clear();
		Type returnType = Type.methodDesc(desc.rawDesc().str(), arguments);
		for (int i = arguments.size() - 1; i >= 0; i--) {
			pop(of(arguments.get(i)));
		}

		if (code != INVOKESTATIC) {
			Var2 instance = new Var2(T_REFERENCE, method.clazz().value().str());
			// initialize T_UNINITIALIZED
			if (method.name().equals("<init>")) instance.bci = bci;
			pop(instance);
		}

		Var2 returnValue = of(returnType);
		if (returnValue != null) push(returnValue);
	}
	private void field(byte code, CstRef field) {
		Var2 fieldType = of(Type.fieldDesc(field.nameAndType().rawDesc().str()));
		switch (code) {
			case GETSTATIC -> push(fieldType);
			case PUTSTATIC -> pop(fieldType);
			case GETFIELD -> {
				pop(field.clazz().value().str());
				push(fieldType);
			}
			case PUTFIELD -> {
				pop(fieldType);
				pop(field.clazz().value().str());
			}
		}
	}
	private void var(byte code, int vid) {
		switch (code) {
			case ALOAD -> push(get(vid, ANY_OBJECT_TYPE));
			case DLOAD -> push(get(vid, T_DOUBLE));
			case FLOAD -> push(get(vid, T_FLOAT));
			case LLOAD -> push(get(vid, T_LONG));
			case ILOAD -> push(get(vid, T_INT));
			case ASTORE -> set(vid, pop(ANY_OBJECT_TYPE));
			case DSTORE -> set(vid, pop(T_DOUBLE));
			case FSTORE -> set(vid, pop(T_FLOAT));
			case LSTORE -> set(vid, pop(T_LONG));
			case ISTORE -> set(vid, pop(T_INT));
		}
	}
	private void jump() {
		var branches = branchTargets.remove(bci);
		if (branches == null) throw new IllegalStateException("在#"+bci+"处找不到预期的分支节点, 列表: "+ branchTargets);

		for (int i = 0; i < branches.size(); i++)
			current.to(branches.get(i));
	}
	private void tableSwitch(DynByteBuf r) {
		r.rIndex += 4;
		int low = r.readInt();
		int hig = r.readInt();
		int count = hig - low + 1;

		r.rIndex += count << 2;

		pop(T_INT);
		controlFlowTerminated = true;
		jump();
	}
	private void lookupSwitch(DynByteBuf r) {
		r.rIndex += 4;
		int count = r.readInt();

		r.rIndex += count << 3;

		pop(T_INT);
		controlFlowTerminated = true;
		jump();
	}
	private static void ret() { throw new UnsupportedOperationException("不允许使用JSR/RET操作"); }

	private Var2 get(int i, byte type) { return get(i, of(type, null)); }
	private Var2 get(int i, String owner) { return get(i, new Var2(T_REFERENCE, owner)); }
	private Var2 get(int i, Var2 type) {
		var block = current;
		if (i < block.assignedLocalCount && block.assignedLocals[i] != null) {
			block.assignedLocals[i].verify(type);
			return block.assignedLocals[i];
		}

		if (i >= block.usedLocals.length) block.usedLocals = Arrays.copyOf(block.usedLocals, i+1);
		if (block.usedLocalCount <= i) block.usedLocalCount = i+1;

		Var2[] used = block.usedLocals;
		if (used[i] != null) {
			used[i].verify(type);
			return used[i];
		} else {
			return used[i] = type;
		}
	}
	private void set(int i, Var2 type) {
		var block = current;
		if (i >= block.assignedLocals.length) block.assignedLocals = Arrays.copyOf(block.assignedLocals, i+1);
		if (block.assignedLocalCount <= i) block.assignedLocalCount = i+1;

		Var2 prev = block.assignedLocals[i];
		if (!exceptionHandlers.isEmpty() &&
				prev != null &&
				prev.type < 10 && type.type < 10 && (prev.type > 4 ? 5 : prev.type) != (type.type > 4 ? 5 : type.type)) {
			block.reassignedLocal(i);
		}

		block.assignedLocals[i] = type;
		if (type.type == T_LONG || type.type == T_DOUBLE) set(i+1, SECOND);
		else if (prev == SECOND && type != prev && i > 0) {
			Var2 prev2 = block.assignedLocals[i-1];
			if (prev2.type == T_LONG || prev2.type == T_DOUBLE)
				throw new IllegalStateException("无法修改被"+prev2+"占用的slot "+i+"为"+type);
		}
	}

	private Var2 pop(byte type) {return pop(of(type, null));}
	private Var2 pop(String type) {return pop(new Var2(T_REFERENCE, type));}
	private Var2 pop1() {
		Var2 v = pop((Var2) null);
		if (v.type == T_LONG || v.type == T_DOUBLE) throw new IllegalArgumentException(v+"不能用pop1处理");
		return v;
	}
	private Var2 pop2() { return pop(new Var2(T_ANY2)); }
	private Var2 pop(Var2 exceptType) {
		var block = current;
		if (block.outStackSize != 0) {
			Var2 v1 = block.outStack[--block.outStackSize];
			if (exceptType != null) {
				// simulation of 1xT2 as 2xT1 failed
				if (v1.type == T_ANY2 && exceptType.type != T_LONG && exceptType.type != T_DOUBLE) {
					Var2 v2 = block.outStack[block.outStackSize-1];
					if (v2.type == T_LONG || v2.type == T_DOUBLE)
						throw new IllegalArgumentException("pop any2 "+v1+"&"+v2);
				}
				v1.verify(exceptType);
			}

			block.outStackInts -= lengthOf(v1);
			return v1;
		}

		if (exceptType == null) exceptType = any();

		if (block.consumedStackSize >= block.consumedStack.length)
			block.consumedStack = Arrays.copyOf(block.consumedStack, block.consumedStackSize+1);
		block.consumedStack[block.consumedStackSize++] = exceptType;
		block.consumedStackInts += lengthOf(exceptType);

		return exceptType;
	}
	private void push(byte type) {push(of(type, null));}
	private void push(String owner) {push(new Var2(T_REFERENCE, owner));}
	private void push(Var2 type) {
		var block = current;
		if (block.outStackSize >= block.outStack.length)
			block.outStack = Arrays.copyOf(block.outStack, block.outStackSize+1);
		block.outStack[block.outStackSize++] = type;
		block.outStackInts += lengthOf(type);
		if (block.outStackIntsMax < block.outStackInts)
			block.outStackIntsMax = block.outStackInts;
	}

	private static int lengthOf(Var2 type) {return type.type == T_LONG || type.type == T_DOUBLE ? 2 : 1;}

	// endregion
	public static String getConcreteChild(String type1, String type2) {return ClassUtil.getInstance().getCommonChild(type1, type2);}
	public static String getCommonAncestor(String type1, String type2) {return ClassUtil.getInstance().getCommonAncestor(type1, type2);}
	// region Frame writing
	public static void readFrames(List<Frame> frames, DynByteBuf r, ConstantPool cp, AbstractCodeWriter pc, String owner, int lMax, int sMax) {
		frames.clear();

		int allOffset = -1;
		int tableLen = r.readUnsignedShort();
		while (tableLen-- > 0) {
			int type = r.readUnsignedByte();
			Frame frame = fromByte(type);
			frames.add(frame);

			int off = -1;
			switch (frame.type) {
				case same -> off = type;
				// keep original chop count
				case same_ex, chop, chop2, chop3 -> off = r.readUnsignedShort();
				case same_local_1_stack -> {
					off = type - 64;
					frame.stacks = new Var2[]{getVar(cp, r, pc, owner)};
				}
				case same_local_1_stack_ex -> {
					off = r.readUnsignedShort();
					frame.stacks = new Var2[]{getVar(cp, r, pc, owner)};
				}
				case append -> {
					off = r.readUnsignedShort();
					int len = type - 251;
					frame.locals = new Var2[len];
					for (int j = 0; j < len; j++) {
						frame.locals[j] = getVar(cp, r, pc, owner);
					}
				}
				case full -> {
					off = r.readUnsignedShort();

					int len = r.readUnsignedShort();
					if (len > lMax) throw new IllegalStateException("Full帧的变量数量超过限制("+len+") > ("+lMax+")");

					frame.locals = new Var2[len];
					for (int j = 0; j < len; j++) frame.locals[j] = getVar(cp, r, pc, owner);

					len = r.readUnsignedShort();
					if (len > sMax) throw new IllegalStateException("Full帧的栈大小超过限制("+len+") > ("+sMax+")");

					frame.stacks = new Var2[len];
					for (int j = 0; j < len; j++) frame.stacks[j] = getVar(cp, r, pc, owner);
				}
			}

			allOffset += off+1;
			frame.monitoredBci = pc._monitor(allOffset);
		}
	}
	private static Var2 getVar(ConstantPool pool, DynByteBuf r, AbstractCodeWriter pc, String owner) {
		byte type = r.readByte();
		return switch (type) {
			case T_REFERENCE -> new Var2(type, pool.getRefName(r, Constant.CLASS));
			case T_UNINITIAL -> new Var2(pc._monitor(r.readUnsignedShort()));
			case T_UNINITIAL_THIS -> new Var2(type, owner);
			default -> of(type, null);
		};
	}

	@SuppressWarnings("fallthrough")
	public static void writeFrames(List<Frame> frames, DynByteBuf w, ConstantPool cp) {
		Frame prev = null;
		w.putShort(frames.size());
		for (int j = 0; j < frames.size(); j++) {
			Frame frame = frames.get(j);

			int offset = frame.bci();
			if (j > 0) offset -= prev.bci() + 1;

			if ((offset & ~0xFFFF) != 0)
				throw new IllegalArgumentException("Illegal delta "+offset+":\n frame="+frame+"\n prev="+prev);

			int type = frame.type;
			type = switch (type) {
				case same -> offset < 64 ? offset : (frame.type = same_ex);
				case same_local_1_stack -> offset < 64 ? (offset + 64) : (frame.type = same_local_1_stack_ex);
				case append -> (251 + frame.locals.length);
				default -> type;
			};
			w.put(type);
			switch (frame.type) {
				case same: break;
				case same_local_1_stack_ex:
					w.putShort(offset);
				case same_local_1_stack:
					putVar(frame.stacks[0], w, cp);
				break;
				case chop, chop2, chop3:
				case same_ex:
					w.putShort(offset);
				break;
				case append:
					w.putShort(offset);
					for (int i = frame.locals.length + 251 - type, e = frame.locals.length; i < e; i++) {
						putVar(frame.locals[i], w, cp);
					}
				break;
				case full:
					w.putShort(offset).putShort(frame.locals.length);
					for (int i = 0, e = frame.locals.length; i < e; i++) {
						putVar(frame.locals[i], w, cp);
					}

					w.putShort(frame.stacks.length);
					for (int i = 0, e = frame.stacks.length; i < e; i++) {
						putVar(frame.stacks[i], w, cp);
					}
				break;
			}

			prev = frame;
		}
	}
	private static void putVar(Var2 v, DynByteBuf w, ConstantPool cp) {
		w.put(v.type);
		switch (v.type) {
			case T_REFERENCE -> w.putShort(cp.getClassId(v.owner));
			case T_UNINITIAL -> w.putShort(v.bci());
		}
	}
	// endregion
}