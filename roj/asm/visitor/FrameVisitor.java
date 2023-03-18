package roj.asm.visitor;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.cst.*;
import roj.asm.frame.Var2;
import roj.asm.frame.VarType;
import roj.asm.misc.ReflectClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.InsnHelper;
import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.mapper.MapUtil;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.util.Arrays;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.asm.frame.VarType.*;
import static roj.asm.visitor.Frame2.*;

/**
 * @author Roj234
 * @since 2022/11/17 0017 13:09
 */
public class FrameVisitor {
	public static final ThreadLocal<FrameVisitor> LOCAL = new ThreadLocal<>();
	static final String ANY_OBJECT = "java/lang/Object";

	public int maxStackSize;
	public int maxLocalSize;
	public String asa;

	boolean firstOnly;
	MethodNode mn;
	DynByteBuf code;
	private final IntMap<BasicBlock2> stateIn = new IntMap<>();
	private final List<BasicBlock2> stateOut = new SimpleList<>();
	private final IntMap<List<BasicBlock2>> jumpTo = new IntMap<>();

	int bci;
	BasicBlock2 current;
	private boolean eof;

	SimpleList<Type> paramList = new SimpleList<>();
	CharList sb = new CharList();

	private List<Frame2> frames;

	void init(MethodNode owner) {
		mn = owner;

		firstOnly = false;
		stateIn.clear();
		jumpTo.clear();
		current = new BasicBlock2(0, "0: first frame");

		boolean _init_ = owner.name().equals("<init>");
		if (0 == (owner.modifier() & AccessFlag.STATIC)) { // this
			set(0, new Var2(_init_ ? UNINITIAL_THIS : REFERENCE, owner.ownerClass()));
		} else if (_init_) {
			throw new IllegalArgumentException("static <init> method");
		}

		int j = 0 == (owner.modifier()&AccessFlag.STATIC) ? 1 : 0;
		List<Type> types = owner.parameters();
		for (int i = 0; i < types.size(); i++) {
			Var2 v = castType(types.get(i));
			if (v == null) throw new IllegalArgumentException("Unexpected VOID at param[" + i + "]");
			set(j++, v);
			if (v.type == DOUBLE || v.type == LONG) set(j++, Var2.TOP);
		}

		initS = Arrays.copyOf(current.local, current.localMax);
		add(current);
	}
	private Var2[] initS;

	public Frame2 visitFirst(MethodNode owner, DynByteBuf r, ConstantPool cp) {
		init(owner);
		firstOnly = true;
		LOCAL.set(this);
		try {
			visitBytecode(r, cp);
		} finally {
			LOCAL.remove();
		}

		Frame2 f0 = new Frame2();
		f0.locals = Arrays.copyOf(current.local, current.localMax);
		f0.stacks = Arrays.copyOf(current.stack, current.stackSize);
		return f0;
	}

	// region Full frame computing methods

	void preVisit(DynByteBuf code, int codeBegin, List<Segment> segments) {
		for (int i = 1; i < segments.size();i++) {
			Segment s = segments.get(i);
			if (s.getClass() == JumpSegment.class) {
				JumpSegment jmp = (JumpSegment) s;

				BasicBlock2 fs = new BasicBlock2(jmp.target.getValue(), i+": "+OpcodeUtil.toString0(jmp.code)+" target");
				jumpTo.putInt(jmp.bci, new SimpleList<>(fs)); add(fs);

				// however is normal execution
				if (jmp.code != GOTO && jmp.code != GOTO_W) {
					fs = new BasicBlock2(jmp.bci + jmp.length, OpcodeUtil.toString0(jmp.code) + " target fail(" + i + ")");
					fs.noFrame = true;
					jumpTo.get(jmp.bci).add(fs);
					add(fs);
				}
			} else if (s.getClass() == SwitchSegment.class) {
				SwitchSegment sw = (SwitchSegment) s;
				List<BasicBlock2> list = Arrays.asList(new BasicBlock2[sw.targets.size()+1]);

				BasicBlock2 _default = new BasicBlock2(sw.def.getValue(), i+": switch default");
				list.set(0, _default);
				add(_default);
				for (int j = 0; j < sw.targets.size();) {
					BasicBlock2 branch = new BasicBlock2(sw.targets.get(j).getBci(), i+": switch branch # "+j);
					list.set(++j, branch);
					add(branch);
				}
				jumpTo.putInt(sw.bci, list);
			}
		}

		this.code = code.slice(codeBegin, code.wIndex()-codeBegin);
	}

	public void visitExceptionEntry(int start, int end, int handler, String type) {
		BasicBlock2 fs = new BasicBlock2(handler, "exception " + type + " handler in " + start + "," + end);
		current = fs;
		push(type == null ? "java/lang/Throwable" : type);
		add(fs);
	}
	private void add(BasicBlock2 fs) {
		BasicBlock2 fs1 = stateIn.putInt(fs.bci, fs);
		if (fs1 != null) fs.merge(fs1);
	}

	public void finish(CodeWriter c) {
		if (stateIn.size() <= 1) return;

		current = null;
		LOCAL.set(this);
		try {
			visitBytecode(code, c.cpw);
		} catch (Throwable e) {
			throw new RuntimeException("Bytecode iteration failed at BCI#" + bci, e);
		} finally {
			LOCAL.remove();
			//mn = null;
			code = null;
			current = null;
			paramList.clear();
			sb.clear();
		}

		if (!stateIn.isEmpty()) throw new IllegalArgumentException("以下BCI未找到: " + stateIn.values());

		List<BasicBlock2> blocks = stateOut;
		List<Frame2> frames = this.frames = new SimpleList<>();

		for (int i = 0; i < blocks.size(); i++) {
			blocks.get(i).updatePreHook();
		}
		blocks.get(0).chainUpdate(new MyHashSet<>(), new SimpleList<>(), initS); // link frames
		System.out.println("名"+mn.name());
		for (int i = 0; i < blocks.size(); i++) {
			blocks.get(i).updatePostHook();
			System.out.println(blocks.get(i));
		}

		Var2[] lastLocal = initS;

		maxLocalSize = maxStackSize = 0;
		for (int i = 1; i < blocks.size(); i++) {
			BasicBlock2 fs = blocks.get(i);
			maxLocalSize = Math.max(maxLocalSize, Math.max(fs.localMax,fs.inheritLocalMax));
			maxStackSize = 100;//Math.max(maxStackSize, fs.inheritStackSize+fs.stackSize);
			if (fs.noFrame) {
				blocks.remove(i--); continue;
			}

			Var2[] local = fs.vLocal;
			for (int j = 0; j < local.length; j++) {
				Var2 var2 = local[j];
				if (var2 == null) local[j] = Var2.TOP;
			}
			Var2[] stack = fs.vStack;

			Frame2 frame = new Frame2();
			frame.target = fs.bci;
			frame.locals = local;
			frame.stacks = stack;
			frames.add(frame);

			/*block:
			if (stack.length < 2) {
				// todo local equal
				if (eq(lastLocal, lastLocal.length, local, local.length)) {
					frame.type = (short) (stack.length == 0 ? same : same_local_1_stack);
				} else if (stack.length == 0) {
					int delta = local.length - lastLocal.length;
					if (delta == 0 || Math.abs(delta) > 3) break block;

					int j = Math.min(local.length, lastLocal.length);

					while (j-- > 0) {
						if (local[j] == null || !local[j].eq(lastLocal[j])) break block;
					}

					frame.type = (short) (delta < 0 ? chop : append);
				}
			}
			if (frame.type < 0) */frame.type = full;

			lastLocal = local;
		}

		asa = String.valueOf(frames);
		blocks.clear();

		int stack = c.visitAttributeI("StackMapTable");
		//frames.remove(0);
		c.bw.putShort(frames.size());
		writeFrames(frames, c.bw, c.cpw);
		c.visitAttributeIEnd(stack);

		//frames.clear();
	}

	// endregion

	public List<Frame2> getFrames() {
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

			BasicBlock2 next = stateIn.remove(bci);
			if (next != null) {
				// if
				if (!eof && current != null) current.to(next);
				current = next;
				stateOut.add(next);
				eof = false;
			}
			if (eof) {
				if (firstOnly) return;
				throw new IllegalStateException("End-of-segment prediction failed from " + OpcodeUtil.toString0(prev) +
					" at " + this.bci + "\nInside method: " + mn);
			}

			this.bci = bci;

			code = OpcodeUtil.byId(r.readByte());

			boolean widen = prev == Opcodes.WIDE;

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
				default:
				case NOP:
				case LNEG: case FNEG: case DNEG: case INEG:
				case I2B: case I2C: case I2S: break;

				case ACONST_NULL: push(NULL); break;
				case ICONST_M1: case ICONST_0:
				case ICONST_1: case ICONST_2: case ICONST_3: case ICONST_4: case ICONST_5:
					push(INT); break;
				case LCONST_0: case LCONST_1:
					push(LONG); break;
				case FCONST_0: case FCONST_1: case FCONST_2:
					push(FLOAT); break;
				case DCONST_0: case DCONST_1:
					push(DOUBLE); break;

				case IADD: case ISUB: case IMUL: case IDIV: case IREM:
				case IAND: case IOR: case IXOR:
				case ISHL: case ISHR: case IUSHR:
					math(INT); break;
				case FADD: case FSUB: case FMUL: case FDIV: case FREM:
					math(FLOAT); break;
				case LADD: case LSUB: case LMUL: case LDIV: case LREM:
				case LAND: case LOR: case LXOR:
					math(LONG); break;
				// 实质上应该是 pop, pop, push
				case LSHL: case LSHR: case LUSHR:
					pop(INT); break;
				case DADD: case DSUB: case DMUL: case DDIV: case DREM:
					math(DOUBLE); break;

				case LDC: ldc(cp.array(r.readUnsignedByte())); break;
				case LDC_W: case LDC2_W: ldc(cp.get(r)); break;

				case BIPUSH: push(INT); r.rIndex += 1; break;
				case SIPUSH: push(INT); r.rIndex += 2; break;

				case IINC:
					int id = widen ? r.readUnsignedShort() : r.readUnsignedByte();
					set(id, get(id, INT));
					r.rIndex += widen ? 2 : 1;
					break;

				case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE:
				case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD:
					var(code, widen ? r.readUnsignedShort() : r.readUnsignedByte());
					break;

				case FCMPL: case FCMPG: cmp(FLOAT); break;
				case DCMPL: case DCMPG: cmp(DOUBLE); break;
				case LCMP: cmp(LONG); break;

				case F2I: pop(FLOAT); push(INT); break;
				case L2I: pop(LONG); push(INT); break;
				case D2I: pop(DOUBLE); push(INT); break;
				case I2F: pop(INT); push(FLOAT); break;
				case L2F: pop(LONG); push(FLOAT); break;
				case D2F: pop(DOUBLE); push(FLOAT); break;
				case I2L: pop(INT); push(LONG); break;
				case F2L: pop(FLOAT); push(LONG); break;
				case D2L: pop(DOUBLE); push(LONG); break;
				case I2D: pop(INT); push(DOUBLE); break;
				case F2D: pop(FLOAT); push(DOUBLE); break;
				case L2D: pop(LONG); push(DOUBLE); break;

				case IALOAD: arrayLoad("[I"); break;
				case BALOAD: arrayLoad("[B"); break;
				case CALOAD: arrayLoad("[C"); break;
				case SALOAD: arrayLoad("[S"); break;
				case LALOAD: arrayLoad("[J", LONG); break;
				case FALOAD: arrayLoad("[F", FLOAT); break;
				case DALOAD: arrayLoad("[D", DOUBLE); break;
				case AALOAD: arrayLoadObj(); break;
				case IASTORE: arrayStore("[I"); break;
				case BASTORE: arrayStore("[B"); break;
				case CASTORE: arrayStore("[C"); break;
				case SASTORE: arrayStore("[S"); break;
				case FASTORE: arrayStore("[F", FLOAT); break;
				case LASTORE: arrayStore("[J", LONG); break;
				case DASTORE: arrayStore("[D", DOUBLE); break;
				case AASTORE:
					pop("java/lang/Object");
					pop(INT);
					pop("[Ljava/lang/Object;");
					break;
				case ARRAYLENGTH: pop("["); push(INT); break;

				case PUTFIELD:
				case GETFIELD:
				case PUTSTATIC:
				case GETSTATIC: field(code, (CstRefField) cp.get(r)); break;

				case INVOKEVIRTUAL:
				case INVOKESPECIAL:
				case INVOKESTATIC: invoke(code, (CstRef) cp.get(r)); break;
				case INVOKEINTERFACE:
					invoke(code, (CstRef) cp.get(r));
					r.rIndex += 2;
					break;
				case INVOKEDYNAMIC:
					invoke_dynamic((CstDynamic) cp.get(r), r.readUnsignedShort());
					break;

				case JSR: case JSR_W: case RET: ret(); break;

				case NEWARRAY:
					pop(INT);
					push("[" + (char) InsnHelper.PrimitiveArray2Type(r.readByte()));
					break;

				case INSTANCEOF:
					r.rIndex += 2;
					pop(ANY_OBJECT);
					push(INT);
					break;
				case NEW:
				case ANEWARRAY:
				case CHECKCAST:
					clazz(code, (CstClass) cp.get(r));
					break;

				case MULTIANEWARRAY:
					CstClass c = (CstClass) cp.get(r);
					int alen = r.readUnsignedByte();
					while (alen-- > 0) pop(INT);
					push(c.getValue().getString());
					break;

				case RETURN: eof = true; break;
				case IRETURN:
				case FRETURN:
				case LRETURN:
				case DRETURN:
				case ARETURN: pop(castType(mn.returnType())); eof = true; break;
				case ATHROW: pop("java/lang/Throwable"); eof = true; break;

				case MONITORENTER: case MONITOREXIT: pop(ANY_OBJECT); break;

				case IFEQ: case IFNE:
				case IFLT: case IFGE: case IFGT: case IFLE:
					pop(INT);
					r.rIndex += 2;
					jump();
					break;
				case IF_icmpeq: case IF_icmpne:
				case IF_icmplt: case IF_icmpge: case IF_icmpgt: case IF_icmple:
					pop(INT); pop(INT);
					r.rIndex += 2;
					jump();
					break;
				case IFNULL: case IFNONNULL:
					pop(ANY_OBJECT);
					r.rIndex += 2;
					jump();
					break;
				case IF_acmpeq: case IF_acmpne:
					pop(ANY_OBJECT); pop(ANY_OBJECT);
					r.rIndex += 2;
					jump();
					break;
				case GOTO: r.rIndex += 2; eof = true; jump(); break;
				case GOTO_W: r.rIndex += 4; eof = true; jump(); break;
				case TABLESWITCH:
					// align
					r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
					tableSwitch(r);
					break;
				case LOOKUPSWITCH:
					r.rIndex += (4 - ((r.rIndex - rBegin) & 3)) & 3;
					lookupSwitch(r);
					break;

				case POP2:
					t1 = pop12();
					if (t1.type != DOUBLE && t1.type != LONG) {
						pop1();
					}
					break;
				case POP: pop1(); break;

				case DUP:
					t1 = pop1();
					push(t1);
					push(t1);
					break;
				case DUP_X1:
					t1 = pop1();
					t2 = pop1();
					push(t1);
					push(t2);
					push(t1);
					break;
				case DUP_X2:
					t1 = pop1();
					t2 = pop1();
					t3 = pop1();
					push(t1);
					push(t3);
					push(t2);
					push(t1);
					break;
				case DUP2:
					t1 = pop12();
					if (t1.type == DOUBLE || t1.type == LONG) {
						push(t1);
						push(t1);
					} else {
						t2 = pop1();
						push(t2);
						push(t1);
						push(t2);
						push(t1);
					}
					break;
				case DUP2_X1:
					t1 = pop12();
					t2 = t1.type == DOUBLE || t1.type == LONG ? null : pop1();
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
					break;
				case DUP2_X2:
					t1 = pop12();
					t2 = t1.type == DOUBLE || t1.type == LONG ? null : pop1();
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
					break;
				case SWAP:
					t1 = pop1();
					t2 = pop1();
					push(t1);
					push(t2);
					break;
			}

			prev = code;
		}
	}

	private void math(byte type) {
		pop(type); pop(type); push(type);
	}
	private void cmp(byte type) {
		pop(type); pop(type); push(INT);
	}

	private void arrayLoad(String ref) {
		pop(INT); pop(ref); push(INT);
	}
	private void arrayLoad(String ref, byte type) {
		pop(INT); pop(ref); push(type);
	}
	private void arrayLoadObj() {
		pop(INT);
		String v = pop("[Ljava/lang/Object;").owner;

		String s = v.charAt(1) == 'L' ? v.substring(2, v.length() - 1) : v.substring(1);
		push(new Var2(REFERENCE, s));
	}
	private void arrayStore(String ref) {
		pop(INT); pop(INT); pop(ref);
	}
	private void arrayStore(String ref, byte type) {
		pop(type); pop(INT); pop(ref);
	}

	private void clazz(byte code, CstClass clz) {
		String name = clz.getValue().getString();
		switch (code) {
			case CHECKCAST: pop(ANY_OBJECT); push(name); break;
			case NEW: push(UNINITIAL, name); break;
			case ANEWARRAY: pop(INT);
				if (name.endsWith(";") || name.startsWith("[")) push("[".concat(name));
				else {
					sb.clear();
					push(sb.append("[L").append(name).append(';').toString());
				}
				break;
		}
	}
	private void ldc(Constant c) {
		int type = c.type();
		if (type == Constant.DYNAMIC) {
			String typeStr = ((CstDynamic) c).desc().getType().getString();
			type = typeStr.charAt(0);
			if (!Type.isValid(type)) throw new UnsupportedOperationException("未知的type:"+typeStr);
			switch (type) {
				case Type.CLASS:
					new Throwable("debug:type_class:"+typeStr).printStackTrace();
					push(typeStr.substring(1,typeStr.length()-1));
					break;
				case Type.BOOLEAN: case Type.BYTE:
				case Type.SHORT: case Type.CHAR:
				case Type.INT: push(INT); break;
				case Type.DOUBLE: push(DOUBLE); break;
				case Type.FLOAT: push(FLOAT); break;
				case Type.LONG: push(LONG); break;
			}
			return;
		}

		switch (type) {
			case Constant.INT: push(INT);break;
			case Constant.LONG: push(LONG);break;
			case Constant.FLOAT: push(FLOAT);break;
			case Constant.DOUBLE: push(DOUBLE);break;
			case Constant.CLASS: push("java/lang/Class");break;
			case Constant.STRING: push("java/lang/String");break;
			case Constant.METHOD_TYPE: push("java/lang/invoke/MethodType");break;
			case Constant.METHOD_HANDLE: push("java/lang/invoke/MethodHandle");break;
			default: throw new IllegalArgumentException("Unknown type: " + type);
		}
	}
	private void invoke_dynamic(CstDynamic dyn, int type) {
		CstNameAndType nat = dyn.desc();
		String desc = nat.getType().getString();

		SimpleList<Type> list = paramList; list.clear();
		TypeHelper.parseMethod(nat.getType().getString(), list);

		Type retVal = list.remove(list.size()-1);
		for (int i = list.size() - 1; i >= 0; i--) {
			pop(castType(list.get(i)));
		}
		push(castType(retVal));
	}
	private void invoke(byte code, CstRef method) {
		CstNameAndType nat = method.desc();

		SimpleList<Type> list = paramList; list.clear();
		TypeHelper.parseMethod(nat.getType().getString(), list);

		Type retVal = list.remove(list.size()-1);
		for (int i = list.size() - 1; i >= 0; i--) {
			pop(castType(list.get(i)));
		}

		if (code != INVOKESTATIC) {
			String owner = method.getClazz().getValue().getString();
			if (code == INVOKESPECIAL && nat.getName().getString().equals("<init>")) {
				Var2 v1 = Var2.except(REFERENCE, owner);
				v1.bci = bci;

				pop(v1);
			} else {
				pop(owner);
			}
		}
		Var2 v = castType(retVal);
		// void
		if (v != null) push(v);
	}
	private void field(byte code, CstRefField field) {
		Type fType = TypeHelper.parseField(field.desc().getType().getString());
		switch (code) {
			case GETSTATIC: push(castType(fType)); break;
			case PUTSTATIC: pop(castType(fType)); break;
			case GETFIELD:
				pop(field.getClazz().getValue().getString());
				push(castType(fType));
				break;
			case PUTFIELD:
				pop(castType(fType));
				pop(field.getClazz().getValue().getString());
				break;
		}
	}
	private void var(byte code, int vid) {
		switch (code) {
			case ALOAD: push(get(vid, ANY_OBJECT)); break;
			case DLOAD: push(get(vid, DOUBLE)); break;
			case FLOAD: push(get(vid, FLOAT)); break;
			case LLOAD: push(get(vid, LONG)); break;
			case ILOAD: push(get(vid, INT)); break;
			case ASTORE: set(vid, pop(ANY_OBJECT)); break;
			case DSTORE: set(vid, pop(DOUBLE)); break;
			case FSTORE: set(vid, pop(FLOAT)); break;
			case LSTORE: set(vid, pop(LONG)); break;
			case ISTORE: set(vid, pop(INT)); break;
		}
	}
	private void jump() {
		List<BasicBlock2> list = jumpTo.remove(bci);
		if (list == null) {
			if (!firstOnly) throw new AssertionError("No branch target at #"+bci + "\n" + jumpTo);
			eof = true;
			return;
		}
		for (int i = 0; i < list.size(); i++) {
			BasicBlock2 t = list.get(i);
			current.to(t);
		}
	}
	private void tableSwitch(DynByteBuf r) {
		r.rIndex += 4;
		int low = r.readInt();
		int hig = r.readInt();
		int count = hig - low + 1;

		r.rIndex += count << 2;

		pop(INT);
		eof = true;
		jump();
	}
	private void lookupSwitch(DynByteBuf r) {
		r.rIndex += 4;
		int count = r.readInt();

		r.rIndex += count << 3;

		pop(INT);
		eof = true;
		jump();
	}
	private void ret() { throw new UnsupportedOperationException("JSR/RET is deprecated in java8 which StackFrameTable added"); }

	private Var2 castType(Type t) {
		int arr = t.array();
		if (arr == 0) {
			int i = ofType(t);
			switch (i) {
				case -1: return null;
				default: return Var2.except((byte) i, null);
				case -2: return Var2.except(REFERENCE, t.owner);
			}
		} else {
			sb.clear();
			while (arr-- > 0) sb.append('[');

			if (t.owner == null) sb.append((char) t.type);
			else if (t.owner.startsWith("[")) {
				throw new IllegalArgumentException(t.toString());
			}
			else sb.append('L').append(t.owner).append(';');
			return Var2.except(REFERENCE, sb.toString());
		}
	}

	private Var2 get(int i, byte type) { return get(i, Var2.except(type, null)); }
	private Var2 get(int i, String type) { return get(i, new Var2(REFERENCE, type)); }
	private Var2 get(int i, Var2 v) {
		BasicBlock2 s = current;
		if (i < s.local.length && s.local[i] != null) {
			s.local[i].merge(v);
			return s.local[i];
		}

		if (i >= s.inheritLocal.length) s.inheritLocal = Arrays.copyOf(s.inheritLocal, i+1);
		if (s.inheritLocalMax <= i) s.inheritLocalMax = i+1;

		Var2[] inherit = s.inheritLocal;
		if (inherit[i] != null) {
			inherit[i].merge(v);
			return inherit[i];
		} else {
			return inherit[i] = v;
		}
	}
	private void set(int i, Var2 v) {
		BasicBlock2 s = current;
		if (i >= s.local.length) s.local = Arrays.copyOf(s.local, i+1);
		if (s.localMax <= i) s.localMax = i+1;

		s.local[i] = v;
		if (v.type == LONG || v.type == DOUBLE) set(i+1, Var2.TOP);
	}

	private Var2 pop(byte type) { return pop(Var2.except(type, null)); }
	private Var2 pop(String type) { return pop(new Var2(REFERENCE, type)); }
	private Var2 pop(Var2 v) {
		BasicBlock2 s = this.current;
		if (s.stackSize == 0) {
			if (v == null) v = Var2.any();
			if (s.inheritStackSize >= s.inheritStack.length) s.inheritStack = Arrays.copyOf(s.inheritStack, s.inheritStackSize+1);
			s.inheritStack[s.inheritStackSize++] = v.copy();
			return v;
		} else {
			Var2 v1;
			while (true) {
				v1 = s.stack[--s.stackSize];
				if (!v1.isDropped()) break;
				if (s.stackSize == 0) return pop(v);
			}
			if (v != null) {
				// simulation of 1xT2 as 2xT1 failed
				if (v1.type == ANY2 && (v.type == LONG || v.type == DOUBLE)) {
					s.stack[--s.stackSize].drop();
				}
				v1.merge(v);
			}
			return v1;
		}
	}
	private Var2 pop1() {
		Var2 v = pop((Var2) null);
		if (v.type == DOUBLE || v.type == LONG) throw new IllegalArgumentException("TOP was operated");
		return v;
	}
	private Var2 pop12() { return pop(new Var2(ANY2)); }

	private void push(byte type) { push(Var2.except(type, null)); }
	private void push(String owner) { push(new Var2(REFERENCE, owner)); }
	private void push(byte type, String owner) { push(Var2.except(type, owner)); }
	private void push(Var2 v) {
		BasicBlock2 s = current;
		if (s.stackSize >= s.stack.length) s.stack = Arrays.copyOf(s.stack, s.stackSize+1);
		s.stack[s.stackSize++] = v.copy();
	}
	// endregion
	// region Frame merge / compute

	public String getCommonUnsuperClass(String type1, String type2) throws Exception {
		ReflectClass r1 = MapUtil.getInstance().reflectClassInfo(type1.replace('/', '.'));
		if (r1 == null) throw new IllegalStateException("Unable to get " + type1);
		Class<?> c1 = r1.owner;

		ReflectClass r2 = MapUtil.getInstance().reflectClassInfo(type2.replace('/', '.'));
		if (r2 == null) throw new IllegalStateException("Unable to get " + type2);
		Class<?> c2 = r2.owner;

		if (c1.isAssignableFrom(c2)) {
			return type2;
		} else if (c2.isAssignableFrom(c1)) {
			return type1;
		}

		return null;
	}
	public String getCommonSuperClass(String type1, String type2) throws Exception {
		ReflectClass r1 = MapUtil.getInstance().reflectClassInfo(type1.replace('/', '.'));
		if (r1 == null) throw new IllegalStateException("Unable to get " + type1);
		Class<?> c1 = r1.owner;

		ReflectClass r2 = MapUtil.getInstance().reflectClassInfo(type2.replace('/', '.'));
		if (r2 == null) throw new IllegalStateException("Unable to get " + type2);
		Class<?> c2 = r2.owner;

		if (c1.isAssignableFrom(c2)) {
			return type1;
		} else if (c2.isAssignableFrom(c1)) {
			return type2;
		} else if (!c1.isInterface() && !c2.isInterface()) {
			do {
				c1 = c1.getSuperclass();
			} while(!c1.isAssignableFrom(c2));

			return c1.getName().replace('.', '/');
		} else {
			return "java/lang/Object";
		}
	}


	// endregion
	// region Frame writing
	public static void readFrames(List<Frame2> fr, DynByteBuf r, ConstantPool cp, IntMap<InsnNode> pc, MethodNode owner, int lMax, int sMax) {
		fr.clear();

		int allOffset = -1;
		int tableLen = r.readUnsignedShort();
		while (tableLen-- > 0) {
			int type = r.readUnsignedByte();
			Frame2 f = fromVarietyType(type);
			fr.add(f);

			int off = -1;
			switch (f.type) {
				case same:
					off = type;
					break;
				case same_ex:
					// keep original chop count
				case chop: case chop2: case chop3:
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

			allOffset += off + 1;
			if (pc != null) {
				InsnNode n = pc.get(allOffset);
				if (n != null) {
					f.target2 = n;
				} else {
					throw new IllegalArgumentException("帧的目标无法找到 "+allOffset+"/"+fr);
				}
			} else {
				f.target = allOffset;
			}
		}
	}
	private static Var2 getVar(ConstantPool pool, DynByteBuf r, IntMap<InsnNode> pc, MethodNode owner) {
		byte type = r.readByte();
		switch (type) {
			case REFERENCE: return new Var2(type, ((CstClass) pool.get(r)).getValue().getString());
			case UNINITIAL:
				if (pc != null) {
					int id = r.readUnsignedShort();
					InsnNode v = pc.get(id);
					if (v == null) throw new IllegalStateException("No code at offset " + id + " in " + owner);
					return new Var2(v);
				}
				Var2 v = new Var2(UNINITIAL);
				v.bci = r.readUnsignedShort();
				return v;
			case UNINITIAL_THIS: return new Var2(type, owner == null ? null : owner.ownerClass());
			default: return Var2.except(type, null);
		}
	}

	@SuppressWarnings("fallthrough")
	public static void writeFrames(List<Frame2> frames, DynByteBuf w, ConstantPool cp) {
		Frame2 prev = null;
		for (int j = 0; j < frames.size(); j++) {
			Frame2 curr = frames.get(j);

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
				case chop: case chop2: case chop3:
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
			case VarType.REFERENCE: w.putShort(cp.getClassId(v.owner)); break;
			case VarType.UNINITIAL: w.putShort(v.bci()); break;
		}
	}
	// endregion
}
