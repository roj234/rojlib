package roj.asm.util;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.cst.*;
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
		list.add(NPInsnNode.of(DUP));
		list.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", new Type("java/lang/PrintStream")));
		list.add(NPInsnNode.of(SWAP));
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

	public static InsnNode loadInt(int number) {
		switch (number) {
			case -1: case 0: case 1: case 2: case 3: case 4: case 5:
				return NPInsnNode.of((byte) (ICONST_0 + number));
			default:
				if ((byte) number == number) {
					return new U1InsnNode(BIPUSH, number);
				}
				if ((short) number == number) {
					return new U2InsnNode(SIPUSH, number);
				}
				return new LdcInsnNode(LDC, new CstInt(number));
		}
	}

	public static void loadLongSlow(long number, InsnList target) {
		switch ((int) number) {
			case 0: case 1:
				target.add(NPInsnNode.of((byte) (LCONST_0 + number)));
				break;
			case -1: case 2: case 3: case 4: case 5:
				target.add(NPInsnNode.of((byte) (ICONST_0 + number)));
				target.add(NPInsnNode.of(I2L));
				break;
			default:
				if ((byte) number == number) {
					target.add(new U2InsnNode(BIPUSH, (int) number));
					target.add(NPInsnNode.of(I2L));
				} else if ((short) number == number) {
					target.add(new U2InsnNode(SIPUSH, (int) number));
					target.add(NPInsnNode.of(I2L));
				} else if ((int) number == number) {
					target.add(new LdcInsnNode(LDC, new CstInt((int) number)));
					target.add(NPInsnNode.of(I2L));
				} else {
					target.add(new LdcInsnNode(LDC2_W, new CstLong(number)));
				}
		}
	}

	public static InsnNode loadLong(long number) {
		switch ((int) number) {
			case 0: case 1: return NPInsnNode.of((byte) (LCONST_0 + number));
			default: return new LdcInsnNode(LDC2_W, new CstLong(number));
		}
	}

	public static void loadFloatSlow(float number, InsnList target) {
		int n = (int) number;
		if (number != n) {
			target.add(new LdcInsnNode(LDC, new CstFloat(number)));
			return;
		}
		switch (n) {
			case 0: case 1: case 2:
				target.add(NPInsnNode.of((byte) (FCONST_0 + number)));
				break;
			case -1: case 3: case 4: case 5:
				target.add(NPInsnNode.of((byte) (ICONST_0 + number)));
				target.add(NPInsnNode.of(I2F));
				break;
			default:
				if ((byte) n == n) {
					target.add(new U2InsnNode(BIPUSH, n));
					target.add(NPInsnNode.of(I2F));
				} /*else if((short)n == n) {
                    target.add(new U2InsnNode(SIPUSH, n));
                    target.add(of(I2F));
                } */ else {
					target.add(new LdcInsnNode(LDC, new CstFloat(number)));
				}
		}
	}

	public static InsnNode loadFloat(float number) {
		int n = (int) number;
		if (number != n || n < 0 || n > 2) {
			return new LdcInsnNode(LDC, new CstFloat(number));
		}
		return NPInsnNode.of((byte) (FCONST_0 + number));
	}

	public static void loadDoubleSlow(double number, InsnList target) {
		int n = (int) number;
		if (number != n) {
			target.add(new LdcInsnNode(LDC2_W, new CstDouble(number)));
			return;
		}
		switch (n) {
			case 0: case 1:
				target.add(NPInsnNode.of((byte) (DCONST_0 + number)));
				break;
			case -1: case 2: case 3: case 4: case 5:
				target.add(NPInsnNode.of((byte) (ICONST_0 + number)));
				target.add(NPInsnNode.of(I2D));
				break;
			default:
				if ((byte) n == n) {
					target.add(new U2InsnNode(BIPUSH, n));
				} else if ((short) n == n) {
					target.add(new U2InsnNode(SIPUSH, n));
				} else {
					//target.add(new LdcInsnNode(LDC, new CstInt(n)));
					target.add(new LdcInsnNode(LDC2_W, new CstDouble(number)));
					return;
				}
				target.add(NPInsnNode.of(I2D));
		}
	}

	public static InsnNode loadDouble(double number) {
		int n = (int) number;
		if (number != n || n < 0 || n > 1) {
			return new LdcInsnNode(LDC2_W, new CstDouble(number));
		}
		return NPInsnNode.of((byte) (DCONST_0 + number));
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

	public static void compress(@Nonnull InsnList list, byte base, int id) {
		switch (base) {
			case ALOAD: case DLOAD: case ILOAD: case FLOAD: case LLOAD:
			case ISTORE: case FSTORE: case LSTORE:
			case DSTORE: case ASTORE:
				if (id <= 3) {
					list.add(i_loadStoreSmall(base, id));
				} else if (id <= 255) {
					list.add(new U1InsnNode(base, id));
				} else if (id <= 65535) {
					list.add(NPInsnNode.of(Opcodes.WIDE));
					list.add(new U2InsnNode(base, id));
				} else {
					throw new IllegalArgumentException("No more thad 65535 types!");
				}
				break;
			default:
				throw new IllegalArgumentException("Unsupported base 0x" + Integer.toHexString(base));
		}
	}

	@Deprecated
	public static void compress(CodeWriter cw, byte base, int id) {
		cw.var(base, id);
	}

	public static byte i_loadStoreSmall222(byte base, int id) {
		return(byte) ((base <= ALOAD ? ((base - ILOAD)*4 + ILOAD_0) : ((base - ISTORE)*4 + ISTORE_0)) + id);
	}

	@Nonnull
	public static NPInsnNode i_loadStoreSmall(byte base, int id) {
		return NPInsnNode.of(i_loadStoreSmall222(base, id));
	}

	public static int getVarId(InsnNode node) {
		String name = OpcodeUtil.toString0(node.code);

		int vid = name.charAt(name.length()-1) - '0';
		if (vid >= 0 && vid <= 3) return vid;

		if ((OpcodeUtil.trait(node.code)&OpcodeUtil.TRAIT_LOAD_STORE_LEN) != 0)
			return ((IIndexInsnNode) node).getIndex();

		return -1;
	}

	public static InsnNode decompress(InsnNode node) {
		switch (node.getOpcode()) {
			case ASTORE_0:
			case ASTORE_1:
			case ASTORE_2:
			case ASTORE_3:
				return new U1InsnNode(ASTORE, (node.getOpcode() - ASTORE_0));

			case FSTORE_0:
			case FSTORE_1:
			case FSTORE_2:
			case FSTORE_3:
				return new U1InsnNode(FSTORE, (node.getOpcode() - FSTORE_0));

			case ISTORE_0:
			case ISTORE_1:
			case ISTORE_2:
			case ISTORE_3:
				return new U1InsnNode(ISTORE, (node.getOpcode() - ISTORE_0));

			case DSTORE_0:
			case DSTORE_1:
			case DSTORE_2:
			case DSTORE_3:
				return new U1InsnNode(DSTORE, (node.getOpcode() - DSTORE_0));

			case LSTORE_0:
			case LSTORE_1:
			case LSTORE_2:
			case LSTORE_3:
				return new U1InsnNode(LSTORE, (node.getOpcode() - LSTORE_0));

			case ALOAD_0:
			case ALOAD_1:
			case ALOAD_2:
			case ALOAD_3:
				return new U1InsnNode(ALOAD, (node.getOpcode() - ALOAD_0));

			case FLOAD_0:
			case FLOAD_1:
			case FLOAD_2:
			case FLOAD_3:
				return new U1InsnNode(FLOAD, (node.getOpcode() - FLOAD_0));

			case ILOAD_0:
			case ILOAD_1:
			case ILOAD_2:
			case ILOAD_3:
				return new U1InsnNode(ILOAD, (node.getOpcode() - ILOAD_0));

			case DLOAD_0:
			case DLOAD_1:
			case DLOAD_2:
			case DLOAD_3:
				return new U1InsnNode(DLOAD, (node.getOpcode() - DLOAD_0));

			case LLOAD_0:
			case LLOAD_1:
			case LLOAD_2:
			case LLOAD_3:
				return new U1InsnNode(LLOAD, (node.getOpcode() - LLOAD_0));

			case ICONST_0:
			case ICONST_1:
			case ICONST_2:
			case ICONST_3:
			case ICONST_4:
			case ICONST_5:
			case ICONST_M1:
				return new LdcInsnNode(LDC, new CstInt(node.getOpcode() - ICONST_0));

			case LCONST_0:
			case LCONST_1:
				return new LdcInsnNode(Opcodes.LDC2_W, new CstLong(node.getOpcode() - LCONST_0));

			case FCONST_0:
			case FCONST_1:
			case FCONST_2:
				return new LdcInsnNode(Opcodes.LDC, new CstFloat(node.getOpcode() - FCONST_0));

			case DCONST_0:
			case DCONST_1:
				return new LdcInsnNode(Opcodes.LDC2_W, new CstDouble(node.getOpcode() - DCONST_0));

			case BIPUSH:
			case SIPUSH:
				return new LdcInsnNode(Opcodes.LDC, new CstInt(((IIndexInsnNode) node).getIndex()));

			case ALOAD:
			case DLOAD:
			case FLOAD:
			case LLOAD:
			case ILOAD:

			case ASTORE:
			case FSTORE:
			case ISTORE:
			case DSTORE:
			case LSTORE:
			default:
				return node;
		}
	}

	public static boolean isReturn(int code) {
		code &= 0xFF;
		return code >= 0xAC && code <= 0xB1;
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
		list.add(loadInt(size));
		if (t.array() == 1) {
			switch (t.type) {
				case CLASS:
					list.add(new ClassInsnNode(ANEWARRAY, t.owner));
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
		list.add(new ClassInsnNode(ANEWARRAY, sb.toString(1,sb.length()-1)));
	}

	public static void switchString(InsnList list, Map<String, InsnNode> target, InsnNode def) {
		if (target.isEmpty()) {
			list.add(new JumpInsnNode(def));
			return;
		}

		list.add(NPInsnNode.of(DUP));
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
			sw.branches.add(new SwitchEntry(entry.getIntKey(), pos));
			list.add(pos);
			List<Map.Entry<String, InsnNode>> list1 = entry.getValue();
			for (int i = 0; i < list1.size(); i++) {
				Map.Entry<String, InsnNode> entry1 = list1.get(i);
				list.add(new LdcInsnNode(LDC, new CstString(entry1.getKey())));
				list.add(new InvokeInsnNode(INVOKESPECIAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
				list.add(new JumpInsnNode(IFNE, entry1.getValue()));
			}
			list.add(new JumpInsnNode(def));
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
				c.ldc(new CstString(entry1.getKey()));
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
			case T_OTHER:
			case T_GOTO_IF:
				return a.code == b.code;
			case T_LOAD_STORE:
				return checkId(getVarId(b), getVarId(a));
			case T_SWITCH: {
				if (a.code != b.code) return false;

				SwitchInsnNode sina = (SwitchInsnNode) a;
				SwitchInsnNode sinb = (SwitchInsnNode) b;
				if (sina.branches.size() != sinb.branches.size()) return false;
				List<SwitchEntry> swa = sina.branches;
				List<SwitchEntry> swb = sinb.branches;
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
				if (!ia.rawDesc().equals(ib.rawDesc())) return false;
				return checkId(-ib.tableIdx, -ia.tableIdx);
			}
			case T_IINC: {
				if (a.code != b.code) return false;

				IncrInsnNode ia = (IncrInsnNode) a;
				IncrInsnNode ib = (IncrInsnNode) b;
				return checkId(ib.variableId, ia.variableId) && ia.amount == ib.amount;
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
		return false;
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
						compress(to, (byte) code, map.get(id));
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
		if (!a.rawDesc().equals(b.rawDesc())) return false;
		return a.owner.equals(b.owner);
	}

	public static boolean eq(InvokeDynInsnNode a, InvokeDynInsnNode b) {
		if (a == b) return true;
		if (a == null) return false;

		if (!a.name.equals(b.name)) return false;
		if (!a.rawDesc().equals(b.rawDesc())) return false;
		return a.tableIdx == b.tableIdx;
	}
}
