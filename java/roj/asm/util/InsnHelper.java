package roj.asm.util;

import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.asm.visitor.XInsnList;
import roj.asm.visitor.XInsnNodeView;
import roj.collect.Int2IntMap;
import roj.io.IOUtil;
import roj.text.CharList;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public class InsnHelper {
	public static byte changeCodeType(int code, Type from, Type to) {
		int flag = flag(code);
		if ((flag&TRAIT_ILFDA) == 0) return (byte) code;

		String name = showOpcode(code);
		if ((flag&0xF) == CATE_ARRAY_SL)
			return (byte) ((name.endsWith("Store") ? 33 : 0)+XALoad(to));

		if (from != null && name.charAt(0) != from.nativeName().charAt(0)) return (byte) code;

		CharList sb = IOUtil.getSharedCharBuf().append(name);
		sb.list[0] = to.nativeName().charAt(0);

		int v = Opcodes.opcodeByName().getOrDefault(sb, -1);
		if (v < 0) throw new IllegalStateException("找不到"+sb);
		return (byte) v;
	}

	public static byte ToPrimitiveArrayId(int nativeType) {
		return switch (nativeType) {
			case 'Z' -> 4;
			case 'C' -> 5;
			case 'F' -> 6;
			case 'D' -> 7;
			case 'B' -> 8;
			case 'S' -> 9;
			case 'I' -> 10;
			case 'J' -> 11;
			default -> throw new IllegalArgumentException(String.valueOf((char) nativeType));
		};
	}

	public static byte FromPrimitiveArrayId(int id) {
		return switch (id) {
			case 4 -> 'Z';
			case 5 -> 'C';
			case 6 -> 'F';
			case 7 -> 'D';
			case 8 -> 'B';
			case 9 -> 'S';
			case 10 -> 'I';
			case 11 -> 'J';
			default -> throw new IllegalStateException("Unknown PrimArrayType " + id);
		};
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

	public static boolean canThrowRuntimeException(byte code) {
		switch (code) {
			// ClassCastException
			case CHECKCAST:
				// NullPointerException
			case ATHROW:
				// NullPointerException
			case MONITORENTER: case MONITOREXIT:
				// NullPointerException, ArrayStoreException
			case IALOAD: case LALOAD: case FALOAD: case DALOAD:
			case AALOAD: case BALOAD: case CALOAD: case SALOAD:
			case IASTORE: case LASTORE: case FASTORE: case DASTORE:
			case AASTORE: case BASTORE: case CASTORE: case SASTORE:
				// NullPointerException
				// NoClassDefFoundError, NoSuchMethodError, AbstractMethodError ...
			case GETFIELD: case PUTFIELD:
			case INVOKEVIRTUAL: case INVOKEINTERFACE: case INVOKESPECIAL:
				// NullPointerException
			case ARRAYLENGTH:
				// NoClassDefFoundError, OutOfMemoryError, NegativeArraySizeException
			case NEW: case NEWARRAY:
			case ANEWARRAY: case MULTIANEWARRAY:
				// NoSuchMethodError
			case INVOKESTATIC: case INVOKEDYNAMIC:
				return true;
			default: return false;
		}
	}

	public static void checkWide(byte code) {
		switch (code) {
			case RET: case IINC:
			case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE:
			case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD: break;
			default: throw new IllegalStateException("Unable wide " + showOpcode(code));
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

	@SuppressWarnings("fallthrough")
	public void mapVarId(XInsnList from) {
		Int2IntMap map = varIdReplace;
		for (XInsnNodeView node : from) {
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
						XInsnNodeView.InsnMod replace = node.replace();
						replace.list.vars((byte) code, map.get(id));
						replace.commit();
					}
					break;
			}
		}
	}
}