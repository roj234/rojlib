package roj.compiler.asm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.attr.LineNumberTable;
import roj.asm.attr.LocalVariableTable;
import roj.asm.insn.*;
import roj.asm.type.IType;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.collect.Hasher;
import roj.compiler.CompileContext;
import roj.compiler.LavaCompiler;
import roj.compiler.api.Compiler;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static roj.asm.Opcodes.TRAIT_JUMP;
import static roj.asm.Opcodes.assertTrait;

/**
 * @author Roj233
 * @since 2022/2/24 19:19
 */
public class MethodWriter extends CodeWriter {
	public final CompileContext ctx;

	public MethodWriter(ClassNode unit, MethodNode mn, boolean generateLVT, CompileContext ctx) {
		this.ctx = ctx;
		this.init(new ByteList(),unit.cp,mn);
		if (generateLVT) locals = new LocalVariableTable("LocalVariableTable");
	}
	protected MethodWriter(MethodWriter parent) {
		this.ctx = parent.ctx;
		this.init(new ByteList(),parent.cpw,parent.mn);
		//lines由JavaTokenizer管理
		locals = parent.locals;
	}

	public MethodWriter fork() {return new MethodWriter(this);}

	//region lazy exception handler
	private final ArrayList<TryCatchEntry> trys = new ArrayList<>();

	public TryCatchEntry addException(Label start, Label end, Label handler, String type) {
		var item = new TryCatchEntry(Objects.requireNonNull(start, "start"), Objects.requireNonNull(end, "end"), Objects.requireNonNull(handler, "handler"), type);
		if (start.equals(end)) {
			LavaCompiler.debugLogger().debug("无意义的异常处理器: "+item);
		} else {
			trys.add(item);
		}
		return item;
	}

	@Override
	public void visitExceptions() {
		super.visitExceptions();
		for (TryCatchEntry entry : trys) {
			visitException(entry.start,entry.end,entry.handler,entry.type);
		}
		trys.clear();
	}
	//endregion
	//region line & locals
	@Nullable public LineNumberTable lines;
	@NotNull public LineNumberTable lines() {
		if (lines == null) lines = new LineNumberTable();
		return lines;
	}

	private LocalVariableTable locals;
	public void addLocal(LocalVariableTable.Item v) {
		if (locals == null) return;
		locals.list.add(v);
	}

	@Override
	public void visitAttributes() {
		super.visitAttributes();

		if (locals != null && !locals.writeIgnore()) {
			var rawVar = locals.list;
			var lvtGeneric = new LocalVariableTable("LocalVariableTypeTable");
			var genericVar = lvtGeneric.list;
			for (int i = 0; i < rawVar.size();) {
				var item = rawVar.get(i);
				if (item.slot < 0) {
					rawVar.remove(i);
					continue;
				}

				if (item.type.genericType() != IType.STANDARD_TYPE) {
					genericVar.add(item);
				}

				i++;
			}

			if (!locals.writeIgnore()) visitAttribute(locals);
			if (!lvtGeneric.writeIgnore()) visitAttribute(lvtGeneric);
		}
		if (lines != null && ctx.compiler.hasFeature(Compiler.EMIT_LINE_NUMBERS)) {
			if (bci() == lines.lastBci()) lines.list.pop();
			if (!lines.writeIgnore()) visitAttribute(lines);
		}
	}
	//endregion

	// TODO use #isContinuousControlFlow()
	@Deprecated
	public boolean isJumpingTo(Label point) {return !segments.isEmpty() && segments.get(segments.size()-1) instanceof JumpTo js && js.target == point;}

	// 不被Segment引用的Label
	private final HashSet<Label> unrefLabels = new HashSet<>(Hasher.identity());

	/**
	 * Creates a label intended for external references such as attributes (exception handlers,
	 * variable tables). These labels are not duplicated during {@link Segment#move} operations
	 * and can only be relocated once.
	 * @see #writeTo(MethodWriter)
	 */
	public Label createExternalLabel() {
		Label label = label();
		unrefLabels.add(label);
		return label;
	}

	/**
	 * Updates the end boundary of a variable's scope to ensure proper lifetime tracking
	 * in the presence of control flow changes. This maintains accurate local variable
	 * table entries even when variables have complex scoping patterns.
	 */
	public void updateVariableScope(LocalVariableTable.Item v) {
		if (!labels.contains(v.end)) {
			v.end = new Label();
			labels.add(v.end);
			unrefLabels.add(v.end);
		} else {
			v.end.clear();
		}
		label(v.end);
	}

	// 这个pos!=0其实是筛掉了lambda
	public void load(Variable v) {
		if (v.slot >= 0 && v.pos != 0) varLoad(v.type.rawType(), v.slot);
		else addSegment(new LazyLoadStore(v, false));
	}
	public void store(Variable v) {
		if (v.slot >= 0 && v.pos != 0) varStore(v.type.rawType(), v.slot);
		else addSegment(new LazyLoadStore(v, true));
	}
	public void iinc(Variable v, int delta) {
		if (v.slot >= 0 && v.pos != 0) iinc(v.slot, delta);
		else addSegment(new LazyIINC(v, delta));
	}

	@Override
	public void jump(byte code, Label target) { assertTrait(code, TRAIT_JUMP); addSegment(new OptimizedJumpTo(code, target)); }

	// 必须，否则第一个segment的标签都是rawLabel
	@Override
	protected boolean skipFirstSegmentLabels() {return false;}

	public int nextSegmentId() {return segments.isEmpty() ? 1 : segments.size() - (segments.get(segments.size()-1).length() == 0 ? 1 : 0);}
	public void replaceSegment(int id, Segment segment) {
		var prev = segments.set(id, segment);

		for (Label label : labels) {
			if (label.getBlock() == id && label.getOffset() != 0) {
				if (label.getOffset() == prev.length()) {
					label.__move(1);
				} else {
					throw new IllegalStateException("无法移动位于"+prev+"中间的标签"+label);
				}
			}
			if (label.isRaw()) throw new AssertionError("无法移动位置未知的标签"+label);
		}
	}
	public int replaceSegment(int id, MethodWriter mw) {
		// only for debug purpose
		var prev = segments.get(id);

		boolean firstIsEmpty;
		if (mw.bw.readableBytes() > 8) {
			segments.set(id, new StaticSegment(mw.bw.slice(8, mw.bw.readableBytes()-8)));
			firstIsEmpty = false;
		} else {
			firstIsEmpty = true;
		}

		List<Segment> toInsert = mw.segments;
		int initialSize = toInsert.size();
		if (!toInsert.isEmpty()) {
			toInsert.remove(0);

			while (toInsert.size() > 0 && toInsert.get(toInsert.size()-1).length() == 0) {
				Segment removed = toInsert.remove(toInsert.size() - 1);
				if (removed == GLOBAL_INIT_PLACEHOLDER) throw new AssertionError("你又要改代码了喵 (remove GlobalInitPlaceholder)");
			}

			/*int blockMoved = segments.size();
			for (int i = 0; i < toInsert.size(); i++) {
				toInsert.set(i, toInsert.get(i).move(this, blockMoved, false));
			}*/

			if (firstIsEmpty) segments.set(id, toInsert.remove(0));

			segments.addAll(id+1, toInsert);

			for (Label label : labels) {
				if (label.getBlock() == id && label.getOffset() != 0) throw new IllegalStateException("无法移动位于"+prev+"中间的标签"+label);
				if (label.getBlock() > id) label.__move(toInsert.size());
				if (label.isRaw()) throw new AssertionError("无法移动位置未知的标签"+label);
			}
		}

		for (Label label : mw.labels) {
			if (label.getBlock() >= initialSize) {
				LavaCompiler.debugLogger().debug("toInsert位置调试 "+label);
				label.__move(initialSize - label.getBlock());
			}
			label.__move(id);
		}
		labels.addAll(mw.labels);
		mw.labels.clear();
		return toInsert.size();
	}

	/**
	 * @see roj.compiler.ast.FlowHook#finallyExecute(MethodWriter, Label, Consumer) 用途
	 */
	public Segment getSegment(int i) {return segments.get(i);}

	private static final Segment GLOBAL_INIT_PLACEHOLDER = StaticSegment.emptyPlaceholder();
	public void insertGlobalInit(CompileContext ctx) {
		addSegment(GLOBAL_INIT_PLACEHOLDER);
		ctx.globalInitInsertTo = nextSegmentId();
		addSegment(StaticSegment.EMPTY);
	}

	/**
	 * @see OptimizedJumpTo#write(CodeWriter, int) 用途
	 */
	List<Segment> getSegments() {return segments;}

	/**
	 * 只能对封闭代码块序列化，这一步（明显的）会要求所有外部引用都已指定，否则直接抛异常 ({@link Label#isValid()})
	 * @see CodeWriter#insertBefore(DynByteBuf) 主要用途
	 */
	public DynByteBuf serialize() {
		var b = bw;
		int pos = b.wIndex();
		finish();
		b.wIndex(pos);
		return b;
	}

	public void writeTo(MethodWriter cw) {
		if (bw.wIndex() > 8) cw.codeOb.put(bw, 8, bw.wIndex()-8);

		if (!segments.isEmpty() && cw.segments.isEmpty()) cw._addFirst();
		List<Segment> tarSeg = cw.segments;
		int offset = tarSeg.size()-1;

		if (!segments.isEmpty()) {
			for (int i = 1; i < segments.size(); i++) {
				Segment seg = segments.get(i);

				Segment move = seg.move(cw, offset, true);
				tarSeg.add(move);
				cw._addOffset(move.length());
			}

			var sb = (StaticSegment) tarSeg.get(tarSeg.size() - 1);
			if (sb.isReadonly()) {
				if (sb.length() == 0) tarSeg.remove(tarSeg.size()-1);
				tarSeg.add(StaticSegment.emptyWritable());
			}
			cw.codeOb = ((StaticSegment) tarSeg.get(tarSeg.size() - 1)).getData();
		}

		cw.labels.addAll(unrefLabels);
		cw.unrefLabels.addAll(unrefLabels);
		for (Label label : unrefLabels) {
			label.__move(offset);
		}
		labels.clear();
		unrefLabels.clear();
	}
}