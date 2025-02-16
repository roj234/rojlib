package roj.asm.util;

import roj.asm.Opcodes;
import roj.asm.insn.InsnList;
import roj.asm.insn.InsnNode;
import roj.collect.Int2IntMap;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public class InsnMatcher {
	Int2IntMap varIdReplace = new Int2IntMap();
	protected boolean failed;

	public void reset() {
		varIdReplace.clear();
		failed = false;
	}

	public boolean isFailed() {
		return failed;
	}

	public boolean checkId(int frm, int to) {
		if (!varIdReplace.containsKey(frm)) {
			varIdReplace.putInt(frm, to);
			return true;
		}
		if (varIdReplace.getOrDefaultInt(frm, -1) != to) {
			failed = true;
			return false;
		}
		return true;
	}

	public boolean isNodeSimilar(InsnNode a, InsnNode b) { return a.isSimilarTo(b, this); }

	@SuppressWarnings("fallthrough")
	public void mapVarId(InsnList from) {
		Int2IntMap map = varIdReplace;
		for (InsnNode node : from) {
			switch (Opcodes.category(node.opcode())) {
				default: if (node.opcode() != IINC) break;
				case CATE_LOAD_STORE_LEN:
					node.setId(map.getOrDefaultInt(node.id(), node.id()));
				break;
				case CATE_LOAD_STORE:
					int id = node.getVarId();
					int code = node.opcode()&0xFF;
					if (code >= ILOAD && code <= ALOAD_3) {
						if (code >= ILOAD_0) code = (byte) (((code - ILOAD_0) / 4) + ILOAD);
					} else {
						if (code >= ISTORE_0) code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
					}
					if (map.containsKey(id)) {
						var replaceList = new InsnList();
						replaceList.vars((byte) code, map.get(id));
						node.replace(replaceList, false);
					}
					break;
			}
		}
	}
}