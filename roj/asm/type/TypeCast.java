package roj.asm.type;

import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.util.AccessFlag;
import roj.collect.Int2IntMap;
import roj.collect.LFUCache;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static roj.asm.type.Generic.ANY_TYPE;
import static roj.asm.type.Generic.EMPTY_TYPE;
import static roj.asm.type.Generic.GENERIC_SUBCLASS_TYPE;
import static roj.asm.type.Generic.GENERIC_TYPE;
import static roj.asm.type.Generic.STANDARD_TYPE;
import static roj.asm.type.Generic.TYPE_PARAMETER_TYPE;
import static roj.asm.type.Generic.*;
import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2023/5/18 0018 11:11
 */
public final class TypeCast {
	// 对泛型只会返回0或1, 然后返回的是最小(差)的结果, 通过控制下面数字的顺序
	public static final int RAWTYPES = 0, UPCAST = 1, NUMBER_UPCAST = 2, BOXING = 3, UNBOXING = 4;
	public static final int E_CAST = -1, E_OBJ2INT = -2, E_INT2OBJ = -3, E_GEN_PARAM_COUNT = -4, E_NODATA = -5, E_NEVER = -6;

	public int type;
	public String error;
	public byte opcode;

	private TypeCast(int i, byte opc) {
		type = i;
		opcode = opc;

		String s;
		switch (i) {
			case UPCAST: s = "可以转换到"; break;
			case NUMBER_UPCAST: s = "向上转型到"; break;
			case BOXING: s = "装箱到"; break;
			case UNBOXING: s = "拆箱到"; break;
			case RAWTYPES: s = "Rawtypes"; break;
			case E_CAST: s = "强转到"; break;
			default:
			case E_NEVER: s = "无法转换到"; break;
			case E_OBJ2INT: s = "无法转换为基本类型"; break;
			case E_INT2OBJ: s = "无法转换自基本类型"; break;
			case E_NODATA: s = "数据不足"; break;
			case E_GEN_PARAM_COUNT: s = "泛型参数不同"; break;
		}
		error = s;
	}

	private static TypeCast RESULT(int i) { return new TypeCast(i, (byte) 0); }
	private static TypeCast RESULT(int i, byte b) { return new TypeCast(i, b); }

	private static final LFUCache<CharSequence, TypeCast> ClassConvertCache = new LFUCache<>(999);
	private static final Int2IntMap DataCap;
	static {
		DataCap = new Int2IntMap(8);
		DataCap.putInt(BYTE, 1);
		DataCap.putInt(SHORT, 2);
		DataCap.putInt(CHAR, 3);
		DataCap.putInt(INT, 4);
		DataCap.putInt(LONG, 5);
		DataCap.putInt(FLOAT, 6);
		DataCap.putInt(DOUBLE, 7);
	}

	public static TypeCast canUpCastTo(IType from, IType to,
									   Function<CharSequence, IClass> klassEnv, Function<CharSequence, List<IType>> genericEnv) {
		return canUpCastTo(from, to, klassEnv, genericEnv, -1);
	}
	public static TypeCast canUpCastTo(IType from, IType to,
									   Function<CharSequence, IClass> klassEnv, Function<CharSequence, List<IType>> genericEnv,
									   int in_generic) {

		if (from.equals(to)) return RESULT(UPCAST);

		List<IType> to_list;
		switch (to.genericType()) {
			case STANDARD_TYPE: to_list = Collections.singletonList(to); break;
			case TYPE_PARAMETER_TYPE: to_list = check_list_subs(genericEnv.apply(((TypeParam) to).name)); break;
			case EMPTY_TYPE: return from.rawType().isPrimitive() ? RESULT(E_INT2OBJ) : RESULT(UPCAST);
			case ANY_TYPE: return RESULT(UPCAST);

			default:
			case GENERIC_SUBCLASS_TYPE: throw new UnsupportedOperationException();

			case GENERIC_TYPE:
				Generic gg = ((Generic) to);
				to_list = Collections.singletonList(gg.sub != null ? composite_sub(gg) : to);
				break;
		}

		List<IType> from_list;
		switch (from.genericType()) {
			case STANDARD_TYPE: from_list = Collections.singletonList(from); break;
			case TYPE_PARAMETER_TYPE: from_list = check_list_subs(genericEnv.apply(((TypeParam) from).name)); break;
			case EMPTY_TYPE: return to_list.get(0).rawType().getActualClass().equals("java/lang/Object") ? RESULT(UPCAST) : RESULT(E_CAST);
			case ANY_TYPE: return RESULT(E_CAST); // *

			default:
			case GENERIC_SUBCLASS_TYPE: throw new UnsupportedOperationException();

			case GENERIC_TYPE:
				Generic gg = ((Generic) from);
				from_list = Collections.singletonList(gg.sub != null ? composite_sub(gg) : from);
				break;
		}

		TypeCast res = null;
		for (int i = 0; i < from_list.size(); i++) {
			IType f = from_list.get(i);
			for (int j = 0; j < to_list.size(); j++) {
				IType t = to_list.get(j);

				TypeCast v = canUpCastTo(f.rawType(),t.rawType(),klassEnv,t instanceof Generic ? ((Generic) t).extendType : in_generic);
				if (res == null || v.type < res.type) res = v;

				if (f.genericType() == GENERIC_TYPE) {
					if (t.genericType() == GENERIC_TYPE) {
						v = canUpCastToEx((Generic)f,(Generic)t, klassEnv, genericEnv);
					} else {
						v = ((Generic)f).children.isEmpty() ? RESULT(UPCAST) : RESULT(RAWTYPES);
					}
				} else if (t.genericType() == GENERIC_TYPE) {
					v = ((Generic)t).children.isEmpty() ? RESULT(UPCAST) : RESULT(RAWTYPES);
				}

				if (res == null || v.type < res.type) res = v;
			}
		}

		return res;
	}
	private static List<IType> check_list_subs(List<IType> to_list) {
		for (int i = 0; i < to_list.size(); i++) {
			IType T = to_list.get(i);
			if (T.genericType() == 1 && ((Generic) T).sub != null) {
				to_list = new SimpleList<>(to_list);
				to_list.set(i++, composite_sub((Generic) T));

				for (; i < to_list.size(); i++) {
					T = to_list.get(i);
					if (T.genericType() == 1 && ((Generic) T).sub != null) {
						to_list.set(i, composite_sub((Generic) T));
					}
				}
				break;
			}
		}
		return to_list;
	}
	private static IType composite_sub(Generic gg) {
		CharList sb = IOUtil.ddLayeredCharBuf();

		sb.append(gg.owner);

		boolean copy = false;
		Generic gg1 = new Generic();
		gg1.setArrayDim(gg.array());
		gg1.children = gg.children;
		gg1.extendType = gg.extendType;

		GenericSub sub = gg.sub;
		while (true) {
			sb.append('$').append(sub.owner);
			if (!sub.children.isEmpty()) {
				if (!copy) {
					// just a hack to check validaty
					gg1.children = new SimpleList<>(gg1.children);
					copy = true;
				}
				gg1.children.addAll(sub.children);
			}

			if (sub.sub == null) break;
			sub = sub.sub;
		}

		gg1.owner = sb.toStringAndFree();

		return gg1;
	}

	public static TypeCast canUpCastTo(Type from, Type to,
									   Function<CharSequence, IClass> klassEnv, int inheritType) {
		if (from.equals(to)) return RESULT(UPCAST);

		if (from.isPrimitive()) {
			// 泛型
			if (inheritType >= 0) return RESULT(E_NEVER);
			// 装箱
			if (!to.isPrimitive()) return isWrapperFor(to, from.type) ? RESULT(BOXING) : RESULT(E_INT2OBJ);

			int fCap = DataCap.getOrDefaultInt(from.type, 0);


			// 皆为基本
			if (to.type == CHAR) { // char不接受upcast
				// 不可能,不然equals了
				// if (from.type == CHAR) return RESULT(UPCAST);

				return RESULT(E_CAST);
			} else { // 容量的upcast
				int tCap = DataCap.getOrDefaultInt(to.type, 0);

				// 0是boolean
				if (fCap > tCap) return fCap == 0 ? RESULT(E_NEVER) : RESULT(E_CAST);

				// 相同或者都是int (fCap不可能等于tCap,理由同上)
				if (/*fCap == tCap || */tCap <= 3) return RESULT(UPCAST);

				//	I2L = 0x85, I2F = 0x86, I2D = 0x87,
				//	            L2F = 0x89, L2D = 0x8A,
				//	                        F2D = 0x8D,
				//switch (fCap) {
				//	case 3: opc = tCap-4; // 2L 2F 2D
				//	case 4: opc = tCap-1; // 2F 2D
				//	case 5: opc = tCap+2; // 2D
				//}
				return RESULT(NUMBER_UPCAST, (byte) (Opcodes.I2L + (tCap-1 + (fCap-4)*3)));
			}
		} else if (to.isPrimitive()) {
			// 泛型
			if (inheritType >= 0) return RESULT(E_NEVER);
			// 拆箱
			return isWrapperFor(from, to.type) ? RESULT(UNBOXING) : RESULT(E_OBJ2INT);
		}
		// 皆非

		// 数组继承
		if (from.array() != 0) {
			int delta = from.array() - to.array();
			if (delta < 0) { // 转成更高维度
				if (from.type != CLASS) return RESULT(E_NEVER); // 基本类型不可能
				return RESULT(E_CAST); // 对象试试checkcast
			}
			if (delta > 0) {
				//int[]      [][] ff = Helpers.nonnull();
				//Object     [][] tt = ff;

				String s = inheritType == EX_SUPER ? from.owner : to.owner;

				if (s == null) return RESULT(E_NEVER); // tt是基本类型数组

				if (s.equals("java/lang/Object") ||
					s.equals("java/lang/Cloneable"))
					return RESULT(UPCAST); // 上图的情况, int[] => Object

				return RESULT(E_NEVER); // int[] 除了转换成Object或Cloneable不能变成任何其他东西
			} else {
				if (from.type != to.type) return RESULT(E_NEVER);
				if (from.type != CLASS) return RESULT(UPCAST); // 维度相同的基本类型数组
				// 判断下能否继承
			}
		} else if (to.array() != 0) {
			// object到数组
			return RESULT(E_CAST);
		}

		// 类继承
		// 到这里: f.array == t.array
		if (inheritType == EX_NONE) return from.owner.equals(to.owner) ? RESULT(UPCAST) : RESULT(E_NEVER);
		if (inheritType == EX_SUPER) {
			Type tmp = from;
			from = to;
			to = tmp;
		}

		CharList key = IOUtil.ddLayeredCharBuf().append(from.owner).append(';').append(to.owner);
		TypeCast v = ClassConvertCache.get(key);
		if (v == null) {
			v = checkInheritable(from, to, klassEnv);
			ClassConvertCache.put(key.toStringAndFree(), v);
		} else {
			key._free();
		}
		return v;
	}
	private static TypeCast checkInheritable(Type from, Type to, Function<CharSequence, IClass> klassEnv) {
		IClass fromClass = klassEnv.apply(from.owner);
		IClass toClass = klassEnv.apply(to.owner);

		if (fromClass == null) return RESULT(E_NODATA);
		if (toClass == null) return RESULT(E_NODATA);

		if (fromClass == toClass) return RESULT(UPCAST);

		if ((toClass.modifier() & AccessFlag.FINAL) == 0) {
			// 接口
			boolean itf = (toClass.modifier() & AccessFlag.INTERFACE) != 0;
			if (itf) {
				if (fromClass.interfaces().contains(to.owner)) return RESULT(UPCAST);
			}

			String parent = fromClass.parent();
			while (parent != null) {
				IClass tmp = klassEnv.apply(parent);
				if (tmp == null) return RESULT(E_NODATA);

				if (itf ?
					tmp.interfaces().contains(to.owner) :
					tmp.equals(toClass)) return RESULT(UPCAST);

				parent = tmp.parent();
			}
		}

		return (fromClass.modifier() & AccessFlag.FINAL) == 0 ? RESULT(E_CAST) : RESULT(E_NEVER);
	}
	private static boolean isWrapperFor(Type self, int type) {
		if (self.array() == 0 && self.owner != null) {
			switch (self.owner) {
				case "java/lang/Character": return type == CHAR;
				case "java/lang/Byte": return type == BYTE;
				case "java/lang/Short": return type == SHORT;
				case "java/lang/Integer": return type == INT;
				case "java/lang/Long": return type == LONG;
				case "java/lang/Float": return type == FLOAT;
				case "java/lang/Double": return type == DOUBLE;
				case "java/lang/Void": return type == VOID;
			}
		}
		return false;
	}

	private static TypeCast canUpCastToEx(Generic from, Generic to,
										  Function<CharSequence, IClass> klassEnv, Function<CharSequence, List<IType>> genericEnv) {
		List<IType> fc = from.children;
		List<IType> tc = to.children;
		if (fc.size() != tc.size()) return RESULT(E_GEN_PARAM_COUNT);

		TypeCast res = null;
		for (int i = 0; i < fc.size(); i++) {
			TypeCast v = canUpCastTo(fc.get(i), tc.get(i), klassEnv, genericEnv, EX_EXTENDS);
			if (res == null || v.type < res.type) res = v;
		}
		return res;
	}
}
