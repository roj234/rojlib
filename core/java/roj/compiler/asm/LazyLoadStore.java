package roj.compiler.asm;

import roj.asm.insn.CodeWriter;
import roj.asm.insn.Segment;
import roj.asm.insn.StaticSegment;
import roj.compiler.LavaCompiler;
import roj.compiler.resolve.TypeCast;
import roj.util.DynByteBuf;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2022/2/5 12:22
 */
final class LazyLoadStore extends Segment {
	private final Variable v;
	private final boolean store;

	public LazyLoadStore(Variable v, boolean store) {
		this.v = v;
		this.store = store;
	}

	@Override
	public boolean write(CodeWriter to, int segmentId) {
		DynByteBuf ob = to.bw;
		int begin = ob.wIndex();

		if (v.slot < 0) {
			if (store) {
				// POP | POP2, this variable not used
				ob.put(0x56 + v.type.rawType().length());
			} else {
				// Store n, Load n 消除
				List<Segment> segments = ((MethodWriter) to).getSegments();
				if (segments.get(segmentId-1) instanceof LazyLoadStore prev && prev.store && prev.v == v) {
					segments.set(segmentId-1, StaticSegment.EMPTY);
					segments.set(segmentId, StaticSegment.EMPTY);
					return true;
				}

				ob.put(NOP);
				LavaCompiler.debugLogger().error("标记为未使用的变量进行了Load操作，内部错误: {}", v);
			}
		} else {
			// Note: 如果以后StreamChain要改的话，这里要和Type.DirtyHacker一起改
			byte code = switch (TypeCast.getDataCap(v.type.getActualType())) {
				default -> ILOAD;
				case 5 -> LLOAD;
				case 6 -> FLOAD;
				case 7 -> DLOAD;
				case 8 -> ALOAD;
			};

			if (store) {
				// Load n, Store n 消除
				List<Segment> segments = ((MethodWriter) to).getSegments();
				if (segments.get(segmentId-1) instanceof LazyLoadStore prev && !prev.store && prev.v.slot == v.slot) {
					segments.set(segmentId-1, StaticSegment.EMPTY);
					segments.set(segmentId, StaticSegment.EMPTY);
					return true;
				}

				code += 33;
			}

			to.vars(code, v.slot);
		}

		begin = ob.wIndex() - begin;
		assert length() == begin;
		return false;
	}

	@Override
	public int length() {
		int slot = v.slot;
		if (slot <= 3) return 1; // CODE
		else if (slot < 255) return 2; // CODE V1
		else return 4; // WIDE CODE V1 V2
	}

	@Override
	public String toString() { return (store?"Put ":"Get ")+v.name+ "("+v.slot+")"; }
}