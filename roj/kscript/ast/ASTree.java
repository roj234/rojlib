package roj.kscript.ast;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Range;
import roj.asm.struct.Clazz;
import roj.collect.CrossFinder;
import roj.collect.MyHashSet;
import roj.collect.ToIntMap;
import roj.config.word.LineHandler;
import roj.kscript.func.KFuncAST;
import roj.kscript.parser.Marks;
import roj.kscript.type.KType;
import roj.kscript.util.ContextPrimer;
import roj.kscript.util.LineInfo;
import roj.kscript.util.SwitchMap;
import roj.kscript.util.Variable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The ASTree (basically called the Abstract Syntax Tree, a kind of machine friendly 'code'), <br>
 * In the KScript, it can also compile to Java's bytecode by using {@link Clazz :the Roj ASM}.
 *
 * @author Roj233
 * @since 2020/9/28 12:44
 */
public final class ASTree implements LineHandler {
    private Node head, tail,
            mark_head, mark_tail;
    private final String depth, file;
    private String self;

    private final ArrayList<LineInfo> lineIndexes = new ArrayList<>();

    private byte markFlag;

    private ArrayList<LabelNode> labels = new ArrayList<>();

    private ASTree(String depth, String file) {
        this.depth = depth;
        this.file = file;
    }

    public void funcName(String name) {
        this.self = name;
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
        Frame frame = new Frame(lineIndexes, ctx);
        _finalOp(frame, ctx.locals);
        System.out.println("Closure for " + frame + " at " + depth + '.' + self + " is " + Arrays.toString(frame.parents));
        return (KFuncAST) new KFuncAST(head, frame).set(file, self, depth);
    }

    /**
     * 后处理
     */
    private void _finalOp(Frame frame, ArrayList<Variable> lets) {
        Node cur = this.head;

        MyHashSet<Frame> closures = new MyHashSet<>();
        ToIntMap<Node> indexer = new ToIntMap<>();
        int fr = 0;

        // pass 1: optimize code
        // pass 2: index nodes
        while (cur != null) {
            indexer.put(cur, fr++);
            switch (cur.code) {
                case SET_VARIABLE:
                case LOAD_VARIABLE:
                    closures.add(frame.findProvider(((VarNode)cur).name));
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
            if(v.start != null && v.end != null)
                cf.add(new CrossFinder.Wrap<>(v, indexer.getInt(v.start), indexer.getInt(v.end)));
            else
                System.out.println("Variable " + v.name + " not used");
        }

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

        // pass 4: generate shorten closure chains.
        fr = 0;
        int ct = 0;
        Context self = frame;
        while (self != null) {
            if(self instanceof Frame)
                fr++;
            self = self.parent;
            ct++;
        }

        if(closures.size() < fr) {
            Context[] selfParent = new Context[ct];
            ct = 0;
            self = frame;
            while (self != null) {
                selfParent[ct++] = self;
                self = self.parent;
            }
            List<Context> sorter = Arrays.asList(selfParent);

            ct = selfParent.length - fr;
            Context[] parents = new Context[closures.size() + ct];
            for (fr = 0; fr < ct; fr++) {
                parents[fr] = selfParent[fr];
            }
            for (Frame f1 : closures) {
                parents[ct++] = f1;
            }

            Arrays.sort(parents, (o1, o2) -> {
                int a = sorter.indexOf(o1),
                        b = sorter.indexOf(o2);
                if(a == -1 || b == -1)
                    throw new IllegalArgumentException(String.valueOf(a == -1 ? o1 : o2));
                return (a < b) ? -1 : ((a == b) ? 0 : 1);
            });

            frame.parents = parents;
        }
    }

    public ASTree Std(ASTCode code) {
        return Node(new NPASTNode(code));
    }

    /**
     * 结果成立往下执行，不成立去往ifFalse
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
        switch (markFlag) {
            case Marks.START: {
                if (mark_head == null) mark_head = node;
                if (mark_tail != null) {
                    mark_tail.next = node;
                }
                mark_tail = node;
            }
            break;
            case Marks.NEXT: {
                if (node.getCode() != ASTCode.POP) {
                    tail.next = mark_head;
                }
                markFlag = Marks.END;
                mark_head = mark_tail = null;
            }
            break;
            default: {
                if (node instanceof LabelNode) {
                    labels.add((LabelNode) node);
                    return;
                } else if (!labels.isEmpty()) {
                    for (int i = 0; i < labels.size(); i++) {
                        LabelNode node1 = labels.get(i);
                        node1.next = node;
                    }
                    labels.clear();
                }

                if (head == null) head = node;
                if (tail != null) {
                    tail.next = node;
                }
                tail = node;
            }
            break;
        }
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
        LineInfo ln = lineIndexes.isEmpty() ? null : lineIndexes.get(lineIndexes.size() - 1);
        if (ln == null || (tail != ln.node && ln.line < line + 1)) {
            if(ln == null)
                lineIndexes.add(ln = new LineInfo());
            ln.node = tail;
        }
        ln.line = line + 1;
    }

    public ASTree Mark(@MagicConstant(valuesFromClass = Marks.class) byte mark) {
        markFlag = mark;
        return this;
    }
}
