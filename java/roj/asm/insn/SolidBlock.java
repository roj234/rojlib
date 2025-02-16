package roj.asm.insn;

import roj.asm.AsmShared;
import roj.asm.Opcodes;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/11/17 0017 12:53
 */
public final class SolidBlock extends CodeBlock {
	public static final SolidBlock EMPTY = new SolidBlock(ArrayCache.BYTES);

	private Object array;
	private short off;
	private char length;

	SolidBlock() { array = new ByteList(); }
	public SolidBlock(DynByteBuf data) { array = data; }
	public SolidBlock(byte... data) { array = data; length = (char) data.length; }
	public static SolidBlock emptyWritable() {return new SolidBlock();}

	@Override protected boolean put(CodeWriter to, int segmentId) {
		if (array instanceof DynByteBuf b) to.bw.put(b);
		else to.bw.put((byte[]) array,off,length);
		return false;
	}
	@Override public int length() { return array instanceof DynByteBuf ? ((DynByteBuf)array).readableBytes() : length; }

	boolean isReadonly() { return array.getClass() == byte[].class; }
	@Override public DynByteBuf getData() { return array.getClass() == byte[].class ? DynByteBuf.wrap((byte[]) array, off, length) : (DynByteBuf) array; }
	@Override
	SolidBlock setData(DynByteBuf b) {
		SolidBlock that = this;

		AsmShared local = AsmShared.local();

		that.length = (char) b.readableBytes();
		byte[] arr = local.getArray(that.length);
		that.array = arr;
		int off = local.getOffset(arr, that.length);
		that.off = (short) off;

		b.readFully(b.rIndex, arr, off, that.length);
		return that;
	}

	@Override
	public boolean isTerminate() {
		if (length() == 0) return false;
		DynByteBuf buf = getData();
		return isTerminate(buf, buf.rIndex, buf.wIndex());
	}
	static boolean isTerminate(DynByteBuf buf, int start, int end) {
		int op = buf.get(end-1);
		if ((op < Opcodes.IRETURN || op > Opcodes.RETURN) && op != Opcodes.ATHROW) return false;

		// 常量池ID可能正好落在这个范围中, 所以再做一次终止检查
		do {
			op = buf.get(start);
			start += 0xF&(InsnNode.OPLENGTH[op&0xFF]>>>4);
		} while (start != end);

		return (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) || op == Opcodes.ATHROW;
	}

	@Override public CodeBlock move(AbstractCodeWriter to, int blockMoved, boolean clone) {
		if (!clone) return this;
		var b = emptyWritable();
		if (isReadonly()) {
			b.array = array;
			b.off = off;
			b.length = length;
		} else {
			b.setData(getData());
		}
		return b;
	}

	@Override public String toString() {return "Code("+length()+", "+getData().hex()+")"; }
}