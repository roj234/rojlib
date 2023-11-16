package roj.asm;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstTop;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.collect.IntMap;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DirectByteList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2020/10/28 23:14
 */
public final class AsmShared {
	private static final ThreadLocal<AsmShared> BUFFERS = ThreadLocal.withInitial(AsmShared::new);

	public static AsmShared local() { return BUFFERS.get(); }
	public static ByteList getBuf() { return BUFFERS.get().current(); }

	private final CodeWriter cw = new CodeWriter();
	public CodeWriter cw() { return cw; }

	private IntMap<Label> pcm = new IntMap<>();
	public IntMap<Label> getBciMap() {
		pcm.clear();
		return pcm;
	}

	private byte[] xInsn_sharedSegmentData = new byte[256];
	private int xInsn_sharedSegmentUsed = 0;
	public byte[] getArray(int length) {
		int avail = xInsn_sharedSegmentData.length - xInsn_sharedSegmentUsed;
		if (avail < length) {
			if (length > 127) return new byte[length];

			xInsn_sharedSegmentData = ArrayCache.getByteArray(256,false);
			xInsn_sharedSegmentUsed = 0;
		}
		return xInsn_sharedSegmentData;
	}
	public int getOffset(byte[] array, int length) {
		if (array != xInsn_sharedSegmentData) return 0;

		int x = xInsn_sharedSegmentUsed;
		xInsn_sharedSegmentUsed = x+length;
		return x;
	}

	public boolean xInsn_isReading;
	public final int[] xInsn_sharedRefPos = new int[512];
	public final Object[] xInsn_sharedRefVal = new Object[512];
	public final Object[] xInsn_sharedSegments = new Object[256];
	public int[] getIntArray_(int len) {
		if (xInsn_sharedRefPos.length < len) return ArrayCache.getIntArray(len,0);
		return xInsn_sharedRefPos;
	}

	public final CstTop fp = new CstTop();
	private ConstantPool pool;
	public ConstantPool constPool() {
		if (pool == null) pool = new ConstantPool();
		pool.setAddListener(null);
		pool.clear();
		return pool;
	}

	private final ByteList.Slice wrapB = new ByteList.Slice();
	private DirectByteList.Slice wrapD;
	public DynByteBuf copy(DynByteBuf src) {
		if (src.hasArray()) return wrapB.copy(src);
		if (src.isDirect()) {
			if (wrapD == null) wrapD = new DirectByteList.Slice();
			return wrapD.copy(src);
		}
		throw new IllegalStateException("Not standard DynByteBuf: " + src.getClass().getName());
	}

	private final ByteList rootBuffer = new ByteList(4096);
	private int level;

	ByteList current() {
		if (level == 0) {
			rootBuffer.clear();
			return rootBuffer;
		}

		// uses ArrayCache now!
		ByteList b = new ByteList();
		b.ensureCapacity(4096);
		return b;
	}

	public void setLevel(boolean add) {
		if (add) level++;
		else level--;
	}
}
