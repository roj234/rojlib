package roj.compiler.asm;

import roj.asm.tree.MethodNode;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.visitor.*;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.compiler.asm.node.LazyIINC;
import roj.compiler.asm.node.LazyLoadStore;
import roj.compiler.context.ClassContext;
import roj.compiler.context.CompileContext;
import roj.compiler.context.CompileUnit;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2022/2/24 19:19
 */
public class MethodWriter extends CodeWriter {
	public CompileUnit owner;
	public ClassContext ctx;
	public CompileContext ctx1;
	public Variable current_variable;

	private SimpleList<TryCatchEntry> entryList = new SimpleList<>();

	public MethodWriter(CompileUnit unit, MethodNode mn) {
		this.owner = unit;
		this.ctx = unit.ctx();
		this.ctx1 = CompileContext.get();
		this.init(new ByteList(),unit.cp,mn,(byte)0);
	}

	public MethodWriter fork() {
		return new MethodWriter(owner, mn);
	}

	public TryCatchEntry addException(Label str, Label end, Label proc, String s) {
		TryCatchEntry entry = new TryCatchEntry(str, end, proc, s);
		entryList.add(entry);
		return entry;
	}

	@Override
	public void visitExceptions() {
		super.visitExceptions();
		for (TryCatchEntry entry : entryList) {
			visitException(entry.start,entry.end,entry.handler,entry.type);
		}
	}

	public void load(Variable v) { addSegment(new LazyLoadStore(v, false)); }
	public void store(Variable v) { addSegment(new LazyLoadStore(v, true)); }
	public void iinc(Variable v, int delta) { addSegment(new LazyIINC(v, delta)); }

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

	public int nextSegmentId() {
		return segments.size()-1;
	}

	public void replaceSegment(int id, Segment segment) {
		segments.set(id, segment);
	}

	public StaticSegment writeTo() {
		MethodWriter fork = fork();
		writeTo(fork);
		fork.visitExceptions();

		DynByteBuf b = fork.bw;
		b.remove(0, 8);
		b.wIndex(b.wIndex()-2);
		return new StaticSegment(b);
	}

	public void writeTo(MethodWriter cw) {
		cw.codeOb.put(bw, 8, bw.wIndex()-8);
		if (segments.isEmpty()) return;

		List<Segment> tarSeg = cw.segments;
		int offset = tarSeg.size();

		for (int i = 1; i < segments.size(); i++) {
			Segment seg = segments.get(i);
			if (seg.length() == 0 && i != segments.size()-1) continue;
			tarSeg.add(seg.move(this, cw, offset, XInsnList.REP_CLONE));
		}

		cw.codeOb = ((StaticSegment) tarSeg.get(tarSeg.size()-1)).getData();
	}
}