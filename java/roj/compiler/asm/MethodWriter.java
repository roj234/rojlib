package roj.compiler.asm;

import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.LineNumberTable;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.visitor.*;
import roj.collect.SimpleList;
import roj.compiler.asm.node.LazyIINC;
import roj.compiler.asm.node.LazyLoadStore;
import roj.reflect.ReflectionUtils;
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
	private final ConstantData owner;

	private final SimpleList<TryCatchEntry> entryList = new SimpleList<>();

	public LineNumberTable lines;
	public boolean debugLines;
	public boolean noverify;

	public MethodWriter(ConstantData unit, MethodNode mn) {
		this.owner = unit;
		this.init(new ByteList(),unit.cp,mn,(byte)0);
	}

	public TryCatchEntry addException(Label str, Label end, Label proc, String s) {
		TryCatchEntry entry = new TryCatchEntry(Objects.requireNonNull(str, "start"), Objects.requireNonNull(end, "end"), Objects.requireNonNull(proc, "handler"), s);
		entryList.add(entry);
		return entry;
	}

	@Override
	public void visitExceptions() {
		super.visitExceptions();
		for (TryCatchEntry entry : entryList) {
			visitException(entry.start,entry.end,entry.handler,entry.type);
		}
		entryList.clear();
	}

	@Override
	public void visitAttributes() {
		super.visitAttributes();

		if (debugLines) lineNumberDebug();
		else if (lines != null && !lines.isEmpty()) visitAttribute(lines);
	}

	public void load(Variable v) { addSegment(new LazyLoadStore(v, false)); }
	public void store(Variable v) { v.hasValue = true; addSegment(new LazyLoadStore(v, true)); }
	public void iinc(Variable v, int delta) { addSegment(new LazyIINC(v, delta)); }

	public void jump(byte code, Label target) { assertTrait(code, TRAIT_JUMP); addSegment(new JumpSegmentAO(code, target)); }

	public int nextSegmentId() {return segments.size()-1;}
	public void replaceSegment(int id, Segment segment) {segments.set(id, segment);}
	public boolean isJumpingTo(Label point) {return !segments.isEmpty() && segments.get(segments.size()-1) instanceof JumpSegment js && js.target == point;}

	public MethodWriter fork() {return new MethodWriter(owner, mn);}

	private final List<Label> zeroLabels = new SimpleList<>();

	@Override
	public void label(Label x) {
		// 预优化无用的跳转
		if (segments.isEmpty()) {
			zeroLabels.add(x);
		} else if (segments.get(segments.size()-1).length() == 0 && segments.get(segments.size()-2) instanceof JumpSegment jump && jump.target == x) {
			segments.remove(segments.size()-2);
		}

		super.label(x);
	}

	public DynByteBuf writeTo() {
		if (getState() < 2) visitExceptions();
		var b = bw;
		b.remove(0, 8);
		b.wIndex(b.wIndex()-2);
		return b;
	}

	private static final long BLOCK_INDEX = ReflectionUtils.fieldOffset(Label.class, "block");
	private static final long OFFSET_INDEX = ReflectionUtils.fieldOffset(Label.class, "offset");
	public void insertBefore(DynByteBuf buf) {
		super.insertBefore(buf);
		int delta = buf.readableBytes();
		for (Label label : zeroLabels) ReflectionUtils.u.getAndAddInt(label, OFFSET_INDEX, delta);
	}

	private boolean disposed;
	public void writeTo(MethodWriter cw) {
		if (disposed) throw new AssertionError();
		disposed = true;

		if (bw.wIndex() > 8) cw.codeOb.put(bw, 8, bw.wIndex()-8);

		int mb = cw.segments.size();
		if (mb == 0) mb = 1;
		for (Label label : labels) {
			ReflectionUtils.u.getAndAddInt(label, BLOCK_INDEX, mb);
			cw.labels.add(label);
		}
		labels.clear();
		for (Label label : zeroLabels) {
			ReflectionUtils.u.getAndAddInt(label, BLOCK_INDEX, mb-1);
			cw.labels.add(label);
		}
		zeroLabels.clear();

		if (segments.isEmpty()) return;
		if (cw.segments.isEmpty()) cw._addFirst();

		List<Segment> tarSeg = cw.segments;
		int offset = tarSeg.size();

		for (int i = 1; i < segments.size(); i++) {
			Segment seg = segments.get(i);
			if (seg.length() == 0 && i != segments.size()-1) continue;

			Segment move = seg.move(this, cw, offset, XInsnList.REP_CLONE);
			tarSeg.add(move);
			cw._addOffset(move.length());
		}

		cw.codeOb = ((StaticSegment) tarSeg.get(tarSeg.size()-1)).getData();
	}
}