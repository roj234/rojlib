package roj.compiler.asm;

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
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.Objects;

import static roj.asm.Opcodes.TRAIT_JUMP;
import static roj.asm.Opcodes.assertTrait;

/**
 * @author Roj233
 * @since 2022/2/24 19:19
 */
public class MethodWriter extends CodeWriter {
	public static final int GLOBAL_INIT_INSERT = 0;

	public final CompileContext ctx;

	private final ArrayList<TryCatchEntry> trys = new ArrayList<>();

	public LineNumberTable lines;
	public LineNumberTable lines() {
		if (lines == null) lines = new LineNumberTable();
		return lines;
	}

	private LocalVariableTable lvt1, lvt2;

	private final HashSet<Label> unrefLabels = new HashSet<>(Hasher.identity());

	public void addLVTEntry(LocalVariableTable.Item v) {
		if (lvt1 == null) return;
		lvt1.list.add(v);
		if (v.type.genericType() != IType.STANDARD_TYPE) {
			lvt2.list.add(v);
		}
	}

	public Label __attrLabel() {
		Label label = label();
		unrefLabels.add(label);
		return label;
	}
	public void __updateVariableEnd(Variable v) {
		if (!labels.contains(v.end)) {
			v.end = new Label();
			labels.add(v.end);
			unrefLabels.add(v.end);
		} else {
			v.end.clear();
		}
		label(v.end);
	}

	public MethodWriter(ClassNode unit, MethodNode mn, boolean generateLVT, CompileContext ctx) {
		this.ctx = ctx;
		this.init(new ByteList(),unit.cp,mn);
		if (generateLVT) {
			lvt1 = new LocalVariableTable("LocalVariableTable");
			lvt2 = new LocalVariableTable("LocalVariableTypeTable");
		}
	}
	protected MethodWriter(MethodWriter parent) {
		this.ctx = parent.ctx;
		this.init(new ByteList(),parent.cpw,parent.mn);
		lvt1 = parent.lvt1;
		lvt2 = parent.lvt2;
	}

	public MethodWriter fork() {return new MethodWriter(this);}
	protected boolean skipFirstSegmentLabels() {return false;}

	public TryCatchEntry addException(Label str, Label end, Label proc, String s) {
		TryCatchEntry entry = new TryCatchEntry(Objects.requireNonNull(str, "start"), Objects.requireNonNull(end, "end"), Objects.requireNonNull(proc, "handler"), s);
		if (!str.equals(end)) {
			trys.add(entry);
		} else {
			LavaCompiler.debugLogger().debug("无意义的异常处理器: "+entry);
		}
		return entry;
	}

	@Override
	public void visitExceptions() {
		super.visitExceptions();
		for (TryCatchEntry entry : trys) {
			visitException(entry.start,entry.end,entry.handler,entry.type);
		}
		trys.clear();
	}

	@Override
	public void visitAttributes() {
		super.visitAttributes();

		if (lvt1 != null && !lvt1.writeIgnore()) {
			visitAttribute(lvt1);
			if (!lvt2.writeIgnore()) visitAttribute(lvt2);
		}
		if (lines != null && !lines.writeIgnore()) visitAttribute(lines);
	}

	// 这个pos!=0其实是筛掉了lambda
	public void load(Variable v) {
		if (v.slot != 0 && v.pos != 0) varLoad(v.type.rawType(), v.slot);
		else addSegment(new LazyLoadStore(v, false));
	}
	public void store(Variable v) {
		if (v.slot != 0 && v.pos != 0) varStore(v.type.rawType(), v.slot);
		else addSegment(new LazyLoadStore(v, true));
	}
	public void iinc(Variable v, int delta) {
		if (v.slot != 0 && v.pos != 0) iinc(v.slot, delta);
		else addSegment(new LazyIINC(v, delta));
	}

	public void jump(byte code, Label target) { assertTrait(code, TRAIT_JUMP); addSegment(new OptimizedJumpTo(code, target)); }

	public int nextSegmentId() {return segments.isEmpty() ? 1 : segments.size() - (segments.get(segments.size()-1).length() == 0 ? 1 : 0);}
	public void replaceSegment(int id, Segment segment) {
		var prev = segments.set(id, segment);

		for (Label label : labels) {
			if (label.getBlock() == id && label.getOffset() != 0) {
				if (label.getOffset() == prev.length()) {
					label.__move(1);
				} else {
					throw new IllegalStateException("Cannot handle "+label);
				}
			}
			if (label.isRaw()) throw new IllegalStateException("raw label "+label+" is not supported");
		}
	}
	public int replaceSegment(int id, MethodWriter mw) {
		boolean firstIsEmpty;
		if (mw.bw.readableBytes() > 8) {
			segments.set(id, new StaticSegment(mw.bw.slice(8, mw.bw.readableBytes()-8)));
			firstIsEmpty = false;
		} else {
			firstIsEmpty = true;
		}

		List<Segment> toInsert = mw.segments;
		if (!toInsert.isEmpty()) {
			toInsert.remove(0);
			if (toInsert.get(toInsert.size()-1).length() == 0)
				toInsert.remove(toInsert.size()-1);

			/*int blockMoved = segments.size();
			for (int i = 0; i < toInsert.size(); i++) {
				toInsert.set(i, toInsert.get(i).move(this, blockMoved, false));
			}*/

			if (firstIsEmpty) segments.set(id, toInsert.remove(0));

			segments.addAll(id+1, toInsert);

			for (Label label : labels) {
				if (label.getBlock() == id && label.getOffset() != 0) throw new IllegalStateException("Cannot handle this!");
				if (label.getBlock() > id) label.__move(toInsert.size());
				if (label.isRaw()) throw new IllegalStateException("raw label "+label+" is not supported");
			}
		}

		for (Label label : mw.labels) label.__move(id);
		labels.addAll(mw.labels);
		mw.labels.clear();
		return toInsert.size();
	}
	public Segment getSegment(int i) {return segments.get(i);}
	public boolean isJumpingTo(Label point) {return !segments.isEmpty() && segments.get(segments.size()-1) instanceof JumpTo js && js.target == point;}

	private final int[] markerBlock = new int[1];
	public void addPlaceholder(int slot) {
		markerBlock[slot] = nextSegmentId();
		addSegment(LazyPlaceholder.PLACEHOLDER);
	}
	public int getPlaceholderId(int slot) {return markerBlock[slot];}

	// JumpSegmentAO
	List<Segment> getCodeBlocks() {return segments;}

	// for insertBefore()
	public DynByteBuf writeTo() {
		var b = bw;
		int pos = b.wIndex();
		finish();
		b.wIndex(pos);
		return b;
	}

	public void writeTo(MethodWriter cw) {
		if (bw.wIndex() > 8) cw.codeOb.put(bw, 8, bw.wIndex()-8);

		if (cw.segments.isEmpty()) cw._addFirst();
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