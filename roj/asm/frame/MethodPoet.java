package roj.asm.frame;

import roj.asm.cst.CstClass;
import roj.asm.frame.node.VarIncrNode;
import roj.asm.frame.node.VarSLNode;
import roj.asm.tree.Method;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.type.LocalVariable;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.InsnHelper;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.asm.frame.VarType.*;

/**
 * 从此ASM不再复杂！
 * 好吧我中二了
 *这里用的VarType的id被修改了，乘法得到的opcode不再适用
 * @author Roj233
 * @since 2022/2/5 11:50
 */
@Deprecated
public class MethodPoet extends Interpreter {
	public CodeWriter cw;

	public Method method;
	@Deprecated
	public AttrCode code;
	@Deprecated
	public InsnList insn;

	private final MyHashSet<Variable> used = new MyHashSet<>();
	private final MyHashSet<Variable> stored = new MyHashSet<>();
	private final List<Variable> thisArgs = new SimpleList<>();

	public MethodPoet() {}

	@Override
	public void init(MethodNode owner) {
		Method m = (Method) owner;
		this.method = m;
		this.code = m.setCode(new AttrCode(owner));
		this.insn = code.instructions;
		super.init(owner);

		used.clear();
		stored.clear();
		thisArgs.clear();

		boolean vir = 0 == (owner.modifier() & AccessFlag.STATIC);
		if (vir) { // this
			Variable v = new Variable("this", new Type(owner.ownerClass()));
			v.curType = local.list[0];
			thisArgs.add(v);
			stored.add(v);
		}
		List<Type> par = owner.parameters();
		for (int i = 0; i < par.size(); i++) {
			Variable v = new Variable("argi", par.get(i));
			v.curType = local.list[(vir ? 1 : 0) + i];
			thisArgs.add(v);
			stored.add(v);
		}
	}

	public Variable arg(int id) {
		return thisArgs.get(id);
	}

	// region opcodes

	public MethodPoet const1(int n) {
		insn.ldc(n);
		
		return this;
	}

	public MethodPoet const1(long n) {
		insn.ldc(n);
		return this;
	}

	public MethodPoet const1(float n) {
		insn.ldc(n);
		return this;
	}

	public MethodPoet const1(double n) {
		insn.ldc(n);
		return this;
	}

	public MethodPoet const1(String value) {
		insn.ldc(value);
		return this;
	}

	public MethodPoet constClass(String value) {
		insn.ldc(new CstClass(value));
		return this;
	}

	public MethodPoet constNull() {
		insn.one(ACONST_NULL);
		return this;
	}

	public MethodPoet load(Variable v) {
		if (!stored.contains(v)) throw new IllegalStateException("Not registered yet");
		used.add(v);
		insn.add(new VarSLNode(v, false));
		
		return this;
	}

	public MethodPoet store(Variable v) {
		used.add(v);
		stored.add(v);
		v.curType = pop();
		insn.add(new VarSLNode(v, true));
		return this;
	}

	public MethodPoet loadAsInt(Variable i) {
		load(i);
		Var2 v = i.curType;
		switch (v.type) {
			case LONG:
			case FLOAT:
			case DOUBLE:
				insn.one((byte) (IXOR + 3 * v.type));
				stack.list[stack.length - 1] = Var2.INT;
				break;
			case INT:
				break;
			default:
				throw new NumberFormatException(v.toString());
		}
		return this;
	}

	public MethodPoet arrayLoad() {
		return _array(null);
	}

	public MethodPoet arrayLoad(Variable i) {
		return loadAsInt(i)._array(null);
	}

	public MethodPoet arrayLoad(int i) {
		return const1(i)._array(null);
	}

	public MethodPoet arrayLoad(Variable a, Variable i) {
		return load(a).arrayLoad(i);
	}

	public MethodPoet arrayLoad(Variable a, int i) {
		return load(a).const1(i)._array(null);
	}

	public MethodPoet arrayStore() {
		return _array(pop0());
	}

	public MethodPoet arrayStore(Variable x) {
		return _array(x.curType);
	}

	public MethodPoet arrayStore(int i, Variable x) {
		return const1(i)._array(x.curType);
	}

	public MethodPoet arrayStore(Variable i, Variable x) {
		return loadAsInt(i)._array(x.curType);
	}

	public MethodPoet arrayStore(Variable a, int i, Variable x) {
		return load(a).const1(i)._array(x.curType);
	}

	public MethodPoet arrayStore(Variable a, Variable i, Variable x) {
		return load(a).loadAsInt(i)._array(x.curType);
	}

	private MethodPoet _array(Var2 store) {
		pop(INT);
		String arr = popArray();
		byte code;
		Var2 stack;
		switch (arr.charAt(1)) {
			case Type.BOOLEAN:
			case Type.BYTE:
				code = BALOAD;
				stack = Var2.INT;
				break;
			case Type.CHAR:
				code = CALOAD;
				stack = Var2.INT;
				break;
			case Type.SHORT:
				code = SALOAD;
				stack = Var2.INT;
				break;
			case Type.INT:
				code = IALOAD;
				stack = Var2.INT;
				break;
			case Type.LONG:
				code = LALOAD;
				stack = Var2.LONG;
				break;
			case Type.FLOAT:
				code = FALOAD;
				stack = Var2.FLOAT;
				break;
			case Type.DOUBLE:
				code = DALOAD;
				stack = Var2.DOUBLE;
				break;
			case Type.ARRAY:
			case Type.CLASS:
				code = AALOAD;
				stack = obj(arr.substring(1));
				break;
			default:
				throw new IllegalStateException();
		}
		if (store != null) {
			code += 33;
			if (!stack.eq(store)) {
				throw new IllegalArgumentException("Unable cast " + store + " to " + stack);
			}
		} else {
		}
		insn.one(code);
		return this;
	}


	// 各种dup... 滚粗
	public MethodPoet noPar(byte opcode) {
		return node(NPInsnNode.of(opcode));
	}

	public MethodPoet node(InsnNode node) {
		super.visitNode(node);
		insn.add(node);
		return this;
	}

	public MethodPoet node(Label node) {
		return this;
	}

	public MethodPoet pop1() {
		byte code = pop0().type;
		insn.one(code == DOUBLE || code == LONG ? POP2 : POP);
		return this;
	}

	public MethodPoet dup() {
		return dup0(DUP);
	}

	/**
	 * stack before: a b
	 * stack after:  b a b
	 */
	public MethodPoet dupX1() {
		return dup0(DUP_X1);
	}

	/**
	 * stack before: a b c
	 * stack after:  c a b c
	 */
	public MethodPoet dupX2() {
		return dup0(DUP_X2);
	}

	private MethodPoet dup0(byte code) {
		boolean f = stackTop().type == TOP;
		insn.one(f ? (byte) (code + 3) : code);
		VarList s = this.stack;
		if (f) {
			Var2 t = s.list[s.length - 1];
			Var2 b = s.list[s.length - 2];
			s.add(b);
			s.add(t);
		} else {
			s.add(s.list[s.length - 1]);
		}
		return this;
	}

	public MethodPoet swap() {
		insn.one(SWAP);

		VarList s = this.stack;
		int p = s.length - 1;

		Var2 t = s.list[p];
		s.list[p] = s.list[p - 1];
		s.list[p - 1] = t;
		return this;
	}

	public MethodPoet add() {
		return math4(IADD);
	}

	public MethodPoet sub() {
		return math4(ISUB);
	}

	public MethodPoet mul() {
		return math4(IMUL);
	}

	public MethodPoet div() {
		return math4(IDIV);
	}

	public MethodPoet mod() {
		return math4(IREM);
	}

	public MethodPoet neg() {
		return math4(INEG);
	}

	private MethodPoet math4(int opcode) {
		Var2 v = pop0();
		checkStackTop(v.type);
		switch (v.type) {
			case INT:
			case LONG:
			case FLOAT:
			case DOUBLE:
				opcode += v.type - 1;
				break;
			default:
				throw new NumberFormatException(v.toString());
		}
		insn.one((byte) opcode);
		return this;
	}

	public MethodPoet shl() {
		return math2(ISHL);
	}

	public MethodPoet shr() {
		return math2(ISHR);
	}

	public MethodPoet ushr() {
		return math2(IUSHR);
	}

	public MethodPoet and() {
		return math2(IAND);
	}

	public MethodPoet or() {
		return math2(IOR);
	}

	public MethodPoet xor() {
		return math2(IXOR);
	}

	private MethodPoet math2(int opcode) {
		Var2 v = pop();
		checkStackTop(v.type);
		switch (v.type) {
			case INT:
				break;
			case LONG:
				opcode += 1;
				break;
			default:
				throw new NumberFormatException(v.toString());
		}
		insn.one((byte) opcode);
		return this;
	}

	// IINC
	public MethodPoet increment(Variable v, int amount) {
		switch (v.curType.type) {
			case INT:
				if (amount == (short) amount) {
					if (!stored.contains(v)) throw new IllegalStateException("Not registered yet");
					used.add(v);
					insn.add(new VarIncrNode(v, amount));
					return this;
				} else {
					load(v).insn.ldc(amount);
					return add();
				}
			case LONG:
				return load(v).const1((long) amount).add();
			case FLOAT:
				return load(v).const1((float) amount).add();
			case DOUBLE:
				return load(v).const1((double) amount).add();
			default:
				throw new NumberFormatException(v.curType.toString());
		}
	}

	public MethodPoet increment(Variable v, long amount) {
		switch (v.curType.type) {
			case LONG:
				return load(v).const1(amount).add();
			case FLOAT:
				return load(v).const1((float) amount).add();
			case DOUBLE:
				return load(v).const1((double) amount).add();
			default:
				throw new NumberFormatException(v.curType.toString());
		}
	}

	public MethodPoet increment(Variable v, float amount) {
		switch (v.curType.type) {
			case FLOAT:
				return load(v).const1(amount).add();
			case DOUBLE:
				return load(v).const1((double) amount).add();
			default:
				throw new NumberFormatException(v.curType.toString());
		}
	}

	public MethodPoet increment(Variable v, double amount) {
		if (v.curType.type == DOUBLE) {
			return load(v).const1(amount).add();
		} else {
			throw new NumberFormatException(v.curType.toString());
		}
	}

	public MethodPoet cast(Type t) {
		int i = ofType(t);
		switch (i) {
			case -1:
				throw new IllegalStateException();
			case -2:
				return cast(t.array() > 0 ? TypeHelper.getField(t) : t.owner);
			case DOUBLE:
			case FLOAT:
			case LONG:
			case INT:
				return cast((byte) i);
			default:
				return castLow((char) t.type);
		}
	}

	@SuppressWarnings("fallthrough")
	public MethodPoet cast(byte to) {
		byte fr = pop0().type;
		if (fr == to) return this;
		int base = I2L - 3 + 3 * fr - 1;
		switch (to) {
			case DOUBLE:
				base++;
			case FLOAT:
				if (fr != FLOAT) base++;
			case LONG:
				if (fr != LONG) base++;
			case INT:
				if (fr != INT) base++;
		}
		insn.one((byte) base);
		return this;
	}

	public MethodPoet castLow(char nativeType) {
		cast(INT);
		switch (nativeType) {
			case Type.CHAR:
				insn.one(I2C);
				break;
			case Type.SHORT:
				insn.one(I2S);
				break;
			case Type.BYTE:
				insn.one(I2B);
				break;
			default:
				throw new IllegalArgumentException();
		}
		return this;
	}

	public boolean executable() {
		if (insn.size() > 0) {
			switch (insn.get(insn.size() - 1).code) {
				case ATHROW:
				case GOTO_W:
				case GOTO:
				case IRETURN:
				case FRETURN:
				case DRETURN:
				case LRETURN:
				case ARETURN:
				case RETURN:
					return false;
			}
		}
		return true;
	}

	public MethodPoet goto1(Label label) {
		return jump(GOTO, label);
	}

	public MethodPoet if1(byte opcode, InsnNode label) {
		insn.jump(opcode, label);
		return this;
	}

	public MethodPoet switch1(SwitchInsnNode node) {
		pop(INT);
		insn.add(node);
		return this;
	}

	public MethodPoet return1() {
		if (returnType == Type.VOID) {
			insn.one(RETURN);
			return this;
		}
		char t1;
		byte opc;
		switch (pop0().type) {
			case TOP:
				throw new IllegalArgumentException();
			case INT:
				t1 = Type.INT;
				opc = IRETURN;
				break;
			case LONG:
				t1 = Type.LONG;
				opc = LRETURN;
				break;
			case FLOAT:
				t1 = Type.FLOAT;
				opc = FRETURN;
				break;
			case DOUBLE:
				t1 = Type.DOUBLE;
				opc = DRETURN;
				break;
			default:
				t1 = Type.CLASS;
				opc = ARETURN;
		}
		checkReturn(t1);
		insn.one(opc);
		return this;
	}

	// getfield, invoke, and comparing nodes use #node

	public MethodPoet new1(String clazz) {
		insn.clazz(NEW, clazz);
		return this;
	}

	public MethodPoet newArray(String clazz) {
		if (clazz.startsWith("[")) throw new IllegalArgumentException();
		pop(INT);
		pushRefArray(clazz);
		insn.add(new ClassInsnNode(ANEWARRAY, clazz));
		return this;
	}

	public MethodPoet newArray(int len, String n) {
		return const1(len).newArray(n);
	}

	public MethodPoet newArray(Variable v, String n) {
		return loadAsInt(v).newArray(n);
	}

	public MethodPoet newArray(char nativeType) {
		byte prm = InsnHelper.Type2PrimitiveArray(nativeType);
		pop(INT);
		// noinspection all

		insn.add(new U1InsnNode(NEWARRAY, prm));
		return this;
	}

	public MethodPoet newArray(int len, char n) {
		return const1(len).newArray(n);
	}

	public MethodPoet newArray(Variable v, char n) {
		return loadAsInt(v).newArray(n);
	}

	public MethodPoet newArray(Type t) {
		if (t.array() == 0) {
			if (t.owner != null) {
				return newArray(t.owner);
			} else return newArray((char) t.type);
		}
		pop(INT, t.array()+1);

		CharList sb = this.sb;
		sb.clear();sb.append('[');
		t.toDesc(sb);

		String clz = sb.toString();

		insn.add(new MDArrayInsnNode(clz, t.array()+1));
		return this;
	}

	public MethodPoet arrayLen() {
		popArray();

		insn.one(ARRAYLENGTH);
		return this;
	}

	public MethodPoet arrayLen(Variable v) {
		return load(v).arrayLen();
	}

	public MethodPoet throw1() {
		pop(REFERENCE);
		insn.one(ATHROW);
		return this;
	}

	public MethodPoet throw1(Variable v) {
		return load(v).throw1();
	}

	public MethodPoet cast(String clazz) {
		pop(REFERENCE);

		insn.add(new ClassInsnNode(CHECKCAST, clazz));
		return this;
	}

	public MethodPoet cast(Class<?> clazz) {
		return cast(clazz.getName().replace('.', '/'));
	}

	public MethodPoet instanceof1(String clazz) {
		pop(REFERENCE);

		insn.add(new ClassInsnNode(INSTANCEOF, clazz));
		return this;
	}

	public MethodPoet instanceof1(Class<?> clazz) {
		return instanceof1(clazz.getName().replace('.', '/'));
	}

	public MethodPoet lock(boolean lock) {
		pop(REFERENCE);
		insn.one(lock ? MONITORENTER : MONITOREXIT);
		return this;
	}

	// endregion

	public void finish() {/*
		VarMapperX vmx = new VarMapperX();
		List<Variable> tmp1 = thisArgs;
		tmp1.clear();
		boolean isStatic = (method.access & AccessFlag.STATIC) != 0;
		for (Variable v : used) {
			if (v.name.equals("this")) {
				if (!isStatic) continue;
			}

			VarX x = new VarX();
			v.att = x;
			x.att = v;
			vmx.add(x);
			tmp1.add(v);
		}
		used.clear();

		InsnList insn = this.insn;
		for (int i = 0; i < insn.size(); ) {
			InsnNode node = insn.get(i++);
			switch (node.nodeType()) {
				case InsnNode.T_IINC:
					VarIncrNode vin = (VarIncrNode) node;
					vmx.getset(vin.v.att, i);
					break;
				case InsnNode.T_LOAD_STORE:
					VarSLNode vsl = (VarSLNode) node;
					if (vsl.isLoad()) {
						vmx.get(vsl.v.att, i);
					} else {
						vmx.set(vsl.v.att, i);
					}
					break;
				case InsnNode.T_GOTO_IF:
					JumpInsnNode gin = (JumpInsnNode) node;
					vmx.jmp(gin.bci, gin.target.bci);
					break;
			}
		}

		int slotInUse = isStatic ? 0 : 1;
		int maxInUse = slotInUse + vmx.map(null);

		for (int i = 0; i < tmp1.size(); i++) {
			Variable v = tmp1.get(i);
			v.slot = v.att.slot + (isStatic ? 0 : 1);
		}
		System.out.println("maxInUse= " + maxInUse);
		System.out.println("maxStackSize= " + maxStackSize);
		code.localSize = (char) maxInUse;
		code.stackSize = (char) maxStackSize;*/
	}

	public Label label() {
		LabelInsnNode label = new LabelInsnNode();
		insn.add(label);
		return Helpers.nonnull();
	}

	public final Var2 stackTop() {
		return stack.list[stack.length - 1];
	}

	private Var2 pop0() {
		VarList s = this.stack;
		Var2 v = s.list[--s.length];
		if (v.type == TOP) v = s.list[--s.length];
		return v;
	}

	private String popArray() {
		Var2 ref = pop(REFERENCE);
		String arr = ref.owner;
		if (arr == null || !arr.startsWith("[")) throw new IllegalArgumentException(ref + " is not array");
		return arr;
	}

	public static class Variable extends LocalVariable {
		public boolean constant;
		//VarX att;

		public Variable(String name) {
			super("", null);
		}

		public Variable(String name, Type type) {
			super(name, type);
		}

		public Var2 curType;
		int startPos, endPos;

		@Override
		public boolean equals(Object o) {
			return o == this;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this);
		}
	}

	public MethodPoet jump(byte code, Label target) {
		return this;
	}
}
