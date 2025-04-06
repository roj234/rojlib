package roj.asm.frame;

import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.insn.AbstractCodeWriter;
import roj.asm.insn.CodeBlock;
import roj.asm.insn.JumpBlock;
import roj.asm.insn.SwitchBlock;
import roj.asm.type.Type;
import roj.asm.util.ClassUtil;
import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.compiler.resolve.TypeCast;
import roj.io.FastFailException;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.asm.frame.Frame.*;
import static roj.asm.frame.Var2.*;

/**
 * @author Roj234
 * @since 2022/11/17 0017 13:09
 */
public class FrameVisitor {
	private static final boolean DEADCODE_LENIENT = true;
	private static final String ANY_OBJECT_TYPE = "java/lang/Object";

	public int maxStackSize, maxLocalSize;

	private MethodNode method;
	private Var2[] initLocal;

	private final IntMap<BasicBlock> stateIn = new IntMap<>();
	private final List<BasicBlock> stateOut = new SimpleList<>();
	private final IntMap<List<BasicBlock>> jumpTo = new IntMap<>();
	private final List<BasicBlock> exceptionHandlers = new SimpleList<>();

	private int bci;
	private BasicBlock current;
	private boolean controlFlowTerminate;

	private final SimpleList<Type> tmpList = new SimpleList<>();
	private final CharList sb = new CharList();

	/**
	 * 初始化
	 */
	private void init(MethodNode method) {
		this.method = method;

		stateIn.clear();
		jumpTo.clear();
		exceptionHandlers.clear();
		current = add(0, "begin", false);

		boolean isConstructor = method.name().equals("<init>");
		int slotBegin = 0 == (method.modifier()&ACC_STATIC) ? 1 : 0;

		if (slotBegin != 0) { // this
			set(0, new Var2(isConstructor ? T_UNINITIAL_THIS : T_REFERENCE, method.ownerClass()));
		} else if (isConstructor) {
			throw new IllegalArgumentException("静态的构造器");
		}

		List<Type> types = method.parameters();
		for (int i = 0; i < types.size(); i++) {
			Var2 v = of(types.get(i));
			if (v == null) throw new IllegalArgumentException("参数["+i+"]不允许是Void类型");
			set(slotBegin++, v);
			if (v.type == T_DOUBLE || v.type == T_LONG) set(slotBegin++, TOP);
		}

		initLocal = Arrays.copyOf(current.assignedLocal, current.assignedLocalCount);
	}
	/**
	 * Visit CodeBlock [Jump | Switch]
	 * CodeWriter在固定偏移量(stage2)之后调用
	 */
	public void visitBlocks(MethodNode mn, List<CodeBlock> blocks) {
		init(mn);

		for (int i = 0; i < blocks.size(); i++) {
			CodeBlock block = blocks.get(i);
			if (block instanceof JumpBlock js) {
				List<BasicBlock> list;

				BasicBlock jumpTarget = add(js.target.getValue(), "jump target", true);
				// normal execution
				if (js.code != GOTO && js.code != GOTO_W) {
					BasicBlock ifNext = add(js.fv_bci+3, "if next", false);
					list = Arrays.asList(jumpTarget,ifNext);
				} else {
					list = Collections.singletonList(jumpTarget);
				}
				jumpTo.putInt(js.fv_bci, list);
			} else if (block.getClass() == SwitchBlock.class) {
				SwitchBlock ss = (SwitchBlock) block;
				List<BasicBlock> list = Arrays.asList(new BasicBlock[ss.targets.size()+1]);

				list.set(0, add(ss.def.getValue(), "switch default", true));
				for (int j = 0; j < ss.targets.size();j++) {
					list.set(j+1, add(ss.targets.get(j).bci(), "switch branch", true));
				}
				jumpTo.putInt(ss.fv_bci, list);
			}
		}
	}
	/**
	 * Visit Exception Entries
	 * CodeWriter在固定偏移量(stage2)之后调用
	 */
	public void visitException(int start, int end, int handler, String type) {
		var _handler = current = add(handler, "exception #["+start+','+end+"].handler", true);
		push(type == null ? "java/lang/Throwable" : type);

		_handler.reachable = true;
		_handler.startStack = new Var2[] {_handler.outStack[0]};

		// 对于任何一个在异常处理器[start,end]之间的基本块，记录每一个变量slot被assign时的类型，如果与start之前的状态不符，那么在异常处理器中它就是TOP类型
		var _begin = add(start, "exception #["+start+','+end+"].begin", false);
		var _end = add(end, "exception #["+start+','+end+"].end", false);

		exceptionHandlers.add(_begin);
		exceptionHandlers.add(_end);
		exceptionHandlers.add(_handler);
	}
	private BasicBlock add(int pos, String desc, boolean isFrame) {
		BasicBlock target = stateIn.get(pos);
		if (target == null) stateIn.putInt(pos, target = new BasicBlock(pos, desc));
		else target.merge(desc);
		if (isFrame) target.isFrame = true;
		return target;
	}

	public List<Frame> finish(DynByteBuf code, ConstantPool cp, boolean generateFrames) {
		current = null;
		controlFlowTerminate = false;
		try {
			visitBytecode(code, cp);
			if (!stateIn.isEmpty()) throw new FastFailException("这些节点不是字节码开始或不存在: "+stateIn.values());
		} catch (Throwable e) {
			throw new IllegalStateException("Parse failed near BCI#"+bci+"\n in method "+method+"\n code "+code.dump(), e);
		}

		List<BasicBlock> blocks = stateOut;

		maxLocalSize = 0;
		for (int i = 0; i < blocks.size(); i++) {
			var block = blocks.get(i);
			maxLocalSize = Math.max(maxLocalSize, Math.max(block.assignedLocalCount, block.usedLocalCount));
		}

		if (blocks.size() <= 1 || !generateFrames) return null;

		System.out.println("FV========================================");

		var frames = new SimpleList<Frame>();
		var tmp = new MyHashSet<BasicBlock>();

		var first = blocks.get(0);
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

		Var2[] lastLocal = initLocal;
		for (int i = 1; i < blocks.size(); i++) {
			var block = blocks.get(i);
			if (!block.reachable) {
				if (DEADCODE_LENIENT) {
					new IllegalStateException(block.toString()).printStackTrace();
				} else {
					throw new IllegalStateException(block.toString());
				}
			}
			if (!block.isFrame) continue;

			Var2[] stack = block.startStack;
			Var2[] local;

			int startLocalSize = -1;
			for (int j = 0; j < block.startLocal.length;) {
				Var2 item = block.startLocal[j++];
				if (item != null && item.type != T_TOP) startLocalSize = j;
			}

			local = startLocalSize < 0 ? NONE : Arrays.copyOf(block.startLocal, startLocalSize);
			for (int j = 0; j < local.length; j++) {
				Var2 item = local[j];
				if (item == null) local[j] = TOP;
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

		System.out.println(frames);
		System.out.println("========================================FV");
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

			BasicBlock next = stateIn.remove(bci);
			if (next != null) {
				// if
				if (!controlFlowTerminate && current != null) current.to(next);
				current = next;
				stateOut.add(next);
				controlFlowTerminate = false;
			}
			if (controlFlowTerminate) {
				if (DEADCODE_LENIENT) {
					current = new BasicBlock(bci, "unreachable");
					controlFlowTerminate = false;
					new IllegalStateException("无法访问的代码: #"+bci+"("+Opcodes.showOpcode(prev)+") ").printStackTrace();
				} else {
					throw new IllegalStateException("无法访问的代码: #"+bci+"("+Opcodes.showOpcode(prev)+") ");
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
					Var2 value = pop("java/lang/Object");
					pop(T_INT);
					Var2 arrayType = pop("[Ljava/lang/Object;");
					String v = arrayType.owner;
					value.verify(new Var2(T_REFERENCE, v.substring(2, v.length() - 1)));
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
				case RETURN -> controlFlowTerminate = true;
				case IRETURN, FRETURN, LRETURN, DRETURN, ARETURN -> {
					pop(of(method.returnType()));
					controlFlowTerminate = true;
				}
				case ATHROW -> {
					pop("java/lang/Throwable");
					controlFlowTerminate = true;
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
					controlFlowTerminate = true;
					jump();
				}
				case GOTO_W -> {
					r.rIndex += 4;
					controlFlowTerminate = true;
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
				String typeStr = ((CstDynamic) c).desc().getType().str();
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
	private void invoke(byte code, CstRef method) {invoke(code, method, method.desc());}
	private void invoke(byte code, CstRef method, CstNameAndType desc) {
		SimpleList<Type> arguments = tmpList; arguments.clear();
		Type.methodDesc(desc.getType().str(), arguments);

		Type returnType = arguments.remove(arguments.size()-1);
		for (int i = arguments.size() - 1; i >= 0; i--) {
			pop(of(arguments.get(i)));
		}

		if (code != INVOKESTATIC) {
			Var2 instance = new Var2(T_REFERENCE, method.clazz().name().str());
			instance.bci = bci;
			pop(instance); // Optionally initialize T_UNINITIALIZED
		}

		Var2 returnValue = of(returnType);
		if (returnValue != null) push(returnValue);
	}
	private void field(byte code, CstRef field) {
		Var2 fieldType = of(Type.fieldDesc(field.desc().getType().str()));
		switch (code) {
			case GETSTATIC -> push(fieldType);
			case PUTSTATIC -> pop(fieldType);
			case GETFIELD -> {
				pop(field.clazz().name().str());
				push(fieldType);
			}
			case PUTFIELD -> {
				pop(fieldType);
				pop(field.clazz().name().str());
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
		var branches = jumpTo.remove(bci);
		if (branches == null) throw new IllegalStateException("在#"+bci+"处找不到预期的分支节点, 列表: "+jumpTo);

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
		controlFlowTerminate = true;
		jump();
	}
	private void lookupSwitch(DynByteBuf r) {
		r.rIndex += 4;
		int count = r.readInt();

		r.rIndex += count << 3;

		pop(T_INT);
		controlFlowTerminate = true;
		jump();
	}
	private static void ret() { throw new UnsupportedOperationException("不允许使用JSR/RET操作"); }

	private Var2 get(int i, byte type) { return get(i, of(type, null)); }
	private Var2 get(int i, String owner) { return get(i, new Var2(T_REFERENCE, owner)); }
	private Var2 get(int i, Var2 type) {
		var block = current;
		if (i < block.assignedLocalCount && block.assignedLocal[i] != null) {
			block.assignedLocal[i].verify(type);
			return block.assignedLocal[i];
		}

		if (i >= block.usedLocal.length) block.usedLocal = Arrays.copyOf(block.usedLocal, i+1);
		if (block.usedLocalCount <= i) block.usedLocalCount = i+1;

		Var2[] used = block.usedLocal;
		if (used[i] != null) {
			used[i].verify(type);
			return used[i];
		} else {
			return used[i] = type;
		}
	}
	private void set(int i, Var2 type) {
		var block = current;
		if (i >= block.assignedLocal.length) block.assignedLocal = Arrays.copyOf(block.assignedLocal, i+1);
		if (block.assignedLocalCount <= i) block.assignedLocalCount = i+1;

		Var2 prev = block.assignedLocal[i];
		if (!exceptionHandlers.isEmpty() &&
				prev != null &&
				prev.type < 10 && type.type < 10 && (prev.type > 4 ? 5 : prev.type) != (type.type > 4 ? 5 : type.type)) {
			block.reassignedLocal(i);
		}

		block.assignedLocal[i] = type;
		if (type.type == T_LONG || type.type == T_DOUBLE) set(i+1, TOP);
		else if (prev == TOP && type != prev && i > 0) {
			Var2 prev2 = block.assignedLocal[i-1];
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

			block.maxStackSize -= v1.type == T_LONG || v1.type == T_DOUBLE ? 2 : 1;
			return v1;
		}

		if (exceptType == null) exceptType = any();

		if (block.consumedStackSize >= block.consumedStack.length)
			block.consumedStack = Arrays.copyOf(block.consumedStack, block.consumedStackSize+1);
		block.consumedStack[block.consumedStackSize++] = exceptType;

		block.maxStackSize += exceptType.type == T_LONG || exceptType.type == T_DOUBLE ? 2 : 1;
		maxStackSize = Math.max(maxStackSize, block.maxStackSize);
		return exceptType;
	}

	private void push(byte type) {push(of(type, null));}
	private void push(String owner) {push(new Var2(T_REFERENCE, owner));}
	private void push(Var2 type) {
		var block = current;
		if (block.outStackSize >= block.outStack.length)
			block.outStack = Arrays.copyOf(block.outStack, block.outStackSize+1);
		block.outStack[block.outStackSize++] = type;

		block.maxStackSize += type.type == T_LONG || type.type == T_DOUBLE ? 2 : 1;
		maxStackSize = Math.max(maxStackSize, block.maxStackSize);
	}
	// endregion
	public static String getConcreteChild(String type1, String type2) {return ClassUtil.getInstance().getCommonChild(type1, type2);}
	public static String getCommonParent(String type1, String type2) {return ClassUtil.getInstance().getCommonParent(type1, type2);}
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