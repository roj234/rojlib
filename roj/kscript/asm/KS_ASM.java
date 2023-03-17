package roj.kscript.asm;

import org.jetbrains.annotations.Range;
import roj.asm.Opcodes;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.SwitchSegment;
import roj.collect.MyHashSet;
import roj.config.word.LineHandler;
import roj.kscript.type.KType;
import roj.kscript.util.ContextPrimer;
import roj.kscript.util.LineInfo;
import roj.kscript.util.SwitchMap;
import roj.kscript.vm.ErrorInfo;
import roj.kscript.vm.Func;
import roj.kscript.vm.Func_Try;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj233
 * @since 2020/9/28 12:44
 */
public final class KS_ASM implements LineHandler {
	public static final boolean DEBUG = System.getProperty("kscript.debug", "false").equalsIgnoreCase("true");

	static final class DataUniquer extends MyHashSet<KType> {
		public Object find1(KType id) {
			if (entries == null) {
				return UNDEFINED;
			}
			Object obj = entries[indexFor(id)];
			while (obj instanceof Entry) {
				Entry prev = (Entry) obj;
				if (Objects.equals(id, prev.k)) return obj;
				obj = prev.next;
			}
			if (Objects.equals(id, obj)) return obj;
			return UNDEFINED;
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
		if (head != null) tree.node0(head);
	}

	public Func build(ContextPrimer ctx, int maxTryLevel) {
		Consumer<Frame> hnd = fr -> {
			//_finalOp(fr, ctx);
			if (DEBUG) System.out.println(KS_ASM.this);
		};

		boolean advanced = maxTryLevel > 0 | ctx.isAdvanced();

		if (advanced) {
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

	public KS_ASM Std(Opcode code) {
		return Node(new NPNode(code));
	}

	CodeWriter cw_;

	/**
	 * 若栈上为false-like去往ifFalse
	 */
	public KS_ASM If(LabelNode ifFalse, short type) {
		return Node(new IfNode(type, ifFalse));
	}

	/**
	 * 把if比较的结果放到栈上
	 */
	public KS_ASM IfLoad(short type) {
		cw_.one((byte) type);
		return this;
	}

	public KS_ASM Goto(LabelNode target) {
		cw_.jump(Opcodes.GOTO, target.javaLabel);
		return this;
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
		node.javaLabel = cw_.label();
		return node;
	}

	public SwitchNode Switch(LabelNode def, SwitchMap map) {
		// todo private static final SwitchMap map000
		final SwitchNode node = new SwitchNode(def, map);
		SwitchSegment seg = CodeWriter.newSwitch(Opcodes.LOOKUPSWITCH);
		cw_.switches(seg);
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
	 * catch / finally 的 终点
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

		if (curLine != null) {
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
		if (ln == null) ln = curLine = new LineInfo();
		ln.line = line;
	}

	public void last_A(Node last) {
		if ((tail = last) == null) head = null;
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
			int a = sorter.indexOf(o1), b = sorter.indexOf(o2);
			if (a == -1 || b == -1) throw new IllegalArgumentException(String.valueOf(a == -1 ? o1 : o2));
			return Integer.compare(a, b);
		}
	}
}
