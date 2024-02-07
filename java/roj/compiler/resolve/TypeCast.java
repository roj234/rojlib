package roj.compiler.resolve;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.*;
import roj.asm.visitor.CodeWriter;
import roj.collect.Int2IntMap;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.compiler.CompilerConfig;
import roj.compiler.asm.Asterisk;
import roj.compiler.context.ClassContext;
import roj.concurrent.OperationDone;
import roj.text.CharList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static roj.asm.type.Generic.ANY_TYPE;
import static roj.asm.type.Generic.ASTERISK_TYPE;
import static roj.asm.type.Generic.CONCRETE_ASTERISK_TYPE;
import static roj.asm.type.Generic.GENERIC_TYPE;
import static roj.asm.type.Generic.STANDARD_TYPE;
import static roj.asm.type.Generic.TYPE_PARAMETER_TYPE;
import static roj.asm.type.Generic.*;
import static roj.asm.type.Type.*;

/**
 * 类型转换
 * @author Roj234
 * @since 2023/5/18 0018 11:11
 */
public class TypeCast {
	// 成功: 返回总distance最小的结果
	// 失败: 返回的type最小(差)的结果
	// 泛型成功只返回0

	// 成功
	public static final int UPCAST = 0, NUMBER_UPCAST = 1, UNBOXING = 2, BOXING = 3;
	// 失败
	public static final int E_EXPLICIT_CAST = -1, E_NUMBER_DOWNCAST = -2, E_DOWNCAST = -3;
	// 致命错误
	public static final int E_OBJ2INT = -4, E_INT2OBJ = -5, E_GEN_PARAM_COUNT = -6, E_NODATA = -7, E_NEVER = -8;
	private static final int DISTANCE_BOXING = 1;

	public static final class Cast {
		public int type;
		public int distance;
		//public boolean rawType;
		byte op1, op2, box;
		Type type1;

		Cast(int t, int d) { type = t; distance = d; }

		public boolean isNoop() { return op1 == op2 && type1 == null; }
		public Type getType1() { return type1; }
		public byte getOp1() { return op1; }
		public byte getOp2() { return op2; }

		public String getCastDesc() {
			return switch (type) {
				case UPCAST -> "可以转换到";
				case NUMBER_UPCAST -> "向上转型到";
				case BOXING -> "装箱到";
				case UNBOXING -> "拆箱到";
				case E_EXPLICIT_CAST, E_NUMBER_DOWNCAST, E_DOWNCAST -> "强转到";
				default -> "无法转换到";
				case E_OBJ2INT -> "无法转换为基本类型";
				case E_INT2OBJ -> "无法转换自基本类型";
				case E_NODATA -> "数据不足";
				case E_GEN_PARAM_COUNT -> "泛型参数不同";
			};
		}

		@SuppressWarnings("fallthrough")
		public void write(CodeWriter cw) {
			switch (type) {
				default: throw new UnsupportedOperationException(getCastDesc()+"无法生成字节码");
				case NUMBER_UPCAST, E_EXPLICIT_CAST, E_NUMBER_DOWNCAST: break;
				case BOXING:
					Type wrapper = WRAPPER.get(box);
					cw.invoke(Opcodes.INVOKESTATIC, wrapper.owner, "valueOf", "("+(char)box+")L"+wrapper.owner+";");
				break;
				case UNBOXING:
					cast(cw);
					cw.invoke(Opcodes.INVOKEVIRTUAL, WRAPPER.get(box).owner, std(box).toString().concat("Value"), "()"+(char)box);
				break;
				case UPCAST, E_DOWNCAST: cast(cw); break;
			}

			if (op1 != 0) {
				cw.one(op1);
				if (op2 != 0) cw.one(op2);
			}
		}

		private void cast(CodeWriter cw) {
			if (type1 != null) cw.clazz(Opcodes.CHECKCAST, type1.getActualClass());
		}
	}

	// region 'macro'
	public static Cast RESULT(int type, int distance) { return new Cast(type, distance); }
	private static final Cast[] SOLID = new Cast[6];
	static {
		for (int i = E_NEVER; i <= E_DOWNCAST; i++) {
			SOLID[i-E_NEVER] = new Cast(i, -1); // -1 : not applicable
		}
	}
	private static Cast ERROR(@Range(from = E_NEVER, to = E_DOWNCAST) int type) { return SOLID[type-E_NEVER]; }
	private static Cast DOWNCAST(Type type) {
		Cast cast = new Cast(E_DOWNCAST, -1);
		cast.type1 = type;
		return cast; }
	private static Cast NUMBER(int type, int distance, int op1) {
		Cast cast = new Cast(type, distance);
		cast.op1 = (byte) op1;
		return cast; }
	private static Cast NUMBER_DOWNCAST(int distance, int op1, int op2) {
		Cast cast = new Cast(E_NUMBER_DOWNCAST, distance);
		cast.op1 = (byte) op1;
		cast.op2 = (byte) op2;
		return cast; }
	// endregion

	public ClassContext context;
	public Function<String, List<IType>> genericResolver;

	public Cast checkCast(IType from, IType to) { return checkCast(from, to, -1); }
	private Cast checkCast(IType from, IType to, int etype) {
		if (from.equals(to)) return RESULT(UPCAST, 0);

		boolean isTypeParam = false;
		//noinspection all
		switch (to.genericType()) {
			default: throw OperationDone.NEVER;
			case STANDARD_TYPE: break;
			case GENERIC_TYPE:
				Generic gg = (Generic) to;
				to = gg.sub != null ? mergeSubClass(gg) : gg;
			break;

			case TYPE_PARAMETER_TYPE: isTypeParam = true; break;
			case ANY_TYPE: return RESULT(UPCAST, 0); // *
			case ASTERISK_TYPE: return ERROR(E_NEVER); // 不能从"某个"具体的类转到"任何"具体的类
			case CONCRETE_ASTERISK_TYPE: to = ((Asterisk) to).getBound(); break;
		}

		Asterisk asterisk = null;
		//noinspection all
		switch (from.genericType()) {
			default: throw OperationDone.NEVER;
			case STANDARD_TYPE: break;
			case GENERIC_TYPE:
				Generic gg = (Generic) from;
				from = gg.sub != null ? mergeSubClass(gg) : gg;
			break;

			case TYPE_PARAMETER_TYPE: isTypeParam = true; break;
			case ANY_TYPE: if (!isTypeParam) return DOWNCAST(to.rawType()); break;
			case ASTERISK_TYPE:
				asterisk = (Asterisk) from;
				if (asterisk.getBound() == null) return to.isPrimitive() ? ERROR(E_OBJ2INT) : RESULT(UPCAST, 0);

				from = to;
				to = asterisk.getBound();
			break;
			case CONCRETE_ASTERISK_TYPE:
				asterisk = (Asterisk) from;
				from = asterisk.getBound();
			break;
		}

		Cast result = isTypeParam ?
			typeParamCast(from, to, etype) :
			genericCast(from, to, etype);

		if (asterisk != null) {
			List<IType> bounds = asterisk.getBounds();
			if (asterisk.genericType() == CONCRETE_ASTERISK_TYPE) {
				if (genericCast(bounds.get(0), to, etype).type >= 0)
					return result;
				result.type1 = asterisk.getBound().rawType();
			} else {
				for (int i = 1; i < bounds.size(); i++) {
					Cast r = genericCast(from, bounds.get(i), etype);
					if (r.type < 0) return r;
				}

				if (result.type >= UNBOXING) {
					result.type ^= 1;
					result.type1 = WRAPPER.get(from.getActualType());
				} else if (result.distance != 0) {
					result.type1 = from.rawType();
				}
			}
		}
		return result;
	}

	private Cast typeParamCast(IType from, IType to, int etype) {
		List<IType> from_list = getTypeParamBound(from);
		if (from_list == null) return ERROR(E_NODATA);
		List<IType> to_list = getTypeParamBound(to);
		if (to_list == null) return ERROR(E_NODATA);

		Cast res = null;
		for (int i = 0; i < from_list.size(); i++) {
			IType f = from_list.get(i);
			Cast ok = null;

			for (int j = 0; j < to_list.size(); j++) {
				IType t = to_list.get(j);

				Cast v = genericCast(f, t, etype);
				if (v.type >= 0) if (ok == null || v.type < ok.type) ok = v;
				else if (res == null || v.type < res.type) res = v;
			}

			if (ok != null) res = ok;
		}

		assert res != null;
		return res;
	}
	private List<IType> getTypeParamBound(IType type) {
		switch (type.genericType()) {
			default: throw OperationDone.NEVER;
			case STANDARD_TYPE: return Collections.singletonList(type);
			case GENERIC_TYPE:
				Generic gg = (Generic) type;
				return Collections.singletonList(gg.sub != null ? mergeSubClass(gg) : gg);
			case TYPE_PARAMETER_TYPE:
				List<IType> bounds = genericResolver.apply(((TypeParam) type).name);
				if (bounds == null || bounds.isEmpty()) return null;
				return mergeSubClasses(bounds);
		}
	}

	private static List<IType> mergeSubClasses(List<IType> list) {
		for (int i = 0; i < list.size(); i++) {
			IType T = list.get(i);
			if (T.genericType() == 1 && ((Generic) T).sub != null) {
				list = new SimpleList<>(list);
				list.set(i++, mergeSubClass((Generic) T));

				for (; i < list.size(); i++) {
					T = list.get(i);
					if (T.genericType() == 1 && ((Generic) T).sub != null) {
						list.set(i, mergeSubClass((Generic) T));
					}
				}
				break;
			}
		}
		return list;
	}
	private static IType mergeSubClass(Generic gg) {
		CharList sb = new CharList();

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
					// just a hack to check validity
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

	private Cast genericCast(IType from, IType to, int etype) {
		List<IType> fc, tc;

		if (to.genericType() == GENERIC_TYPE) {
			Generic gt = (Generic) to;
			etype = gt.extendType;
			tc = gt.children;
		} else {
			tc = !context.isSpecEnabled(CompilerConfig.ADVANCED_GENERIC_CHECK) ? null : getClassTPB(to);
		}

		Cast r = checkCast(from.rawType(), to.rawType(), etype);

		if (from.genericType() == GENERIC_TYPE) {
			fc = ((Generic)from).children;
		} else {
			fc = !context.isSpecEnabled(CompilerConfig.ADVANCED_GENERIC_CHECK) ? null : getClassTPB(from);
		}

		if (fc != null && tc != null) {
			if (fc.size() == 1 && fc.get(0) == Asterisk.anyGeneric) return r;

			// 计算泛型继承并擦除类型 这里仍然没支持GSub
			if (r.distance > 0) {
				checkTPB:
				try {
					List<IType> tmp;
					if (r.type == UPCAST) {
						IClass info = context.getClassInfo(from.owner());
						tmp = context.getTypeParamOwner(info, to);
						if (tmp == null) break checkTPB;
						// TODO 如果泛型类继承非泛型类，那么tmp可能为空，这时候传个上界？
						fc = clearTypeParams(tmp, info, fc);
					} else if (r.type == E_DOWNCAST) {
						IClass info = context.getClassInfo(to.owner());
						tmp = context.getTypeParamOwner(info, from);
						if (tmp == null) break checkTPB;
						tc = clearTypeParams(tmp, info, tc);
					} else {
						ClassContext.debugLogger().debug("from={},to={}, this cast will fail", from, to);
						return r;
					}
				} catch (ClassNotFoundException e) {
					return ERROR(E_NODATA);
				}
			} else {
				assert r.type == UPCAST;
				//assert fc.size() == tc.size(); // probably not
			}

			if (fc.size() != tc.size()) return ERROR(E_GEN_PARAM_COUNT);

			for (int i = 0; i < fc.size(); i++) {
				Cast v = checkCast(fc.get(i), tc.get(i), EX_EXTENDS);
				if (v.type != UPCAST) return ERROR(E_NEVER);
			}
		} else {
			// 基本类型泛型模板 SimpleList<int>继承自List<Integer>但不是SimpleList的实例
			if (checkPrimitive(tc) || checkPrimitive(fc)) return ERROR(E_NEVER);
		}

		return r;
	}

	private static boolean checkPrimitive(List<IType> fc) {
		if (fc != null) {
			for (int i = 0; i < fc.size(); i++) {
				if (fc.get(i).getActualType() != CLASS) return true;
			}
		}
		return false;
	}

	private List<IType> getClassTPB(IType from) {
		if (from.owner() == null) return null;
		IClass info = context.getClassInfo(from.owner());
		if (info != null) {
			Signature sign = info.parsedAttr(info.cp(), Attribute.SIGNATURE);
			if (sign != null && !sign.typeParams.isEmpty()) {
				List<IType> list = Arrays.asList(new IType[sign.typeParams.size()]);
				int i = 0;
				for (List<IType> value : sign.typeParams.values()) {
					list.set(i++, new Asterisk(value.get(0).genericType() == IType.PLACEHOLDER_TYPE ? value.subList(1, value.size()) : value));
				}
				return list;
			}
		}
		return null;
	}

	private static List<IType> clearTypeParams(List<IType> tps, IClass childTypeInst, List<IType> childType) {
		if (tps.getClass() != SimpleList.class) {
			tps = new SimpleList<>(tps);

			int i = 0;
			Map<String, IType> tpVis = new MyHashMap<>();
			// TODO 这里可能会有多级，那么首先通过self找到自己，然后通过parent找到父亲，然后PutIfAbsent一路往上
			System.out.println(childTypeInst.parsedAttr(childTypeInst.cp(), Attribute.InnerClasses));
			Map<String, List<IType>> tpBound = childTypeInst.parsedAttr(childTypeInst.cp(), Attribute.SIGNATURE).typeParams;
			for (Map.Entry<String, List<IType>> entry : tpBound.entrySet())
				tpVis.put(entry.getKey(), childType.get(i++));

			for (i = 0; i < tps.size(); i++) {
				IType x = tps.get(i);
				tps.set(i, Inferrer.clearTypeParams(x.clone(), tpVis, tpBound));
			}
		}
		return tps;
	}

	public Cast checkCast(Type from, Type to, int inheritType) {
		if (from.equals(to)) return RESULT(UPCAST, 0);

		if (from.isPrimitive()) {
			// 泛型
			if (inheritType >= 0) return ERROR(E_NEVER);
			// 装箱
			if (!to.isPrimitive()) {
				Cast cast = checkCast(WRAPPER.get(from.type), to, inheritType);
				if (cast.type < E_NUMBER_DOWNCAST) return cast;
				assert cast.type <= NUMBER_UPCAST;

				// possible: E_EXPLICIT_CAST E_NUMBER_DOWNCAST UPCAST NUMBER_UPCAST
				cast.type = BOXING;
				cast.distance += DISTANCE_BOXING;
				cast.box = from.type;
				return cast;
			}

			// 皆为基本
			int fCap = DataCap.getOrDefaultInt(from.type, 0);

			int tCap = DataCap.getOrDefaultInt(to.type, 0);
			// downcast
			if (fCap > tCap) {
				// 0是boolean
				if (fCap == 0) return ERROR(E_NEVER);

				int distance = fCap - tCap;
				//	L2I = (byte) 0X88,
				//	F2I = (byte) 0X8B, F2L = (byte) 0X8C,
				//	D2I = (byte) 0X8E, D2L = (byte) 0X8F, D2F = (byte) 0X90,
				if (fCap > 4) {
					int caster1 = (Opcodes.L2I-19) + (Math.max(tCap, 4) + fCap*3);
					if (tCap >= 4) return NUMBER(E_NUMBER_DOWNCAST, distance, caster1);

					//	I2B = (byte) 0X91, I2C = (byte) 0X92, I2S = (byte) 0X93,
					return NUMBER_DOWNCAST(distance, caster1, (Opcodes.I2B-1) + tCap);
				}

				return NUMBER(E_NUMBER_DOWNCAST, distance, (Opcodes.I2B-1) + tCap);
			}

			int distance = tCap - fCap;
			// E_EXPLICIT_CAST => char不能被动向上转型

			// 还在int范围里
			if (tCap <= 4) return RESULT(tCap == 2 ? E_EXPLICIT_CAST : UPCAST, distance);

			//	I2L = 0x85, I2F = 0x86, I2D = 0x87,
			//	            L2F = 0x89, L2D = 0x8A,
			//	                        F2D = 0x8D,
			return NUMBER(fCap == 2 ? E_EXPLICIT_CAST : NUMBER_UPCAST, distance, (Opcodes.I2L-17) + (tCap + Math.max(fCap, 4)*3));
		} else if (to.isPrimitive()) {
			// 泛型
			if (inheritType >= 0) return ERROR(E_NEVER);
			// 拆箱
			int primitive = getWrappedPrimitive(from);
			if (primitive == 0) return ERROR(E_OBJ2INT);

			Cast cast;
			if (primitive == to.type) {
				cast = RESULT(UNBOXING, DISTANCE_BOXING);
			} else {
				//noinspection MagicConstant
				cast = checkCast(std(primitive), to, inheritType);
				if (cast.type < E_NUMBER_DOWNCAST) return cast;
				assert cast.type <= NUMBER_UPCAST;

				cast.type = UNBOXING;
				cast.distance += DISTANCE_BOXING;
			}

			cast.box = to.type;
			return cast;
		}
		// 皆非

		// 反转 基本类型不适用于Generic
		if (inheritType == EX_SUPER) {
			Type tmp = from;
			from = to;
			to = tmp;
		}

		// 数组继承
		int arrayDelta = from.array() - to.array();
		if (arrayDelta < 0) { // 转成更高维度 (包括从Object到)
			if (from.type != CLASS) return ERROR(E_NEVER); // 基本类型不可能

			/*String[] x;
			x = (String[]) (String) null;
			x = (String[]) (Object) null;
			x = (String[]) (Serializable) null;*/
			if (from.owner.equals("java/lang/Object") ||
				ClassContext.anyArray().interfaces().contains(from.owner))
				return DOWNCAST(to);

			return ERROR(E_NEVER);
		}
		if (from.array() != 0) {
			if (arrayDelta > 0) {
				//int[]      [] t1 = null;
				//Object     [] t2 = t1;
				String owner = inheritType == EX_SUPER ? from.owner : to.owner;
				if (owner == null) return ERROR(E_NEVER); // t2是基本类型数组

				if (owner.equals("java/lang/Object")) return RESULT(UPCAST, 2);
				if (ClassContext.anyArray().interfaces().contains(owner)) return RESULT(UPCAST, 1);

				return ERROR(E_NEVER); // int[] 除了转换成数组实现的类不能变成任何其他东西
			} else {
				if (from.type != to.type) return ERROR(E_NEVER);

				// 维度相同的基本类型数组 （然而，若是这种情况，前面equals就会直接返回真）
				// if (from.type != CLASS) return RESULT(UPCAST, 0);

				// 维度相同的对象数组 => 判断能否继承
			}
		}

		// 类继承 此处：f.array == t.array
		// 被注释掉的部分会equals不用重复检测
		// if (inheritType == EX_NONE) return /*from.owner.equals(to.owner) ? RESULT(UPCAST, 0) : */ERROR(E_NEVER);

		return checkInheritable(from, to);
	}
	public Cast checkInheritable(Type from, Type to) {
		IClass fromClass = context.getClassInfo(from.owner);
		IClass toClass = context.getClassInfo(to.owner);

		if (fromClass == null) return ERROR(E_NODATA);
		if (toClass == null) return ERROR(E_NODATA);

		// 本来是不该发生的（前面检测了），但是说不定未来会支持class aliasing呢/doge
		if (fromClass == toClass) return RESULT(UPCAST, 0);

		try {
			int distance = context.parentList(fromClass).getInt(toClass.name())&0xFFFF;
			if (distance != 0xFFFF) return RESULT(UPCAST, distance);
		} catch (ClassNotFoundException e) {
			return ERROR(E_NODATA);
		}

		return (fromClass.modifier() & Opcodes.ACC_FINAL) == 0 ? DOWNCAST(to) : ERROR(E_NEVER);
	}

	private static final Int2IntMap DataCap = new Int2IntMap(8);
	static {
		DataCap.putInt(BOOLEAN, 0);
		DataCap.putInt(BYTE, 1);
		DataCap.putInt(CHAR, 2);
		DataCap.putInt(SHORT, 3);
		DataCap.putInt(INT, 4);
		DataCap.putInt(LONG, 5);
		DataCap.putInt(FLOAT, 6);
		DataCap.putInt(DOUBLE, 7);
	}
	@Range(from = 0, to = 8)
	public static int getDataCap(int type) { return DataCap.getOrDefaultInt(type, 8); }

	private static final IntBiMap<Type> WRAPPER = new IntBiMap<>(9);
	static {
		WRAPPER.putInt(BOOLEAN, new Type("java/lang/Boolean"));
		WRAPPER.putInt(BYTE, new Type("java/lang/Byte"));
		WRAPPER.putInt(CHAR, new Type("java/lang/Character"));
		WRAPPER.putInt(SHORT, new Type("java/lang/Short"));
		WRAPPER.putInt(INT, new Type("java/lang/Integer"));
		WRAPPER.putInt(LONG, new Type("java/lang/Long"));
		WRAPPER.putInt(FLOAT, new Type("java/lang/Float"));
		WRAPPER.putInt(DOUBLE, new Type("java/lang/Double"));
		WRAPPER.putInt(VOID, new Type("java/lang/Void"));
	}
	public static int getWrappedPrimitive(IType self) { return WRAPPER.getValueOrDefault(self, 0); }
	@Nullable
	public static Type getWrapper(IType self) { return WRAPPER.get(self.getActualType()); }
}