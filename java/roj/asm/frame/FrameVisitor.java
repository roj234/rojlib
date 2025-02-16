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
	private static final String ANY_OBJECT_TYPE = "java/lang/Object";

	public int maxStackSize, maxLocalSize;

	private boolean firstBlockOnly;
	private MethodNode method;
	private Var2[] initLocal;

	private final IntMap<BasicBlock> stateIn = new IntMap<>();
	private final List<BasicBlock> stateOut = new SimpleList<>();
	private final IntMap<List<BasicBlock>> jumpTo = new IntMap<>();

	private int bci;
	private BasicBlock current;
	private boolean controlFlowTerminate;

	private final SimpleList<Type> tmpList = new SimpleList<>();
	@Deprecated private final CharList tmpSb = new CharList();

	@Deprecated
	public Frame visitFirst(MethodNode owner, DynByteBuf r, ConstantPool cp) {
		visitBegin(owner, 0);
		firstBlockOnly = true;
		visitBytecode(r, cp);

		Frame f0 = new Frame();
		f0.locals = Arrays.copyOf(current.outLocal, current.outLocalSize);
		f0.stacks = Arrays.copyOf(current.outStack, current.outStackSize);
		return f0;
	}

	/**
	 * 初始化
	 *
	 * @param method
	 * @param flags
	 */
	public void visitBegin(MethodNode method, int flags) {
		this.method = method;

		firstBlockOnly = false;
		stateIn.clear();
		jumpTo.clear();
		current = add(0, "begin");

		boolean isConstructor = method.name().equals("<init>");
		int slotBegin = 0 == (method.modifier()&ACC_STATIC) ? 1 : 0;

		if (slotBegin != 0) { // this
			set(0, new Var2(isConstructor ? T_UNINITIAL_THIS : T_REFERENCE, method.ownerClass()));
		} else if (isConstructor) {
			throw new IllegalArgumentException("static constructor");
		}

		List<Type> types = method.parameters();
		for (int i = 0; i < types.size(); i++) {
			Var2 v = castType(types.get(i));
			if (v == null) throw new IllegalArgumentException("Unexpected VOID at param["+i+"]");
			set(slotBegin++, v);
			if (v.type == T_DOUBLE || v.type == T_LONG) set(slotBegin++, TOP);
		}

		initLocal = Arrays.copyOf(current.outLocal, current.outLocalSize);
	}
	/**
	 * Visit CodeBlock [Jump | Switch]
	 * CodeWriter在固定偏移量(stage2)之后调用
	 */
	public void visitBlocks(List<CodeBlock> blocks) {
		for (int i = 0; i < blocks.size(); i++) {
			CodeBlock block = blocks.get(i);
			if (block instanceof JumpBlock js) {
				List<BasicBlock> list;

				BasicBlock jumpTarget = add(js.target.getValue(), "jump target");
				// normal execution
				if (js.code != GOTO && js.code != GOTO_W) {
					BasicBlock ifNext = add(js.fv_bci+3, "if next");
					if (ifNext.refCount == 0) ifNext.noFrame = true;
					list = Arrays.asList(jumpTarget,ifNext);
				} else {
					list = Collections.singletonList(jumpTarget);
				}
				jumpTo.putInt(js.fv_bci, list);
			} else if (block.getClass() == SwitchBlock.class) {
				SwitchBlock ss = (SwitchBlock) block;
				List<BasicBlock> list = Arrays.asList(new BasicBlock[ss.targets.size()+1]);

				list.set(0, add(ss.def.getValue(), "switch default"));
				for (int j = 0; j < ss.targets.size();j++) {
					list.set(j+1, add(ss.targets.get(j).bci(), "switch branch"));
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
		BasicBlock exceptionBeginRef = add(start, "exception handler#"+handler+".beginRef");
		if (exceptionBeginRef.refCount == 0) exceptionBeginRef.noFrame = true;

		current = add(handler, "exception handler#["+start+','+end+"]: "+type);
		push(type == null ? "java/lang/Throwable" : type);
		exceptionBeginRef.to(current);
	}
	private BasicBlock add(int pos, String desc) {
		BasicBlock target = stateIn.get(pos);
		if (target == null) stateIn.putInt(pos, target = new BasicBlock(pos, desc));
		else target.merge(desc, false);
		return target;
	}

	public List<Frame> finish(DynByteBuf code, ConstantPool cp) {
		if (stateIn.size() <= 1) return null;

		current = stateIn.get(0);
		controlFlowTerminate = false;
		try {
			visitBytecode(code, cp);
			if (!stateIn.isEmpty()) throw new FastFailException("这些节点不是字节码开始或不存在: "+stateIn.values());
		} catch (Throwable e) {
			throw new IllegalStateException("Parse failed near BCI#"+bci+"\n in method "+method+"\n code "+code.dump(), e);
		} finally {
			//method = null;
			current = null;
			tmpList.clear();
			tmpSb.clear();
			stateIn.clear();
		}

		System.out.println("FV========================================");

		List<BasicBlock> blocks = stateOut;
		List<Frame> frames = new SimpleList<>();

		maxLocalSize = 0;
		for (int i = 1; i < blocks.size(); i++) {
			BasicBlock block = blocks.get(i);
			maxLocalSize = Math.max(maxLocalSize, Math.max(block.outLocalSize,block.inLocalSize));
		}

		for (int i = 0; i < blocks.size(); i++) {
			blocks.get(i).ensureCapacity(maxStackSize, maxLocalSize, initLocal);
		}

		try {
			for (int i = 0; i < blocks.size(); i++) {
				blocks.get(0).traverse(new SimpleList<>(), i); // link frames
			}
		} catch (Exception e) {
			synchronized (FrameVisitor.class) {
				System.out.println("Parse failed near BCI#"+bci+"\n in method "+method);
				e.printStackTrace();
				for (int i = 0; i < blocks.size(); i++) {
					System.out.println(blocks.get(i));
				}
				System.out.println("========================================FV");
			}
		}


		Var2[] lastLocal = initLocal;
		for (int i = 1; i < blocks.size(); i++) {
			var block = blocks.get(i);
			if (block.noFrame) continue;

			Var2[] stack = Arrays.copyOf(block.startStack, block.startStackSize);
			Var2[] local = Arrays.copyOf(block.startLocal, block.startLocalSize);
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
						frame.type = (short) (stack.length == 0 ? same : same_local_1_stack);
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

							frame.type = (short) (delta < 0 ? chop : append);
							if (delta > 0) frame.locals = local;
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
		System.out.println(maxLocalSize+","+maxStackSize);
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
				if (firstBlockOnly) return;
				throw new IllegalStateException("ControlFlowTerminate flag error at BCI#"+bci+"("+Opcodes.showOpcode(prev)+") ");
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
				/*case NOP,LNEG, FNEG, DNEG, INEG, I2B, I2C, I2S -> {}*/

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
				case LDC -> ldc(cp.array(r.readUnsignedByte()));
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
					// set(id, get(id, INT));
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
					pop("[");
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
				case PUTFIELD, GETFIELD, PUTSTATIC, GETSTATIC -> field(code, (CstRefField) cp.get(r));
				case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC -> invoke(code, (CstRef) cp.get(r));
				case INVOKEINTERFACE -> {
					invoke(code, (CstRef) cp.get(r));
					r.rIndex += 2;
				}
				case INVOKEDYNAMIC -> invokeDynamic((CstDynamic) cp.get(r), r.readUnsignedShort());
				case JSR, JSR_W, RET -> ret();
				case NEWARRAY -> {
					pop(T_INT);
					push("["+AbstractCodeWriter.FromPrimitiveArrayId(r.readByte()));
				}
				case INSTANCEOF -> {
					r.rIndex += 2;
					pop(ANY_OBJECT_TYPE);
					push(T_INT);
				}
				case NEW, ANEWARRAY, CHECKCAST -> clazz(code, ((CstClass) cp.get(r)).name().str());
				case MULTIANEWARRAY -> {
					var arrayType = ((CstClass) cp.get(r)).name().str();
					int arrayDepth = r.readUnsignedByte();
					while (arrayDepth-- > 0) pop(T_INT);
					push(arrayType);
				}
				case RETURN -> controlFlowTerminate = true;
				case IRETURN, FRETURN, LRETURN, DRETURN, ARETURN -> {
					pop(castType(method.returnType()));
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

	private void clazz(byte code, String className) {
		switch (code) {
			case CHECKCAST -> {
				pop(ANY_OBJECT_TYPE);
				push(className);
			}
			case NEW -> push(of(T_UNINITIAL, className));
			case ANEWARRAY -> {
				pop(T_INT);
				if (className.endsWith(";") || className.startsWith("[")) push("[".concat(className));
				else {
					tmpSb.clear();
					push(tmpSb.append("[L").append(className).append(';').toString());
				}
			}
		}
	}
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
	private void invokeDynamic(CstDynamic dyn, int type) {
		CstNameAndType nat = dyn.desc();

		SimpleList<Type> arguments = tmpList; arguments.clear();
		Type.methodDesc(nat.getType().str(), arguments);

		Type retVal = arguments.remove(arguments.size()-1);
		for (int i = arguments.size() - 1; i >= 0; i--) {
			pop(castType(arguments.get(i)));
		}
		pushD(castType(retVal));
	}
	private void invoke(byte code, CstRef method) {
		CstNameAndType nat = method.desc();

		SimpleList<Type> arguments = tmpList; arguments.clear();
		Type.methodDesc(nat.getType().str(), arguments);

		Type returnType = arguments.remove(arguments.size()-1);
		for (int i = arguments.size() - 1; i >= 0; i--) {
			pop(castType(arguments.get(i)));
		}

		if (code != INVOKESTATIC) {
			Var2 instance = new Var2(T_REFERENCE, method.clazz().name().str());
			instance.bci = bci;
			pop(instance); // Optionally initialize T_UNINITIALIZED
		}

		Var2 returnValue = castType(returnType);
		if (returnValue != null) push(returnValue);
	}
	private void field(byte code, CstRefField field) {
		Var2 fType = castType(Type.fieldDesc(field.desc().getType().str()));
		switch (code) {
			case GETSTATIC -> push(fType);
			case PUTSTATIC -> pop(fType);
			case GETFIELD -> {
				pop(field.clazz().name().str());
				push(fType);
			}
			case PUTFIELD -> {
				pop(fType);
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
		if (firstBlockOnly) {
			controlFlowTerminate = true;
			return;
		}

		List<BasicBlock> successors = jumpTo.remove(bci);
		if (successors == null) {
			new Throwable("在"+bci+"处找不到期待的分支节点").printStackTrace();
			System.out.println(jumpTo);
			System.exit(0);
			return;
		}

		for (int i = 0; i < successors.size(); i++) {
			BasicBlock t = successors.get(i);
			current.to(t);
		}
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
	private void ret() { throw new UnsupportedOperationException("JSR/RET is deprecated in java7 when StackFrameTable added"); }

	private Var2 castType(Type t) {return of(t);}

	private Var2 get(int i, byte type) { return get(i, of(type, null)); }
	private Var2 get(int i, String type) { return get(i, new Var2(T_REFERENCE, type)); }
	private Var2 get(int i, Var2 v) {
		BasicBlock s = current;
		if (i < s.outLocalSize && s.outLocal[i] != null) {
			s.outLocal[i].verify(v);
			return s.outLocal[i];
		}

		if (i >= s.inLocal.length) s.inLocal = Arrays.copyOf(s.inLocal, i+1);
		if (s.inLocalSize <= i) s.inLocalSize = i+1;

		Var2[] inherit = s.inLocal;
		if (inherit[i] != null) {
			inherit[i].verify(v);
			return inherit[i];
		} else {
			return inherit[i] = v;
		}
	}
	private void set(int i, Var2 v) {
		BasicBlock s = current;
		if (i >= s.outLocal.length) s.outLocal = Arrays.copyOf(s.outLocal, i+1);
		if (s.outLocalSize <= i) s.outLocalSize = i+1;

		s.outLocal[i] = v;
		if (v.type == T_LONG || v.type == T_DOUBLE) set(i+1, TOP);
	}

	private Var2 pop(byte type) { return pop(of(type, null)); }
	private Var2 pop(String type) { return pop(new Var2(T_REFERENCE, type)); }
	private Var2 pop1() {
		Var2 v = pop((Var2) null);
		if (v.type == T_LONG || v.type == T_DOUBLE) throw new IllegalArgumentException(v+"不能用pop1处理");
		return v;
	}
	private Var2 pop2() { return pop(new Var2(T_ANY2)); }
	private Var2 pop(Var2 exceptType) {
		BasicBlock s = current;
		if (s.outStackSize != 0) {
			Var2 v1 = s.outStack[--s.outStackSize];
			if (exceptType != null) {
				// simulation of 1xT2 as 2xT1 failed
				if (v1.type == T_ANY2 && exceptType.type != T_LONG && exceptType.type != T_DOUBLE) {
					Var2 v2 = s.outStack[s.outStackSize-1];
					if (v2.type == T_LONG || v2.type == T_DOUBLE)
						throw new IllegalArgumentException("pop any2 "+v1+"&"+v2);
				}
				v1.verify(exceptType);
			}
			return v1;
		}

		if (exceptType == null) exceptType = any();

		if (s.inStackSize >= s.inStack.length) s.inStack = Arrays.copyOf(s.inStack, s.inStackSize+1);
		s.inStack[s.inStackSize++] = exceptType;

		maxStackSize = Math.max(maxStackSize, s.inStackSize +s.outStackSize);
		return exceptType;
	}

	private void push(byte type) { push(of(type, null)); }
	private void push(String owner) { push(new Var2(T_REFERENCE, owner)); }
	private void push(Var2 v) {pushD(v);}
	private void pushD(Var2 v) {
		BasicBlock s = current;
		if (s.outStackSize >= s.outStack.length) s.outStack = Arrays.copyOf(s.outStack, s.outStackSize +1);
		s.outStack[s.outStackSize++] = v;

		maxStackSize = Math.max(maxStackSize, s.inStackSize +s.outStackSize);
	}
	// endregion
	// region Frame merge / compute

	public static String getCommonUnsuperClass(String type1, String type2) {return ClassUtil.getInstance().getCommonChild(type1, type2);}
	public static String getCommonSuperClass(String type1, String type2) {return ClassUtil.getInstance().getCommonParent(type1, type2);}

	// endregion
	// region Frame writing
	public static void readFrames(List<Frame> fr, DynByteBuf r, ConstantPool cp, AbstractCodeWriter pc, String owner, int lMax, int sMax) {
		fr.clear();

		int allOffset = -1;
		int tableLen = r.readUnsignedShort();
		while (tableLen-- > 0) {
			int type = r.readUnsignedByte();
			Frame f = fromVarietyType(type);
			fr.add(f);

			int off = -1;
			switch (f.type) {
				case same:
					off = type;
					break;
				case same_ex:
					// keep original chop count
				case chop, chop2, chop3:
					off = r.readUnsignedShort();
					break;
				case same_local_1_stack:
					off = type - 64;
					f.stacks = new Var2[] { getVar(cp, r, pc, owner) };
					break;
				case same_local_1_stack_ex:
					off = r.readUnsignedShort();
					f.stacks = new Var2[] { getVar(cp, r, pc, owner) };
					break;
				case append: {
					off = r.readUnsignedShort();
					int len = type - 251;
					f.locals = new Var2[len];
					for (int j = 0; j < len; j++) {
						f.locals[j] = getVar(cp, r, pc, owner);
					}
				}
				break;
				case full: {
					off = r.readUnsignedShort();

					int len = r.readUnsignedShort();
					if (len > lMax) throw new IllegalStateException("Full帧的变量数量超过限制("+len+") > ("+lMax+")");

					f.locals = new Var2[len];
					for (int j = 0; j < len; j++) f.locals[j] = getVar(cp, r, pc, owner);

					len = r.readUnsignedShort();
					if (len > sMax) throw new IllegalStateException("Full帧的栈大小超过限制("+len+") > ("+sMax+")");

					f.stacks = new Var2[len];
					for (int j = 0; j < len; j++) f.stacks[j] = getVar(cp, r, pc, owner);
				}
				break;
			}

			allOffset += off+1;
			f.monitor_bci = pc._monitor(allOffset);
		}
	}
	private static Var2 getVar(ConstantPool pool, DynByteBuf r, AbstractCodeWriter pc, String owner) {
		byte type = r.readByte();
		return switch (type) {
			case T_REFERENCE -> new Var2(type, pool.getRefName(r));
			case T_UNINITIAL -> new Var2(pc._monitor(r.readUnsignedShort()));
			case T_UNINITIAL_THIS -> new Var2(type, owner);
			default -> of(type, null);
		};
	}

	@SuppressWarnings("fallthrough")
	public static void writeFrames(List<Frame> frames, DynByteBuf w, ConstantPool cp) {
		Frame prev = null;
		for (int j = 0; j < frames.size(); j++) {
			Frame curr = frames.get(j);

			int offset = curr.bci();
			if (j > 0) offset -= prev.bci() + 1;

			if ((offset & ~0xFFFF) != 0)
				throw new IllegalArgumentException("Illegal frame delta " + offset + ":\n curr=" + curr + "\n prev=" + prev);

			short type = curr.type;
			switch (type) {
				case same:
					if (offset < 64) {
						type = (short) offset;
					} else {
						curr.type = type = same_ex;
					}
					break;
				case same_local_1_stack:
					if (offset < 64) {
						type = (short) (offset + 64);
					} else {
						curr.type = type = same_local_1_stack_ex;
					}
					break;
				case append:
					type = (short) (251 + curr.locals.length);
					break;
			}
			w.put((byte) type);
			switch (curr.type) {
				case same: break;
				case same_local_1_stack_ex:
					w.putShort(offset);
				case same_local_1_stack:
					putVar(curr.stacks[0], w, cp);
					break;
				case chop, chop2, chop3:
				case same_ex:
					w.putShort(offset);
					break;
				case append:
					w.putShort(offset);
					for (int i = curr.locals.length + 251 - type, e = curr.locals.length; i < e; i++) {
						putVar(curr.locals[i], w, cp);
					}
					break;
				case full:
					w.putShort(offset).putShort(curr.locals.length);
					for (int i = 0, e = curr.locals.length; i < e; i++) {
						putVar(curr.locals[i], w, cp);
					}

					w.putShort(curr.stacks.length);
					for (int i = 0, e = curr.stacks.length; i < e; i++) {
						putVar(curr.stacks[i], w, cp);
					}
					break;
			}

			prev = curr;
		}
	}

	private static void putVar(Var2 v, DynByteBuf w, ConstantPool cp) {
		w.put(v.type);
		switch (v.type) {
			case T_REFERENCE: w.putShort(cp.getClassId(v.owner)); break;
			case T_UNINITIAL: w.putShort(v.bci()); break;
		}
	}
	// endregion
}