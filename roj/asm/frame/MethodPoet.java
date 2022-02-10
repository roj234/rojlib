package roj.asm.frame;

import roj.asm.frame.node.VarIncrNode;
import roj.asm.frame.node.VarSLNode;
import roj.asm.tree.Method;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.type.LocalVariable;
import roj.asm.type.NativeType;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.InsnList;
import roj.asm.util.NodeHelper;
import roj.collect.MyHashSet;
import roj.collect.Unioner;
import roj.text.CharList;

import static roj.asm.Opcodes.*;
import static roj.asm.frame.VarType.*;

/**
 * 从此ASM不再复杂！
 * 好吧我中二了
 *
 * @author Roj233
 * @since 2022/2/5 11:50
 */
public final class MethodPoet extends Interpreter {
    public ClassPoet parent;

    public AttrCode            code;
    public InsnList            insn;
    public boolean             preferSpace;
    public MyHashSet<Variable> registered = new MyHashSet<>();

    public MethodPoet(ClassPoet parent) {
        this.parent = parent;
    }

    @Override
    public void init(MethodNode owner) {
        Method m = (Method) owner;
        this.code = m.code;
        this.insn = code.instructions;
        super.init(owner);
    }

    // region opcodes

    public MethodPoet const1(int n) {
        insn.add(NodeHelper.loadInt(n));
        stack.add(Var.std(INT));
        return this;
    }
    public MethodPoet const1(long n) {
        if (preferSpace) {
            NodeHelper.loadLongSlow(n, insn);
        } else {
            insn.add(NodeHelper.loadLong(n));
        }
        stack.add(Var.std(LONG));
        return this;
    }
    public MethodPoet const1(float n) {
        if (preferSpace) {
            NodeHelper.loadFloatSlow(n, insn);
        } else {
            insn.add(NodeHelper.loadFloat(n));
        }
        stack.add(Var.std(FLOAT));
        return this;
    }
    public MethodPoet const1(double n) {
        if (preferSpace) {
            NodeHelper.loadDoubleSlow(n, insn);
        } else {
            insn.add(NodeHelper.loadDouble(n));
        }
        stack.add(Var.std(DOUBLE));
        return this;
    }
    public MethodPoet constNull() {
        insn.add(NodeHelper.npc(ACONST_NULL));
        return this;
    }

    public MethodPoet load(Variable v) {
        if (!registered.contains(v))
            throw new IllegalStateException("Not registered yet");
        insn.add(new VarSLNode(v, false));
        return this;
    }
    public MethodPoet store(Variable v) {
        registered.add(v);
        insn.add(new VarSLNode(v, true));
        return this;
    }

    public MethodPoet loadAsInt(Variable i) {
        load(i);
        Var v = i.type;
        switch (v.type) {
            case LONG:
            case FLOAT:
            case DOUBLE:
                insn.add(NodeHelper.npc((byte) (IXOR + 3 * v.type)));
                stack.list[stack.size - 1] = Var.INT;
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
        return _array(x.type);
    }
    public MethodPoet arrayStore(int i, Variable x) {
        return const1(i)._array(x.type);
    }
    public MethodPoet arrayStore(Variable i, Variable x) {
        return loadAsInt(i)._array(x.type);
    }
    public MethodPoet arrayStore(Variable a, int i, Variable x) {
        return load(a).const1(i)._array(x.type);
    }
    public MethodPoet arrayStore(Variable a, Variable i, Variable x) {
        return load(a).loadAsInt(i)._array(x.type);
    }

    private MethodPoet _array(Var store) {
        pop(INT);
        String arr = popArray();
        byte code; Var stack;
        switch (arr.charAt(1)) {
            case NativeType.BOOLEAN:
            case NativeType.BYTE:
                code = BALOAD;
                stack = Var.INT;
                break;
            case NativeType.CHAR:
                code = CALOAD;
                stack = Var.INT;
                break;
            case NativeType.SHORT:
                code = SALOAD;
                stack = Var.INT;
                break;
            case NativeType.INT:
                code = IALOAD;
                stack = Var.INT;
                break;
            case NativeType.LONG:
                code = LALOAD;
                stack = Var.LONG;
                break;
            case NativeType.FLOAT:
                code = FALOAD;
                stack = Var.FLOAT;
                break;
            case NativeType.DOUBLE:
                code = DALOAD;
                stack = Var.DOUBLE;
                break;
            case NativeType.ARRAY:
            case NativeType.CLASS:
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
            this.stack.add(stack);
        }
        insn.add(NodeHelper.npc(code));
        return this;
    }


    // 各种dup... 滚粗
    public MethodPoet node(InsnNode node) {
        super.visitNode(node);
        insn.add(node);
        return this;
    }
    public MethodPoet pop1() {
        byte code = pop0().type;
        insn.add(NodeHelper.npc(code == DOUBLE || code == LONG ? POP2 : POP));
        return this;
    }
    public MethodPoet dup() {
        boolean f = stackTop().type == TOP;
        insn.add(NodeHelper.npc(f ? DUP2 : DUP));
        VarList s = this.stack;
        if (f) {
            Var t = s.list[s.size - 1];
            Var b = s.list[s.size - 2];
            s.add(b);
            s.add(t);
        } else {
            s.add(s.list[s.size - 1]);
        }
        return this;
    }
    public MethodPoet swap() {
        insn.add(NodeHelper.npc(SWAP));

        VarList s = this.stack;
        int p = s.size - 1;

        Var t = s.list[p];
        s.list[p] = s.list[p-1];
        s.list[p-1] = t;
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
        Var v = pop0();
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
        insn.add(NodeHelper.npc((byte) opcode));
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
        Var v = pop();
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
        insn.add(NodeHelper.npc((byte) opcode));
        return this;
    }

    // IINC
    public MethodPoet addStatic(Variable v, int amount) {
        switch (stackTop().type) {
            case INT:
                if (amount == (short) amount) {
                    insn.add(new VarIncrNode(v, amount));
                    return this;
                } else {
                    _add(v);
                    insn.add(NodeHelper.loadInt(amount));
                    return add();
                }
            case LONG:
                _add(v);
                return const1((long) amount).add();
            case FLOAT:
                _add(v);
                return const1((float) amount).add();
            case DOUBLE:
                _add(v);
                return const1((double) amount).add();
            default:
                throw new NumberFormatException(stackTop().toString());
        }
    }
    public MethodPoet addStatic(Variable v, long amount) {
        switch (stackTop().type) {
            case LONG:
                _add(v);
                return const1(amount).add();
            case FLOAT:
                _add(v);
                return const1((float) amount).add();
            case DOUBLE:
                _add(v);
                return const1((double) amount).add();
            default:
                throw new NumberFormatException(stackTop().toString());
        }
    }
    public MethodPoet addStatic(Variable v, float amount) {
        switch (stackTop().type) {
            case FLOAT:
                _add(v);
                return const1(amount).add();
            case DOUBLE:
                _add(v);
                return const1((double) amount).add();
            default:
                throw new NumberFormatException(stackTop().toString());
        }
    }
    public MethodPoet addStatic(Variable v, double amount) {
        if (stackTop().type == DOUBLE) {
            _add(v);
            return const1(amount).add();
        } else {
            throw new NumberFormatException(stackTop().toString());
        }
    }
    private void _add(Variable v) {
        stack.add(v.type);
        load(v);
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
        insn.add(NodeHelper.npc((byte) base));
        return this;
    }
    public MethodPoet castLow(char nativeType) {
        cast(INT);
        switch (nativeType) {
            case NativeType.CHAR:
                insn.add(NodeHelper.npc(I2C));
                break;
            case NativeType.SHORT:
                insn.add(NodeHelper.npc(I2S));
                break;
            case NativeType.BYTE:
                insn.add(NodeHelper.npc(I2B));
                break;
            default:
                throw new IllegalArgumentException();
        }
        return this;
    }

    public MethodPoet if1() {
        /**
         *
         LCMP = (byte) 0X94,
         FCMPL = (byte) 0X95,
         FCMPG = (byte) 0X96,
         DCMPL = (byte) 0X97,
         DCMPG = (byte) 0X98,

         // Condition / Jump
         IFEQ = (byte) 0x99,
         IFNE = (byte) 0x9a,
         IFLT = (byte) 0x9b,
         IFGE = (byte) 0x9c,
         IFGT = (byte) 0x9d,
         IFLE = (byte) 0x9e,
         IF_icmpeq = (byte) 0x9f,
         IF_icmpne = (byte) 0xa0,
         IF_icmplt = (byte) 0xa1,
         IF_icmpge = (byte) 0xa2,
         IF_icmpgt = (byte) 0xa3,
         IF_icmple = (byte) 0xa4,
         IF_acmpeq = (byte) 0xa5,
         IF_acmpne = (byte) 0xa6,
         */
        // todo
        throw new UnsupportedOperationException();
    }

    public MethodPoet goto1(LabelInsnNode label) {
        insn.add(new GotoInsnNode(label));
        return this;
    }

    public MethodPoet switch1(SwitchInsnNode node) {
        pop(INT);
        insn.add(node);
        return this;
    }

    public MethodPoet return1() {
        if (returnType == NativeType.VOID) {
            insn.add(NodeHelper.npc(RETURN));
            return this;
        }
        char t1; byte opc;
        switch (pop0().type) {
            case TOP:
                throw new IllegalArgumentException();
            case INT:
                t1 = NativeType.INT;
                opc = IRETURN;
                break;
            case LONG:
                t1 = NativeType.LONG;
                opc = LRETURN;
                break;
            case FLOAT:
                t1 = NativeType.FLOAT;
                opc = FRETURN;
                break;
            case DOUBLE:
                t1 = NativeType.DOUBLE;
                opc = DRETURN;
                break;
            default:
                t1 = NativeType.CLASS;
                opc = ARETURN;
        }
        checkReturn(t1);
        insn.add(NodeHelper.npc(opc));
        return this;
    }

    // getfield, invoke, and comparing nodes use #node

    public MethodPoet new1(String clazz) {
        ClassInsnNode node = new ClassInsnNode(NEW, clazz);
        stack.add(new Var(node));
        insn.add(node);
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
        byte prm;
        switch (nativeType) {
            case 'Z':
                prm =  4;
                break;
            case 'C':
                prm =  5;
                break;
            case 'F':
                prm =  6;
                break;
            case 'D':
                prm =  7;
                break;
            case 'B':
                prm =  8;
                break;
            case 'S':
                prm =  9;
                break;
            case 'I':
                prm =  10;
                break;
            case 'J':
                prm =  11;
                break;
            default:
                throw new IllegalArgumentException();
        }
        pop(INT);
        stack.add(fromType(Type.std(nativeType), sb));
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
        if (t.array == 0) {
            if (t.owner != null)
                return newArray(t.owner);
            else
                return newArray((char) t.type);
        }
        pop(INT, t.array+1);

        t.array++;
        CharList sb = this.sb;
        sb.clear();
        ParamHelper.getOne(t, sb);

        String clz = sb.toString();
        stack.add(new Var(clz));
        insn.add(new MDArrayInsnNode(clz, t.array--));

        return this;
    }

    public MethodPoet arrayLen() {
        popArray();
        stack.add(Var.INT);
        insn.add(NodeHelper.npc(ARRAYLENGTH));
        return this;
    }
    public MethodPoet arrayLen(Variable v) {
        return load(v).arrayLen();
    }

    public MethodPoet throw1() {
        pop(REFERENCE);
        insn.add(NodeHelper.npc(ATHROW));
        return this;
    }
    public MethodPoet throw1(Variable v) {
        return load(v).throw1();
    }

    public MethodPoet cast(String clazz) {
        pop(REFERENCE);
        stack.add(obj(clazz));
        insn.add(new ClassInsnNode(CHECKCAST, clazz));
        return this;
    }
    public MethodPoet cast(Class<?> clazz) {
        return cast(clazz.getName().replace('.', '/'));
    }

    public MethodPoet instanceof1(String clazz) {
        pop(REFERENCE);
        stack.add(Var.INT);
        insn.add(new ClassInsnNode(INSTANCEOF, clazz));
        return this;
    }
    public MethodPoet instanceof1(Class<?> clazz) {
        return cast(clazz.getName().replace('.', '/'));
    }

    public MethodPoet lock(boolean lock) {
        pop(REFERENCE);
        insn.add(NodeHelper.npc(lock ? MONITORENTER : MONITOREXIT));
        return this;
    }

    // endregion

    public ClassPoet done() {
        for (Variable v : registered) {
            v.startPos = Integer.MAX_VALUE;
            v.endPos = -1;
            v.slot = -1;
        }
        InsnList insn = this.insn;
        for (int i = 0; i < insn.size(); i++) {
            InsnNode node = insn.get(i);
            switch(node.nodeType()) {
                case InsnNode.T_IINC:
                    VarIncrNode vin = (VarIncrNode) node;

                    Variable v = vin.v;
                    if (v.start == null) {
                        v.start = node;
                        v.startPos = i;
                    }
                    v.end = node;
                    v.endPos = i;
                    break;
                case InsnNode.T_LOAD_STORE:
                    VarSLNode vsl = (VarSLNode) node;

                    v = vsl.v;
                    if (v.start == null) {
                        v.start = node;
                        v.startPos = i;
                    }
                    v.end = node;
                    v.endPos = i;
                    break;
            }
        }
        Unioner<Variable> union = new Unioner<>(registered.size());
        for (Variable v : registered) {
            union.add(v);
        }
        registered.clear();

        boolean isStatic = true;

        // todo parameter
        int slotInUse = isStatic ? 0 : 1;
        int maxInUse = slotInUse;
        // satisfy variable id
        for (Unioner.Region region : union) {
            Unioner.Point point = region.node();
            while (point != null) {
                Variable v = point.owner();
                if(point.end()) {
                    if(v.slot == slotInUse) slotInUse--;
                } else {
                    assert v.slot == -1;
                    v.slot = slotInUse++;

                    if(slotInUse > maxInUse) {
                        maxInUse = slotInUse;
                    }
                }
                point = point.next();
            }
        }

        return parent;
    }

    public LabelInsnNode label() {
        LabelInsnNode label = new LabelInsnNode();
        insn.add(label);
        return label;
    }

    private Var stackTop() {
        return stack.list[stack.size - 1];
    }
    private Var pop0() {
        VarList s = this.stack;
        Var v = s.list[s.size--];
        if (v.type == TOP)
            v = s.list[s.size--];
        return v;
    }
    private String popArray() {
        Var ref = pop(REFERENCE);
        String arr = ref.owner;
        if (arr == null || !arr.startsWith("[")) throw new IllegalArgumentException(ref + " is not array");
        return arr;
    }

    public static class Variable extends LocalVariable implements Unioner.Range {
        public Variable(MethodPoet owner, String name, Type type) {
            super(name, type);
            this.type = fromType(type, owner.sb);
        }

        public final Var type;
        int startPos, endPos;

        @Override
        public long startPos() {
            return startPos;
        }

        @Override
        public long endPos() {
            return endPos;
        }
    }
}
