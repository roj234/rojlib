package roj.kscript.ast;

import org.jetbrains.annotations.Range;
import roj.asm.struct.Clazz;
import roj.collect.CrossFinder;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.word.LineHandler;
import roj.kscript.func.KFuncAST;
import roj.kscript.type.KType;
import roj.kscript.util.ContextPrimer;
import roj.kscript.util.LineInfo;
import roj.kscript.util.SwitchMap;
import roj.kscript.util.Variable;

import java.util.*;

/**
 * The ASTree (basically called the Abstract Syntax Tree, a kind of machine friendly 'code'), <br>
 * In the KScript, it can also compile to Java's bytecode by using {@link Clazz :the Roj ASM}.
 *
 * @author Roj233
 * @since 2020/9/28 12:44
 */
public final class ASTree implements LineHandler {
    public static final int COMPRESS_DT_CTXS = 1;

    private Node head, tail;
    private final String depth, file;
    private String self;

    private final ArrayList<LineInfo> lineIndexes = new ArrayList<>();
    private LineInfo curLine;

    private ArrayList<LabelNode> labels = new ArrayList<>();

    private ASTree(String depth, String file) {
        this.depth = depth;
        this.file = file;
    }

    public void funcName(String name) {
        this.self = name;
    }

    public String funcName() {
        return depth + '.' + self;
    }

    public static ASTree builder(String file) {
        ASTree tree = new ASTree("JsFile", file);
        tree.self = "<root>";
        return tree;
    }

    public static ASTree builder(ASTree parent) {
        return new ASTree(parent.depth + '.' + parent.self, parent.file);
    }

    public static StringBuilder toString(Node begin, StringBuilder sb) {
        while (begin != null) {
            sb.append(begin).append(';').append('\n');
            begin = begin.next;
        }
        return sb;
    }

    /**
     * 作用域不变
     */
    public static ASTree copy(ASTree tree) {
        ASTree tree1 = new ASTree(tree.depth, tree.file);
        tree1.self = tree.self;
        tree1.labels = tree.labels;
        return tree1;
    }

    public void AppendTo(ASTree tree) {
        if (head != null)
            tree.node0(head);
    }

    public KFuncAST build(ContextPrimer ctx) {
        //System.out.println("Build " + depth + '.' + self/* + " ctx = " + ctx*/);
        //System.out.println("LineIndexes " + lineIndexes);
        Frame frame = new Frame(lineIndexes, ctx, fr -> {
            ctx.finish(fr);
            _finalOp(fr, ctx.locals);
            System.out.println(ASTree.this);
        });
        //System.out.println("Closure chain is " + Arrays.toString(frame.parents));
        //System.out.println("AST: \r\n" + this);
        return (KFuncAST) new KFuncAST(head, frame).set(file, self, depth);
    }

    /**
     * 后处理
     */
    private void _finalOp(Frame frame, ArrayList<Variable> lets) {
        Node cur = this.head;

        MyHashSet<String> stringInterner = new MyHashSet<>();
        MyHashSet<KType> dataInterner = new MyHashSet<>();

        MyHashSet<Frame> closures = new MyHashSet<>();
        int i = 0;

        if(!lets.isEmpty()) {
            IntBiMap<Node> indexer = new IntBiMap<>();

            // pass 1: optimize code
            // pass 2: index nodes
            while (cur != null) {
                indexer.put(i++, cur);
                switch (cur.code) {
                    case PUT_VAR:
                    case GET_VAR:
                        VarNode cur1 = (VarNode) cur;
                        cur1.name = stringInterner.intern(cur1.name);
                        closures.add(frame.findProvider(cur1.name));
                        break;
                    case INCREASE:
                        IncrNode cur2 = (IncrNode) cur;
                        cur2.name = stringInterner.intern(cur2.name);
                        closures.add(frame.findProvider(cur2.name));
                        break;
                    case LOAD:
                        LoadDataNode ld = ((LoadDataNode) cur);
                        dataInterner.intern(ld.data);
                        break;
                    case GOTO:
                    case IF:
                    case SWITCH:
                    case TRY_ENTER: // only these node need to be compiled for better performance
                        cur.compile();
                        break;
                }

                cur = cur.next;
            }

            CrossFinder<CrossFinder.Wrap<Variable>> cf = new CrossFinder<>(lets.size() + 1);
            for (Variable v : lets) {
                // cf: start include and end exclude
                if (v.end != null) {
                    cf.add(new CrossFinder.Wrap<>(v, v.start == null ? 0 : indexer.getByValue(v.start), indexer.getByValue(v.end) + 1));
                } else
                    System.out.println("local " + v.name + " is not used.");
            }

            MyHashMap<Node, VInfo> mi = new MyHashMap<>(cf.arraySize());

            List<CrossFinder.Wrap<Variable>> last = Collections.emptyList();
            for (CrossFinder.Region region : cf) {
                List<CrossFinder.Wrap<Variable>> curr = region._int_mod_value();

                VInfo info = NodeUtil.calcDiff(last, curr);
                if (info == null)
                    System.out.println("Should not be null: " + curr);

                mi.put(indexer.get(region.node().pos()), info);

                last = curr;
            }
            frame.linearDiff = mi.isEmpty() ? Collections.emptyMap() : mi;

            cur = this.head;

            // pass 3: create actions on control flow nodes.
            while (cur != null) {
                switch (cur.code) {
                    case GOTO:
                    case IF:
                    case SWITCH:
                        cur.genDiff(cf, indexer);
                        break;
                }

                cur = cur.next;
            }
        } else {
            // pass 1: optimize code
            while (cur != null) {
                switch (cur.code) {
                    case PUT_VAR:
                    case GET_VAR:
                        VarNode cur1 = (VarNode) cur;
                        cur1.name = stringInterner.intern(cur1.name);
                        closures.add(frame.findProvider(cur1.name));
                        break;
                    case INCREASE:
                        IncrNode cur2 = (IncrNode) cur;
                        cur2.name = stringInterner.intern(cur2.name);
                        closures.add(frame.findProvider(cur2.name));
                        break;
                    case LOAD:
                        LoadDataNode ld = ((LoadDataNode) cur);
                        dataInterner.intern(ld.data);
                        break;
                    case GOTO:
                    case IF:
                    case SWITCH:
                    case TRY_ENTER:
                        cur.compile();
                        break;
                }

                cur = cur.next;
            }
            frame.linearDiff = Collections.emptyMap();
        }

        // pass 4: generate shorten closure chains.
        ArrayList<Context> sorter = new ArrayList<>();

        Context self = frame;
        while (self != null) {
            sorter.add(self);
            self = self.parent;
        }

        if(sorter.size() - closures.size() > COMPRESS_DT_CTXS) {
            int j = 0;
            if(closures.remove(null)) // 使用了 Context, but, 现在是JsFile.<root>
                closures.add((Frame) sorter.get(0));

            Context[] parents = new Context[closures.size()];
            for (Frame f1 : closures) {
                parents[j++] = f1;
            }

            Arrays.sort(parents, CtxCpr.take(sorter));
            //System.out.println(funcName() + ".ClosureSt=" + Arrays.toString(parents));

            frame.parents = parents;
        }
    }

    public ASTree Std(OpCode code) {
        return Node(new NPASTNode(code));
    }

    /**
     * 若栈上为false-like去往ifFalse
     */
    public ASTree If(LabelNode ifFalse, short type) {
        return Node(new IfNode(type, ifFalse));
    }

    /**
     * 把if比较的结果放到栈上
     */
    public ASTree IfLoad(short type) {
        return Node(new IfLoadNode(type));
    }

    public ASTree Goto(LabelNode target) {
        return Node(new GotoNode(target));
    }

    public ASTree Invoke(@Range(from = 0, to = Integer.MAX_VALUE) int argc, boolean noRet) {
        return Node(new InvokeNode(true, argc, noRet));
    }

    public ASTree New(@Range(from = 0, to = Integer.MAX_VALUE) int argc, boolean noRet) {
        return Node(new InvokeNode(false, argc, noRet));
    }

    public ASTree Inc(String name, int count) {
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

    public ASTree Load(KType constant) {
        return Node(new LoadDataNode(constant));
    }

    public ASTree Get(String name) {
        return Node(new VarNode(name, VarNode.GET));
    }

    public void Set(String name) {
        node0(new VarNode(name, VarNode.SET));
    }

    public ASTree TryEnter(LabelNode _catch, LabelNode _finally, LabelNode _norm_exec_end) {
        return Node(new TryNode(_catch, _finally, _norm_exec_end));
    }

    public ASTree TryEnd(LabelNode realEnd) {
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

    public ASTree Node(Node node) {
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
    }

    public Node last() {
        return tail;
    }

    public void last(Node last) {
        last.getClass();
        tail = last;
    }

    @Override
    public void handleLineNumber(int line) {
        LineInfo ln = curLine;
        if(ln == null)
            ln = curLine = new LineInfo();
        ln.line = line;
    }

    private static class CtxCpr implements Comparator<Context> {
        static final CtxCpr instance = new CtxCpr();

        private ArrayList<Context> sorter;

        public static CtxCpr take(ArrayList<Context> sorter) {
            instance.sorter = sorter;
            return instance;
        }

        @Override
        public int compare(Context o1, Context o2) {
            int a = sorter.indexOf(o1),
                    b = sorter.indexOf(o2);
            if (a == -1 || b == -1)
                throw new IllegalArgumentException(String.valueOf(a == -1 ? o1 : o2));
            return (a < b) ? -1 : ((a == b) ? 0 : 1);
        }
    }
}
