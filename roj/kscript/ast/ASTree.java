package roj.kscript.ast;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Range;
import roj.asm.struct.Clazz;
import roj.collect.IntMap;
import roj.config.word.LineHandler;
import roj.kscript.KConstants;
import roj.kscript.ast.node.*;
import roj.kscript.func.KFuncAST;
import roj.kscript.parser.Marks;
import roj.kscript.type.Context;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.math.MutableBoolean;
import roj.math.MutableInt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final String depth, file, self;
    private String region;

    private MutableInt index; // 作用域offset
    private MutableBoolean changed;
    private LineNumber ln;
    private byte markFlag;

    private List<LabelNode> labels = new ArrayList<>();


    private ASTree(String depth, String file, String self) {
        this.depth = depth;
        this.file = file;
        this.self = self;
        this.index = new MutableInt(1);
        this.changed = new MutableBoolean(false);
    }

    private ASTree(String depth, String file, String self, int magic) {
        this.depth = depth;
        this.file = file;
        this.self = self;
    }

    public static ASTree builder(String file) {
        return new ASTree("JsFile", file, "<root>");
    }

    public static ASTree builder(ASTree parent, String selfName) {
        return new ASTree(parent.depth + '.' + parent.self, parent.file, selfName);
    }

    public static StringBuilder toString(Node begin, StringBuilder sb) {
        while (begin != null) {
            sb.append(begin).append(';').append('\n');
            begin = begin.next();
        }
        return sb;
    }

    /**
     * 作用域不变
     */
    public static ASTree copy(ASTree tree) {
        ASTree tree1 = new ASTree(tree.depth, tree.file, tree.self, 0);
        tree1.index = tree.index;
        tree1.changed = tree.changed;
        tree1.labels = tree.labels;
        return tree1;
    }

    public void AppendTo(ASTree tree) {
        if (head != null)
            tree.node0(head);
    }

    public KFuncAST build(KObject proto, Context ctx) {
        removeUselessCode();
        KFuncAST ast = new KFuncAST(proto, ctx.init(), head, depth);
        ast.setSource(file);
        ast.setName(self);
        return ast;
    }

    private void removeUselessCode() {
        Node pr = null;
        Node n = this.head;

        while (n != null) {
            if (n.getCode() == ASTCode.POP) {
                if (pr.getCode() == ASTCode.INVOKE_FUNCTION) {
                    pr.setCode(ASTCode.INVOKE_FUNC_NORET);
                    pr.next(n.next());
                }
            }
            pr = n;
            n = n.next();
        }
    }

    public KFuncAST build(Context ctx) {
        return build(KConstants.FUNCTION, ctx);
    }

    public int NextRegion() {
        return NextRegion(null);
    }

    public int NextRegion(String name) {
        changed.set(false);
        node0(new RegionNode(index.getValue()));
        this.region = name;
        return index.getAndIncrement();
    }

    public ASTree Std(ASTCode code) {
        return Node(new NPASTNode(code));
    }

    /**
     * 结果成立往下执行，不成立去往ifFalse
     */
    public ASTree If(LabelNode ifFalse, @MagicConstant(intValues = {IfNode.EQU, IfNode.FEQ, IfNode.GEQ, IfNode.GTR, IfNode.IS_TRUE, IfNode.LEQ, IfNode.LSS, IfNode.NEQ}) byte type) {
        return Node(new IfNode(type, ifFalse));
    }

    public ASTree Goto(LabelNode target) {
        return Node(new GotoNode(target));
    }

    public ASTree Invoke(@Range(from = 0, to = Integer.MAX_VALUE) int argc) {
        return Node(new InvocationNode(true, argc));
    }

    public ASTree New(@Range(from = 0, to = Integer.MAX_VALUE) int argc) {
        return Node(new InvocationNode(false, argc));
    }

    public ASTree Inc(String name, int count) {
        return Node(new IncrNode(name, count));
    }

    public LabelNode Label(String name) {
        LabelNode node = new LabelNode();
        node0(node);
        return node;
    }

    public ASTree Switch(LabelNode def, Map<String, LabelNode> map) {
        return Node(new StringSwitchNode(def, map));
    }

    public ASTree Switch(LabelNode def, IntMap<LabelNode> map) {
        return Node(new IntSwitchNode(def, map));
    }

    public ASTree Load(KType constant) {
        return Node(new LoadDataNode(constant));
    }

    public ASTree Get(String constant) {
        return Node(new VarNode(constant, true));
    }

    public void Set(String constant) {
        node0(new VarNode(constant, false));
    }

    public ASTree TryEnter(LabelNode handler, LabelNode fin, LabelNode end) {
        return Node(new TryCatchNode(handler, fin, end));
    }

    public String lastRegionName() {
        return region == null ? "" : region;
    }

    @SuppressWarnings("fallthrough")
    public void node0(Node node) {
        switch (markFlag) {
            case Marks.START: {
                if (mark_head == null) mark_head = node;
                if (mark_tail != null) {
                    mark_tail.next(node);
                }
                mark_tail = node;
            }
            break;
            case Marks.NEXT: {
                if (node.getCode() != ASTCode.POP) {
                    tail.next(mark_head);
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
                    for (LabelNode node1 : labels) {
                        node1.next(node);
                    }
                    labels.clear();
                }

                switch (node.getCode()) {
                    case LOAD_VARIABLE:
                    case SET_VARIABLE:
                        changed.set(true);
                }

                if (head == null) head = node;
                if (tail != null) {
                    tail.next(node);
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

    @Override
    public void handleLineNumber(int line) {
        if (ln == null || (tail != ln && ln.line < line + 1)) {
            node0(ln = new LineNumber());
        }
        if (ln != null) {
            ln.line = line + 1;
        }
    }

    public ASTree Section(@MagicConstant(valuesFromClass = Marks.class) byte mark) {
        markFlag = mark;
        return this;
    }
}
