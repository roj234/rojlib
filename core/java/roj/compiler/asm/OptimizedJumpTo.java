package roj.compiler.asm;

import roj.asm.insn.*;
import roj.compiler.LavaCompiler;
import roj.util.DynByteBuf;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/2/25 0:17
 */
final class OptimizedJumpTo extends JumpTo {
	private Segment writeReplace;

	public OptimizedJumpTo(byte code, Label target) { super(code, target); }

	public boolean isAlive() {return writeReplace == null;}

	@Override
	@SuppressWarnings("fallthrough")
	public boolean write(CodeWriter x, int segmentId) {
		var to = (MethodWriter) x;
		int bci = to.bci();
		List<Segment> segments = to.getSegments();
		if (!target.isValid()) throw new IllegalStateException("target is not valid: "+target);

		while (target.getOffset() == 0 && target.getBlock() > 0) {
			int i = target.getBlock();
			for (; i < segments.size()-1; i++) {
				if (segments.get(i).length() != 0) break;
			}

			Segment segment = segments.get(i);
			if (!(segment instanceof OptimizedJumpTo j) || !j.isTerminate() || target == j.target) break;
			target = j.target;
		}

		if (writeReplace != null) return writeReplace.write(to, segmentId);

		if (!isTerminate() && target.getOffset() == 0) {
			int nonEmptySegments = 0;
			int i = segmentId+1;
			for (; i < target.getBlock(); i++) {
				if (segments.get(i).length() != 0) {
					nonEmptySegments++;
				}
			}

			// 匹配并优化下列模式
			// IfXX => A
			// Goto => B
			// A: ...
			if (nonEmptySegments == 1 && segments.get(i - 1) instanceof OptimizedJumpTo t && t.isTerminate()) {
				target = t.target;
				code = (byte) (IFEQ + ((code - IFEQ) ^ 1));
				t.writeReplace = StaticSegment.EMPTY;
				//segments.set(i - 1, StaticSegment.EMPTY);
				return true;
			}

			// 匹配并优化下列模式
			// IfXX => A
			// Goto => A
			if (segments.get(segmentId+1) instanceof JumpTo t) {
				if (t.target.equals(target)) {
					LavaCompiler.debugLogger().warn("无意义的比较: (from b"+segmentId+", bci="+bci+") => "+this);
					removeSelf();
					segments.set(segmentId, writeReplace);
					return true;
				}
			}
		}

		DynByteBuf o = to.bw;
		int off = target.getValue() - bci;

		fv_bci = bci;

		int len = 3;
		int newLen = length();

		if (target.getBlock() == segmentId+1 && target.getOffset() == 0) {
			LavaCompiler.debugLogger().warn("无意义的跳转: (from b"+segmentId+", bci="+bci+") => "+this);
			if (code >= GOTO) {
				removeSelf();
				segments.set(segmentId, writeReplace);
				return true;
			} else {
				//code = GOTO;
				//segments.add(segmentId, new JumpTo(code, target));
			}
		}

		// 无法访问的代码
		if (!to.isContinuousControlFlow(segmentId-1)) {
			LavaCompiler.debugLogger().warn("无法访问的代码: (from b"+segmentId+", bci="+bci+") => "+this);
			removeSelf();
			segments.set(segmentId, writeReplace);
			return true;
		}

		switch (code) {
			case JSR: case JSR_W:
				if (((short) off) != off) {
					o.put(code = JSR_W).putInt(off);
					len = 5;
				} else {
					o.put(code = JSR).putShort(off);
				}
			break;
			case GOTO: case GOTO_W:
				if (((short) off) != off) {
					o.put(code = GOTO_W).putInt(off);
					len = 5;
				} else {
					o.put(code = GOTO).putShort(off);
				}
			break;
			default:
				if (((short) off) != off) {
					o.put(IFEQ + ((code-IFEQ) ^ 1)).putShort(8).put(GOTO_W).putInt(off);
					len = 8;
				} else {
					o.put(code).putShort(off);
				}
			break;
		}

		return len != newLen;
	}

	private void removeSelf() {writeReplace = code < GOTO ? new StaticSegment(code >= IF_icmpeq ? POP2 : POP) : StaticSegment.EMPTY;}

	@Override public final int length() { return writeReplace != null ? writeReplace.length() : super.length(); }

	@Override
	public Segment move(AbstractCodeWriter to, int blockMoved, boolean clone) {
		Label rx = copyLabel(target, to, blockMoved, clone);
		return clone?new OptimizedJumpTo(code,rx):this;
	}
}