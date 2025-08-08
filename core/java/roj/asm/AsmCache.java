package roj.asm;

import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstTop;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.Label;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.collect.IntMap;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2020/10/28 23:14
 */
public final class AsmCache {
	private static final ThreadLocal<AsmCache> BUFFERS = ThreadLocal.withInitial(AsmCache::new);

	public static AsmCache getInstance() { return BUFFERS.get(); }
	public static void clear() { BUFFERS.remove(); }

	private ByteList buf;
	public static ByteList buf() {
		AsmCache inst = BUFFERS.get();
		var buf = inst.buf;
		if (buf == null) buf = new ByteList(4096);
		else buf.clear();
		inst.buf = null;
		return buf;
	}
	public static void buf(ByteList x) {
		var inst = BUFFERS.get();
		inst.buf = x;
	}

	public static byte[] toByteArray(ClassDefinition c) {
		var buf = buf();
		byte[] array = c.toByteArray(buf).toByteArray();
		buf(buf);
		return array;
	}

	public static ByteList toByteArrayShared(ClassDefinition c) {
		ByteList buf = buf();
		c.toByteArray(buf);
		buf(buf);
		return buf;
	}

	private final ArrayList<Type> mp = new ArrayList<>();
	public <T> ArrayList<T> methodTypeTmp() {mp.clear(); return Helpers.cast(mp);}

	private final Object[][] cpArr = new Object[10][];
	private int cpCount;

	public void getCpWriter(ArrayList<Constant> constants) {
		if (cpCount == 10) return;
		int i = cpCount++;
		Object[] objects = cpArr[i];
		if (objects == null) objects = new Object[1024];

		if (constants.isEmpty()) {
			constants._setArray(objects);
		}
	}

	public void freeCpWriter(ArrayList<Constant> constants, boolean discard) {
		if (cpCount == 0) return;

		Object[] cpArray = constants.getInternalArray();

		if (discard) constants._setArray(null);
		else {
			if (cpArray.length == constants.size()) return;
			constants.trimToSize();
		}

		int len = constants.size();
		for (int i = len-1; i >= 0; i--) cpArray[i] = null;

		cpArr[--cpCount] = cpArray;
	}

	private CodeWriter cw = new CodeWriter();
	public CodeWriter cw() { var tmp = cw; cw = null; return tmp == null ? new CodeWriter() : tmp; }
	public void cw(CodeWriter cw) { this.cw = cw; }

	private final IntMap<Label> pcm = new IntMap<>();
	public IntMap<Label> getBciMap() {pcm.clear(); return pcm;}

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

	public final CstTop constantMatcher = new CstTop();
	private ConstantPool pool;
	public ConstantPool constPool() {
		if (pool == null) return new ConstantPool();
		var t = pool;
		pool = null;
		return t;
	}
	public void constPool(ConstantPool cp) {
		pool = cp;
		pool.setAddListener(null);
		pool.clear();
	}
}