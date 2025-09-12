package roj.asm.frame;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.insn.AbstractCodeWriter;
import roj.asm.insn.Segment;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.FastFailException;
import roj.util.Helpers;

import java.util.*;

import static roj.asm.Opcodes.*;
import static roj.asm.frame.Frame.*;
import static roj.asm.frame.Var2.*;

/**
 * 一个访问者类，继承自 {@link SizeVisitor}，专门用于生成Java方法的StackMapTable属性。
 * 它通过解析字节码并执行数据流分析来模拟虚拟机执行过程，为每个需要帧指示的位置（通常是方法入口、跳转目标、异常处理器入口等）
 * 生成相应的帧信息。
 * @version 4.0
 * @author Roj234
 * @since 2022/11/17 13:09
 */
public class FrameVisitor extends SizeVisitor {
	public static final Var2 UNRESOLVED_REFERENCE = new Var2(T_NULL, "UNRESOLVED NULL REFERENCE");

	private Var2[] currentLocals;
	private Var2[] currentStack;
	private int currentStackSize;

	private final Map<BasicBlock, List<BasicBlock>> tryHandlerMap = new HashMap<>();

	private final CharList sb = new CharList();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init(MethodNode method, List<Segment> blocks) {
		super.init(method, blocks);

		currentLocals = NONE;
		currentStack = NONE;
		currentStackSize = 0;

		boolean isConstructor = method.name().equals("<init>");
		int slot = 0 == (method.modifier()&ACC_STATIC) ? 1 : 0;

		if (slot != 0) { // this
			set(0, new Var2(isConstructor ? T_UNINITIAL_THIS : T_REFERENCE, method.owner()));
		} else if (isConstructor) {
			throw new IllegalArgumentException("静态的构造器");
		}

		List<Type> types = method.parameters();
		for (int i = 0; i < types.size(); i++) {
			Var2 item = of(types.get(i));
			if (item == null) throw new IllegalArgumentException("参数["+i+"]是Void");
			set(slot++, item);
			if (isDual(item)) slot++;
		}

		current.enterLocals = currentLocals.clone();
	}

	public static boolean debug;

	/**
	 * 完成帧的生成。
	 * 这个方法执行工作列表算法，模拟指令执行，计算每个基本块的出口状态，
	 * 并最终将这些状态转换为StackMapTable格式的 {@link Frame} 列表。
	 *
	 * @param code           方法字节码
	 * @param cp             方法的常量池
	 * @param generateFrames 指示是否要生成帧。如果为 {@code false}，则返回 {@code null}。
	 * @return 生成的StackMapTable帧列表，如果 {@code generateFrames} 为 {@code false}，则返回 {@code null}。
	 */
	@Nullable
	@Contract("_, _, false -> null")
	public List<Frame> finish(DynByteBuf code, ConstantPool cp, boolean generateFrames) {
		int begin = code.rIndex;
		super.finish(code, cp, false);
		List<BasicBlock> blocks = processed;

		if (blocks.size() == 1 && !blocks.get(0).isFrame) return null;

		// 1. 初始化 Worklist，这是一个存放待处理块的队列。
		Deque<BasicBlock> changeset = new ArrayDeque<>();

		// 2. 初始化入口块
		BasicBlock first = blocks.get(0);
		changeset.add(first);

		// 3. 创建一个从 try 块到其 handler 的快速查找映射
		// 这能避免在循环中反复遍历 exceptionHandlers 列表
		Map<BasicBlock, List<BasicBlock>> tryBlockToHandlers = this.tryHandlerMap;
		tryBlockToHandlers.clear();

		for (int i = 0; i < exceptionHandlers.size(); i += 3) {
			BasicBlock start = exceptionHandlers.get(i);
			BasicBlock end = exceptionHandlers.get(i + 1);
			BasicBlock handler = exceptionHandlers.get(i + 2);
			int startIndex = blocks.indexOf(start);
			int endIndex = blocks.indexOf(end);
			for (int j = startIndex; j < endIndex; j++) {
				tryBlockToHandlers.computeIfAbsent(blocks.get(j), Helpers.fnArrayList()).add(handler);
			}
		}

		int end = code.wIndex();
		// 4. 主循环，直到不动点
		while (!changeset.isEmpty()) {
			BasicBlock block = changeset.poll();

			code.rIndex = begin + block.pc;
			int i = processed.indexOf(block);
			code.wIndex(i == processed.size() - 1 ? end : begin + processed.get(i+1).pc);

			// 4.1. 计算当前块的出口状态
			loadState(block);
			try {
				interpret(code, cp, begin);
			} catch (Exception e) {
				throw new FastFailException("Exception processing block \n"+block, e);
			}
			saveState(block);

			boolean changed;

			// 4.2. 传播到正常后继
			for (BasicBlock successor : block.successors) {
				try {
					changed = successor.merge(block);
				} catch (Exception e) {
					throw new FastFailException("Exception merging successor \n"+successor+" from \n"+block, e);
				}
				if (changed && !changeset.contains(successor)) {
					changeset.add(successor);
				}
			}

			// 4.3. 传播到异常处理器
			List<BasicBlock> handlers = tryBlockToHandlers.getOrDefault(block, Collections.emptyList());
			for (BasicBlock handler : handlers) {
				try {
					changed = handler.mergeException(block);
				} catch (Exception e) {
					throw new FastFailException("Exception merging exception handler \n"+handler+" with \n"+block, e);
				}

				if (changed && !changeset.contains(handler)) {
					changeset.add(handler);
				}
			}
		}
		code.wIndex(end);
		if (debug) for (BasicBlock block : blocks) {
			System.out.println(block);
		}

		var frames = new ArrayList<Frame>();
		toFrames(blocks, frames);
		blocks.clear();

		return frames;
	}

	private void loadState(BasicBlock block) {
		current = block;
		currentLocals = block.enterLocals.clone();
		currentStack = block.enterStack.clone();
		currentStackSize = block.enterStack.length;
	}

	private void saveState(BasicBlock block) {
		block.exitLocals = currentLocals.clone();
		block.exitStack = Arrays.copyOf(currentStack, currentStackSize);
		block.reachable = true;
	}

	/**
	 * 将所有已处理的基本块转换为StackMapTable帧的列表。
	 * 此方法会根据基本块的类型（是否为帧位置）和状态（局部变量、栈），
	 * 生成不同类型的帧（full frame, same frame, append frame 等）。
	 *
	 * @param blocks       所有已处理的基本块列表。
	 * @param frames       用于存储生成的帧的 ArrayList。
	 */
	private static void toFrames(List<BasicBlock> blocks, ArrayList<Frame> frames) {
		List<Var2> localsTemp = new ArrayList<>();

		Var2[] lastLocals = compressLocals(blocks.get(0).enterLocals, localsTemp);

		for (int i = 0; i < blocks.size(); i++) {
			var block = blocks.get(i);
			if (!block.reachable) throw new FastFailException("基本块不可达: "+block);
			else if (!block.isFrame) continue;

			Var2[] stack = block.enterStack;
			Var2[] locals = compressLocals(block.enterLocals, localsTemp);

			Frame frame = new Frame();
			frame.pc = block.pc;
			frame.stack = stack;
			frames.add(frame);

			typeSimplified: {
				if (stack.length < 2) {
					if (Arrays.equals(lastLocals, locals)) {
						frame.type = (stack.length == 0 ? same : same_local_1_stack);
						break typeSimplified;
					} else if (stack.length == 0) {
						int delta = locals.length - lastLocals.length;
						mayNotSimplify:
						if (delta != 0 && Math.abs(delta) <= 3) {
							int j = Math.min(locals.length, lastLocals.length);

							while (j-- > 0) {
								if (locals[j] == null || !locals[j].equals(lastLocals[j]))
									break mayNotSimplify;
							}

							frame.type = delta < 0 ? (byte) (chop + 1 + delta) : append;
							if (delta > 0) frame.locals = Arrays.copyOfRange(locals, lastLocals.length, locals.length);
							break typeSimplified;
						}
					}
				}

				frame.type = full;
				frame.locals = locals;
			}

			lastLocals = locals;
		}
	}

	private static Var2 @NotNull [] compressLocals(Var2[] locals, List<Var2> tmpLocals) {
		int localSize = 0;
		for (int j = 0; j < locals.length;) {
			Var2 item = locals[j++];
			if (item == null || item.type == T_TOP) continue;
			localSize = j;
		}

		tmpLocals.clear();
		for (int j = 0; j < localSize; j++) {
			Var2 item = locals[j];
			if (item == SECOND && isDual(locals[j-1])) continue;

			if (item == null) item = TOP;
			tmpLocals.add(item);
		}

		return tmpLocals.toArray(new Var2[tmpLocals.size()]);
	}

	// region Basic block type prediction + emulate
	/**
	 * 模拟字节码指令的执行，更新局部变量和操作数栈的状态。
	 * @see #visitCode(DynByteBuf, ConstantPool, int)
	 * @param rBegin 方法字节码的起始读取位置。
	 */
	private void interpret(DynByteBuf r, ConstantPool cp, int rBegin) {
		byte prev = 0, code;
		while (r.isReadable()) {
			pc = r.rIndex - rBegin;

			code = Opcodes.validateOpcode(r.readByte());

			boolean widen = prev == Opcodes.WIDE;

			Var2 t1, t2, t3;
			switch (code) {
				default -> {
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
				}

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
					get(id);
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
					pop();
					push(T_INT);
				}
				case IALOAD, BALOAD, CALOAD, SALOAD -> arrayLoad(T_INT);
				case LALOAD -> arrayLoad(T_LONG);
				case FALOAD -> arrayLoad(T_FLOAT);
				case DALOAD -> arrayLoad(T_DOUBLE);
				case IASTORE, BASTORE, CASTORE, SASTORE -> arrayStore(T_INT);
				case FASTORE -> arrayStore(T_FLOAT);
				case LASTORE -> arrayStore(T_LONG);
				case DASTORE -> arrayStore(T_DOUBLE);
				case AALOAD -> {
					pop(T_INT);
					Var2 array = pop();
					if (array.owner != null) {
						push(componentType(array));
					} else {
						push(UNRESOLVED_REFERENCE);
					}
				}
				case AASTORE -> {
					pop();
					pop(T_INT);
					pop();
				}
				case PUTFIELD, GETFIELD, PUTSTATIC, GETSTATIC -> field(code, cp.getRef(r, true));
				case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC -> invoke(code, cp.getRef(r, false));
				case INVOKEINTERFACE -> {
					invoke(code, cp.get(r));
					r.rIndex += 2;
				}
				case INVOKEDYNAMIC -> invokeDynamic(cp.get(r), r.readUnsignedShort());
				case NEWARRAY -> {
					pop(T_INT);
					sb.clear();
					push(sb.append('[').append(AbstractCodeWriter.FromPrimitiveArrayId(r.readByte())).toString());
				}
				case INSTANCEOF -> {
					r.rIndex += 2;
					pop();
					push(T_INT);
				}
				case NEW -> {
					Var2 var2 = of(T_UNINITIAL, cp.getRefName(r, Constant.CLASS));
					var2.pc = pc;
					push(var2);
				}
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
					pop();
					push(cp.getRefName(r, Constant.CLASS));
				}
				case MULTIANEWARRAY -> {
					var arrayType = cp.getRefName(r, Constant.CLASS);
					int arrayDepth = r.readUnsignedByte();
					while (arrayDepth-- > 0) pop(T_INT);
					push(arrayType);
				}
				case RETURN -> {
					return;
				}
				case IRETURN, FRETURN, LRETURN, DRETURN, ARETURN -> {
					pop(of(method.returnType()));
					return;
				}
				case ATHROW -> {
					pop();
					return;
				}
				case MONITORENTER, MONITOREXIT -> pop();
				case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
					pop(T_INT);
					r.rIndex += 2;
					return;
				}
				case IF_icmpeq, IF_icmpne, IF_icmplt, IF_icmpge, IF_icmpgt, IF_icmple -> {
					pop(T_INT);
					pop(T_INT);
					r.rIndex += 2;
					return;
				}
				case IFNULL, IFNONNULL -> {
					pop();
					r.rIndex += 2;
					return;
				}
				case IF_acmpeq, IF_acmpne -> {
					pop();
					pop();
					r.rIndex += 2;
					return;
				}
				case GOTO -> {
					r.rIndex += 2;
					return;
				}
				case GOTO_W -> {
					r.rIndex += 4;
					return;
				}
				case TABLESWITCH -> {
					// align
					r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
					r.rIndex += 4;
					int low = r.readInt();
					int hig = r.readInt();
					int count = hig - low + 1;

					r.rIndex += count << 2;

					pop(T_INT);
					return;
				}
				case LOOKUPSWITCH -> {
					r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
					r.rIndex += 4;
					int count = r.readInt();

					r.rIndex += count << 3;

					pop(T_INT);
					return;
				}
				case POP -> pop1();
				case POP2 -> {
					t1 = pop();
					if (!isDual(t1)) pop1();
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
					t1 = pop();
					if (isDual(t1)) {
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
					t1 = pop();
					t2 = isDual(t1) ? null : pop1();
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
					t1 = pop();
					t2 = isDual(t1) ? null : pop1();
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

	private static Var2 componentType(Var2 array) {
		String type = array.owner;
		type = type.charAt(1) == 'L' ? type.substring(2, type.length() - 1) : type.substring(1);
		return new Var2(T_REFERENCE, type);
	}

	private static boolean isDual(Var2 item) {return item.type == T_DOUBLE || item.type == T_LONG;}

	private void math(byte type) {pop(type); pop(type); push(type);}
	private void cmp(byte type) {pop(type); pop(type); push(T_INT);}

	private void arrayLoad(byte type) {pop(T_INT); pop(); push(type);}
	private void arrayStore(byte type) {pop(type); pop(T_INT); pop();}

	private void ldc(Constant c) {
		switch (c.type()) {
			case Constant.DYNAMIC -> push(of(Type.fieldDesc(((CstDynamic) c).desc().rawDesc().str())));
			case Constant.INT -> push(T_INT);
			case Constant.LONG -> push(T_LONG);
			case Constant.FLOAT -> push(T_FLOAT);
			case Constant.DOUBLE -> push(T_DOUBLE);
			case Constant.CLASS -> push("java/lang/Class");
			case Constant.STRING -> push("java/lang/String");
			case Constant.METHOD_TYPE -> push("java/lang/invoke/MethodType");
			case Constant.METHOD_HANDLE -> push("java/lang/invoke/MethodHandle");
			default -> throw new IllegalArgumentException("不支持的常量:"+c);
		}
	}

	private void invokeDynamic(CstDynamic dyn, int reserved) {invoke(INVOKESTATIC, null, dyn.desc());}
	private void invoke(byte code, CstRef method) {invoke(code, method, method.nameAndType());}
	private void invoke(byte code, CstRef method, CstNameAndType desc) {
		ArrayList<Type> arguments = tmpList; arguments.clear();
		Type returnType = Type.methodDesc(desc.rawDesc().str(), arguments);
		for (int i = arguments.size() - 1; i >= 0; i--) {
			pop(of(arguments.get(i)));
		}

		if (code != INVOKESTATIC) {
			Var2 instance = new Var2(T_REFERENCE, method.clazz().value().str());
			Var2 pop = pop(instance);

			if (method.name().equals("<init>") && (pop.type == T_UNINITIAL_THIS || pop.type == T_UNINITIAL)) {
				if (pop.type == T_UNINITIAL_THIS) instance.owner = this.method.owner();

				for (int i = 0; i < currentLocals.length; i++) {
					Var2 x = currentLocals[i];
					if (x == pop) currentLocals[i] = instance;
				}
				for (int i = 0; i < currentStackSize; i++) {
					Var2 x = currentStack[i];
					if (x == pop) currentStack[i] = instance;
				}
			}
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
			case ALOAD, DLOAD, FLOAD, LLOAD, ILOAD -> push(get(vid));
			case ASTORE -> set(vid, pop());
			case DSTORE -> set(vid, pop(T_DOUBLE));
			case FSTORE -> set(vid, pop(T_FLOAT));
			case LSTORE -> set(vid, pop(T_LONG));
			case ISTORE -> set(vid, pop(T_INT));
		}
	}

	private Var2 get(int i) {
		if (i >= currentLocals.length) throw new FastFailException("未定义的变量#"+i);
		Var2 item = currentLocals[i];
		if (item == null) throw new FastFailException("未定义的变量#"+i);
		return item;
	}
	private void set(int i, Var2 type) {
		if (i >= currentLocals.length) currentLocals = Arrays.copyOf(currentLocals, i+1);

		Var2 prev = currentLocals[i];
		currentLocals[i] = type;
		if (isDual(type)) set(i+1, SECOND);
		if (prev == null || prev == type) return;

		if (isDual(prev) && !isDual(type)) {
			assert currentLocals[i+1] == SECOND;
			currentLocals[i+1] = TOP;
		}

		// 对于任何一个在异常处理器[startBci,endBci]之间的基本块，记录每一个变量slot被assign时的类型，如果与start之前的状态不符，那么在异常处理器中它就是TOP类型
		// JVMS 4.10.1.6
		if (!exceptionHandlers.isEmpty() && (prev.type >= T_NULL ? T_NULL : prev.type) != (prev.type >= T_NULL ? T_NULL : type.type)) {
			current.reassignedLocal(i);
		}
	}

	private Var2 pop1() {
		Var2 item = pop();
		if (isDual(item)) throw new FastFailException(item+"不能用pop1处理");
		return item;
	}

	private Var2 pop(byte type) {return pop(of(type, null));}
	private Var2 pop(String type) {return pop(new Var2(T_REFERENCE, type));}
	private Var2 pop(Var2 exceptType) {
		var item = pop();
		if (exceptType != null && item.type != T_NULL) {
			item.mergeWith(exceptType);
		}
		return item;
	}
	private Var2 pop() {
		if (currentStackSize == 0) throw new FastFailException("Attempt to pop empty stack.");
		var item = currentStack[--currentStackSize];
		currentStack[currentStackSize] = null;
		return item;
	}

	private void push(byte type) {push(of(type, null));}
	private void push(String owner) {push(new Var2(T_REFERENCE, owner));}
	private void push(Var2 type) {
		if (currentStackSize >= currentStack.length)
			currentStack = Arrays.copyOf(currentStack, currentStack.length+4);
		currentStack[currentStackSize++] = type;
	}
	// endregion
	// region Frame writing
	/**
	 * 将StackMapTable的帧数据从字节缓冲区读取为 {@link Frame} 对象列表。
	 *
	 * @param frames  用于存储读取到的帧的列表。
	 * @param r       包含帧信息的 {@link DynByteBuf}。
	 * @param cp      常量池 {@link ConstantPool}。
	 * @param cw      代码写入器 {@link AbstractCodeWriter}，用于获取位置信息。
	 * @param owner   当前方法的所属类名。
	 * @param maxLocals    方法的最大局部变量槽位数量。
	 * @param maxState    方法的最大栈深度。
	 */
	public static void readFrames(List<Frame> frames, DynByteBuf r, ConstantPool cp, AbstractCodeWriter cw, String owner, int maxLocals, int maxState) {
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
					frame.stack = new Var2[]{getVar(cp, r, cw, owner)};
				}
				case same_local_1_stack_ex -> {
					off = r.readUnsignedShort();
					frame.stack = new Var2[]{getVar(cp, r, cw, owner)};
				}
				case append -> {
					off = r.readUnsignedShort();
					int len = type - 251;
					frame.locals = new Var2[len];
					for (int j = 0; j < len; j++) {
						frame.locals[j] = getVar(cp, r, cw, owner);
					}
				}
				case full -> {
					off = r.readUnsignedShort();

					int len = r.readUnsignedShort();
					if (len > maxLocals) throw new IllegalStateException("Full帧的变量数量超过限制("+len+") > ("+maxLocals+")");

					frame.locals = new Var2[len];
					for (int j = 0; j < len; j++) frame.locals[j] = getVar(cp, r, cw, owner);

					len = r.readUnsignedShort();
					if (len > maxState) throw new IllegalStateException("Full帧的栈大小超过限制("+len+") > ("+maxState+")");

					frame.stack = new Var2[len];
					for (int j = 0; j < len; j++) frame.stack[j] = getVar(cp, r, cw, owner);
				}
			}

			allOffset += off+1;
			frame.pcLabel = cw._monitor(allOffset);
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

	/**
	 * 将 {@link Frame} 对象列表写入到字节缓冲区，生成StackMapTable属性。
	 *
	 * @param frames  要写入的帧列表。
	 * @param w       要写入的字节缓冲区。
	 * @param cp      常量池 {@link ConstantPool}（用于写入类名）。
	 */
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
					putVar(frame.stack[0], w, cp);
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

					w.putShort(frame.stack.length);
					for (int i = 0, e = frame.stack.length; i < e; i++) {
						putVar(frame.stack[i], w, cp);
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