package roj.compiler.asm;

import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.LineNumberTable;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.visitor.*;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.compiler.asm.node.LazyIINC;
import roj.compiler.asm.node.LazyLoadStore;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static roj.asm.Opcodes.*;

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

	private final SimpleList<Label> jumpNo = new SimpleList<>();
	private final MyBitSet cond = new MyBitSet();
	private boolean canuse;
	public int beginJumpOn(boolean ifNe, Label label) {
		int size = jumpNo.size();
		jumpNo.add(label);
		cond.set(size, ifNe);
		canuse = true;
		return size;
	}

	public void endJumpOn(int size) {
		if (jumpNo.size() > size) {
			assert jumpNo.size() == size+1;

			Label target = jumpNo.pop();
			jump(cond.remove(jumpNo.size()) ? IFNE : IFEQ, target);
			canuse = true;
		}
	}

	public void skipJumpOn(int size) {
		if (jumpNo.size() > size) {
			assert jumpNo.size() == size+1;

			jumpNo.pop();
			cond.remove(jumpNo.size());
			canuse = true;
		}
	}

	public boolean jumpOn(int code) {
		if (!canuse) return false;
		canuse = false;

		Label target = jumpNo.remove(jumpNo.size()-1);
		boolean invert = !cond.remove(jumpNo.size());
		if (invert) {
			assert code >= IFEQ && code <= IF_acmpne;
			code = IFEQ + ((code-IFEQ) ^ 1); // 草，Opcode的排序还真的很有讲究
		}

		jump((byte) code, target);
		return true;
	}

	public int nextSegmentId() {return segments.size()-1;}
	public void replaceSegment(int id, Segment segment) {segments.set(id, segment);}

	public MethodWriter fork() {return new MethodWriter(owner, mn);}

	public Label lineMarker() {
		LineMarker marker = new LineMarker();
		super.label(marker);
		labels.add(marker);
		return marker;
	}

	@Override
	public void label(Label x) {
		// 预优化无用的跳转
		if (!segments.isEmpty() && segments.get(segments.size()-1).length() == 0 && segments.get(segments.size()-2) instanceof JumpSegment jump && jump.target == x) {
			segments.remove(segments.size()-2);
		}
		super.label(x);
	}

	public DynByteBuf writeTo() {
		MethodWriter fork = fork();
		writeTo(fork);
		fork.visitExceptions();

		DynByteBuf b = fork.bw;
		b.remove(0, 8);
		b.wIndex(b.wIndex()-2);
		return b;
	}

	public void insertBefore(DynByteBuf buf) {
		int offset = getTmpLenOffset();
		bw.preInsert(offset, buf.readableBytes());
		int pos = bw.wIndex();
		bw.wIndex(offset);
		bw.put(buf);
		bw.wIndex(pos);
	}

	public void writeTo(MethodWriter cw) {
		int off;

		if (bw.wIndex() > 8) {
			cw.codeOb.put(bw, 8, bw.wIndex()-8);
			off = bw.wIndex()-8;
		} else {
			off = 0;
		}

		for (Iterator<Label> itr = labels.iterator(); itr.hasNext(); ) {
			Label label = itr.next();
			if (label instanceof LineMarker m) {
				m.move(cw.segments.size(), off);
				cw.labels.add(m);
				itr.remove();
			}
		}

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