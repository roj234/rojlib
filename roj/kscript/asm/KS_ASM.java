/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.kscript.asm;

import org.jetbrains.annotations.Range;
import roj.collect.IntBiMap;
import roj.collect.IntList;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.Unioner.Region;
import roj.config.word.LineHandler;
import roj.kscript.type.KType;
import roj.kscript.util.*;
import roj.kscript.util.opm.ConstMap;
import roj.kscript.vm.ErrorInfo;
import roj.kscript.vm.Func;
import roj.kscript.vm.Func_Try;
import roj.util.Helpers;
import roj.util.VarMapper;
import roj.util.VarMapper.Var;

import java.util.*;
import java.util.function.Consumer;

import static roj.collect.IntMap.NOT_USING;

/**
 * @author Roj233
 * @since 2020/9/28 12:44
 */
public final class KS_ASM implements LineHandler {
    public static final boolean DEBUG = System.getProperty("kscript.debug", "false").equalsIgnoreCase("true");

    static final class DataUniquer extends MyHashSet<KType> {
        @Override
        public Object getEntry(KType id) {
            if (entries == null) {
                return NOT_USING;
            }
            Object obj = entries[indexFor(id)];
            while (obj instanceof Entry) {
                Entry prev = (Entry) obj;
                if (Objects.equals(id, prev.k))
                    return obj;
                obj = prev.next;
            }
            if (Objects.equals(id, obj))
                return obj;
            return NOT_USING;
        }

        public KType find1(KType data) {
            KType r = find(data);
            if (r == data)
                add(data);
            return r;
        }
    }

    private Node head, tail;
    private final String depth, file;
    private String self;

    private final ArrayList<LineInfo> lineIndexes = new ArrayList<>();
    private LineInfo curLine;

    private final ArrayList<LabelNode> labels = new ArrayList<>();

    private KS_ASM(String depth, String file) {
        this.depth = depth;
        this.file = file;
    }

    public void funcName(String name) {
        this.self = name;
    }

    public String funcName() {
        return depth + '.' + self;
    }

    public static KS_ASM builder(String file) {
        KS_ASM tree = new KS_ASM("JsFile", file);
        tree.self = "<root>";
        return tree;
    }

    public static KS_ASM builder(KS_ASM parent) {
        return new KS_ASM(parent.depth + '.' + parent.self, parent.file);
    }

    public static StringBuilder toString(Node begin, StringBuilder sb) {
        while (begin != null) {
            sb.append(begin).append(';').append('\n');
            begin = begin.next;
        }
        return sb;
    }

    public void AppendTo(KS_ASM tree) {
        if (head != null)
            tree.node0(head);
    }

    public Func build(ContextPrimer ctx, int maxTryLevel) {
        Consumer<Frame> hnd = fr -> {
            _finalOp(fr, ctx);
            if (DEBUG)
                System.out.println(KS_ASM.this);
        };

        boolean advanced = maxTryLevel > 0 | ctx.isAdvanced();

        if(advanced) {
            FramePlus frame = new FramePlus(lineIndexes, hnd);

            if (maxTryLevel > 0) {
                frame.tryNodeBuf = new TryEnterNode[maxTryLevel];
                ErrorInfo[] ei = frame.tryDataBuf = new ErrorInfo[maxTryLevel];
                for (int i = 0; i < maxTryLevel; i++) {
                    ei[i] = new ErrorInfo(0, null, null);
                }
            }

            //if(ctx.defaults.size() > 0) {

            //}

            return (Func) (maxTryLevel > 0 ? new Func_Try(head, frame) : new Func(head, frame)).set(file, self, depth);
        } else {
            return (Func) new Func(head, new Frame(lineIndexes, hnd)).set(file, self, depth);
        }
    }

    static KType[] EMPTY = new KType[0];

    /**
     * ?????????
     */
    private void _finalOp(Frame frame, ContextPrimer ctx) {
        ctx.handle(frame);

        ConstMap vars = ctx.globals;
        ArrayList<Variable> lets = ctx.locals;
        ArrayList<String> usedArgs = ctx.usedArgs;

        int i;

        Node cur = this.head;

        DataUniquer dataUniquer = new DataUniquer();

        MyHashMap<Frame, List<Node>> closures = new MyHashMap<>();

        if(!lets.isEmpty()) {
            IntBiMap<Node> indexer = new IntBiMap<>();

            // pass 1: transform variable node
            // pass 2: index nodes
            i = 0;
            Node prev = null, n1;
            while (cur != null) {
                indexer.put(i++, cur);
                switch (cur.getCode()) {
                    case PUT_VAR:
                    case GET_VAR: {
                        VarGNode cur1 = (VarGNode) cur;
                        Frame fr = ctx.findProvider(cur1.name.toString());
                        if (fr != null) {
                            cur1.name = new Object[]{
                                    cur1.name,
                                    n1 = cur.getCode() == Opcode.GET_VAR ? new LVarGNode() : new LVarSNode()
                            };
                            if (prev != null)
                                prev.next = n1;
                            else
                                head = frame.owner.begin = n1;
                            n1.next = cur.next;
                            indexer.put(i - 1, cur = n1);
                            closures.computeIfAbsent(fr, Helpers.fnArrayList()).add(cur1);
                        } else {
                            closures.put(null, Collections.emptyList());
                        }
                    }
                    break;
                    case INCREASE: {
                        IncrNode cur2 = (IncrNode) cur;
                        Frame fr1 = ctx.findProvider(cur2.name.toString());
                        if (fr1 != null) {
                            cur2.name = new Object[]{
                                    cur2.name,
                                    n1 = new LvtIncrNode(cur2.val)
                            };
                            if (prev != null)
                                prev.next = n1;
                            else
                                head = frame.owner.begin = n1;
                            n1.next = cur.next;
                            indexer.put(i - 1, cur = n1);
                            closures.computeIfAbsent(fr1, Helpers.fnArrayList()).add(cur2);
                        } else {
                            closures.put(null, Collections.emptyList());
                        }
                    }
                    break;
                    case LOAD: {
                        LoadDataNode ld = ((LoadDataNode) cur);
                        ld.data = dataUniquer.find1(ld.data);
                    }
                    break;
                    case INVOKE:
                        sect: {
                            InvokeNode node1 = (InvokeNode) cur;
                            if(!ctx.top()) {
                                if(cur.next != null)
                                    switch (cur.next.getCode()) {
                                        default:
                                            break sect;
                                        case RETURN:
                                        case RETURN_EMPTY:
                                            break;
                                    }

                                n1 = new TailCall(node1);
                                if (prev != null)
                                    prev.next = n1;
                                else
                                    head = frame.owner.begin = n1;
                                n1.next = cur.next.next;
                                indexer.put(i - 1, cur = n1);
                            }
                        }
                        break;
                }

                prev = cur;
                cur = cur.next;
            }

            // ?????????????????????
            MyHashMap<Node, VInfo> mi = new MyHashMap<>();
            VarMapper cf = new VarMapper() {
                @Override
                protected void onRegion(Region prev, Region r) {
                    List<IVar> last = prev == null ? Collections.emptyList() : prev.value();
                    VInfo info = null;//NodeUtil.calcDiff(last, r.value());
                    if (info == null)
                        System.out.println("Should not be null: " + r.value());

                    mi.put(indexer.get((int) r.pos()), info);
                }
            };

            List<Variable> usedVar = new ArrayList<>();

            for (int j = 0; j < lets.size(); j++) {
                Variable v = lets.get(j);
                // cf: start include and end exclude
                if (v.end != null) {
                    Var v1 = new Var();
                    v1.att = v;
                    v1.start = v.start == null ? 0 : indexer.getInt(v.start.replacement());
                    v1.end = indexer.getInt(v.end.replacement()) + 1;
                    cf.add(v1);
                    usedVar.add(v);
                }
            }

            mi.ensureCapacity(cf.regionCount());


            int slotInUse = vars.size() + usedArgs.size();
            int maxInUse = cf.map(Collections.emptyList());

            mkVarName(frame, vars, usedVar, usedArgs, ctx.restParId);

            if(vars.isEmpty() && maxInUse == usedArgs.size()) {
                frame.lvt = usedArgs.isEmpty() ? EMPTY : new KType[usedArgs.size()];
                frame.lvtChk = frame.lvtDef = EMPTY;
            } else {
                frame.lvt = new KType[vars.size() + usedArgs.size() + maxInUse];
                KType[] def = frame.lvtDef = new KType[vars.size() + maxInUse];

                i = 0;
                for (KType type : vars.values()) {
                    def[i++] = type;
                }
                for (int j = 0; j < usedVar.size(); j++) {
                    def[i++] = usedVar.get(j).def;
                }
            }

            frame.linearDiff = mi.isEmpty() ? Collections.emptyMap() : mi;

            mkVarIds(ctx, closures);

            cur = this.head;
            prev = null;

            // pass 3: create actions on control flow nodes.
            while (cur != null) {
                switch (cur.getCode()) {
                    case IF:
                    case SWITCH:
                    case TRY_ENTER:
                    case TRY_EXIT: // only these node need to be compiled for better performance
                        cur.compile();
                        //cur.genDiff(cf, indexer);
                        break;
                    case GOTO:
                        cur.compile();
                        //cur.genDiff(cf, indexer);
                        ((GotoNode)cur).checkNext(prev);
                        break;
                }

                prev = cur;
                cur = cur.next;
            }
        } else {
            frame.linearDiff = Collections.emptyMap();

            Node prev = null, n1;
            // pass 1: transform variable node
            while (cur != null) {
                switch (cur.getCode()) {
                    case PUT_VAR:
                    case GET_VAR: {
                        VarGNode cur1 = (VarGNode) cur;
                        Frame fr = ctx.findProvider(cur1.name.toString());
                        if (fr != null) {
                            cur1.name = new Object[]{
                                    cur1.name,
                                    n1 = cur.getCode() == Opcode.GET_VAR ? new LVarGNode() : new LVarSNode()
                            };
                            if (prev != null)
                                prev.next = n1;
                            else
                                head = frame.owner.begin = n1;
                            n1.next = cur.next;
                            cur = n1;
                            closures.computeIfAbsent(fr, Helpers.fnArrayList()).add(cur1);
                        } else {
                            closures.put(null, Collections.emptyList());
                        }
                    }
                    break;
                    case INCREASE: {
                        IncrNode cur2 = (IncrNode) cur;
                        Frame fr1 = ctx.findProvider(cur2.name.toString());
                        if (fr1 != null) {
                            cur2.name = new Object[]{
                                    cur2.name,
                                    n1 = new LvtIncrNode(cur2.val)
                            };
                            if (prev != null)
                                prev.next = n1;
                            else
                                head = frame.owner.begin = n1;
                            n1.next = cur.next;
                            closures.computeIfAbsent(fr1, Helpers.fnArrayList()).add(cur2);
                        } else {
                            closures.put(null, Collections.emptyList());
                        }
                    }
                    break;
                    case LOAD: {
                        LoadDataNode ld = ((LoadDataNode) cur);
                        ld.data = dataUniquer.find1(ld.data);
                    }
                    break;
                    case INVOKE:
                        sect: {
                            InvokeNode node1 = (InvokeNode) cur;
                            if(!ctx.top()) {
                                if(cur.next != null)
                                    switch (cur.next.getCode()) {
                                        default:
                                            break sect;
                                        case RETURN:
                                        case RETURN_EMPTY:
                                            break;
                                    }

                                n1 = new TailCall(node1);
                                if (prev != null)
                                    prev.next = n1;
                                else
                                    head = frame.owner.begin = n1;
                                n1.next = cur.next.next;
                            }
                        }
                        break;
                }

                prev = cur;
                cur = cur.next;
            }

            mkVarName(frame, vars, Collections.emptyList(), usedArgs, ctx.restParId);

            if(vars.isEmpty()) {
                frame.lvt = usedArgs.isEmpty() ? EMPTY : new KType[usedArgs.size()];
                frame.lvtChk = frame.lvtDef = EMPTY;
            } else {
                frame.lvt = new KType[vars.size() + usedArgs.size()];
                KType[] def = frame.lvtDef = new KType[vars.size()];
                i = 0;
                for (KType type : vars.values()) {
                    def[i++] = type;
                }
            }

            mkVarIds(ctx, closures);

            // pass 2 optimize code
            cur = this.head;
            prev = null;

            // pass 3: create actions on control flow nodes.
            while (cur != null) {
                switch (cur.getCode()) {
                    case IF:
                    case SWITCH:
                    case TRY_ENTER:
                    case TRY_EXIT: // only these nodes need to be compiled for better performance
                        cur.compile();
                        break;
                    case GOTO:
                        cur.compile();
                        ((GotoNode)cur).checkNext(prev);
                        break;
                }

                prev = cur;
                cur = cur.next;
            }
        }

        for (i = 0; i < lineIndexes.size(); i++) {
            LineInfo info = lineIndexes.get(i);
            info.node = info.node.replacement();
        }

        if(DEBUG)
            System.out.println("LvtSize: " + frame.lvt.length);

        ctx.finish();
    }

    private static void mkVarName(Frame frame, ConstMap vars, List<Variable> lets, ArrayList<String> usedArgs, int spreadArgId) {
        for (int j = 0; j < usedArgs.size(); j++) {
            String key = usedArgs.get(j);
            if(key != null) {
                vars.remove(key);
            }
        }

        String[] st = frame.varNames = new String[vars.size() + usedArgs.size() + lets.size()];
        int i = 0;
        IntList usedArgs1 = new IntList();
        for (int j = 0; j < usedArgs.size(); j++) {
            String key = usedArgs.get(j);
            if(key != null) {
                st[i++] = key;
                vars.remove(key);
                usedArgs1.add(j);
            }
        }

        if(spreadArgId >= 0 && usedArgs.size() > spreadArgId && usedArgs.get(spreadArgId) != null) {
            usedArgs1.add(-1);
        }

        frame.usedArgs = usedArgs1.toArray();
        for (String key : vars.keySet()) {
            st[i++] = key;
        }
        for (int j = 0; j < lets.size(); j++) {
            Variable key = lets.get(j);
            st[i++] = key.name;
        }

        if(DEBUG) {
            System.out.println("VarNames: " + Arrays.toString(st));
            System.out.println("UsedArgs: " + Arrays.toString(frame.usedArgs));
        }
    }

    private static void mkVarIds(ContextPrimer ctx, MyHashMap<? extends IContext, List<Node>> closures) {
        // pass 4: generate shorten closure chains.
        List<IContext> sorter = new ArrayList<>();

        // by reverse order
        ContextPrimer self = ctx;
        do {
            sorter.add(self.built);
            self = self.parent;
        } while (self != null);

        // ????????? Context
        IContext global;
        if(closures.remove(null) == null) {
            sorter.remove(sorter.size() - 1);
            global = null;
        } else {
            global = sorter.get(sorter.size() - 1);
        }

        int j = 0;
        IContext[] parents = new IContext[closures.size() + (global == null ? 0 : 1)];
        for (IContext f1 : closures.keySet()) {
            parents[j++] = f1;
        }
        if(global != null)
            parents[j] = global;

        Arrays.sort(parents, CtxCpr.take(sorter));
        sorter = Arrays.asList(parents);

        for(Map.Entry<? extends IContext, List<Node>> entry : closures.entrySet()) {
            IContext key = entry.getKey();
            if(key.getClass() != Frame.class) continue;

            int ctxId = sorter.indexOf(key);
            for (Node node : entry.getValue()) {
                if(node instanceof VarGNode) {
                    VarGNode n = (VarGNode) node;
                    String name = (String) ((Object[])n.name)[0];
                    if(n.getCode() == Opcode.PUT_VAR) {
                        if(ctx.globals.isConst(name)) {
                            throw new JavaException("?????????????????? " + name);
                        }
                    }
                    LVarGNode n1 = (LVarGNode) ((Object[])n.name)[1];
                    n.name = n1;
                    n1.ctxId = ctxId;
                    n1.varId = Arrays.asList(((Frame)key).varNames).indexOf(name);
                } else {
                    IncrNode n = (IncrNode) node;
                    String name = (String) ((Object[])n.name)[0];
                    if(ctx.globals.isConst(name)) {
                        throw new JavaException("?????????????????? " + name);
                    }
                    LvtIncrNode n1 = (LvtIncrNode) ((Object[])n.name)[1];
                    n.name = n1;
                    n1.ctxId = ctxId;
                    n1.varId = Arrays.asList(((Frame)key).varNames).indexOf(name);
                }
            }
        }

        ((Frame)ctx.built).parents = parents;
    }

    public KS_ASM Std(Opcode code) {
        return Node(new NPNode(code));
    }

    /**
     * ????????????false-like??????ifFalse
     */
    public KS_ASM If(LabelNode ifFalse, short type) {
        return Node(new IfNode(type, ifFalse));
    }

    /**
     * ???if???????????????????????????
     */
    public KS_ASM IfLoad(short type) {
        return Node(new IfLoadNode(type));
    }

    public KS_ASM Goto(LabelNode target) {
        return Node(new GotoNode(target));
    }

    public KS_ASM Invoke(@Range(from = 0, to = Integer.MAX_VALUE) int argc, boolean noRet) {
        return Node(new InvokeNode(true, argc, noRet));
    }

    public KS_ASM New(@Range(from = 0, to = Integer.MAX_VALUE) int argc, boolean noRet) {
        return Node(new InvokeNode(false, argc, noRet));
    }

    public KS_ASM InvokeSpread(@Range(from = 0, to = Integer.MAX_VALUE) int argc, boolean noRet, long activeBits) {
        return Node(new InvokeDynamicNode(true, argc, noRet, activeBits));
    }

    public KS_ASM NewSpread(@Range(from = 0, to = Integer.MAX_VALUE) int argc, boolean noRet, long activeBits) {
        return Node(new InvokeDynamicNode(false, argc, noRet, activeBits));
    }

    public KS_ASM Inc(String name, int count) {
        return Node(new IncrNode(name, count));
    }

    public LabelNode Label() {
        LabelNode node = new LabelNode();
        node0(node);
        return node;
    }

    public SwitchNode Switch(LabelNode def, SwitchMap map) {
        final SwitchNode node = new SwitchNode(def, map);
        node0(node);
        return node;
    }

    public KS_ASM Load(KType constant) {
        return Node(new LoadDataNode(constant));
    }

    public KS_ASM Get(String name) {
        return Node(new VarGNode(name));
    }

    public void Set(String name) {
        node0(new VarSNode(name));
    }

    public TryEnterNode TryEnter(LabelNode _catch, LabelNode _finally, LabelNode _norm_exec_end) {
        TryEnterNode node = new TryEnterNode(_catch, _finally, _norm_exec_end);
        node0(node);
        return node;
    }

    /**
     * catch / finally ??? ??????
     */
    public KS_ASM TryRegionEnd(LabelNode realEnd) {
        return Node(new TryEndNode(realEnd));
    }

    @SuppressWarnings("fallthrough")
    public void node0(Node node) {
        ArrayList<LabelNode> labels = this.labels;
        if (node instanceof LabelNode) {
            labels.add((LabelNode) node);
            return;
        } else if (!labels.isEmpty()) {
            for (int i = 0; i < labels.size(); i++) {
                labels.get(i).next = node;
            }
            labels.clear();
        }

        if(curLine != null) {
            curLine.node = node;
            lineIndexes.add(curLine);
            curLine = null;
        }

        if (head == null) head = node;
        if (tail != null) {
            tail.next = node;
        }
        tail = node;
    }

    public KS_ASM Node(Node node) {
        node0(node);
        return this;
    }

    public boolean isEmpty() {
        return head == null;
    }

    @Override
    public String toString() {
        return toString(head, new StringBuilder()).toString();
    }

    public void Clear() {
        head = tail = null;
        labels.clear();
        lineIndexes.clear();
        curLine = null;
    }

    public Node last() {
        return tail;
    }

    @Override
    public void handleLineNumber(int line) {
        LineInfo ln = curLine;
        if(ln == null)
            ln = curLine = new LineInfo();
        ln.line = line;
    }

    public void last_A(Node last) {
        if((tail = last) == null)
            head = null;
    }

    private static class CtxCpr implements Comparator<IContext> {
        static final CtxCpr instance = new CtxCpr();

        private List<IContext> sorter;

        public static CtxCpr take(List<IContext> sorter) {
            instance.sorter = sorter;
            return instance;
        }

        @Override
        public int compare(IContext o1, IContext o2) {
            int a = sorter.indexOf(o1),
                    b = sorter.indexOf(o2);
            if (a == -1 || b == -1)
                throw new IllegalArgumentException(String.valueOf(a == -1 ? o1 : o2));
            return Integer.compare(a, b);
        }
    }
}
