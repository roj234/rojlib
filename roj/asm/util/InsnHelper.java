package roj.asm.util;

import roj.asm.OpcodeUtil;
import roj.asm.type.Type;
import roj.asm.visitor.*;
import roj.collect.Int2IntMap;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;

import java.util.List;
import java.util.Map;

import static roj.asm.OpcodeUtil.*;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public class InsnHelper {
	public static byte changeCodeType(int code, Type from, Type to) {
		int flag = OpcodeUtil.flag(code);
		if ((flag&OpcodeUtil.TRAIT_ILFDA) == 0) return (byte) code;

		String name = OpcodeUtil.toString0(code);
		if ((flag&0xF) == OpcodeUtil.CATE_ARRAY_SL)
			return (byte) ((name.endsWith("Store") ? 33 : 0)+XALoad(to));

		if (from != null && name.charAt(0) != from.nativeName().charAt(0)) return (byte) code;

		CharList sb = IOUtil.getSharedCharBuf().append(name);
		sb.list[0] = to.nativeName().charAt(0);

		int v = OpcodeUtil.getByName().getOrDefault(sb, -1);
		if (v < 0) throw new IllegalStateException("找不到"+sb);
		return (byte) v;
	}

	public static int getVarId(XInsnNodeView node) {
		switch (OpcodeUtil.category(node.opcode())) {
			case OpcodeUtil.CATE_LOAD_STORE: return getVarId(node.opcode());
			case OpcodeUtil.CATE_LOAD_STORE_LEN: return node.id();
		}
		return -1;
	}

	public static int getVarId(byte code) {
		if (OpcodeUtil.category(code) == OpcodeUtil.CATE_LOAD_STORE) {
			String name = OpcodeUtil.toString0(code);
			return name.charAt(name.length()-1) - '0';
		}
		return -1;
	}

	public static boolean isReturn(int code) {
		return OpcodeUtil.category(code) == OpcodeUtil.CATE_RETURN;
	}

	public static byte ToPrimitiveArrayId(int nativeType) {
		switch (nativeType) {
			case 'Z': return 4;
			case 'C': return 5;
			case 'F': return 6;
			case 'D': return 7;
			case 'B': return 8;
			case 'S': return 9;
			case 'I': return 10;
			case 'J': return 11;
			default: throw new IllegalArgumentException(String.valueOf((char)nativeType));
		}
	}

	public static byte FromPrimitiveArrayId(int id) {
		switch (id) {
			case 4: return 'Z';
			case 5: return 'C';
			case 6: return 'F';
			case 7: return 'D';
			case 8: return 'B';
			case 9: return 'S';
			case 10: return 'I';
			case 11: return 'J';
		}
		throw new IllegalStateException("Unknown PrimArrayType " + id);
	}

	public static byte XAStore(Type type) { return (byte) (XALoad(type)+33); }
	public static byte XALoad(Type type) {
		switch (type.type) {
			case 'I': return IALOAD;
			case 'J': return LALOAD;
			case 'F': return FALOAD;
			case 'D': return DALOAD;
			case 'L': return AALOAD;
			case 'Z':
			case 'B': return BALOAD;
			case 'C': return CALOAD;
			case 'S': return SALOAD;
			default: throw new IllegalArgumentException();
		}
	}

	public static void switchString222(CodeWriter c, Map<String, Label> target, Label def) {
		if (target.isEmpty()) {
			c.jump(GOTO, def);
			return;
		}

		c.one(DUP);
		c.invoke(INVOKESPECIAL, "java/lang/String", "hashCode", "()I");

		SwitchSegment sw = CodeWriter.newSwitch(LOOKUPSWITCH);
		c.addSegment(sw);
		sw.def = def;

		// check duplicate
		IntMap<List<Map.Entry<String, Label>>> tmp = new IntMap<>(target.size());
		for (Map.Entry<String, Label> entry : target.entrySet()) {
			int hash = entry.getKey().hashCode();

			List<Map.Entry<String, Label>> dup = tmp.get(hash);
			if (dup == null) tmp.putInt(hash, dup = new SimpleList<>(2));
			dup.add(entry);
		}

		for (IntMap.Entry<List<Map.Entry<String, Label>>> entry : tmp.selfEntrySet()) {
			Label pos = new Label();
			sw.branch(entry.getIntKey(), pos);
			c.label(pos);
			List<Map.Entry<String, Label>> list1 = entry.getValue();
			for (int i = 0; i < list1.size(); i++) {
				Map.Entry<String, Label> entry1 = list1.get(i);
				c.ldc(entry1.getKey());
				c.invoke(INVOKESPECIAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z");
				c.jump(IFNE, entry1.getValue());
			}
			c.jump(GOTO, def);
		}
	}

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

	public boolean isNodeSimilar(XInsnNodeView a, XInsnNodeView b) { return a.isSimilarTo(b, this); }

	public void mapVarId(XInsnList from) {
		Int2IntMap map = varIdReplace;
		for (XInsnNodeView node : from) {
			switch (OpcodeUtil.category(node.opcode())) {
				default: if (node.opcode() != IINC) break;
				case CATE_LOAD_STORE_LEN:
					node.setId(map.getOrDefaultInt(node.id(), node.id()));
				break;
				case CATE_LOAD_STORE:
					int id = getVarId(node);
					int code = node.opcode()&0xFF;
					if (code >= ILOAD && code <= ALOAD_3) {
						if (code >= ILOAD_0) code = (byte) (((code - ILOAD_0) / 4) + ILOAD);
					} else {
						if (code >= ISTORE_0) code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
					}
					if (map.containsKey(id)) {
						XInsnNodeView.InsnMod replace = node.replace();
						replace.list.vars((byte) code, map.get(id));
						replace.commit();
					}
					break;
			}
		}
	}
}
