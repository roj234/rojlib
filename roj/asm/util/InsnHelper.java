package roj.asm.util;

import roj.asm.OpcodeUtil;
import roj.asm.tree.insn.*;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.SwitchSegment;
import roj.collect.Int2IntMap;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.*;
import static roj.asm.tree.insn.InsnNode.*;
import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public class InsnHelper {
	public static void insertDebug(InsnList list) {
		list.one(DUP);
		list.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", new Type("java/lang/PrintStream")));
		list.one(SWAP);
		list.add(new InvokeInsnNode(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;"));
		list.add(new InvokeInsnNode(INVOKEVIRTUAL, "java/lang/PrintStream", "println", "(Ljava/lang/String;)V"));
	}

	private static byte getValue(char x, byte base) {
		switch (Character.toUpperCase(x)) {
			case 'Z':
			case 'B':
			case 'S':
			case 'C':
			case 'I': break;
			case 'L': base += 1; break;
			case 'F': base += 2; break;
			case 'D': base += 3; break;
			case 'A': base += 4; break;
			default: throw new NumberFormatException("Value out of range [ILFDA] got " + x);
		}
		return base;
	}

	public static byte X_LOAD(char x) {
		return getValue(x, ILOAD);
	}

	public static NPInsnNode X_RETURN(String x) {
		return NPInsnNode.of(x.isEmpty() ? RETURN : getValue(x.charAt(0), IRETURN));
	}

	public static byte X_RETURN222(String x) {
		return x.isEmpty() ? RETURN : getValue(x.charAt(0), IRETURN);
	}
	@Deprecated
	public static byte X_RETURN222(Type x) {
		return x.shiftedOpcode(IRETURN, true);
	}

	public static byte X_LOAD_I222(char x, int i) {
		if (i < 0 || i > 3) throw new NumberFormatException("i not in [0, 3] : " + i);
		return i_loadStoreSmall222(getValue(x, ILOAD), i);
	}

	public static byte XSTORE_I222(char x, int i) {
		if (i < 0 || i > 3) throw new NumberFormatException("i not in [0, 3] : " + i);
		return i_loadStoreSmall222(getValue(x, ISTORE), i);
	}

	public static byte changeCodeType(int code, Type from, Type to) {
		int flag = OpcodeUtil.flag(code);
		if ((flag&OpcodeUtil.TRAIT_ILFDA) == 0) return (byte) code;

		String name = OpcodeUtil.toString0(code);
		if ((flag&0xF) == OpcodeUtil.CATE_ARRAY_SL)
			return (byte) ((name.endsWith("STORE") ? 33 : 0)+XALoad(to));

		if (from != null && name.charAt(0) != from.nativeName().charAt(0)) return (byte) code;

		CharList sb = IOUtil.getSharedCharBuf().append(name);
		sb.list[0] = to.nativeName().charAt(0);

		int v = OpcodeUtil.getByName().getOrDefault(sb, -1);
		if (v < 0) throw new IllegalStateException("找不到"+sb);
		return (byte) v;
	}

	public static byte i_loadStoreSmall222(byte base, int id) {
		byte c = base >= ISTORE ? ISTORE : ILOAD;
		return (byte) (((base-c) << 2) + c + 5 + id);
	}

	@Nonnull
	public static NPInsnNode i_loadStoreSmall(byte base, int id) {
		return NPInsnNode.of(i_loadStoreSmall222(base, id));
	}

	public static int getVarId(InsnNode node) {
		switch (OpcodeUtil.category(node.code)) {
			case OpcodeUtil.CATE_LOAD_STORE: return getVarId(node.code);
			case OpcodeUtil.CATE_LOAD_STORE_LEN: return ((IIndexInsnNode) node).getIndex();
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

	/**
	 * XLOAD / XRETURN 的前缀
	 */
	public static String XPrefix(Class<?> clazz) {
		if (clazz.isPrimitive()) {
			switch (clazz.getName()) {
				case "int":
				case "char":
				case "byte":
				case "boolean":
				case "short": return "I";
				case "long": return "L";
				case "float": return "F";
				case "double": return "D";
				case "void": return "";
			}
		}
		return "A";
	}

	public static byte Type2PrimitiveArray(int nativeType) {
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

	public static byte PrimitiveArray2Type(int id) {
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

	public static void newArray(InsnList list, Type t, int size) {
		if (t.array() == 0) throw new IllegalStateException("不是数组");
		list.ldc(size);
		if (t.array() == 1) {
			switch (t.type) {
				case CLASS:
					list.clazz(ANEWARRAY, t.owner);
					return;
				case BYTE:
				case SHORT:
				case CHAR:
				case BOOLEAN:
				case DOUBLE:
				case INT:
				case FLOAT:
				case LONG:
					list.add(new U1InsnNode(NEWARRAY, Type2PrimitiveArray(t.array())));
					return;
				default:
					list.remove(list.size() - 1);
					throw new IllegalStateException("不支持的参数类型 " + t.type);
			}
		}

		CharList sb = IOUtil.getSharedCharBuf();
		t.toDesc(sb);
		list.clazz(ANEWARRAY, sb.toString(1,sb.length()-1));
	}

	public static void switchString(InsnList list, Map<String, InsnNode> target, InsnNode def) {
		if (target.isEmpty()) {
			list.jump(def);
			return;
		}

		list.one(DUP);
		list.add(new InvokeInsnNode(INVOKESPECIAL, "java/lang/String", "hashCode", "()I"));

		SwitchInsnNode sw = new SwitchInsnNode(LOOKUPSWITCH);
		list.add(sw);
		sw.def = def;

		// check duplicate
		IntMap<List<Map.Entry<String, InsnNode>>> tmp = new IntMap<>(target.size());
		for (Map.Entry<String, InsnNode> entry : target.entrySet()) {
			int hash = entry.getKey().hashCode();
			List<Map.Entry<String, InsnNode>> list1 = tmp.get(hash);
			if (list1 == null) {
				tmp.putInt(hash, list1 = new SimpleList<>(2));
			}
			list1.add(entry);
		}

		for (IntMap.Entry<List<Map.Entry<String, InsnNode>>> entry : tmp.selfEntrySet()) {
			LabelInsnNode pos = new LabelInsnNode();
			sw.targets.add(new SwitchEntry(entry.getIntKey(), pos));
			list.add(pos);
			List<Map.Entry<String, InsnNode>> list1 = entry.getValue();
			for (int i = 0; i < list1.size(); i++) {
				Map.Entry<String, InsnNode> entry1 = list1.get(i);
				list.ldc(entry1.getKey());
				list.invokeD("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
				list.jump(IFNE, entry1.getValue());
			}
			list.jump(def);
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
		c.switches(sw);
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
			sw.targets.add(new SwitchEntry(entry.getIntKey(), pos));
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

	public boolean isNodeSimilar(InsnNode a, InsnNode b) {
		switch (a.nodeType()) {
			default:
			case T_GOTO_IF:
				return a.code == b.code;
			case T_LOAD_STORE:
				return checkId(getVarId(b), getVarId(a));
			case T_SWITCH: {
				if (a.code != b.code) return false;

				SwitchInsnNode sina = (SwitchInsnNode) a;
				SwitchInsnNode sinb = (SwitchInsnNode) b;
				if (sina.targets.size() != sinb.targets.size()) return false;
				List<SwitchEntry> swa = sina.targets;
				List<SwitchEntry> swb = sinb.targets;
				for (int i = 0; i < swa.size(); i++) {
					SwitchEntry ea = swa.get(i);
					SwitchEntry eb = swb.get(i);
					if (ea.key != eb.key) return false;
				}
				return true;
			}
			case T_INVOKE_DYNAMIC: {
				if (a.code != b.code) return false;

				InvokeDynInsnNode ia = (InvokeDynInsnNode) a;
				InvokeDynInsnNode ib = (InvokeDynInsnNode) b;
				if (!ia.name.equals(ib.name)) return false;
				if (!ia.desc.equals(ib.desc)) return false;
				return checkId(-ib.tableIdx, -ia.tableIdx);
			}
			case T_IINC: {
				if (a.code != b.code) return false;

				IncrInsnNode ia = (IncrInsnNode) a;
				IncrInsnNode ib = (IncrInsnNode) b;
				return ia.amount == ib.amount && checkId(ib.variableId, ia.variableId);
			}
			case T_CLASS:
				if (a.code != b.code) return false;

				return eq((ClassInsnNode) a, (ClassInsnNode) b);
			case T_FIELD:
				if (a.code != b.code) return false;

				return eq((FieldInsnNode) a, (FieldInsnNode) b);
			case T_INVOKE:
				if (a.code != b.code) return false;

				return eq((InvokeInsnNode) a, (InvokeInsnNode) b);
			case T_MULTIANEWARRAY:
				if (a.code != b.code) return false;

				return eq((MDArrayInsnNode) a, (MDArrayInsnNode) b);
		}
	}

	public void mapId(InsnList from, InsnList to) {
		Int2IntMap map = varIdReplace;
		for (int i = 0; i < from.size(); i++) {
			InsnNode node = from.get(i);
			switch (node.nodeType()) {
				case T_IINC:
					IncrInsnNode ia = (IncrInsnNode) node;
					ia.variableId = (char) map.getOrDefaultInt(ia.variableId, ia.variableId);
					break;
				case T_LOAD_STORE:
					int id = getVarId(node);
					int code = node.getOpcodeInt();
					if (code >= ILOAD && code <= ALOAD_3) {
						if (code >= ILOAD_0) code = (byte) (((code - ILOAD_0) / 4) + ILOAD);
					} else {
						if (code >= ISTORE_0) code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
					}
					if (map.containsKey(id)) {
						to.var((byte) code, map.get(id));
						node._i_replace(to.get(to.size() - 1));
						continue;
					}
			}
			to.add(node);
		}
	}

	public static boolean eq(MDArrayInsnNode a, MDArrayInsnNode b) {
		if (a == b) return true;
		if (a == null) return false;

		if (a.getIndex() != b.getIndex()) return false;
		return a.owner.equals(b.owner);
	}

	public static boolean eq(IncrInsnNode a, IncrInsnNode b) {
		if (a == b) return true;
		if (a == null) return false;

		if (a.variableId != b.variableId) return false;
		return a.amount == b.amount;
	}

	public static boolean eq(ClassInsnNode a, ClassInsnNode b) {
		if (a == b) return true;
		if (a == null) return false;

		return a.owner.equals(b.owner);
	}

	public static boolean eq(FieldInsnNode a, FieldInsnNode b) {
		if (a == b) return true;
		if (a == null) return false;

		if (!a.rawType.equals(b.rawType)) return false;
		return a.owner.equals(b.owner);
	}

	public static boolean eq(InvokeInsnNode a, InvokeInsnNode b) {
		if (a == b) return true;
		if (a == null) return false;

		if (!a.name.equals(b.name)) return false;
		if (!a.desc.equals(b.desc)) return false;
		return a.owner.equals(b.owner);
	}

	public static boolean eq(InvokeDynInsnNode a, InvokeDynInsnNode b) {
		if (a == b) return true;
		if (a == null) return false;

		if (!a.name.equals(b.name)) return false;
		if (!a.desc.equals(b.desc)) return false;
		return a.tableIdx == b.tableIdx;
	}
}
