package roj.asm;

import roj.asm.cst.ConstantPool;
import roj.asm.tree.MethodNode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.util.ByteList;
import roj.util.DirectByteList;
import roj.util.DynByteBuf;

import java.util.Map;

/**
 * @author Roj234
 * @version 1.0
 * @since 2020/10/28 23:14
 */
public final class AsmShared {
	private static final ThreadLocal<AsmShared> BUFFERS = ThreadLocal.withInitial(AsmShared::new);

	public static AsmShared local() {
		return BUFFERS.get();
	}
	public static ByteList getBuf() {
		return BUFFERS.get().current();
	}

	private final CodeWriter cw = new CodeWriter();
	public CodeWriter cw() {
		return cw;
	}
	private final Map<InsnNode, Label> cwl = new MyHashMap<>();
	public Map<InsnNode, Label> _Map(int len) {
		cwl.clear();
		return cwl;
	}

	private final IntMap<InsnNode> pcm = new IntMap<>();
	public IntMap<InsnNode> _IntMap(MethodNode node) {
		pcm.clear();
		return pcm;
	}

	private final ConstantPool pool = new ConstantPool();
	public ConstantPool constPool() {
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

	ByteList[] buffers = new ByteList[LEVEL_MAX];
	int level;

	static final int LEVEL_MAX = 6;

	private AsmShared() {
		for (int i = 0; i < LEVEL_MAX; i++) {
			buffers[i] = new ByteList();
		}
	}

	ByteList current() {
		if (level == LEVEL_MAX) return new ByteList(4096);
		ByteList bl = buffers[level];
		bl.ensureCapacity(4096);
		bl.clear();
		return bl;
	}

	public boolean setLevel(boolean add) {
		if (add) {
			if (level >= LEVEL_MAX) return false;
			level++;
		} else {
			if (level == 0) return false;
			level--;
		}
		return true;
	}
}
