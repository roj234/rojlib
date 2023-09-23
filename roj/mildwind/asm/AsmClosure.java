package roj.mildwind.asm;

import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Segment;
import roj.collect.*;
import roj.mildwind.type.JsMap;
import roj.mildwind.type.JsNull;
import roj.mildwind.type.JsObject;
import roj.util.VarMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2023/6/17 0017 16:58
 */
public final class AsmClosure {
    private final AsmClosure parent;
    private final JsMap window;

    private JsMethodWriter asm;

    final int depth;

    private final SimpleList<Variable> variables = new SimpleList<>();
    private final MyHashMap<String, Variable> activeVars = new MyHashMap<>();
    private final SimpleList<MyHashSet<String>> blockDef = new SimpleList<>();

    private ToIntMap<String> paramRef;
    private Int2IntMap def;
    private int rest = -1;

    private boolean finished;
    private JsObject[] closure;

    public AsmClosure child(ToIntMap<String> par) { return new AsmClosure(this, par); }

    public void setDefault(int i, JsObject ref) {
        int fid = asm.sync(ref);

        if (def == null) def = new Int2IntMap();
        def.put(i, fid);
    }
    public void setRestId(int i) { rest = i; }

    public static final class Variable extends VarMapper.Var {
        public int depth;
        public String name;
        public boolean immutable, isVar;
        public JsObject constval;

        { start = -1; }

        @Override
        public String toString() {
            return "Variable{" + "depth=" + depth + ", name='" + name + '\'' + ", immutable=" + immutable + ", isVar=" + isVar + ", constval=" + constval + '}' + super.toString();
        }
    }

    public AsmClosure(JsMap window) {
        this.parent = null;
        this.window = window;
        this.depth = 0;
        this.paramRef = new ToIntMap<>(); // can be a 'shared empty'
    }

    public AsmClosure(AsmClosure parent, ToIntMap<String> paramRef) {
        this.parent = parent;
        this.window = parent.window;
        this.depth = parent.depth+1;
        this.paramRef = paramRef;
    }

    public void enterBlock() { blockDef.add(new MyHashSet<>()); }
    public void exitBlock() {
        MyHashSet<String> set = blockDef.pop();
        for (String name : set) activeVars.remove(name);
    }

    public void _var(String name) {
        Variable v = activeVars.get(name);
        if (v == null) {
            activeVars.put(name, v = new Variable());
            variables.add(v);

            v.name = name;
            v.isVar = true;
            v.depth = depth;
        } else if (!v.isVar) throw new IllegalStateException("Identifier '"+name+"' has already been declared");
    }
    public void _let(String name) { create(name, false); }
    public void _const(String name) { create(name, true); }

    private void create(String name, boolean immutable) {
        Variable v = activeVars.get(name);
        if (v != null) throw new IllegalStateException("Identifier '"+name+"' has already been declared");

        activeVars.put(name, v = new Variable());
        variables.add(v);
        if (!blockDef.isEmpty()) blockDef.get(blockDef.size()-1).add(name);

        v.name = name;
        v.isVar = true;
        v.depth = depth;
        v.immutable = immutable;
    }

    HeaderSegment hs;
    public void set_on_enter(String name, int fid) {
        Variable v = activeVars.get(name);
        v.start = 0;
        hs.add(v, fid);
    }
    public void set(String name) { set(name, null); }
    public int set(String name, JsObject constval) {
        // todo: if constval is not null, dont store from stack
        Variable v = activeVars.get(name);
        if (v == null) {
            v = findParentVar(name);
            if (v == null) {
                asm.ldc(name);
                asm.invokeS("roj/mildwind/asm/JsFunctionCompiled", "gsv", "(Lroj/mildwind/type/JsObject;Ljava/lang/String;)V");
                return -1;
            } else {
                if (v.immutable) throw new IllegalStateException("is const");
            }
        } else {
            if (v.start >= 0) {
                if (v.immutable) {
                    throw new IllegalStateException("is const");
                }
            } else {
                v.start = asm.bci();
                //v.constval = ;
            }

            v.end = asm.bci();
        }

        asm.addSegment(new VarSegment(v, false));
        return -1;
    }
    public void get(String name) {
        Variable v = activeVars.get(name);
        block:
        if (v == null) {
            int id = paramRef.getOrDefault(name, -1);
            if (id >= 0) {
                asm.one(ALOAD_2);
                asm.ldc(id);
                if (id == rest) {
                    asm.invokeV("roj/mildwind/api/Arguments", "getAfter", "(I)Lroj/mildwind/type/JsArray;");
                } else if (def != null && def.containsKey(id)) {
                    asm.one(ALOAD_0);
                    asm.field(GETFIELD, asm.data, def.getOrDefaultInt(id, -1));
                    asm.invokeV("roj/mildwind/api/Arguments", "getOrDefault", "(ILroj/mildwind/type/JsObject;)Lroj/mildwind/type/JsObject;");
                    // todo deepcopy(cow)
                } else {
                    asm.invokeV("roj/mildwind/api/Arguments", "getIntChild", "(I)Lroj/mildwind/type/JsObject;");
                }
                activeVars.put(name, v = new Variable());
                variables.add(v);
                //asm.getTmpVar();
                asm.addSegment(new VarSegment(v, false));

                v.name = name;
                v.isVar = true;
                v.depth = depth;
                v.end = asm.bci();
                v.start = v.end-1;
                break block;
            }

            v = findParentVar(name);
            if (v == null) {
                asm.ldc(name);
                asm.invokeS("roj/mildwind/asm/JsFunctionCompiled", "ggv", "(Ljava/lang/String;)Lroj/mildwind/type/JsObject;");
                return;
            } else {
                if (v.start < 0) {
                    //hole(v);
                    //return;
                }
            }
        } else {
            if (v.start < 0) {
                //hole(v);
                //return;
            }

            v.end = asm.bci();
        }

        asm.addSegment(new VarSegment(v, true));
    }

    private Variable findParentVar(String name) {
        AsmClosure ctx = parent;
        while (ctx != null) {
            Variable v = ctx.activeVars.get(name);
            if (v != null) return v;
            ctx = ctx.parent;
        }
        return null;
    }

    private void hole(Variable v) {
        if (!v.isVar) throw new IllegalStateException("trap: '"+v.name+"' is not defined");
        asm.field(GETSTATIC, "roj/mildwind/type/JsNull", "UNDEFINED", "Lroj/mildwind/type/JsObject;");
    }

    public boolean has(String name) { return activeVars.containsKey(name); }
    public JsObject getIfConstant(String name) {
        Variable v = activeVars.get(name);
        if (v == null) {
            if (paramRef.containsKey(name)) return null;
            v = findParentVar(name);
        }
        return v == null ? null : v.constval;
    }

    public List<Variable> finish() {
        if (finished) return Collections.emptyList();
        finished = true;

        List<Variable> unused = new SimpleList<>();

        VarMapper vm = new VarMapper();
        for (Variable v : variables) {
            if (v.start < 0 || v.start == v.end) unused.add(v);
            else vm.add(v);
        }

        int count = vm.map();
        closure = new JsObject[count];
        // null？没事，有hole，除非乱goto...
        Arrays.fill(closure, JsNull.UNDEFINED);

        return unused;
    }

    public void setAsm(JsMethodWriter asm) {
        this.asm = asm;
        asm.addSegment(hs = new HeaderSegment());
    }

    final class HeaderSegment extends Segment {
        List<Variable> ks;
        IntList vs;

        @Override
        protected boolean put(CodeWriter to) {
            if (length != 0 || ks == null) return false;
            int begin = to.bw.wIndex();

            for (int i = 0; i < ks.size(); i++) {
                to.one(ALOAD_0);
                to.field(GETFIELD, asm.data, vs.get(i));
                to.vars(ASTORE, ks.get(i).slot+3);
            }
            length = (char) (to.bw.wIndex() - begin);
            return true;
        }

        public void add(Variable v, int fid) {
            if (ks == null) {
                ks = new SimpleList<>();
                vs = new IntList();
            }
            ks.add(v);
            vs.add(fid);
        }
    }

    final class VarSegment extends Segment {
        final Variable ref;
        final boolean get;

        VarSegment(Variable ref, boolean get) {
            this.ref = ref;
            this.get = get;
            this.length = 6; // ALOAD_0(1) ICONST_(1) ICONST_(1) INVOKE(3)
        }

        @Override
        protected boolean put(CodeWriter to) {
            int begin = to.bw.wIndex();

            if (!variables.contains(ref)) {
                to.one(ALOAD_0);
                to.ldc(ref.depth);
                to.ldc(ref.slot);
                if (!get) {
                    to.invoke(INVOKESTATIC, "roj/mildwind/asm/JsFunctionCompiled", "sv", "(Lroj/mildwind/type/JsObject;Lroj/mildwind/asm/JsFunctionCompiled;II)V");
                } else {
                    to.invoke(INVOKEVIRTUAL, "roj/mildwind/asm/JsFunctionCompiled", "gv", "(II)Lroj/mildwind/type/JsObject;");
                }
            } else {
                to.vars(get?ALOAD:ASTORE, ref.slot+3);
            }

            begin = to.bw.wIndex() - begin;
            if (length != begin) {
                length = (char) begin;
                return true;
            }
            return false;
        }
    }
}
